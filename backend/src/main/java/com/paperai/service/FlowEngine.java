package com.paperai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperai.agent.*;
import com.paperai.agent.base.BaseAgent;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.entity.FlowDefinition;
import com.paperai.model.entity.Task;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import com.paperai.common.Constants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 动态流程执行引擎
 * 线程安全：每个 execute() 调用创建独立的 ExecState，不共享实例字段。
 */
@Slf4j
@Service
public class FlowEngine {

    @Resource private SupervisorAgent supervisorAgent;
    @Resource private ResearcherAgent researcherAgent;
    @Resource private WriterAgent writerAgent;
    @Resource private ReviewerAgent reviewerAgent;
    @Resource private PolisherAgent polisherAgent;
    @Resource private PaperService paperService;
    @Resource private AgentTaskService agentTaskService;
    @Resource private StepEventPublisher stepEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<Long, Boolean> runningTasks = new ConcurrentHashMap<>();

    // ===== 图结构（parseGraph 产出） =====
    private record GraphData(
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges,
            Map<String, List<String>> forwardOut,   // nodeId -> [targetId]
            Map<String, List<String>> loopOut,       // nodeId -> [targetId] (回退边)
            Map<String, Map<String, Object>> nodeById,
            Map<String, Map<String, Object>> edgeBySrcType // "src->target:conditionType" -> edge
    ) {}

    // ===== 执行状态（每 execute() 独立创建） =====
    private static class ExecState {
        final Long paperId;
        final AgentContext ctx;
        int stepSeq;
        final StringBuilder finalContent = new StringBuilder();
        final Map<String, Integer> loopCounter = new HashMap<>();
        final Set<String> completed = new HashSet<>();
        final int maxSteps = 50;
        String lastAgentNodeId;

        ExecState(Long paperId, String contextId, String topic) {
            this.paperId = paperId;
            this.ctx = new AgentContext(contextId, topic);
        }
    }

    public void stop(Long paperId) {
        runningTasks.put(paperId, true);
    }

    public void execute(Long paperId, FlowDefinition def, PaperWritingRequestDTO req) {
        ExecState s = new ExecState(paperId, UUID.randomUUID().toString(), req.getTopic());
        s.ctx.setAttribute("direction", req.getDescription() != null ? req.getDescription() : "");
        runningTasks.put(paperId, false);

        String name = def.getName() != null ? def.getName() : "未知流程";
        log.info("===== FlowEngine 开始 paperId={}, flowName={} =====", paperId, name);
        try {
            String gd = def.getGraphData();
            if (gd == null || gd.isBlank()) {
                throw new IllegalArgumentException("流程 graphData 为空");
            }
            GraphData g = parseGraph(gd);
            paperService.getPaperById(paperId);
            executeDAG(s, g);
            finish(s, g);
        } catch (Exception e) {
            if (runningTasks.getOrDefault(paperId, false)) {
                log.info("任务被停止 paperId={}", paperId);
                stepEventPublisher.publishError(paperId, "任务已被停止");
            } else {
                log.error("FlowEngine 异常 paperId={}: {}", paperId, e.getMessage(), e);
                paperService.updateStatus(paperId, Constants.PAPER_STATUS_FAILED);
                stepEventPublisher.publishError(paperId, e.getMessage() != null ? e.getMessage() : "FlowEngine 执行异常");
            }
        } finally {
            runningTasks.remove(paperId);
        }
    }

    // ===== 解析 graphData =====
    @SuppressWarnings("unchecked")
    private GraphData parseGraph(String graphData) throws Exception {
        Map<String, Object> graph = objectMapper.readValue(graphData, Map.class);
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        if (nodes == null) nodes = Collections.emptyList();
        if (edges == null) edges = Collections.emptyList();

        Map<String, List<String>> forwardOut = new HashMap<>();
        Map<String, List<String>> loopOut = new HashMap<>();
        Map<String, Map<String, Object>> nodeById = new HashMap<>();
        Map<String, Map<String, Object>> edgeBySrcType = new HashMap<>();

        for (Map<String, Object> n : nodes) {
            String id = (String) n.get("id");
            nodeById.put(id, n);
            forwardOut.put(id, new ArrayList<>());
            loopOut.put(id, new ArrayList<>());
        }
        for (Map<String, Object> e : edges) {
            String src = (String) e.get("source");
            String tgt = (String) e.get("target");
            Map<String, Object> data = (Map<String, Object>) e.get("data");
            String ct = data != null ? (String) data.get("conditionType") : "normal";
            String key = src + "->" + tgt + ":" + ct;
            edgeBySrcType.put(key, e);
            if ("loop".equals(ct)) {
                loopOut.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
            } else {
                forwardOut.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
            }
        }

        return new GraphData(nodes, edges, forwardOut, loopOut, nodeById, edgeBySrcType);
    }

