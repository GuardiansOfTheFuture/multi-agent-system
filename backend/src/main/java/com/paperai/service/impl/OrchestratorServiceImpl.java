package com.paperai.service.impl;

import com.paperai.agent.AgentContext;
import com.paperai.agent.AgentDefinition;
import com.paperai.agent.AgentDefinitions;
import com.paperai.agent.AgentExecutor;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.entity.FlowDefinition;
import com.paperai.model.entity.Paper;
import com.paperai.model.entity.Task;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import com.paperai.model.flow.FlowProfile;
import com.paperai.model.vo.PaperWritingVO;
import com.paperai.common.Constants;
import com.paperai.event.StepEventPublisher;
import com.paperai.service.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Service
public class OrchestratorServiceImpl implements OrchestratorService {

    @Resource private AgentExecutor agentExecutor;
    @Resource private PaperService paperService;
    @Resource private AgentTaskService agentTaskService;
    @Resource private StepEventPublisher stepEventPublisher;
    @Resource private FlowDefinitionService flowDefinitionService;
    @Resource private FlowEngineService flowEngine;
    @Resource private KnowledgeGraphService knowledgeGraphService;
    @Resource private KnowledgeService knowledgeService;
    @org.springframework.beans.factory.annotation.Autowired(required = false) private org.redisson.api.RedissonClient redisson;

    private final java.util.concurrent.ConcurrentHashMap<Long, Boolean> runningTasks = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String RUNNING_TASKS_KEY = "paperai:running:tasks";

    @Override
    public void stopTask(Long paperId) {
        markStopRequested(paperId);
        log.info("收到停止请求 paperId={}", paperId);
    }

