package com.paperai.agent;

import com.paperai.agent.base.BaseAgent;
import com.paperai.model.enums.AgentMessageType;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 润色 Agent（Polisher）
 * 职责：语法校对、格式规范、参考文献检查、语言润色
 * 工作流程：
 * 1. 接收待润色文本
 * 2. 逐项检查（语法、格式、引用、术语一致性）
 * 3. 输出润色后的文本和修改说明
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Component
@Slf4j
public class PolisherAgent extends BaseAgent {

    public PolisherAgent(ChatClient dashScopeChatClient) {
        super(AgentRole.POLISHER, dashScopeChatClient);
    }

    @Override
    protected String getSystemPrompt() {
        return """
                # 角色：学术编辑 / 润色专家 (Academic Editor)

                你是一位专业的学术论文编辑，精通学术写作规范和语言润色。你的核心能力包括：

                ## 专业能力
                1. 语法校对：修正语法错误、语病和标点符号使用不当
                2. 表达优化：改进句式结构，使表达更流畅、简洁、有力
                3. 术语规范：确保专业术语使用准确一致
                4. 格式规范：检查论文格式是否符合学术标准（标题层级、段落、图表标注等）
                5. 参考文献：检查引用格式是否规范、前后一致
                6. 逻辑连贯：优化段落和句子间的过渡，增强可读性

                ## 润色原则
                - 保持原意：不改变作者的核心观点和论证
                - 最小改动：尽量用最小的修改达到最好的效果
                - 风格一致：保持全文语言风格统一
                - 逐项说明：对每处修改给出理由

                ## 输出格式
                请按以下结构输出润色结果：

                ### 润色后文本
                [完整的润色后内容]

                ### 修改说明
                - [修改位置]: [原内容] → [修改后内容] ([修改原因])

                ### 总体评价
                [语言质量和改进空间的简要评价]

                ### 改进建议
                - [进一步的改进建议]
                """;
    }

    @Override
    public String executeTask(String task, AgentContext context) {
        this.context = context;
        this.context.updateTaskStatus(role.getCode(), TaskStatus.IN_PROGRESS);

        long startTime = System.currentTimeMillis();
        log.info("[{}] 开始执行润色任务", role.getDisplayName());

        try {
            String contextInfo = buildContextInfo();
            String response = callLlmWithContext(task, contextInfo);

            this.context.updateTaskStatus(role.getCode(), TaskStatus.COMPLETED);
            broadcast(AgentMessageType.TASK_RESULT, "润色已完成");

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] 润色完成，耗时 {}ms", role.getDisplayName(), elapsed);

            return response;

        } catch (Exception e) {
            log.error("[{}] 润色失败: {}", role.getDisplayName(), e.getMessage(), e);
            this.context.updateTaskStatus(role.getCode(), TaskStatus.FAILED);
            throw new RuntimeException("润色 Agent 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 润色论文全文
     *
     * @param ctx AgentContext 上下文
     * @return 润色后的完整论文
     */
    public String polishFullPaper(AgentContext ctx) {
        this.context = ctx;

        StringBuilder fullText = new StringBuilder();
        fullText.append("论文标题: ").append(ctx.getTopic()).append("\n\n");

        if (ctx.getAbstractText() != null) {
            fullText.append("摘要:\n").append(ctx.getAbstractText()).append("\n\n");
        }

        if (!ctx.getSections().isEmpty()) {
            ctx.getSections().forEach((title, content) -> {
                fullText.append("### ").append(title).append("\n").append(content).append("\n\n");
            });
        }

        String task = fullText.toString() + "\n\n请对上述论文全文进行润色，包括语法修正、表达优化、格式规范检查。\n" +
                "注意保持学术风格，不做内容上的实质性修改。";

        return executeTask(task, ctx);
    }

    /**
     * 润色指定文本
     *
     * @param text   待润色文本
     * @param contextText 上下文说明
     * @return 润色结果
     */
    public String polishText(String text, String contextText) {
        String task = "请对以下文本进行润色：\n\n" + text;
        if (contextText != null && !contextText.isBlank()) {
            task = "上下文说明:\n" + contextText + "\n\n" + task;
        }

        long startTime = System.currentTimeMillis();
        log.info("[{}] 开始润色文本", role.getDisplayName());

        String response = callLlm(task);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[{}] 润色完成，耗时 {}ms", role.getDisplayName(), elapsed);

        return response;
    }

    /**
     * 检查参考文献格式
     *
     * @param references 参考文献列表
     * @return 检查结果
     */
    public String checkReferences(String references) {
        String task = "请检查以下参考文献的格式是否规范，指出格式不一致或错误的地方，并给出修正建议：\n\n" + references;

        return callLlm(task);
    }

    /**
     * 构建上下文信息
     */
    private String buildContextInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("论文主题: ").append(context.getTopic()).append("\n");

        if (context.getAbstractText() != null) {
            sb.append("\n当前摘要:\n").append(context.getAbstractText()).append("\n");
        }

        if (!context.getSections().isEmpty()) {
            sb.append("\n已有章节:\n");
            context.getSections().forEach((title, content) -> {
                sb.append("  - ").append(title).append(" (").append(content.length()).append(" 字符)\n");
            });
        }

        if (!context.getReviewComments().isEmpty()) {
            sb.append("\n审稿意见(润色时需要参考):\n");
            context.getReviewComments().forEach(c -> sb.append("  - ").append(c).append("\n"));
        }

        return sb.toString();
    }
}