    // ===== 执行 DAG =====
    private void executeDAG(ExecState s, GraphData g) {
        Set<String> allIn = new HashSet<>();
        for (Map<String, Object> e : g.edges) {
            allIn.add((String) e.get("target"));
        }
        List<String> entries = new ArrayList<>();
        for (String id : g.forwardOut.keySet()) {
            if (!allIn.contains(id)) entries.add(id);
        }
        if (entries.isEmpty()) {
            entries.addAll(g.forwardOut.keySet());
        }

        String currentId = entries.get(0);
        int steps = 0;
        while (currentId != null && steps < s.maxSteps) {
            if (runningTasks.getOrDefault(s.paperId, false)) throw new RuntimeException("任务被停止");
            String nextId = executeNode(s, g, currentId);
            steps++;
            if (nextId == null) break;
            currentId = nextId;
        }
    }

    @SuppressWarnings("unchecked")
    private String executeNode(ExecState s, GraphData g, String nodeId) {
        Map<String, Object> node = g.nodeById.get(nodeId);
        if (node == null) return null;
        String type = (String) node.get("type");
        if (type == null) type = "agent";

        Map<String, Object> data = (Map<String, Object>) node.get("data");
        if (data == null) data = Collections.emptyMap();

        String rawLabel = data.get("label") != null ? (String) data.get("label") : "未命名";
        String label = rawLabel.replaceAll("^[✍️🧭🔬📝✨📄⇢↺]\\s*", "").trim();
        if (label.isEmpty() || label.matches("^(写作者|导师|研究员|审稿人|润色师|论文任务)$")) {
            label = "当前步骤";
        }
        String roleStr = (String) data.get("agentRole");
        AgentRole role = roleStr != null ? tryParseRole(roleStr) : AgentRole.WRITER;

        if ("paper".equals(type)) {
            Map<String, Object> config = (Map<String, Object>) data.get("config");
            String paperTitle = config != null ? (String) config.get("paperTitle") : "";
            s.ctx.setAttribute("paperTitle", paperTitle);
            if (config != null && config.get("paperId") != null) {
                s.ctx.setAttribute("paperId", config.get("paperId"));
            }
            publishNodeStatus(s, nodeId, "completed", label, null);
            s.completed.add(nodeId);
            log.info("  📄 论文节点 {}: {}", nodeId, paperTitle);
            return getNextNode(s, g, nodeId, type, data);
        }

        publishNodeStatus(s, nodeId, "in_progress", label, role);

        String result = null;
        try {
            if ("condition".equals(type)) {
                String prevOutput = getPreviousOutput(s, g, nodeId);
                result = "条件评估: " + ((String) data.getOrDefault("label", "判断"));
                s.completed.add(nodeId);
            } else if ("loop".equals(type)) {
                result = "循环控制";
                s.completed.add(nodeId);
            } else {
                BaseAgent agent = getAgent(role);
                Map<String, Object> config = (Map<String, Object>) data.get("config");
                data.put("__nodeId", nodeId);
                String task = buildTask(s, g, label, data, config);
                result = callAgent(s, agent, task, nodeId, label, role);
                s.completed.add(nodeId);
            }
        } catch (Exception e) {
            publishNodeStatus(s, nodeId, "failed", label + ": " + e.getMessage(), role);
            throw new RuntimeException(e);
        }

        publishNodeStatus(s, nodeId, "completed", label, role);

        if (result != null && !"WRITER".equals(roleStr)) {
            s.finalContent.append(result).append("\n\n");
        }

        return getNextNode(s, g, nodeId, type, data);
    }

