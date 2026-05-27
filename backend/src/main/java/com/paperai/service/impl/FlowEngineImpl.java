package com.paperai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperai.agent.AgentContext;
import com.paperai.agent.AgentDefinition;
import com.paperai.agent.AgentDefinitions;
import com.paperai.agent.AgentExecutor;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.entity.FlowDefinition;
import com.paperai.model.entity.Task;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import com.paperai.common.Constants;
import com.paperai.event.StepEventPublisher;
import com.paperai.service.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.ai.chat.client.ChatClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
public class FlowEngineImpl implements FlowEngineService {

    @Resource private AgentExecutor agentExecutor;
    @Resource private PaperService paperService;
    @Resource private AgentTaskService agentTaskService;
    @Resource private StepEventPublisher stepEventPublisher;
    @Resource private KnowledgeGraphService knowledgeGraphService;
    @Resource private KnowledgeService knowledgeService;
    @Resource private com.paperai.config.AiConfig aiConfig;
    @org.springframework.beans.factory.annotation.Autowired(required = false) private org.redisson.api.RedissonClient redisson;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<Long, Boolean> runningTasks = new ConcurrentHashMap<>();
    private static final String RUNNING_TASKS_KEY = "paperai:running:tasks";

    private record GraphData(
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges,
            Map<String, List<String>> forwardOut,
            Map<String, List<String>> loopOut,
            Map<String, Map<String, Object>> nodeById,
            Map<String, Map<String, Object>> edgeBySrcType
    ) {}

    private static class ExecState {
        final Long paperId;
        final AgentContext ctx;
        int stepSeq;
        final StringBuilder finalContent = new StringBuilder();
        final Map<String, Integer> loopCounter = new HashMap<>();
        final Set<String> completed = new HashSet<>();
        final int maxSteps = 50;
        String lastAgentNodeId;
        Long userId;
        final Map<String, String> nodeOutputs = new ConcurrentHashMap<>();

        ExecState(Long paperId, String contextId, String topic) {
            this.paperId = paperId;
            this.ctx = new AgentContext(contextId, topic);
        }
    }

    @Override
    public void stop(Long paperId) {
        if (redisson != null) {
            redisson.getMap(RUNNING_TASKS_KEY).put(paperId.toString(), true);
        } else {
            runningTasks.put(paperId, true);
        }
    }

    private boolean isStopRequested(Long paperId) {
        if (redisson != null) {
            return Boolean.TRUE.equals(redisson.getMap(RUNNING_TASKS_KEY).get(paperId.toString()));
        }
        return runningTasks.getOrDefault(paperId, false);
    }

