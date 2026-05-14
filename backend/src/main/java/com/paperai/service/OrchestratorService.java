package com.paperai.service;

import com.paperai.agent.*;
import com.paperai.agent.base.BaseAgent;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.entity.FlowDefinition;
import com.paperai.model.entity.Paper;
import com.paperai.model.entity.Task;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import com.paperai.model.flow.FlowProfile;
import com.paperai.model.vo.PaperWritingVO;
import com.paperai.common.Constants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 编排引擎 — 根据流程定义（预设 FlowProfile 或自定义 FlowDefinition）串行执行 Agent。
 * 每步通过 SSE 逐 token 流式推送到前端。
 */
@Slf4j
@Service
public class OrchestratorService {

    @Resource
    private SupervisorAgent supervisorAgent;
    @Resource
    private ResearcherAgent researcherAgent;
    @Resource
    private WriterAgent writerAgent;
    @Resource
    private ReviewerAgent reviewerAgent;
    @Resource
    private PolisherAgent polisherAgent;
    @Resource
    private PaperService paperService;
    @Resource
    private AgentTaskService agentTaskService;
    @Resource
    private StepEventPublisher stepEventPublisher;
    @Resource
    private FlowDefinitionService flowDefinitionService;
    @Resource
    private FlowEngine flowEngine;

    private int stepSeq = 0;
    private Consumer<PaperWritingVO.StepRecordVO> stepCallback = null;

    /**
     * 运行中的任务：paperId → 中断标记
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, Boolean> runningTasks = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 停止指定 paperId 的写作任务
     */
    public void stopTask(Long paperId) {
        runningTasks.put(paperId, true);
        log.info("收到停止请求 paperId={}", paperId);
    }

    public boolean isRunning(Long paperId) {
        return runningTasks.containsKey(paperId) && !runningTasks.get(paperId);
    }

    /**
     * 同步执行
     */
    public PaperWritingVO execute(PaperWritingRequestDTO req) {
        return execute(req, null);
    }

    public PaperWritingVO execute(PaperWritingRequestDTO req, Long userId) {
        this.stepCallback = null;
        return doExecute(req, userId);
    }

    /**
     * SSE 流式执行
     */
    public void executeStream(PaperWritingRequestDTO req, Consumer<PaperWritingVO.StepRecordVO> cb) {
        executeStream(req, cb, null);
    }

    public void executeStream(PaperWritingRequestDTO req, Consumer<PaperWritingVO.StepRecordVO> cb, Long userId) {
        this.stepCallback = cb;
        doExecute(req, userId);
    }

    /**
     * 异步执行（WebSocket 实时推送）
     */
    public void executeAsync(Long paperId, PaperWritingRequestDTO req) {
        // 自定义流程 → FlowEngine 执行
        if (isCustomFlow(req.getFlowId())) {
            Long dbId = Long.parseLong(req.getFlowId().substring(7));
            FlowDefinition def = flowDefinitionService.getById(dbId);
            flowEngine.execute(paperId, def, req);
            return;
        }
        // 预设流程 → 标准执行
        this.stepCallback = null;
        this.stepSeq = 0;
        runningTasks.put(paperId, false);
        AgentContext ctx = new AgentContext(UUID.randomUUID().toString(), req.getTopic());
        List<PaperWritingVO.StepRecordVO> steps = new ArrayList<>();
        long start = System.currentTimeMillis();
        FlowProfile flow = resolveFlow(req.getFlowId());

        log.info("===== 异步写作开始 paperId={}, flow={} =====", paperId, flow.getId());
        try {
            paperService.getPaperById(paperId);
            runSteps(paperId, ctx, req, steps, flow);
            finish(paperId, ctx, start, null);
        } catch (Exception e) {
            if (runningTasks.getOrDefault(paperId, false)) {
                log.info("任务被用户停止 paperId={}", paperId);
                stepEventPublisher.publishError(paperId, "任务已被停止");
            } else {
                log.error("异步写作异常 paperId={}: {}", paperId, e.getMessage(), e);
                paperService.updateStatus(paperId, Constants.PAPER_STATUS_FAILED);
                stepEventPublisher.publishError(paperId, e.getMessage() != null ? e.getMessage() : "写作异常");
            }
        } finally {
            runningTasks.remove(paperId);
        }
    }

