package com.paperai.service;

import com.paperai.agent.*;
import com.paperai.agent.base.BaseAgent;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.entity.Paper;
import com.paperai.model.entity.Task;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import com.paperai.model.vo.PaperWritingVO;
import com.paperai.common.Constants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 编排引擎 — 将 5 个 Agent 串成完整论文写作流程。
 * 每步执行结果持久化到 paper / task 表。
 *
 * @author: ch
 * @date 2026年05月11日
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

    /** 步骤计数器 */
    private int stepSeq = 0;

    /** SSE 回调（非 null 时每步推事件） */
    private Consumer<PaperWritingVO.StepRecordVO> stepCallback = null;

    /**
     * 执行完整论文写作流程（同步，返回最终 VO）
     */
    public PaperWritingVO execute(PaperWritingRequestDTO request) {
        this.stepCallback = null;
        return doExecute(request);
    }

    /**
     * 执行完整论文写作流程（SSE 流式，每步通过 callback 推送）
     */
    public void executeStream(PaperWritingRequestDTO request, Consumer<PaperWritingVO.StepRecordVO> callback) {
        this.stepCallback = callback;
        doExecute(request);
    }

    private PaperWritingVO doExecute(PaperWritingRequestDTO request) {
        long startTime = System.currentTimeMillis();

        // 1. 先创建论文记录
        Paper paper = paperService.createPaper(request);
        Long paperId = paper.getId();
        log.info("论文记录已创建: id={}", paperId);

        AgentContext context = new AgentContext(UUID.randomUUID().toString(), request.getTopic());
        List<PaperWritingVO.StepRecordVO> steps = new ArrayList<>();

        log.info("===== 论文写作流程开始 =====");
        log.info("主题: {}, paperId={}", request.getTopic(), paperId);

        try {
            // ===== Step 1: 选题评估 =====
            step(paperId, "选题评估", AgentRole.SUPERVISOR, steps, () -> {
                context.setAttribute("direction", request.getDescription());
                return supervisorAgent.evaluateTopic(request.getTopic(), request.getDescription());
            });

            // ===== Step 2: 文献调研 =====
            step(paperId, "文献调研", AgentRole.RESEARCHER, steps, () -> {
                String taskDesc = "研究主题：" + request.getTopic();
                if (request.getKeywords() != null) taskDesc += "\n关键词：" + request.getKeywords();
                if (request.getRequirements() != null) taskDesc += "\n要求：" + request.getRequirements();
                return researcherAgent.executeTask(taskDesc, context);
            });

            // ===== Step 3: 导师审阅大纲 =====
            step(paperId, "大纲审阅", AgentRole.SUPERVISOR, steps, () -> {
                String outline = generateOutline(context, request);
                context.setOutline(outline);
                return supervisorAgent.reviewOutline(context);
            });

            // ===== Step 4: 写手撰写全文 =====
            step(paperId, "全文撰写", AgentRole.WRITER, steps, () -> {
                List<String> sections = request.getSections();
                if (sections == null || sections.isEmpty()) {
                    sections = parseSectionsFromOutline(context.getOutline());
                }
                for (String section : sections) {
                    String taskDesc = "请撰写论文的【" + section + "】章节。\n基于已有大纲和研究材料展开。";
                    writerAgent.executeTask(taskDesc, context);
                }
                return "完成 " + sections.size() + " 个章节的撰写";
            });

            // ===== Step 5: 审稿 + 迭代修改 =====
            int maxRounds = request.getMaxReviewRounds() != null ? request.getMaxReviewRounds() : 3;
            for (int round = 1; round <= maxRounds; round++) {
                step(paperId, "审稿迭代#" + round, AgentRole.REVIEWER, steps, () -> {
                    String review = reviewerAgent.reviewFullPaper(context);
                    context.addReviewComment(review);
                    return review;
                });

                String lastReview = getLastReview(steps);
                if (lastReview != null && !lastReview.contains("严重问题")) {
                    log.info("审稿无严重问题，结束迭代");
                    break;
                }

                if (round < maxRounds) {
                    step(paperId, "修改#" + round, AgentRole.WRITER, steps, () -> {
                        return writerAgent.executeTask(
                                "请根据审稿意见修改论文，解决提出的问题。\n审稿意见：" + lastReview,
                                context
                        );
                    });
                }
            }

            // ===== Step 6: 润色定稿 =====
            step(paperId, "润色定稿", AgentRole.POLISHER, steps, () -> {
                return polisherAgent.polishFullPaper(context);
            });

            // ===== Step 7: 最终审核 =====
            step(paperId, "最终审核", AgentRole.SUPERVISOR, steps, () -> {
                return supervisorAgent.finalReview(context);
            });

            // 构建最终稿 → 写回数据库
            String finalDraft = buildFinalDraft(context);
            context.setFinalDraft(finalDraft);
            paperService.updateContent(paperId, finalDraft);
            paperService.updateStatus(paperId, Constants.PAPER_STATUS_COMPLETED);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("===== 论文写作流程完成，总耗时 {}ms =====", totalTime);

            return toWritingVO(paperId, context, steps, totalTime, finalDraft);

        } catch (Exception e) {
            log.error("论文写作流程异常中断: {}", e.getMessage(), e);
            paperService.updateStatus(paperId, Constants.PAPER_STATUS_FAILED);
            long totalTime = System.currentTimeMillis() - startTime;
            return toWritingVO(paperId, context, steps, totalTime, null);
        }
    }

    /**
     * 执行单个步骤（含数据库持久化）
     */
    private void step(Long paperId, String name, AgentRole role,
                        List<PaperWritingVO.StepRecordVO> steps, StepExecutor executor) {
        long t = System.currentTimeMillis();
        stepSeq++;

        // ① 写入 task 表（PENDING）
        Task task = agentTaskService.createTask(paperId, role.getCode(), /* sortOrder 由 DB 自增 */ null, name);
        Long taskId = task.getId();

        log.info("→ Step#{}: [{}] {} 开始... taskId={}", stepSeq, role.getDisplayName(), name, taskId);
        agentTaskService.updateStatus(taskId, TaskStatus.IN_PROGRESS);

        try {
            String result = executor.execute();
            long elapsed = System.currentTimeMillis() - t;

            // ② 更新 task 表（COMPLETED + 输出）
            agentTaskService.updateOutput(taskId, result, elapsed);
            log.info("  ✓ [{}] {} 完成 ({}ms)", role.getDisplayName(), name, elapsed);

            addStep(paperId, steps, name, role, TaskStatus.COMPLETED, elapsed, truncate(result, 100), result);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t;
            log.error("  ✗ [{}] {} 失败: {}", role.getDisplayName(), name, e.getMessage());

            // ③ 更新 task 表（FAILED）
            agentTaskService.updateOutput(taskId, e.getMessage(), elapsed);
            agentTaskService.updateStatus(taskId, TaskStatus.FAILED);

            addStep(paperId, steps, name, role, TaskStatus.FAILED, elapsed, e.getMessage(), e.getMessage());
            throw e;
        }
    }

    private void addStep(Long paperId, List<PaperWritingVO.StepRecordVO> steps, String name, AgentRole role,
                         TaskStatus status, long ms, String summary, String fullOutput) {
        PaperWritingVO.StepRecordVO s = new PaperWritingVO.StepRecordVO();
        s.setAgentName(name);
        s.setAgentRole(role);
        s.setStatus(status);
        s.setDurationMs(ms);
        s.setSummary(summary);
        s.setFullOutput(fullOutput);
        steps.add(s);
        // ① 如果有 SSE 回调，立即推送
        if (stepCallback != null) {
            stepCallback.accept(s);
        }
        // ② WebSocket STOMP 广播
        if (stepEventPublisher != null) {
            stepEventPublisher.publishStep(paperId, s);
        }
    }

    @FunctionalInterface
    private interface StepExecutor {
        String execute();
    }

    /**
     * 简单生成大纲（后续可抽成 Agent 调用）
     */
    private String generateOutline(AgentContext ctx, PaperWritingRequestDTO req) {
        List<String> sections = req.getSections();
        if (sections != null && !sections.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sections.size(); i++) {
                sb.append(i + 1).append(". ").append(sections.get(i)).append("\n");
            }
            return sb.toString();
        }
        return "1. 引言\n2. 相关工作\n3. 方法\n4. 实验\n5. 结论";
    }

    private List<String> parseSectionsFromOutline(String outline) {
        List<String> sections = new ArrayList<>();
        if (outline == null) return List.of("引言", "相关工作", "方法", "实验", "结论");
        for (String line : outline.split("\n")) {
            String s = line.replaceAll("^\\d+[.、]\\s*", "").trim();
            if (!s.isEmpty()) sections.add(s);
        }
        return sections.isEmpty() ? List.of("引言", "相关工作", "方法", "实验", "结论") : sections;
    }

    private String getLastReview(List<PaperWritingVO.StepRecordVO> steps) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).getAgentRole() == AgentRole.REVIEWER) {
                return steps.get(i).getSummary();
            }
        }
        return null;
    }

    private String buildFinalDraft(AgentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(ctx.getTopic()).append("\n\n");
        if (ctx.getAbstractText() != null) {
            sb.append("## 摘要\n").append(ctx.getAbstractText()).append("\n\n");
        }
        ctx.getSections().forEach((title, content) -> {
            sb.append("## ").append(title).append("\n").append(content).append("\n\n");
        });
        return sb.toString();
    }

    private PaperWritingVO toWritingVO(Long paperId, AgentContext ctx, List<PaperWritingVO.StepRecordVO> steps,
                                       long totalMs, String finalDraft) {
        PaperWritingVO vo = new PaperWritingVO();
        vo.setContextId(ctx.getContextId());
        vo.setPaperId(paperId);
        vo.setTopic(ctx.getTopic());
        vo.setFinalDraft(finalDraft);
        vo.setAbstractText(ctx.getAbstractText());
        vo.setSections(ctx.getSections().entrySet().stream().map(e -> {
            PaperWritingVO.SectionVO s = new PaperWritingVO.SectionVO();
            s.setTitle(e.getKey());
            s.setLength(e.getValue().length());
            return s;
        }).toList());
        vo.setReviewComments(ctx.getReviewComments());
        vo.setSteps(steps);
        vo.setStatus(ctx.isAllTasksCompleted() ? "COMPLETED" : "PARTIAL");
        vo.setTotalDurationMs(totalMs);
        vo.setCreatedAt(ctx.getCreatedAt());
        return vo;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