    @SuppressWarnings("unchecked")
    private String getNextNode(ExecState s, GraphData g, String nodeId, String type, Map<String, Object> data) {
        if ("condition".equals(type)) {
            String lastOutput = getPreviousOutput(s, g, nodeId);
            double score = extractScore(lastOutput);
            boolean pass = score >= 6.5;
            log.info("条件判断: nodeId={}, score={}, pass={}", nodeId, score, pass);

            for (Map<String, Object> e : g.edges) {
                if (!nodeId.equals(e.get("source"))) continue;
                Map<String, Object> ed = (Map<String, Object>) e.get("data");
                String ct = ed != null ? (String) ed.get("conditionType") : "normal";
                if ("success".equals(ct) && pass) return (String) e.get("target");
                if ("failure".equals(ct) && !pass) return (String) e.get("target");
            }
        }

        if ("loop".equals(type)) {
            Map<String, Object> config = (Map<String, Object>) data.get("config");
            int maxIter = config != null && config.get("maxIterations") instanceof Number
                ? ((Number) config.get("maxIterations")).intValue() : 3;
            int curIter = s.loopCounter.getOrDefault(nodeId, 0);

            if (curIter < maxIter && !g.loopOut.getOrDefault(nodeId, Collections.emptyList()).isEmpty()) {
                s.loopCounter.put(nodeId, curIter + 1);
                String backId = g.loopOut.get(nodeId).get(0);
                resetCompletedBetween(s, nodeId);
                return backId;
            }
        }

        List<String> fwds = g.forwardOut.getOrDefault(nodeId, Collections.emptyList());
        if (!fwds.isEmpty()) {
            String next = fwds.get(0);
            if (s.completed.contains(next)) {
                log.info("隐式循环: {} → {}（目标已访问，重置后重新执行）", nodeId, next);
                s.completed.remove(next);
                resetForwardPath(s, g, next);
            }
            return next;
        }

        List<String> loops = g.loopOut.getOrDefault(nodeId, Collections.emptyList());
        if (!loops.isEmpty()) return loops.get(0);

        return null;
    }

    private void resetForwardPath(ExecState s, GraphData g, String nodeId) {
        Set<String> toReset = new HashSet<>();
        Queue<String> q = new LinkedList<>();
        if (s.completed.contains(nodeId)) { toReset.add(nodeId); q.add(nodeId); }
        while (!q.isEmpty()) {
            String id = q.poll();
            for (String fwd : g.forwardOut.getOrDefault(id, Collections.emptyList())) {
                if (s.completed.contains(fwd) && !toReset.contains(fwd)) {
                    toReset.add(fwd); q.add(fwd);
                }
            }
        }
        toReset.forEach(id -> {
            s.completed.remove(id);
            Map<String, Object> n = g.nodeById.get(id);
            if (n != null) {
                Map<String, Object> d = (Map<String, Object>) n.get("data");
                if (d != null) d.put("status", "pending");
            }
        });
        log.info("重置路径: {} 个节点", toReset.size());
    }

    private void resetCompletedBetween(ExecState s, String toId) {
        Set<String> toReset = new HashSet<>();
        for (String nid : s.completed) {
            if (!nid.equals(toId)) toReset.add(nid);
        }
        toReset.forEach(s.completed::remove);
    }