    @Override
    public void execute(Long paperId, FlowDefinition def, PaperWritingRequestDTO req) {
        ExecState s = new ExecState(paperId, UUID.randomUUID().toString(), req.getTopic());
        s.ctx.setAttribute("direction", req.getDescription() != null ? req.getDescription() : "");
        try {
            com.paperai.model.entity.Paper p = paperService.getPaperById(paperId);
            s.userId = p.getUserId();
            if (p.getKgId() != null) {
                com.paperai.model.entity.KnowledgeGraph kg = knowledgeGraphService.getById(p.getKgId());
                if (kg != null && kg.getGraphData() != null) {
                    s.ctx.setKgGraphData(kg.getGraphData());
                    log.info("[知识图谱] 论文「{}」关联知识图谱「{}」（ID:{}）", p.getTitle(), kg.getName(), kg.getId());
                }
            }
        } catch (Exception e) { log.warn("加载论文元数据失败 paperId={}: {}", paperId, e.getMessage()); }
        if (redisson != null) {
            redisson.getMap(RUNNING_TASKS_KEY).put(paperId.toString(), false);
        } else {
            runningTasks.put(paperId, false);
        }

        String name = def.getName() != null ? def.getName() : "未知流程";
        log.info("===== FlowEngine 开始 paperId={}, flowName={} =====", paperId, name);
        try {
            String gd = def.getGraphData();
            if (gd == null || gd.isBlank()) throw new IllegalArgumentException("流程 graphData 为空");
            GraphData g = parseGraph(gd);
            executeDAG(s, g);
            finish(s, g);
        } catch (Exception e) {
            if (isStopRequested(paperId)) {
                log.info("任务被停止 paperId={}", paperId);
                stepEventPublisher.publishError(paperId, "任务已被停止");
            } else {
                log.error("FlowEngine 异常 paperId={}: {}", paperId, e.getMessage(), e);
                paperService.updateStatus(paperId, Constants.PAPER_STATUS_FAILED);
                stepEventPublisher.publishError(paperId, e.getMessage() != null ? e.getMessage() : "FlowEngine 执行异常");
            }
        } finally {
            if (redisson != null) {
                redisson.getMap(RUNNING_TASKS_KEY).remove(paperId.toString());
            } else {
                runningTasks.remove(paperId);
            }
        }
    }

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
            if ("loop".equals(ct)) loopOut.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
            else forwardOut.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
        }
        return new GraphData(nodes, edges, forwardOut, loopOut, nodeById, edgeBySrcType);
    }

    private void executeDAG(ExecState s, GraphData g) {
        Set<String> allIn = new HashSet<>();
        for (Map<String, Object> e : g.edges) allIn.add((String) e.get("target"));
        List<String> entries = new ArrayList<>();
        for (String id : g.forwardOut.keySet()) if (!allIn.contains(id)) entries.add(id);
        if (entries.isEmpty()) entries.addAll(g.forwardOut.keySet());

        String currentId = entries.get(0);
        int steps = 0;
        while (currentId != null && steps < s.maxSteps) {
            if (isStopRequested(s.paperId)) throw new RuntimeException("任务被停止");
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
        if (label.isEmpty() || label.matches("^(写作者|导师|研究员|审稿人|润色师|论文任务)$")) label = "当前步骤";
        String roleStr = (String) data.get("agentRole");
        AgentRole role = (roleStr != null && !roleStr.startsWith("CUSTOM_")) ? tryParseRole(roleStr) : AgentRole.WRITER;

        if ("paper".equals(type)) {
            Map<String, Object> config = (Map<String, Object>) data.get("config");
            if (config != null) {
                String paperTitle = (String) config.get("paperTitle");
                s.ctx.setAttribute("paperTitle", paperTitle);
                if (config.get("paperId") != null) s.ctx.setAttribute("paperId", config.get("paperId"));
            }
            publishNodeStatus(s, nodeId, "completed", label, null);
            s.completed.add(nodeId);
            return getNextNode(s, g, nodeId, type, data);
        }

        publishNodeStatus(s, nodeId, "in_progress", label, role);
        try {
            if ("condition".equals(type)) {
                s.completed.add(nodeId);
            } else if ("loop".equals(type)) {
                s.completed.add(nodeId);
            } else {
                Map<String, Object> config = (Map<String, Object>) data.get("config");
                data.put("__nodeId", nodeId);
                String task = buildTask(s, g, label, data, config);
                String result = callAgent(s, node, task, nodeId, label, role);
                s.completed.add(nodeId);
                if (result != null && !"WRITER".equals(roleStr)) s.finalContent.append(result).append("\n\n");
            }
        } catch (Exception e) {
            publishNodeStatus(s, nodeId, "failed", label + ": " + e.getMessage(), role);
            throw new RuntimeException(e);
        }
        publishNodeStatus(s, nodeId, "completed", label, role);
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
        return !loops.isEmpty() ? loops.get(0) : null;
    }

    private void resetForwardPath(ExecState s, GraphData g, String nodeId) {
        Set<String> toReset = new HashSet<>();
        Queue<String> q = new LinkedList<>();
        if (s.completed.contains(nodeId)) { toReset.add(nodeId); q.add(nodeId); }
        while (!q.isEmpty()) {
            for (String fwd : g.forwardOut.getOrDefault(q.poll(), Collections.emptyList())) {
                if (s.completed.contains(fwd) && !toReset.contains(fwd)) { toReset.add(fwd); q.add(fwd); }
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
    }

    private void resetCompletedBetween(ExecState s, String toId) {
        Set<String> toReset = new HashSet<>();
        for (String nid : s.completed) if (!nid.equals(toId)) toReset.add(nid);
        toReset.forEach(s.completed::remove);
    }

    @SuppressWarnings("unchecked")
    private String callAgent(ExecState s, Map<String, Object> node, String task,
                              String nodeId, String label, AgentRole role) {
        s.stepSeq++;
        int seq = s.stepSeq;
        int ver = paperService.getPaperById(s.paperId).getCurrentVersion();
        Task taskRecord = agentTaskService.createTask(s.paperId, role.getCode(), null, label, ver);
        agentTaskService.updateStatus(taskRecord.getId(), TaskStatus.IN_PROGRESS);

        ChatClient nodeClient = null;
        String customPrompt = null;
        AgentDefinition def = AgentDefinitions.forRole(role);
        if (node != null) {
            Map<String, Object> data = (Map<String, Object>) node.get("data");
            if (data != null) {
                Map<String, Object> config = (Map<String, Object>) data.get("config");
                if (config != null) {
                    String model = (String) config.get("model");
                    customPrompt = (String) config.get("systemPrompt");
                    Object tempObj = config.get("temperature");
                    if (model != null && !model.isBlank()) {
                        double temp = tempObj instanceof Number ? ((Number) tempObj).doubleValue() : 0.7;
                        nodeClient = aiConfig.createChatClient(model, temp);
                        log.info("  🔧 节点 {} 使用模型: {} (温度={})", nodeId, model, temp);
                    } else if (tempObj instanceof Number) {
                        nodeClient = aiConfig.createChatClient(com.paperai.config.AiConfig.getDefaultModel(),
                                ((Number) tempObj).doubleValue());
                    }
                }
            }
        }

        log.info("→ FlowStep#{}: [{}] {} (node={})", seq, role.getDisplayName(), label, nodeId);
        stepEventPublisher.publishStreamToken(s.paperId, seq, label, "");

        try {
            Consumer<String> onToken = full ->
                stepEventPublisher.publishStreamToken(s.paperId, seq, label, full);
            String augmentedTask = knowledgeService.augment(task, s.userId);
            String result = nodeClient != null || (customPrompt != null && !customPrompt.isBlank())
                ? agentExecutor.executeWithConfig(def, augmentedTask, onToken, nodeClient, customPrompt)
                : agentExecutor.executeStream(def, augmentedTask, onToken);

            agentTaskService.updateOutput(taskRecord.getId(), result, System.currentTimeMillis());
            s.nodeOutputs.put(nodeId, result);
            s.lastAgentNodeId = nodeId;

            if (role == AgentRole.RESEARCHER) s.ctx.setResearchOutput(result);
            else if (role == AgentRole.WRITER) {
                String sectionTitle = extractSectionTitle(label);
                if (sectionTitle != null) s.ctx.addSection(sectionTitle, result);
            } else if (role == AgentRole.REVIEWER) {
                String summary = extractReviewSummary(result);
                if (!summary.isEmpty()) s.ctx.addReviewComment(summary);
            }
            if (role == AgentRole.SUPERVISOR && label != null && label.contains("选题") && result != null) {
                String suggested = extractTopicSuggestion(result);
                if (suggested != null && !suggested.isBlank() && !suggested.equals(s.ctx.getTopic())) {
                    s.ctx.setTopic(suggested);
                }
            }
            log.info("  ✓ [{}] {} 完成 (node={})", role.getDisplayName(), label, nodeId);
            return result;
        } catch (Exception e) {
            agentTaskService.updateOutput(taskRecord.getId(), e.getMessage(), System.currentTimeMillis());
            agentTaskService.updateStatus(taskRecord.getId(), TaskStatus.FAILED);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private String buildTask(ExecState s, GraphData g, String label,
                              Map<String, Object> data, Map<String, Object> config) {
        String notes = config != null ? (String) config.get("notes") : "";
        String roleStr = (String) data.get("agentRole");
        StringBuilder sb = new StringBuilder();

        String topic = s.ctx.getTopic();
        if (topic == null || topic.isBlank()) topic = (String) s.ctx.getAttribute("paperTitle");
        if (topic == null || topic.isBlank()) topic = "学术论文";
        sb.append("【论文主题】").append(topic).append("\n");

        boolean isTerminal = g.forwardOut.get(data.get("__nodeId")) == null
                          || g.forwardOut.get(data.get("__nodeId")).isEmpty();

        String outline = s.ctx.getOutline();
        if (outline != null && !outline.isBlank()) sb.append("【论文大纲】\n").append(outline).append("\n");
        else if ("WRITER".equals(roleStr))
            sb.append("【论文大纲】\n（暂无预设大纲，请根据主题自行规划）\n");

        if (s.ctx.getResearchOutput() != null && !s.ctx.getResearchOutput().isBlank())
            sb.append("【研究材料】\n").append(compressResearchOutput(s.ctx.getResearchOutput())).append("\n");

        if (!s.ctx.getSections().isEmpty()) {
            if (isTerminal) {
                String lastSection = getLastWriterOutput(s, g);
                if (lastSection != null) sb.append("【论文全文】\n").append(truncate(lastSection, 12000)).append("\n\n");
            } else if ("WRITER".equals(roleStr)) {
                sb.append("【已撰写章节】\n");
                String prevSectionEnd = null;
                for (var entry : s.ctx.getSections().entrySet()) {
                    String c = entry.getValue();
                    sb.append("- ").append(entry.getKey()).append("（").append(c != null ? c.length() : 0).append("字）\n");
                    if (c != null && c.length() > 200) prevSectionEnd = c.substring(c.length() - 200);
                }
                if (prevSectionEnd != null) sb.append("\n【前一章结尾】\n").append(prevSectionEnd).append("\n");
            } else {
                sb.append("【已撰写章节】\n");
                s.ctx.getSections().forEach((t, c) ->
                    sb.append("- ").append(t).append("（").append(c != null ? c.length() : 0).append("字）\n"));
            }
        }

        if (!s.ctx.getReviewComments().isEmpty() && !isTerminal) {
            sb.append("【审稿意见（请逐条修改）】\n");
            s.ctx.getReviewComments().forEach(c -> sb.append("- ").append(c).append("\n"));
            sb.append("\n请严格对照以上每一条审稿意见进行修改。\n");
        }
        if (notes != null && !notes.isBlank()) sb.append("【用户备注】").append(notes).append("\n");

        sb.append("\n━━━━━━ 当前任务 ━━━━━━\n");
        appendRoleSpecificPrompt(sb, roleStr, label, isTerminal);
        appendKgContext(s, sb);
        return sb.toString();
    }

    private void appendRoleSpecificPrompt(StringBuilder sb, String roleStr, String label, boolean isTerminal) {
        if ("RESEARCHER".equals(roleStr)) sb.append("""
            你是资深学术研究员。请对以上主题进行文献调研。

            输出要求：1. 研究现状（至少 800 字）2. 关键技术和方法概述
            3. 存在的挑战和研究空白 4. 建议的研究方向（至少 3 点）
            注意：只输出调研内容本身，不要写"我将为您调研..."之类的废话。使用学术化语言。""");
        else if ("WRITER".equals(roleStr)) {
            sb.append("你是专业学术写作者。请撰写【").append(label).append("】章节。\n\n");
            sb.append("写作要求：学术化语言，每段不少于 200 字，与已有章节保持逻辑连贯。\n");
            sb.append("禁止：不要输出'我将撰写...'、'以下是...'之类的元描述。直接开始写内容。\n");
            if (label == null || label.equals("当前步骤"))
                sb.append("（请根据大纲判断当前应撰写哪个章节）\n");
        } else if ("REVIEWER".equals(roleStr)) sb.append("""
            你是严谨的学术审稿人。检查维度：逻辑一致性、学术严谨性、结构合理性、表达质量。
            输出格式：### 总体评分: X.X/10
            （评分标准：8-10=可发表，6.5-7.9=需修改，<6.5=需重写）
            ### 总体评价（100字内）### 逐章审阅
            - 章节名 - 问题1：具体描述（标注严重程度：严重/一般/建议）
            ### 修改优先级建议。如存在严重问题，请明确标注"严重问题:"前缀。""");
        else if ("POLISHER".equals(roleStr)) sb.append("""
            你是学术编辑。请对论文全文进行最终润色和格式化。直接输出润色后的完整论文。\n
            禁止解释修改。保持原有章节结构和核心内容不变。""");
        else if ("SUPERVISOR".equals(roleStr)) {
            if (label != null && label.contains("选题"))
                sb.append("请评估以上选题的学术价值、创新性和可行性。如有更优选题方向，请按格式输出：\n建议选题: [新题目]\n理由: [简述理由]\n最后给出综合评分: X.X/10\n");
            else if (label != null && label.contains("大纲"))
                sb.append("请审阅大纲结构，评估章节逻辑和完整性。");
            else sb.append("请对论文进行最终审核，评估整体学术质量。");
            sb.append("\n要求：简洁明确，直接给结论和建议。\n");
        } else sb.append("请完成任务: ").append(label).append("\n");

        if (isTerminal) sb.append("""
            ⚠ 你是本流程的最后一个节点。你的任务是润色整合，不是重写。
            保留现有的 # 标题和 ## 章节结构。禁止输出"我将为您..."之类的元描述。
            直接输出润色后的完整论文正文。""");
    }

    private void appendKgContext(ExecState s, StringBuilder sb) {
        String kgData = s.ctx.getKgGraphData();
        if (kgData == null || kgData.isBlank()) return;
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(kgData);
            com.fasterxml.jackson.databind.JsonNode knodes = root.get("nodes");
            com.fasterxml.jackson.databind.JsonNode kedges = root.get("edges");
            if (knodes != null && knodes.size() > 0) {
                Map<String, String> nodeNames = new LinkedHashMap<>();
                for (com.fasterxml.jackson.databind.JsonNode n : knodes) {
                    String nid = n.has("id") ? n.get("id").asText() : "";
                    String nlabel = n.has("data") ? n.get("data").get("label").asText()
                            : (n.has("label") ? n.get("label").asText() : "");
                    if (!nid.isBlank() && !nlabel.isBlank()) nodeNames.put(nid, nlabel);
                }
                sb.append("\n\n【知识图谱参考】\n");
                if (kedges != null && kedges.size() > 0)
                    for (com.fasterxml.jackson.databind.JsonNode e : kedges) {
                        String src = e.has("source") ? e.get("source").asText() : "";
                        String tgt = e.has("target") ? e.get("target").asText() : "";
                        String rel = e.has("data") ? e.get("data").get("label").asText() : "";
                        String srcName = nodeNames.getOrDefault(src, src);
                        String tgtName = nodeNames.getOrDefault(tgt, tgt);
                        if (!srcName.isBlank() && !tgtName.isBlank())
                            sb.append("- ").append(srcName).append(" → ")
                              .append(rel.isBlank() ? "关联" : rel).append(" → ").append(tgtName).append("\n");
                    }
                sb.append("实体：");
                int count = 0;
                for (String name : nodeNames.values()) { sb.append(name).append("、"); if (++count % 8 == 0) sb.append("\n"); }
                sb.append("\n");
            }
        } catch (Exception e) { log.warn("知识图谱解析失败: {}", e.getMessage()); }
    }

    private String compressResearchOutput(String research) {
        if (research == null || research.length() <= 4000) return research;
        try {
            String prompt = "你是学术编辑。把以下研究材料压缩到 2500 字以内，保留关键发现和结论、核心方法名称、5条最重要的参考文献。直接输出压缩后的文本。\n\n" + research;
            String result = aiConfig.callLightLlm("你是学术编辑，擅长提炼关键信息。", prompt);
            return result != null && !result.isBlank() ? result : research;
        } catch (Exception e) { return research; }
    }

    private AgentRole tryParseRole(String roleStr) {
        try { return AgentRole.valueOf(roleStr); } catch (IllegalArgumentException e) { return AgentRole.WRITER; }
    }

    private String getPreviousOutput(ExecState s, GraphData g, String nodeId) {
        for (Map<String, Object> e : g.edges)
            if (nodeId.equals(e.get("target")))
                return s.nodeOutputs.getOrDefault((String) e.get("source"), "");
        return "";
    }

    private String extractTopicSuggestion(String text) {
        if (text == null) return null;
        try {
            String result = aiConfig.callLightLlm("你是文本提取器。从导师评审意见中提取建议的论文题目。如果没有，返回 NONE。只返回题目或 NONE。", text);
            return result != null && !result.contains("NONE") && result.length() > 2 ? result.trim() : null;
        } catch (Exception e) { return null; }
    }

    private double extractScore(String text) {
        if (text == null || text.isBlank()) return 5.0;
        try {
            String result = aiConfig.callLightLlm("你是评分提取器。从审稿意见中提取 1-10 的综合评分，只返回数字（如 7.5）。如果没有评分，返回 5。只返回数字。", text);
            double s = Double.parseDouble(result.trim());
            return s > 10 ? s / 10.0 : s;
        } catch (Exception e) { return 5.0; }
    }

    private String extractSectionTitle(String label) {
        return label != null && !label.equals("当前步骤") ? label : null;
    }

    private String extractReviewSummary(String response) {
        if (response == null) return "";
        int overallIdx = response.indexOf("### 总体评价");
        if (overallIdx != -1) {
            int after = overallIdx + "### 总体评价".length();
            int next = response.indexOf("### ", after);
            return "总体评价: " + truncate(next != -1 ? response.substring(after, next).trim()
                    : response.substring(after).trim(), 100);
        }
        return "";
    }

    private void publishNodeStatus(ExecState s, String nodeId, String status, String label, AgentRole role) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", nodeId); payload.put("status", status);
        payload.put("label", label);
        payload.put("agentRole", role != null ? role.name() : null);
        stepEventPublisher.publishNodeStatus(s.paperId, payload);
    }

    private void finish(ExecState s, GraphData g) {
        String draft = buildDraft(s, g);
        s.ctx.setFinalDraft(draft);
        AgentDefinition reviewerDef = AgentDefinitions.REVIEWER;
        AgentDefinition writerDef = AgentDefinitions.WRITER;

        for (int attempt = 0; attempt <= 2; attempt++) {
            try {
                String reviewTask = "【论文主题】%s\n\n【论文全文】\n%s\n\n请对以上论文进行最终质量评审，输出格式：### 总体评分: X.X/10\n### 总体评价\n### 逐章评审意见\n### 改进建议"
                        .formatted(s.ctx.getTopic(), truncate(draft, 10000));
                String review = agentExecutor.executeStream(reviewerDef, reviewTask, null);
                if (review == null || review.isBlank()) break;

                double score = extractScore(review);
                log.info("[质量门禁] paperId={}, 第{}轮评分={}", s.paperId, attempt + 1, score);
                if (score >= 7.0 || attempt >= 2) {
                    paperService.saveVersion(s.paperId, score >= 7.0 ? "FINAL" : "REVIEWED",
                            score >= 7.0 ? "论文终稿（评分 " + score + "）" : "论文终稿（评分 " + score + "，需人工审核）", draft);
                    paperService.saveVersion(s.paperId, "REVIEW", "自动审稿意见", review);
                    paperService.updateStatus(s.paperId,
                            score >= 7.0 ? Constants.PAPER_STATUS_COMPLETED : Constants.PAPER_STATUS_FAILED);
                    break;
                }
                draft = agentExecutor.executeStream(writerDef,
                        "【论文主题】%s\n\n【审稿意见】\n%s\n\n【当前论文】\n%s\n\n请根据以上审稿意见修改论文。"
                                .formatted(s.ctx.getTopic(), review, truncate(draft, 8000)), null);
                if (draft == null || draft.isBlank()) break;
                s.ctx.setFinalDraft(draft);
            } catch (Exception e) { log.warn("质量门禁异常 paperId={}: {}", s.paperId, e.getMessage()); break; }
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
                if (s.nodeOutputs.get(nid) != null) lastWriterId = nid;
            }
        }
        return lastWriterId != null ? s.nodeOutputs.get(lastWriterId) : null;
    }

    private String buildDraft(ExecState s, GraphData g) {
        if (s.lastAgentNodeId != null) {
            String output = s.nodeOutputs.get(s.lastAgentNodeId);
            if (output != null && output.length() > 200) return output;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(s.ctx.getTopic()).append("\n\n");
        if (!s.ctx.getSections().isEmpty())
            s.ctx.getSections().forEach((t, c) -> sb.append("## ").append(t).append("\n").append(c).append("\n\n"));
        else if (s.finalContent.length() > 0) sb.append(s.finalContent);
        return sb.toString();
    }

    private String truncate(String s, int max) {
        return s == null ? "" : s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
