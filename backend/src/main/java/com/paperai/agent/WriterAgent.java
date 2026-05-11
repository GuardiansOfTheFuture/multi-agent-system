package com.paperai.agent;

import com.paperai.agent.base.BaseAgent;
import com.paperai.model.enums.AgentMessageType;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 写手 Agent（Writer）
 * 职责：撰写论文各章节，组织语言，构建论证逻辑
 * 工作流程：
 * 1. 接收研究综述和论文大纲
 * 2. 按章节逐步撰写内容
 * 3. 构建连贯的论证逻辑
 * 4. 输出符合学术规范的文本
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Component
@Slf4j
public class WriterAgent extends BaseAgent {

    public WriterAgent(ChatClient dashScopeChatClient) {
        super(AgentRole.WRITER, dashScopeChatClient);
    }

    @Override
    protected String getSystemPrompt() {
        return """
                # 角色：学术写作者 (Academic Writer)

                你是一位经验丰富的学术论文写作者，擅长将研究成果转化为清晰、严谨、有说服力的学术论文。你的核心能力包括：

                ## 专业能力
                1. 论文结构设计：能够根据研究内容设计合理的论文框架
                2. 学术写作：精通学术写作规范，语言准确、简洁、客观
                3. 论证构建：善于组织论据，构建严密的逻辑链条
                4. 章节撰写：熟悉论文各章节（引言、方法、结果、讨论、结论）的写作要点

                ## 写作原则
                - 保持客观中立的学术语气
                - 使用准确的专业术语
                - 段落之间要有清晰的逻辑过渡
                - 每个段落围绕一个核心观点展开
                - 避免冗余和重复表达
                - 注意中文学术写作习惯

                ## 输出要求
                请按以下格式输出论文内容：

                ### 章节标题
                [章节内容正文]

                每个章节输出时，需标注该章节在论文中的定位和作用。
                """;
    }

    @Override
    public String executeTask(String task, AgentContext context) {
        this.context = context;
        this.context.updateTaskStatus(role.getCode(), TaskStatus.IN_PROGRESS);

        long startTime = System.currentTimeMillis();
        log.info("[{}] 开始执行写作任务", role.getDisplayName());

        try {
            String contextInfo = buildContextInfo();
            String response = callLlmWithContext(task, contextInfo);

            String sectionTitle = extractSectionTitle(task, response);
            if (sectionTitle != null) {
                this.context.addSection(sectionTitle, response);
            }
            this.context.updateTaskStatus(role.getCode(), TaskStatus.COMPLETED);

            broadcast(AgentMessageType.TASK_RESULT,
                    "写手已完成章节写作: " + (sectionTitle != null ? sectionTitle : "未知章节"));

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] 写作完成，耗时 {}ms", role.getDisplayName(), elapsed);

            return response;

        } catch (Exception e) {
            log.error("[{}] 写作失败: {}", role.getDisplayName(), e.getMessage(), e);
            this.context.updateTaskStatus(role.getCode(), TaskStatus.FAILED);
            throw new RuntimeException("写手 Agent 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 撰写论文完整初稿
     *
     * @param sections 需要撰写的章节列表
     * @param ctx      AgentContext 上下文
     * @return 完整初稿
     */
    public String writeFullDraft(java.util.List<String> sections, AgentContext ctx) {
        this.context = ctx;

        StringBuilder fullDraft = new StringBuilder();
        fullDraft.append("# ").append(ctx.getTopic()).append("\n\n");

        for (String section : sections) {
            log.info("[{}] 开始撰写章节: {}", role.getDisplayName(), section);
            String task = "请撰写论文的【" + section + "】章节。\n\n" +
                    "注意：\n" +
                    "1. 确保与前文逻辑连贯\n" +
                    "2. 使用规范的学术语言\n" +
                    "3. 内容要充实且有深度\n" +
                    "4. 篇幅适中，重点突出";

            String content = executeTask(task, ctx);
            fullDraft.append(content).append("\n\n---\n\n");
        }

        return fullDraft.toString();
    }

    /**
     * 从任务描述和响应中提取章节标题
     */
    private String extractSectionTitle(String task, String response) {
        // 尝试从任务中提取章节名
        int start = task.indexOf("【");
        int end = task.indexOf("】");
        if (start != -1 && end > start) {
            return task.substring(start + 1, end);
        }
        // 尝试从响应中提取标题
        if (response != null && response.startsWith("### ")) {
            int lineEnd = response.indexOf("\n");
            if (lineEnd != -1) {
                return response.substring(4, lineEnd).trim();
            }
        }
        return null;
    }

    /**
     * 构建上下文信息
     */
    private String buildContextInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("论文主题: ").append(context.getTopic()).append("\n");

        if (context.getResearchOutput() != null) {
            sb.append("\n研究综述:\n").append(context.getResearchOutput()).append("\n");
        }

        if (context.getOutline() != null) {
            sb.append("\n论文大纲:\n").append(context.getOutline()).append("\n");
        }

        if (!context.getSections().isEmpty()) {
            sb.append("\n已有章节内容:\n");
            context.getSections().forEach((title, content) -> {
                String preview = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                sb.append("  [").append(title).append("]: ").append(preview).append("\n");
            });
        }

        return sb.toString();
    }
}