    private PaperWritingVO doExecute(PaperWritingRequestDTO req, Long userId) {
        this.stepSeq = 0;
        long start = System.currentTimeMillis();
        Paper paper = paperService.createPaper(req, userId != null ? userId : 0L);
        Long paperId = paper.getId();
        AgentContext ctx = new AgentContext(UUID.randomUUID().toString(), req.getTopic());
        List<PaperWritingVO.StepRecordVO> steps = new ArrayList<>();
        FlowProfile flow = resolveFlow(req.getFlowId());

        try {
            runSteps(paperId, ctx, req, steps, flow);
            return finish(paperId, ctx, start, steps);
        } catch (Exception e) {
            paperService.updateStatus(paperId, Constants.PAPER_STATUS_FAILED);
            return toWritingVO(paperId, ctx, steps, System.currentTimeMillis() - start, null);
        }
    }

    /**
     * 按流程定义执行步骤 — 每个步骤由 FlowProfile 控制是否启用
     */
    private void runSteps(Long paperId, AgentContext ctx, PaperWritingRequestDTO req,
                          List<PaperWritingVO.StepRecordVO> steps, FlowProfile flow) {
        ctx.setAttribute("direction", req.getDescription() != null ? req.getDescription() : "");

        // 1. 选题评估
        if (flow.isTopicEvaluation()) {
            step(paperId, "选题评估", AgentRole.SUPERVISOR, steps, supervisorAgent, ctx,
                () -> supervisorAgent.evaluateTopic(req.getTopic(), req.getDescription()));
        }

        // 2. 文献调研
        if (flow.isLiteratureResearch()) {
            step(paperId, "文献调研", AgentRole.RESEARCHER, steps, researcherAgent, ctx, () -> {
                String d = "研究主题：" + req.getTopic();
                if (req.getKeywords() != null) d += "\n关键词：" + req.getKeywords();
                if (req.getRequirements() != null) d += "\n要求：" + req.getRequirements();
                return d;
            });
        }

        // 3. 大纲生成（始终执行，引擎内部操作）
        String outline = generateOutline(ctx, req);
        ctx.setOutline(outline);

        // 4. 大纲审阅
        if (flow.isOutlineReview()) {
            step(paperId, "大纲审阅", AgentRole.SUPERVISOR, steps, supervisorAgent, ctx,
                () -> supervisorAgent.reviewOutline(ctx));
        }

        // 5. 逐章节写作
        if (flow.isWriteSections()) {
            List<String> sections = req.getSections();
            if (sections == null || sections.isEmpty()) sections = parseSections(outline);
            for (String sec : sections)
                step(paperId, sec, AgentRole.WRITER, steps, writerAgent, ctx,
                    () -> "请撰写论文的【" + sec + "】章节。\n基于已有大纲和研究材料展开。");
        }

        // 6. 审稿迭代
        if (flow.isReviewIteration()) {
            int maxR = flow.getForceReviewRounds() != null
                ? flow.getForceReviewRounds()
                : (req.getMaxReviewRounds() != null ? req.getMaxReviewRounds() : 3);
            for (int r = 1; r <= maxR; r++) {
                step(paperId, "审稿迭代#" + r, AgentRole.REVIEWER, steps, reviewerAgent, ctx,
                    () -> reviewerAgent.reviewFullPaper(ctx));
                String lr = getLastReview(steps);
                if (lr != null && !lr.contains("严重问题")) break;
                if (r < maxR)
                    step(paperId, "修改#" + r, AgentRole.WRITER, steps, writerAgent, ctx,
                        () -> "请根据审稿意见修改：" + lr);
            }
        }

        // 7. 润色定稿
        if (flow.isPolish()) {
            step(paperId, "润色定稿", AgentRole.POLISHER, steps, polisherAgent, ctx,
                () -> polisherAgent.polishFullPaper(ctx));
        }

        // 8. 最终审核
        if (flow.isFinalReview()) {
            step(paperId, "最终审核", AgentRole.SUPERVISOR, steps, supervisorAgent, ctx,
                () -> supervisorAgent.finalReview(ctx));
        }
    }

    /**
     * 解析 flowId → FlowProfile。预设走枚举值，自定义流程返回 STANDARD 作为信号
     * 值（实际执行由 executeAsync 委托给 FlowEngine）。
     */
    private FlowProfile resolveFlow(String flowId) {
        FlowProfile preset = FlowProfile.fromIdRaw(flowId);
        return preset != null ? preset : FlowProfile.STANDARD;
    }

    /** flowId 是否是自定义流程（需 FlowEngine 执行） */
    private boolean isCustomFlow(String flowId) {
        return flowId != null && flowId.startsWith("custom-");
    }

