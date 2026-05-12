package com.paperai.agent;

import com.paperai.agent.base.BaseAgent;
import com.paperai.model.enums.AgentMessageType;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 导师 Agent（Supervisor）
 * 职责：把控研究方向，审阅大纲，给出修改意见，最终审核
 * 工作流程：
 * 1. 接收研究生题和初始方向
 * 2. 评估选题的可行性和创新性
 * 3. 审阅研究大纲并给出指导意见
 * 4. 审核各阶段产出
 * 5. 最终评估论文质量
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Component
@Slf4j
public class SupervisorAgent extends BaseAgent {

    public SupervisorAgent(ChatClient dashScopeChatClient) {
        super(AgentRole.SUPERVISOR, dashScopeChatClient);
    }

    @Override
    protected String getSystemPrompt() {
        return """
                # 角色：博士生导师 (PhD Supervisor)

                你是一位经验丰富的博士生导师，在多个顶级期刊担任编委。你的核心能力包括：

                ## 专业能力
                1. 选题评估：判断研究选题的学术价值、创新性和可行性
                2. 方向把控：确保研究不偏离核心方向，聚焦关键科学问题
                3. 质量把关：对各阶段产出进行质量评估
                4. 学术指导：给出建设性的指导意见，帮助学生成长
                5. 全局视野：从更高的学术视角审视研究的整体贡献

                ## 指导原则
                - 高标准：对学术质量有严格要求
                - 启发性：引导学生思考，而非直接给答案
                - 建设性：批评的同时给出改进方向
                - 阶段性：根据学生水平给出适当难度的指导

                ## 输出格式
                请按以下结构输出指导意见：

                ### 总体评价
                [对当前工作的整体评价]

                ### 肯定之处
                - [做得好的方面]

                ### 需要改进
                - [具体问题] → [改进建议]

                ### 下一步工作
                - [建议的下一步行动]

                ### 综合评分
                [1-10分] / 10
                """;
    }

    @Override
    public String executeTask(String task, AgentContext context) {
        return executeTaskStream(task, context, null);
    }

    @Override
    public String executeTaskStream(String task, AgentContext context, java.util.function.Consumer<String> onToken) {
        this.context = context;
        this.context.updateTaskStatus(role.getCode(), TaskStatus.IN_PROGRESS);

        long startTime = System.currentTimeMillis();
        log.info("[{}] 开始执行指导任务", role.getDisplayName());

        try {
            String contextInfo = buildSupervisionContext();
            String response = onToken != null
                    ? callLlmStream(task, onToken)  // TODO: callLlmStream 不支持带extraContext，先用同步
                    : callLlmWithContext(task, contextInfo);

            this.context.updateTaskStatus(role.getCode(), TaskStatus.COMPLETED);
            broadcast(AgentMessageType.COORDINATION, "导师已完成审阅指导");

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] 指导完成，耗时 {}ms", role.getDisplayName(), elapsed);

            return response;

        } catch (Exception e) {
            log.error("[{}] 指导失败: {}", role.getDisplayName(), e.getMessage(), e);
            this.context.updateTaskStatus(role.getCode(), TaskStatus.FAILED);
            throw new RuntimeException("导师 Agent 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 评估选题
     *
     * @param topic       研究主题
     * @param description 研究方向描述
     * @return 选题评估意见
     */
    public String evaluateTopic(String topic, String description) {
        String task = "请评估以下研究选题的学术价值和可行性：\n\n" +
                "研究主题: " + topic + "\n\n" +
                "方向描述: " + (description != null ? description : "无") + "\n\n" +
                "请从创新性、可行性、学术价值、研究潜力四个维度进行评价，并给出是否建议继续的建议。";

        if (context != null) {
            return executeTask(task, context);
        }
        return callLlm(task);
    }

    /**
     * 审阅大纲
     *
     * @param ctx AgentContext 上下文
     * @return 大纲审阅意见
     */
    public String reviewOutline(AgentContext ctx) {
        this.context = ctx;
        if (ctx.getOutline() == null) {
            return "尚无大纲可供审阅";
        }

        String task = "请审阅以下论文大纲，评估结构是否合理、章节安排是否科学：\n\n" +
                ctx.getOutline() + "\n\n" +
                "请重点关注：\n" +
                "1. 整体结构是否逻辑清晰\n" +
                "2. 章节划分是否合理\n" +
                "3. 研究重点是否突出\n" +
                "4. 是否有遗漏的重要内容";

        return executeTask(task, ctx);
    }

    /**
     * 最终审核 — 给出是否可发表的结论
     *
     * @param ctx AgentContext 上下文
     * @return 最终审核报告
     */
    public String finalReview(AgentContext ctx) {
        this.context = ctx;

        StringBuilder paperSummary = new StringBuilder();
        paperSummary.append("论文标题: ").append(ctx.getTopic()).append("\n");

        if (ctx.getAbstractText() != null) {
            paperSummary.append("摘要: ").append(ctx.getAbstractText()).append("\n");
        }

        paperSummary.append("\n章节概况:\n");
        ctx.getSections().forEach((title, content) -> {
            paperSummary.append("  - ").append(title).append(" (").append(content.length()).append(" 字符)\n");
        });

        if (!ctx.getReviewComments().isEmpty()) {
            paperSummary.append("\n审稿意见摘要:\n");
            ctx.getReviewComments().forEach(c -> paperSummary.append("  - ").append(c).append("\n"));
        }

        String task = "请对上述论文进行最终审核。\n\n" +
                "请从以下方面综合评价：\n" +
                "1. 研究贡献和创新点\n" +
                "2. 方法学严谨性\n" +
                "3. 论证完整性和逻辑性\n" +
                "4. 写作质量\n" +
                "5. 整体学术水平\n\n" +
                "请给出是否建议发表/提交的明确结论，并附上最终改进建议。";

        return executeTask(task, ctx);
    }

    /**
     * 构建指导上下文
     */
    private String buildSupervisionContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("论文主题: ").append(context.getTopic()).append("\n");

        if (context.getAbstractText() != null) {
            sb.append("\n摘要:\n").append(context.getAbstractText()).append("\n");
        }

        if (context.getKeywords() != null && !context.getKeywords().isEmpty()) {
            sb.append("关键词: ").append(String.join(", ", context.getKeywords())).append("\n");
        }

        if (context.getResearchOutput() != null) {
            sb.append("\n研究综述:\n")
                    .append(context.getResearchOutput().length() > 300 ?
                            context.getResearchOutput().substring(0, 300) + "..." :
                            context.getResearchOutput())
                    .append("\n");
        }

        if (context.getOutline() != null) {
            sb.append("\n论文大纲:\n").append(context.getOutline()).append("\n");
        }

        if (!context.getSections().isEmpty()) {
            sb.append("\n已完成的章节:\n");
            context.getSections().forEach((title, content) -> {
                String preview = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                sb.append("  [").append(title).append("]: ").append(preview).append("\n");
            });
        }

        if (!context.getReviewComments().isEmpty()) {
            sb.append("\n审稿意见:\n");
            context.getReviewComments().forEach(c -> sb.append("  - ").append(c).append("\n"));
        }

        sb.append("\n当前各 Agent 状态:\n");
        context.getTaskStatusMap().forEach((role, status) ->
                sb.append("  - ").append(role).append(": ").append(status.getCode()).append("\n"));

        return sb.toString();
    }
}
