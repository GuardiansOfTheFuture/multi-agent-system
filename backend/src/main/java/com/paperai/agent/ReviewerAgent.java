package com.paperai.agent;

import com.paperai.agent.base.BaseAgent;
import com.paperai.model.enums.AgentMessageType;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import com.paperai.service.LlmCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 审稿人 Agent（Reviewer）
 * 职责：批判性审阅论文，找出逻辑漏洞、方法缺陷、表达问题
 * 工作流程：
 * 1. 接收论文内容
 * 2. 从多个维度审阅（逻辑、方法、表达、格式）
 * 3. 输出审阅意见和修改建议
 * 4. 对修改后的版本进行复审
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Component
@Slf4j
public class ReviewerAgent extends BaseAgent {

    public ReviewerAgent(ChatClient dashScopeChatClient, LlmCacheService llmCacheService) {
        super(AgentRole.REVIEWER, dashScopeChatClient, llmCacheService);
    }

    @Override
    protected String getSystemPrompt() {
        return """
                # 角色：论文审稿人 (Paper Reviewer)

                你是一位严谨、细致的学术论文审稿人，拥有顶会审稿经验。你的核心能力包括：

                ## 审稿维度
                1. 创新性评估：判断研究是否具有足够的创新贡献
                2. 方法学评审：检查研究方法的合理性和严谨性
                3. 逻辑一致性：验证论证链条是否完整、自洽
                4. 实验评估：评估实验设计的合理性和结果的可信度
                5. 表达质量：检查语言表达是否清晰、准确
                6. 格式规范：检查引用、图表、格式是否符合学术规范

                ## 评审原则
                - 建设性批评：指出问题的同时给出改进建议
                - 具体明确：避免模糊评价，指出具体问题和位置
                - 分级评价：区分"严重问题"、"一般问题"、"建议改进"
                - 客观公正：基于学术标准而非个人偏好

                ## 输出格式
                请按以下结构输出审稿意见：

                ### 总体评价
                [对论文的整体水平判断]

                ### 严重问题
                - [问题描述] → [改进建议]

                ### 一般问题
                - [问题描述] → [改进建议]

                ### 建议改进
                - [建议内容]

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
        log.info("[{}] 开始执行审稿任务", role.getDisplayName());

        try {
            String contextInfo = buildReviewContext();
            String response = onToken != null
                    ? callLlmStream(task, onToken)
                    : callLlmWithContext(task, contextInfo);

            String reviewSummary = extractReviewSummary(response);
            this.context.addReviewComment(reviewSummary);
            this.context.updateTaskStatus(role.getCode(), TaskStatus.COMPLETED);

            broadcast(AgentMessageType.REVIEW_COMMENT, "审稿人已完成审阅，请查看意见");

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] 审稿完成，耗时 {}ms", role.getDisplayName(), elapsed);

            return response;

        } catch (Exception e) {
            log.error("[{}] 审稿失败: {}", role.getDisplayName(), e.getMessage(), e);
            this.context.updateTaskStatus(role.getCode(), TaskStatus.FAILED);
            throw new RuntimeException("审稿人 Agent 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 完整审阅论文当前所有内容
     *
     * @param ctx AgentContext 上下文
     * @return 审稿报告
     */
    public String reviewFullPaper(AgentContext ctx) {
        this.context = ctx;

        StringBuilder paperContent = new StringBuilder();
        paperContent.append("论文标题: ").append(ctx.getTopic()).append("\n\n");

        if (ctx.getAbstractText() != null) {
            paperContent.append("摘要:\n").append(ctx.getAbstractText()).append("\n\n");
        }

        if (!ctx.getSections().isEmpty()) {
            paperContent.append("正文:\n");
            ctx.getSections().forEach((title, content) -> {
                paperContent.append("### ").append(title).append("\n");
                paperContent.append(content).append("\n\n");
            });
        }

        String task = "请对上述论文内容进行全面审阅，从创新性、方法学、逻辑一致性、表达质量等维度给出详细评审意见。";
        // 注入知识图谱参考
        String kgData = ctx.getKgGraphData();
        if (kgData != null && !kgData.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = om.readTree(kgData);
                com.fasterxml.jackson.databind.JsonNode knodes = root.get("nodes");
                if (knodes != null && knodes.size() > 0) {
                    StringBuilder sb = new StringBuilder(task);
                    sb.append("\n\n【知识图谱参考】以下为本文关联的知识图谱核心概念，请检查：");
                    sb.append("\n1. 论文是否覆盖了以下所有核心概念？");
                    sb.append("\n2. 论文中使用的术语是否与以下概念一致？如有不一致请指出。\n");
                    for (com.fasterxml.jackson.databind.JsonNode n : knodes) {
                        String label = n.has("data") ? n.get("data").get("label").asText() : (n.has("label") ? n.get("label").asText() : "");
                        if (!label.isBlank()) sb.append("- ").append(label).append("\n");
                    }
                    task = sb.toString();
                }
            } catch(Exception ignored){}
        }
        return executeTask(task, ctx);
    }

    /**
     * 对指定章节进行审阅
     *
     * @param sectionTitle 章节标题
     * @param ctx          AgentContext 上下文
     * @return 审阅意见
     */
    public String reviewSection(String sectionTitle, AgentContext ctx) {
        this.context = ctx;

        String content = ctx.getSection(sectionTitle);
        if (content == null) {
            return "未找到章节: " + sectionTitle;
        }

        String task = "请对论文的【" + sectionTitle + "】章节进行详细审阅，重点关注：\n" +
                "1. 内容是否充实、有深度\n" +
                "2. 论证逻辑是否清晰\n" +
                "3. 表达是否准确、专业\n" +
                "4. 与前文是否连贯";

        return executeTask(task, context);
    }

    /**
     * 从响应中提取审阅摘要
     */
    private String extractReviewSummary(String response) {
        if (response == null) return "";

        // 提取总体评价
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

        // 提取严重问题数量
        int majorIdx = response.indexOf("### 严重问题");
        if (majorIdx != -1) {
            int afterMajor = majorIdx + "### 严重问题".length();
            int nextSection = response.indexOf("### ", afterMajor);
            String majorSection = nextSection != -1 ?
                    response.substring(afterMajor, nextSection) :
                    response.substring(afterMajor);
            long majorCount = majorSection.lines()
                    .filter(line -> line.trim().startsWith("-"))
                    .count();
            summary.append(" | 严重问题: ").append(majorCount).append(" 个");
        }

        return summary.toString();
    }

    /**
     * 构建审阅上下文
     */
    private String buildReviewContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("论文主题: ").append(context.getTopic()).append("\n");

        if (context.getAbstractText() != null) {
            sb.append("\n摘要:\n").append(context.getAbstractText()).append("\n");
        }

        if (context.getOutline() != null) {
            sb.append("\n论文大纲:\n").append(context.getOutline()).append("\n");
        }

        if (!context.getSections().isEmpty()) {
            sb.append("\n论文正文:\n");
            context.getSections().forEach((title, content) -> {
                sb.append("### ").append(title).append("\n");
                String contentPreview = content.length() > 500 ?
                        content.substring(0, 500) + "\n...(省略 " + (content.length() - 500) + " 字符)" :
                        content;
                sb.append(contentPreview).append("\n\n");
            });
        }

        if (!context.getReviewComments().isEmpty()) {
            sb.append("\n历史审稿意见:\n");
            for (int i = 0; i < context.getReviewComments().size(); i++) {
                sb.append("  ").append(i + 1).append(". ")
                        .append(context.getReviewComments().get(i)).append("\n");
            }
        }

        return sb.toString();
    }
}