    private String callAgent(ExecState s, BaseAgent agent, String task, String nodeId, String label, AgentRole role) {
        s.stepSeq++;
        int seq = s.stepSeq;
        int ver = paperService.getPaperById(s.paperId).getCurrentVersion();
        Task taskRecord = agentTaskService.createTask(s.paperId, role.getCode(), null, label, ver);
        agentTaskService.updateStatus(taskRecord.getId(), TaskStatus.IN_PROGRESS);

        log.info("→ FlowStep#{}: [{}] {} (node={})", seq, role.getDisplayName(), label, nodeId);
        stepEventPublisher.publishStreamToken(s.paperId, seq, label, "");

        try {
            agent.setContext(s.ctx);
            String result = agent.executeTaskStream(task, s.ctx,
                full -> stepEventPublisher.publishStreamToken(s.paperId, seq, label, full));
            long elapsed = System.currentTimeMillis();
            agentTaskService.updateOutput(taskRecord.getId(), result, elapsed);

            s.ctx.putNodeOutput(nodeId, result);
            s.lastAgentNodeId = nodeId;

            if (role == AgentRole.SUPERVISOR && label != null && label.contains("选题") && result != null) {
                String suggested = extractTopicSuggestion(result);
                if (suggested != null && !suggested.isBlank() && !suggested.equals(s.ctx.getTopic())) {
                    String oldTopic = s.ctx.getTopic();
                    s.ctx.setTopic(suggested);
                    log.info("选题已更新: '{}' → '{}'", oldTopic, suggested);
                }
            }

            log.info("  ✓ [{}] {} 完成 (node={})", role.getDisplayName(), label, nodeId);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis();
            agentTaskService.updateOutput(taskRecord.getId(), e.getMessage(), elapsed);
            agentTaskService.updateStatus(taskRecord.getId(), TaskStatus.FAILED);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private String buildTask(ExecState s, GraphData g, String label, Map<String, Object> data, Map<String, Object> config) {
        String notes = config != null ? (String) config.get("notes") : "";
        String role = (String) data.get("agentRole");

        StringBuilder sb = new StringBuilder();

        String topic = s.ctx.getTopic();
        if (topic == null || topic.isBlank()) {
            topic = (String) s.ctx.getAttribute("paperTitle");
        }
        if (topic == null || topic.isBlank()) topic = "学术论文";
        sb.append("【论文主题】").append(topic).append("\n");

        boolean isTerminal = g.forwardOut.get(data.get("__nodeId")) == null
                          || g.forwardOut.get(data.get("__nodeId")).isEmpty();

        String outline = s.ctx.getOutline();
        if (outline != null && !outline.isBlank()) {
            sb.append("【论文大纲】\n").append(outline).append("\n");
        } else if ("WRITER".equals(role)) {
            sb.append("【论文大纲】\n（暂无预设大纲，请根据主题自行规划合适的章节结构，至少包含：摘要、引言、方法、实验、结论）\n");
        }

        if (s.ctx.getResearchOutput() != null && !s.ctx.getResearchOutput().isBlank()) {
            sb.append("【研究材料】\n").append(truncate(s.ctx.getResearchOutput(), 3000)).append("\n");
        } else if ("WRITER".equals(role)) {
            sb.append("【研究材料】\n（暂无研究材料，请基于你的知识库进行撰写，但请确保内容的学术准确性。如有不确定之处请标注）\n");
        }

        if (!s.ctx.getSections().isEmpty()) {
            if (isTerminal) {
                String lastSection = getLastWriterOutput(s, g);
                if (lastSection != null) {
                    sb.append("【论文全文（已完成所有修改的最终版本）】\n");
                    sb.append(truncate(lastSection, 12000)).append("\n\n");
                }
            } else {
                sb.append("【已撰写章节】\n");
                s.ctx.getSections().forEach((t, c) ->
                    sb.append("- ").append(t).append("（").append(c != null ? c.length() : 0).append("字）\n"));
            }
        }

        if (!s.ctx.getReviewComments().isEmpty() && !isTerminal) {
            sb.append("【审稿意见（请逐条修改）】\n");
            s.ctx.getReviewComments().forEach(c -> sb.append("- ").append(c).append("\n"));
            sb.append("\n请严格对照以上每一条审稿意见进行修改，不要遗漏。\n");
        }

        if (notes != null && !notes.isBlank()) {
            sb.append("【用户备注】").append(notes).append("\n");
        }

        sb.append("\n━━━━━━ 当前任务 ━━━━━━\n");

        if ("RESEARCHER".equals(role)) {
            sb.append("""
                你是资深学术研究员。请对以上主题进行文献调研。

                输出要求：
                1. 研究现状（至少 800 字，按主题分点论述）
                2. 关键技术和方法概述
                3. 存在的挑战和研究空白
                4. 建议的研究方向（至少 3 点）

                注意：
                - 只输出调研内容本身，不要写"我将为您调研..."之类的废话
                - 使用学术化语言
                - 如有不确定性请明确标注
                """);
        } else if ("WRITER".equals(role)) {
            sb.append("你是专业学术写作者。请撰写【").append(label).append("】章节。\n\n");
            sb.append("""
                写作要求：
                - 学术化语言，客观严谨，杜绝口语化
                - 每段不少于 200 字，围绕一个核心论点展开
                - 章节开头概述本章内容，结尾自然过渡到下一章
                - 与已有章节保持逻辑连贯和风格一致
                - 如引用研究材料中的数据/观点，请自然融入而非生硬堆砌
                - 使用准确的学术术语

                禁止：
                - 不要输出"我将撰写..."、"以下是..."之类的元描述
                - 不要使用"首先其次然后"的流水账句式
                - 不要大段重复论文主题。直接开始写内容
                """);

            if (label == null || label.equals("当前步骤")) {
                sb.append("（请根据大纲判断当前应撰写哪个章节，如无法判断则撰写完整论文）\n");
            }
        } else if ("REVIEWER".equals(role)) {
            sb.append("""
                你是严谨的学术审稿人。请逐章审阅以上论文。

                检查维度：
                1. 逻辑一致性：论证链条是否完整
                2. 学术严谨性：方法、数据、结论是否可靠
                3. 结构合理性：章节组织是否清晰
                4. 表达质量：语言是否准确流畅

                输出格式（严格遵守）：
                ### 总体评分: X.X/10
                （评分标准：8-10=可发表，6.5-7.9=需修改，6.5以下=需重写。此评分行将被系统读取用于流程分支判断）
                ### 总体评价（100字内）
                ### 逐章审阅
                - 章节名
                  - 问题1：具体描述（标注严重程度：严重/一般/建议）
                ### 修改优先级建议

                如存在严重问题，请明确标注"严重问题:"前缀。
                """);
        } else if ("POLISHER".equals(role)) {
            sb.append("""
                你是学术编辑。请对论文全文进行最终润色和格式化。

                工作内容：
                1. 语法校对：修正错误和不规范表达
                2. 表达优化：改进句式，提升可读性
                3. 术语统一：确保全文术语一致
                4. 格式规范：标题层级、段落间距、标注格式

                输出要求：
                - 直接输出润色后的完整论文（Markdown 格式）
                - 不要解释你的修改
                - 保持原有章节结构和核心内容不变
                """);
        } else if ("SUPERVISOR".equals(role)) {
            if (label != null && label.contains("选题")) {
                sb.append("""
                    请评估以上选题的学术价值、创新性和可行性。
                    如有更优选题方向，请按格式输出：
                    建议选题: [新题目]
                    理由: [简述理由]
                    最后给出综合评分: X.X/10
                    """);
            } else if (label != null && label.contains("大纲")) {
                sb.append("请审阅大纲结构，评估章节逻辑和完整性，给出具体修改建议。");
            } else {
                sb.append("请对论文进行最终审核，评估整体学术质量，给出明确的结论和改进建议。");
            }
            sb.append("\n要求：简洁明确，直接给结论和建议，不要冗余铺垫。\n");
        } else {
            sb.append("请完成任务: ").append(label).append("\n");
        }

        if (isTerminal) {
            sb.append("""

                ⚠ 你是本流程的最后一个节点。请基于以上所有素材，输出最终的完整论文。

                输出规范：
                # {论文标题}
                ## 摘要（200-300字概括）
                **关键词：** 关键词1, 关键词2, 关键词3
                ## 引言
                [引言内容]
                ## 方法
                [方法内容]
                ## 实验
                [实验内容]
                ## 结论
                [结论内容]

                要求：
                - 直接输出 Markdown 格式论文正文，禁止输出解释性文字
                - 各章节内容完整、逻辑连贯、学术规范
                - 这是最终交付物，质量直接影响论文评价
                """);
        }

        return sb.toString();
    }

    private String truncate(String s, int max) {
        return s == null ? "" : s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private AgentRole tryParseRole(String roleStr) {
        try { return AgentRole.valueOf(roleStr); } catch (IllegalArgumentException e) { return AgentRole.WRITER; }
    }

    private BaseAgent getAgent(AgentRole role) {
        return switch (role) {
            case SUPERVISOR -> supervisorAgent;
            case RESEARCHER -> researcherAgent;
            case WRITER -> writerAgent;
            case REVIEWER -> reviewerAgent;
            case POLISHER -> polisherAgent;
        };
    }

    private String getPreviousOutput(ExecState s, GraphData g, String nodeId) {
        for (Map<String, Object> e : g.edges) {
            if (nodeId.equals(e.get("target"))) {
                String src = (String) e.get("source");
                String out = s.ctx.getNodeOutput(src);
                return out != null ? out : "";
            }
        }
        return "";
    }

    private String extractTopicSuggestion(String text) {
        if (text == null) return null;
        String[] patterns = {
            "建议(?:题目|选题|改为)[：:]\\s*(.+?)(?:[。\\n]|$)",
            "推荐(?:题目|选题)[：:]\\s*(.+?)(?:[。\\n]|$)",
            "题目[可]?(?:改为|调整为)[：:]\\s*(.+?)(?:[。\\n]|$)",
            "选题建议[：:]\\s*(.+?)(?:[。\\n]|$)"
        };
        for (String pat : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pat);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group(1).trim().replaceAll("^[「「《『\"]|[」」》』\"]$", "");
            }
        }
        return null;
    }

    private double extractScore(String text) {
        if (text == null || text.isBlank()) return 5.0;
        String[] scorePatterns = {
            "评分[：:]\\s*(\\d+\\.?\\d*)",
            "综合评分[：:]\\s*(\\d+\\.?\\d*)",
            "(\\d+\\.?\\d*)\\s*/\\s*10",
            "(\\d+\\.?\\d*)\\s*分"
        };
        for (String pat : scorePatterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pat);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                try {
                    double score = Double.parseDouble(m.group(1));
                    if (score > 10) score = score / 10.0;
                    return score;
                } catch (NumberFormatException ignored) {}
            }
        }
        if (text.contains("严重问题")) return 4.0;
        return 5.0;
    }

    private void publishNodeStatus(ExecState s, String nodeId, String status, String label, AgentRole role) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("status", status);
        payload.put("label", label);
        payload.put("agentRole", role != null ? role.name() : null);
        stepEventPublisher.publishNodeStatus(s.paperId, payload);
    }

    private void finish(ExecState s, GraphData g) {
        String draft = buildDraft(s, g);
        s.ctx.setFinalDraft(draft);
        paperService.saveVersion(s.paperId, "FINAL", "论文终稿", draft);
        paperService.updateStatus(s.paperId, Constants.PAPER_STATUS_COMPLETED);

        try {
            String reviewTask = """
                【论文主题】%s

                【论文全文】
                %s

                请对以上论文进行最终质量评审，输出格式：
                ### 总体评分: X.X/10
                ### 总体评价
                ### 逐章评审意见
                ### 改进建议
                """.formatted(s.ctx.getTopic(), truncate(draft, 10000));

            String review = reviewerAgent.executeTaskStream(reviewTask, s.ctx, null);
            if (review != null && !review.isBlank()) {
                paperService.saveVersion(s.paperId, "REVIEW", "自动审稿意见", review);
                log.info("自动审稿完成 paperId={}, reviewLength={}", s.paperId, review.length());
            }
        } catch (Exception e) {
            log.warn("自动审稿失败 paperId={}: {}", s.paperId, e.getMessage());
        }

        log.info("===== FlowEngine 完成 paperId={} =====", s.paperId);
        stepEventPublisher.publishComplete(s.paperId);
    }

    private String getLastWriterOutput(ExecState s, GraphData g) {
        String lastWriterId = null;
        for (Map<String, Object> n : g.nodes) {
            Map<String, Object> d = (Map<String, Object>) n.get("data");
            if (d != null && "WRITER".equals(d.get("agentRole"))) {
                String nid = (String) n.get("id");
                if (s.ctx.getNodeOutput(nid) != null) lastWriterId = nid;
            }
        }
        return lastWriterId != null ? s.ctx.getNodeOutput(lastWriterId) : null;
    }

    private String buildDraft(ExecState s, GraphData g) {
        if (s.lastAgentNodeId != null) {
            String output = s.ctx.getNodeOutput(s.lastAgentNodeId);
            if (output != null && output.length() > 200) {
                log.info("buildDraft: 使用最后一个 Agent 输出 [nodeId={}, length={}]", s.lastAgentNodeId, output.length());
                return output;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(s.ctx.getTopic()).append("\n\n");
        if (!s.ctx.getSections().isEmpty()) {
            s.ctx.getSections().forEach((t, c) -> sb.append("## ").append(t).append("\n").append(c).append("\n\n"));
        } else if (s.finalContent.length() > 0) {
            sb.append(s.finalContent);
        }
        return sb.toString();
    }
}