    private void markStopRequested(Long paperId) {
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

    private void removeRunningTask(Long paperId) {
        if (redisson != null) {
            redisson.getMap(RUNNING_TASKS_KEY).remove(paperId.toString());
        } else {
            runningTasks.remove(paperId);
        }
    }

    @Override
    public PaperWritingVO execute(PaperWritingRequestDTO req) {
        return execute(req, null);
    }

    @Override
    public PaperWritingVO execute(PaperWritingRequestDTO req, Long userId) {
        return doExecute(req, userId, 0, null);
    }

    @Override
    public void executeStream(PaperWritingRequestDTO req, Consumer<PaperWritingVO.StepRecordVO> cb) {
        executeStream(req, cb, null);
    }

    @Override
    public void executeStream(PaperWritingRequestDTO req, Consumer<PaperWritingVO.StepRecordVO> cb, Long userId) {
        doExecute(req, userId, 0, cb);
    }

    @Override
    public void executeAsync(Long paperId, PaperWritingRequestDTO req) {
        if (isCustomFlow(req.getFlowId())) {
            Long dbId = Long.parseLong(req.getFlowId().substring(7));
            FlowDefinition def = flowDefinitionService.getById(dbId);
            flowEngine.execute(paperId, def, req);
            return;
        }
        if (redisson != null) {
            redisson.getMap(RUNNING_TASKS_KEY).put(paperId.toString(), false);
        } else {
            runningTasks.put(paperId, false);
        }
        AgentContext ctx = new AgentContext(UUID.randomUUID().toString(), req.getTopic());
        List<PaperWritingVO.StepRecordVO> steps = new ArrayList<>();
        long start = System.currentTimeMillis();
        FlowProfile flow = resolveFlow(req.getFlowId());

        log.info("===== 异步写作开始 paperId={}, flow={} =====", paperId, flow.getId());
        try {
            Paper paper = paperService.getPaperById(paperId);
            ctx.setAttribute("userId", paper.getUserId());
            loadKgIntoContext(ctx, paper.getKgId());
            runSteps(paperId, ctx, req, steps, flow, 0, null);
            finish(paperId, ctx, start, null, null);
        } catch (Exception e) {
            if (isStopRequested(paperId)) {
                log.info("任务被用户停止 paperId={}", paperId);
                stepEventPublisher.publishError(paperId, "任务已被停止");
            } else {
                log.error("异步写作异常 paperId={}: {}", paperId, e.getMessage(), e);
                paperService.updateStatus(paperId, Constants.PAPER_STATUS_FAILED);
                stepEventPublisher.publishError(paperId, e.getMessage() != null ? e.getMessage() : "写作异常");
            }
        } finally {
            removeRunningTask(paperId);
        }
    }

    private PaperWritingVO doExecute(PaperWritingRequestDTO req, Long userId,
                                      int initSeq, Consumer<PaperWritingVO.StepRecordVO> callback) {
        long start = System.currentTimeMillis();
        Paper paper = paperService.createPaper(req, userId != null ? userId : 0L);
        Long paperId = paper.getId();
        AgentContext ctx = new AgentContext(UUID.randomUUID().toString(), req.getTopic());
        ctx.setAttribute("userId", userId);
        loadKgIntoContext(ctx, req.getKgId());
        List<PaperWritingVO.StepRecordVO> steps = new ArrayList<>();
        FlowProfile flow = resolveFlow(req.getFlowId());

        try {
            runSteps(paperId, ctx, req, steps, flow, initSeq, callback);
            return finish(paperId, ctx, start, steps, callback);
        } catch (Exception e) {
            paperService.updateStatus(paperId, Constants.PAPER_STATUS_FAILED);
            return toWritingVO(paperId, ctx, steps, System.currentTimeMillis() - start, null);
        }
    }

    private void runSteps(Long paperId, AgentContext ctx, PaperWritingRequestDTO req,
                          List<PaperWritingVO.StepRecordVO> steps, FlowProfile flow,
                          int initialSeq, Consumer<PaperWritingVO.StepRecordVO> callback) {
        int[] seq = {initialSeq};
        ctx.setAttribute("direction", req.getDescription() != null ? req.getDescription() : "");

        if (flow.isTopicEvaluation()) {
            step(paperId, "选题评估", AgentRole.SUPERVISOR, AgentDefinitions.SUPERVISOR, steps, ctx, seq, callback,
                () -> "请评估以下研究选题的学术价值和可行性：\n\n" +
                      "研究主题: " + req.getTopic() + "\n\n" +
                      "方向描述: " + (req.getDescription() != null ? req.getDescription() : "无") + "\n\n" +
                      "请从创新性、可行性、学术价值、研究潜力四个维度进行评价，并给出是否建议继续的建议。");
        }

        if (flow.isLiteratureResearch()) {
            step(paperId, "文献调研", AgentRole.RESEARCHER, AgentDefinitions.RESEARCHER, steps, ctx, seq, callback, () -> {
                String d = "研究主题：" + req.getTopic();
                if (req.getKeywords() != null) d += "\n关键词：" + req.getKeywords();
                if (req.getRequirements() != null) d += "\n要求：" + req.getRequirements();
                d += buildKgContext(ctx);
                return d;
            });
        }

        String outline = generateOutline(ctx, req);
        ctx.setOutline(outline);

        if (flow.isOutlineReview()) {
            step(paperId, "大纲审阅", AgentRole.SUPERVISOR, AgentDefinitions.SUPERVISOR, steps, ctx, seq, callback,
                () -> ctx.getOutline() != null
                    ? "请审阅以下论文大纲，评估结构是否合理、章节安排是否科学：\n\n" +
                      ctx.getOutline() + "\n\n" +
                      "请重点关注：\n1. 整体结构是否逻辑清晰\n2. 章节划分是否合理\n" +
                      "3. 研究重点是否突出\n4. 是否有遗漏的重要内容"
                    : "尚无大纲可供审阅");
        }

        if (flow.isWriteSections()) {
            List<String> sections = req.getSections();
            if (sections == null || sections.isEmpty()) sections = parseSections(outline);
            for (String sec : sections)
                step(paperId, sec, AgentRole.WRITER, AgentDefinitions.WRITER, steps, ctx, seq, callback,
                    () -> "请撰写论文的【" + sec + "】章节。\n基于已有大纲和研究材料展开。" + buildKgContext(ctx));
        }

        if (flow.isReviewIteration()) {
            int maxR = flow.getForceReviewRounds() != null
                ? flow.getForceReviewRounds()
                : (req.getMaxReviewRounds() != null ? req.getMaxReviewRounds() : 3);
            for (int r = 1; r <= maxR; r++) {
                step(paperId, "审稿迭代#" + r, AgentRole.REVIEWER, AgentDefinitions.REVIEWER, steps, ctx, seq, callback,
                    () -> buildReviewTask(ctx, null));
                String lr = getLastReview(steps);
                if (lr != null && !lr.contains("严重问题")) break;
                if (r < maxR)
                    step(paperId, "修改#" + r, AgentRole.WRITER, AgentDefinitions.WRITER, steps, ctx, seq, callback,
                        () -> "请根据审稿意见修改：" + lr);
            }
        }

        if (flow.isPolish()) {
            step(paperId, "润色定稿", AgentRole.POLISHER, AgentDefinitions.POLISHER, steps, ctx, seq, callback,
                () -> buildPolishTask(ctx));
        }

        if (flow.isFinalReview()) {
            step(paperId, "最终审核", AgentRole.SUPERVISOR, AgentDefinitions.SUPERVISOR, steps, ctx, seq, callback,
                () -> buildFinalReviewTask(ctx));
        }
    }

    private String step(Long paperId, String name, AgentRole role, AgentDefinition def,
                        List<PaperWritingVO.StepRecordVO> steps, AgentContext ctx, int[] seqRef,
                        Consumer<PaperWritingVO.StepRecordVO> callback, Supplier<String> taskSupplier) {
        if (isStopRequested(paperId)) throw new RuntimeException("任务已被用户停止");
        long t = System.currentTimeMillis();
        seqRef[0]++;
        int seq = seqRef[0];

        int ver = paperService.getPaperById(paperId).getCurrentVersion();
        Task task = agentTaskService.createTask(paperId, role.getCode(), null, name, ver);
        agentTaskService.updateStatus(task.getId(), TaskStatus.IN_PROGRESS);
        log.info("→ Step#{}: [{}] {} 开始...", seq, role.getDisplayName(), name);
        stepEventPublisher.publishStreamToken(paperId, seq, name, "");

        try {
            String taskStr = taskSupplier.get();
            Long userId = ctx.getAttribute("userId");
            String augmented = knowledgeService.augment(taskStr, userId);
            String result = agentExecutor.executeStream(def, augmented,
                full -> stepEventPublisher.publishStreamToken(paperId, seq, name, full));

            if (role == AgentRole.RESEARCHER) ctx.setResearchOutput(result);
            else if (role == AgentRole.WRITER) {
                String sectionTitle = extractSectionTitle(taskStr, result);
                if (sectionTitle != null) ctx.addSection(sectionTitle, result);
            } else if (role == AgentRole.REVIEWER) {
                String summary = extractReviewSummary(result);
                if (!summary.isEmpty()) ctx.addReviewComment(summary);
            }

            long elapsed = System.currentTimeMillis() - t;
            agentTaskService.updateOutput(task.getId(), result, elapsed);
            log.info("  ✓ [{}] {} 完成 ({}ms)", role.getDisplayName(), name, elapsed);
            addStep(paperId, steps, name, role, TaskStatus.COMPLETED, elapsed, truncate(result, 100), result, callback);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t;
            log.error("  ✗ [{}] {} 失败: {}", role.getDisplayName(), name, e.getMessage());
            agentTaskService.updateOutput(task.getId(), e.getMessage(), elapsed);
            agentTaskService.updateStatus(task.getId(), TaskStatus.FAILED);
            addStep(paperId, steps, name, role, TaskStatus.FAILED, elapsed, e.getMessage(), e.getMessage(), callback);
            throw e;
        }
    }

    private void addStep(Long paperId, List<PaperWritingVO.StepRecordVO> steps, String name, AgentRole role,
                         TaskStatus status, long ms, String summary, String fullOutput,
                         Consumer<PaperWritingVO.StepRecordVO> callback) {
        PaperWritingVO.StepRecordVO s = new PaperWritingVO.StepRecordVO();
        s.setAgentName(name); s.setAgentRole(role); s.setStatus(status);
        s.setDurationMs(ms); s.setSummary(summary); s.setFullOutput(fullOutput);
        steps.add(s);
        if (callback != null) callback.accept(s);
        if (stepEventPublisher != null) stepEventPublisher.publishStep(paperId, s);
    }

    private PaperWritingVO finish(Long paperId, AgentContext ctx, long start,
                                   List<PaperWritingVO.StepRecordVO> steps,
                                   Consumer<PaperWritingVO.StepRecordVO> callback) {
        String draft = buildFinalDraft(ctx);
        ctx.setFinalDraft(draft);
        paperService.saveVersion(paperId, "FINAL", "论文终稿", draft);
        paperService.updateStatus(paperId, Constants.PAPER_STATUS_COMPLETED);
        long total = System.currentTimeMillis() - start;
        log.info("===== 写作完成 paperId={}，总耗时 {}ms =====", paperId, total);
        stepEventPublisher.publishComplete(paperId);
        return steps != null ? toWritingVO(paperId, ctx, steps, total, draft) : null;
    }

    private FlowProfile resolveFlow(String flowId) {
        FlowProfile preset = FlowProfile.fromIdRaw(flowId);
        return preset != null ? preset : FlowProfile.STANDARD;
    }

    private boolean isCustomFlow(String flowId) {
        return flowId != null && flowId.startsWith("custom-");
    }

    private String generateOutline(AgentContext ctx, PaperWritingRequestDTO req) {
        List<String> secs = req.getSections();
        if (secs != null && !secs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < secs.size(); i++) sb.append(i + 1).append(". ").append(secs.get(i)).append("\n");
            return sb.toString();
        }
        return "1. 引言\n2. 相关工作\n3. 方法\n4. 实验\n5. 结论";
    }