    private PaperWritingVO finish(Long paperId, AgentContext ctx, long start, List<PaperWritingVO.StepRecordVO> steps) {
        String draft = buildFinalDraft(ctx);
        ctx.setFinalDraft(draft);

        // 保存终稿版本
        paperService.saveVersion(paperId, "FINAL", "论文终稿", draft);

        paperService.updateStatus(paperId, Constants.PAPER_STATUS_COMPLETED);
        long total = System.currentTimeMillis() - start;
        log.info("===== 写作完成 paperId={}，总耗时 {}ms =====", paperId, total);
        stepEventPublisher.publishComplete(paperId);
        return steps != null ? toWritingVO(paperId, ctx, steps, total, draft) : null;
    }

    /**
     * 核心步骤：流式推送 + DB 持久化
     */
    private String step(Long paperId, String name, AgentRole role, List<PaperWritingVO.StepRecordVO> steps, BaseAgent agent, AgentContext ctx, Supplier<String> taskSupplier) {
        // 检查是否被停止
        if (runningTasks.getOrDefault(paperId, false)) {
            throw new RuntimeException("任务已被用户停止");
        }
        long t = System.currentTimeMillis();
        stepSeq++;
        final int seq = stepSeq;

        int ver = paperService.getPaperById(paperId).getCurrentVersion();
        Task task = agentTaskService.createTask(paperId, role.getCode(), null, name, ver);
        agentTaskService.updateStatus(task.getId(), TaskStatus.IN_PROGRESS);
        log.info("→ Step#{}: [{}] {} 开始...", seq, role.getDisplayName(), name);
        stepEventPublisher.publishStreamToken(paperId, seq, name, "");

        try {
            String result = agent.executeTaskStream(taskSupplier.get(), ctx, full -> stepEventPublisher.publishStreamToken(paperId, seq, name, full));
            long elapsed = System.currentTimeMillis() - t;
            agentTaskService.updateOutput(task.getId(), result, elapsed);
            log.info("  ✓ [{}] {} 完成 ({}ms)", role.getDisplayName(), name, elapsed);
            addStep(paperId, steps, name, role, TaskStatus.COMPLETED, elapsed, truncate(result, 100), result);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t;
            log.error("  ✗ [{}] {} 失败: {}", role.getDisplayName(), name, e.getMessage());
            agentTaskService.updateOutput(task.getId(), e.getMessage(), elapsed);
            agentTaskService.updateStatus(task.getId(), TaskStatus.FAILED);
            addStep(paperId, steps, name, role, TaskStatus.FAILED, elapsed, e.getMessage(), e.getMessage());
            throw e;
        }
    }

    private void addStep(Long paperId, List<PaperWritingVO.StepRecordVO> steps, String name, AgentRole role, TaskStatus status, long ms, String summary, String fullOutput) {
        PaperWritingVO.StepRecordVO s = new PaperWritingVO.StepRecordVO();
        s.setAgentName(name);
        s.setAgentRole(role);
        s.setStatus(status);
        s.setDurationMs(ms);
        s.setSummary(summary);
        s.setFullOutput(fullOutput);
        steps.add(s);
        if (stepCallback != null) stepCallback.accept(s);
        if (stepEventPublisher != null) stepEventPublisher.publishStep(paperId, s);
    }

    // ========== 辅助 ==========
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

    private String buildFinalDraft(AgentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(ctx.getTopic()).append("\n\n");
        if (ctx.getAbstractText() != null) sb.append("## 摘要\n").append(ctx.getAbstractText()).append("\n\n");
        ctx.getSections().forEach((t, c) -> sb.append("## ").append(t).append("\n").append(c).append("\n\n"));
        return sb.toString();
    }

    private PaperWritingVO toWritingVO(Long paperId, AgentContext ctx, List<PaperWritingVO.StepRecordVO> steps, long totalMs, String finalDraft) {
        PaperWritingVO vo = new PaperWritingVO();
        vo.setContextId(ctx.getContextId());
        vo.setPaperId(paperId);
        vo.setTopic(ctx.getTopic());
        vo.setFinalDraft(finalDraft);
        vo.setAbstractText(ctx.getAbstractText());
        vo.setSections(ctx.getSections().entrySet().stream().map(e -> {
            PaperWritingVO.SectionVO sv = new PaperWritingVO.SectionVO();
            sv.setTitle(e.getKey());
            sv.setLength(e.getValue().length());
            return sv;
        }).toList());
        vo.setReviewComments(ctx.getReviewComments());
        vo.setSteps(steps);
        vo.setStatus(ctx.isAllTasksCompleted() ? "COMPLETED" : "PARTIAL");
        vo.setTotalDurationMs(totalMs);
        vo.setCreatedAt(ctx.getCreatedAt());
        return vo;
    }

    private String truncate(String s, int max) {
        return s == null ? "" : s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