    private List<String> parseSections(String outline) {
        if (outline == null) return List.of("引言", "相关工作", "方法", "实验", "结论");
        List<String> list = new ArrayList<>();
        for (String line : outline.split("\n")) {
            String s = line.replaceAll("^\\d+[.、]\\s*", "").trim();
            if (!s.isEmpty()) list.add(s);
        }
        return list.isEmpty() ? List.of("引言", "相关工作", "方法", "实验", "结论") : list;
    }

    private String getLastReview(List<PaperWritingVO.StepRecordVO> steps) {
        for (int i = steps.size() - 1; i >= 0; i--)
            if (steps.get(i).getAgentRole() == AgentRole.REVIEWER) return steps.get(i).getSummary();
        return null;
    }

    private String extractSectionTitle(String task, String response) {
        int start = task.indexOf("【");
        int end = task.indexOf("】");
        if (start != -1 && end > start) return task.substring(start + 1, end);
        if (response != null && response.startsWith("### ")) {
            int lineEnd = response.indexOf("\n");
            if (lineEnd != -1) return response.substring(4, lineEnd).trim();
        }
        return null;
    }

    private String extractReviewSummary(String response) {
        if (response == null) return "";
        StringBuilder summary = new StringBuilder();
        int overallIdx = response.indexOf("### 总体评价");
        if (overallIdx != -1) {
            int afterOverall = overallIdx + "### 总体评价".length();
            int nextSection = response.indexOf("### ", afterOverall);
            String overall = nextSection != -1 ?
                    response.substring(afterOverall, nextSection).trim() :
                    response.substring(afterOverall).trim();
            summary.append("总体评价: ").append(overall.length() > 100 ?
                    overall.substring(0, 100) + "..." : overall);
        }
        return summary.toString();
    }

    private String buildReviewTask(AgentContext ctx, String kgDataOverride) {
        StringBuilder paperContent = new StringBuilder();
        paperContent.append("论文标题: ").append(ctx.getTopic()).append("\n\n");
        if (ctx.getAbstractText() != null)
            paperContent.append("摘要:\n").append(ctx.getAbstractText()).append("\n\n");
        if (!ctx.getSections().isEmpty()) {
            paperContent.append("正文:\n");
            ctx.getSections().forEach((title, content) -> {
                paperContent.append("### ").append(title).append("\n");
                paperContent.append(content).append("\n\n");
            });
        }
        String task = "请对上述论文内容进行全面审阅，从创新性、方法学、逻辑一致性、表达质量等维度给出详细评审意见。";
        String kgData = kgDataOverride != null ? kgDataOverride : ctx.getKgGraphData();
        if (kgData != null && !kgData.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = om.readTree(kgData);
                com.fasterxml.jackson.databind.JsonNode knodes = root.get("nodes");
                if (knodes != null && knodes.size() > 0) {
                    StringBuilder sb = new StringBuilder(task);
                    sb.append("\n\n【知识图谱参考】以下为本文关联的知识图谱核心概念，请检查：\n");
                    sb.append("1. 论文是否覆盖了以下所有核心概念？\n");
                    sb.append("2. 论文中使用的术语是否与以下概念一致？如有不一致请指出。\n");
                    for (com.fasterxml.jackson.databind.JsonNode n : knodes) {
                        String label = n.has("data") ? n.get("data").get("label").asText()
                                : (n.has("label") ? n.get("label").asText() : "");
                        if (!label.isBlank()) sb.append("- ").append(label).append("\n");
                    }
                    task = sb.toString();
                }
            } catch (Exception ignored) {}
        }
        return task;
    }

    private String buildPolishTask(AgentContext ctx) {
        StringBuilder fullText = new StringBuilder();
        fullText.append("论文标题: ").append(ctx.getTopic()).append("\n\n");
        if (ctx.getAbstractText() != null)
            fullText.append("摘要:\n").append(ctx.getAbstractText()).append("\n\n");
        if (!ctx.getSections().isEmpty())
            ctx.getSections().forEach((title, content) ->
                fullText.append("### ").append(title).append("\n").append(content).append("\n\n"));
        return fullText + "\n\n请对上述论文全文进行润色，包括语法修正、表达优化、格式规范检查。\n" +
                "注意保持学术风格，不做内容上的实质性修改。";
    }

    private String buildFinalReviewTask(AgentContext ctx) {
        StringBuilder summary = new StringBuilder();
        summary.append("论文标题: ").append(ctx.getTopic()).append("\n");
        if (ctx.getAbstractText() != null) summary.append("摘要: ").append(ctx.getAbstractText()).append("\n");
        summary.append("\n章节概况:\n");
        ctx.getSections().forEach((title, content) ->
            summary.append("  - ").append(title).append(" (").append(content.length()).append(" 字符)\n"));
        if (!ctx.getReviewComments().isEmpty()) {
            summary.append("\n审稿意见摘要:\n");
            ctx.getReviewComments().forEach(c -> summary.append("  - ").append(c).append("\n"));
        }
        return "请对以下论文进行最终审核。\n\n" + summary + "\n" +
                "请从以下方面综合评价：\n1. 研究贡献和创新点\n2. 方法学严谨性\n3. 论证完整性和逻辑性\n" +
                "4. 写作质量\n5. 整体学术水平\n\n请给出是否建议发表/提交的明确结论，并附上最终改进建议。";
    }

    private void loadKgIntoContext(AgentContext ctx, Long kgId) {
        if (kgId == null) return;
        try {
            com.paperai.model.entity.KnowledgeGraph kg = knowledgeGraphService.getById(kgId);
            if (kg != null && kg.getGraphData() != null) {
                ctx.setKgGraphData(kg.getGraphData());
                log.info("已加载知识图谱到写作上下文: kgId={}, name={}", kgId, kg.getName());
            }
        } catch (Exception e) {
            log.warn("加载知识图谱失败 kgId={}: {}", kgId, e.getMessage());
        }
    }

    private String buildKgContext(AgentContext ctx) {
        String kgData = ctx.getKgGraphData();
        if (kgData == null || kgData.isBlank()) return "";
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(kgData);
            com.fasterxml.jackson.databind.JsonNode nodes = root.get("nodes");
            com.fasterxml.jackson.databind.JsonNode edges = root.get("edges");
            if (nodes == null || nodes.size() == 0) return "";
            StringBuilder sb = new StringBuilder("\n\n【参考知识图谱】\n实体列表：");
            for (com.fasterxml.jackson.databind.JsonNode n : nodes) {
                String label = n.has("data") ? n.get("data").get("label").asText()
                        : (n.has("label") ? n.get("label").asText() : "");
                if (!label.isBlank()) sb.append("\n- ").append(label);
            }
            if (edges != null && edges.size() > 0) {
                sb.append("\n已知关系：");
                for (com.fasterxml.jackson.databind.JsonNode e : edges) {
                    String relLabel = e.has("data") ? e.get("data").get("label").asText() : "";
                    if (!relLabel.isBlank()) sb.append("\n- ").append(relLabel);
                }
            }
            sb.append("\n请在撰写时保持与图谱中概念和关系的一致性。");
            return sb.toString();
        } catch (Exception ex) { return ""; }
    }

    private String buildFinalDraft(AgentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(ctx.getTopic()).append("\n\n");
        if (ctx.getAbstractText() != null) sb.append("## 摘要\n").append(ctx.getAbstractText()).append("\n\n");
        ctx.getSections().forEach((t, c) -> sb.append("## ").append(t).append("\n").append(c).append("\n\n"));
        return sb.toString();
    }

    private PaperWritingVO toWritingVO(Long paperId, AgentContext ctx, List<PaperWritingVO.StepRecordVO> steps,
                                       long totalMs, String finalDraft) {
        PaperWritingVO vo = new PaperWritingVO();
        vo.setContextId(ctx.getContextId()); vo.setPaperId(paperId); vo.setTopic(ctx.getTopic());
        vo.setFinalDraft(finalDraft); vo.setAbstractText(ctx.getAbstractText());
        vo.setSections(ctx.getSections().entrySet().stream().map(e -> {
            PaperWritingVO.SectionVO sv = new PaperWritingVO.SectionVO();
            sv.setTitle(e.getKey()); sv.setLength(e.getValue().length());
            return sv;
        }).toList());
        vo.setReviewComments(ctx.getReviewComments()); vo.setSteps(steps);
        vo.setStatus(ctx.getFinalDraft() != null ? "COMPLETED" : "PARTIAL");
        vo.setTotalDurationMs(totalMs); vo.setCreatedAt(ctx.getCreatedAt());
        return vo;
    }

    private String truncate(String s, int max) {
        return s == null ? "" : s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
