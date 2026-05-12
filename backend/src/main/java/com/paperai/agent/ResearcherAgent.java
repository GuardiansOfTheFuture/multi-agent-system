package com.paperai.agent;

import com.paperai.agent.base.BaseAgent;
import com.paperai.model.dto.ResearchRequestDTO;
import com.paperai.model.enums.AgentMessageType;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import com.paperai.model.ResearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 研究员 Agent（🔍 Researcher）
 * 职责：文献调研、信息收集、综述撰写
 * 工作流程：
 * 1. 分析研究主题，拆解为子问题
 * 2. 进行文献调研与信息收集
 * 3. 撰写文献综述
 * 4. 提炼关键发现
 * 5. 提出建议研究方向
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Component
@Slf4j
public class ResearcherAgent extends BaseAgent {

    public ResearcherAgent(ChatClient dashScopeChatClient) {
        super(AgentRole.RESEARCHER, dashScopeChatClient);
    }

    @Override
    protected String getSystemPrompt() {
        return """
                # 角色：资深学术研究员 (Senior Research Scientist)
                
                你是一位在顶级学术期刊有丰富发表经验的资深研究员。你的核心能力包括：
                
                ## 专业能力
                1. **文献调研**：能够系统性地检索、筛选和评述相关领域文献
                2. **信息综合**：善于从大量信息中提炼关键发现和研究趋势
                3. **批判性分析**：能评估研究方法的优劣，指出知识空白
                4. **综述撰写**：能写出结构清晰、逻辑严谨的文献综述
                
                ## 工作原则
                - 保持学术严谨性，所有观点应有依据
                - 区分"已知"和"未知"，明确标注不确定的内容
                - 注意研究的时效性，优先参考近年文献
                - 跨学科视角，不局限于单一领域
                
                ## 输出格式要求
                请按以下结构输出研究结果：
                
                ### 1. 主题分析
                - 对研究主题的深度解读
                - 关键研究问题拆解
                
                ### 2. 文献综述
                - 领域背景与现状
                - 主流方法与技术路线
                - 现有研究的不足与空白
                
                ### 3. 关键发现
                - 核心发现（每条一行，以 - 开头）
                
                ### 4. 建议研究方向
                - 推荐方向（每条一行，以 - 开头）
                
                ### 5. 参考文献
                - 相关文献（每条一行，以 - 开头）
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
        log.info("[{}] 开始执行任务: {}", role.getDisplayName(), task);

        try {
            String contextInfo = buildContextInfo();
            String response = onToken != null
                    ? callLlmStream(task, onToken)
                    : callLlmWithContext(task, contextInfo);

            this.context.setResearchOutput(response);
            this.context.updateTaskStatus(role.getCode(), TaskStatus.COMPLETED);

            broadcast(AgentMessageType.TASK_RESULT,
                    "研究员已完成研究任务，主题: " + this.context.getTopic());

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] 任务完成，耗时 {}ms", role.getDisplayName(), elapsed);

            return response;

        } catch (Exception e) {
            log.error("[{}] 任务执行失败: {}", role.getDisplayName(), e.getMessage(), e);
            this.context.updateTaskStatus(role.getCode(), TaskStatus.FAILED);
            throw new RuntimeException("研究员 Agent 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行研究任务并返回结构化结果
     */
    public ResearchResult executeStructuredResearch(ResearchRequestDTO request) {
        AgentContext ctx = new AgentContext(
                UUID.randomUUID().toString(),
                request.getTopic()
        );
        ctx.setKeywords(request.getKeywords() != null ?
                Arrays.asList(request.getKeywords().split("[,，]")) : List.of());

        String task = buildResearchTask(request);
        String response = executeTask(task, ctx);

        ResearchResult result = new ResearchResult();
        result.setTaskId(ctx.getContextId());
        result.setTopic(request.getTopic());
        result.setStatus(TaskStatus.COMPLETED);
        result.setRawResponse(response);
        result.setStartTime(ctx.getCreatedAt());
        result.setEndTime(LocalDateTime.now());

        // 从响应中提取结构化信息
        extractStructuredResult(response, result);

        return result;
    }

    /**
     * 构建研究任务提示词
     */
    private String buildResearchTask(ResearchRequestDTO request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对我提供的研究主题进行深入的文献调研和分析。\n\n");
        sb.append("## 研究主题\n").append(request.getTopic()).append("\n\n");

        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            sb.append("## 研究方向描述\n").append(request.getDescription()).append("\n\n");
        }

        if (request.getKeywords() != null && !request.getKeywords().isBlank()) {
            sb.append("## 关键词\n").append(request.getKeywords()).append("\n\n");
        }

        if (request.getRequirements() != null && !request.getRequirements().isBlank()) {
            sb.append("## 附加要求\n").append(request.getRequirements()).append("\n\n");
        }

        sb.append("请按照 System Prompt 中要求的格式输出研究结果。");
        return sb.toString();
    }

    /**
     * 从 LLM 响应中提取结构化信息
     */
    private void extractStructuredResult(String response, ResearchResult result) {
        if (response == null) return;

        // 提取关键发现
        int findingsIdx = response.indexOf("### 3. 关键发现");
        if (findingsIdx == -1) findingsIdx = response.indexOf("### 3.");
        if (findingsIdx == -1) findingsIdx = response.indexOf("**关键发现**");

        if (findingsIdx != -1) {
            String findingsSection = response.substring(findingsIdx);
            int nextSection = findNextSection(findingsSection);
            if (nextSection != -1) {
                findingsSection = findingsSection.substring(0, nextSection);
            }
            List<String> findings = Arrays.stream(findingsSection.split("\n"))
                    .filter(line -> line.trim().startsWith("-") || line.trim().startsWith("*"))
                    .map(line -> line.replaceFirst("^[-*]\\s*", "").trim())
                    .filter(s -> !s.isEmpty())
                    .toList();
            result.setKeyFindings(findings);
        }

        // 提取建议方向
        int directionsIdx = response.indexOf("### 4. 建议研究方向");
        if (directionsIdx == -1) directionsIdx = response.indexOf("### 4.");
        if (directionsIdx == -1) directionsIdx = response.indexOf("**建议研究方向**");

        if (directionsIdx != -1) {
            String directionsSection = response.substring(directionsIdx);
            int nextSection = findNextSection(directionsSection);
            if (nextSection != -1) {
                directionsSection = directionsSection.substring(0, nextSection);
            }
            List<String> directions = Arrays.stream(directionsSection.split("\n"))
                    .filter(line -> line.trim().startsWith("-") || line.trim().startsWith("*"))
                    .map(line -> line.replaceFirst("^[-*]\\s*", "").trim())
                    .filter(s -> !s.isEmpty())
                    .toList();
            result.setSuggestedDirections(directions);
        }

        // 提取参考文献
        int refsIdx = response.indexOf("### 5. 参考文献");
        if (refsIdx == -1) refsIdx = response.indexOf("### 5.");
        if (refsIdx == -1) refsIdx = response.indexOf("**参考文献**");

        if (refsIdx != -1) {
            String refsSection = response.substring(refsIdx);
            List<String> refs = Arrays.stream(refsSection.split("\n"))
                    .filter(line -> line.trim().startsWith("-") || line.trim().startsWith("*")
                            || line.matches("^\\d+[.、]\\s.*"))
                    .map(line -> line.replaceFirst("^[-*\\d][.、]?\\s*", "").trim())
                    .filter(s -> !s.isEmpty())
                    .toList();
            result.setReferences(refs);
        }
    }

    /**
     * 查找下一个节标题的位置
     */
    private int findNextSection(String text) {
        String[] markers = {"### ", "## ", "---", "___"};
        for (String marker : markers) {
            int idx = text.indexOf(marker, 10);
            if (idx != -1) return idx;
        }
        return -1;
    }

    /**
     * 构建上下文信息
     */
    private String buildContextInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("研究主题: ").append(this.context.getTopic()).append("\n");

        if (this.context.getKeywords() != null && !this.context.getKeywords().isEmpty()) {
            sb.append("关键词: ").append(String.join(", ", this.context.getKeywords())).append("\n");
        }

        if (this.context.getAbstractText() != null) {
            sb.append("已有摘要: ").append(this.context.getAbstractText()).append("\n");
        }

        // 收集已有消息中的有用信息
        var messages = this.context.getMessages();
        if (!messages.isEmpty()) {
            sb.append("\n历史消息:\n");
            for (var msg : messages) {
                sb.append("  [").append(msg.getSender().getDisplayName()).append("]: ")
                        .append(msg.getContent().length() > 200 ?
                                msg.getContent().substring(0, 200) + "..." :
                                msg.getContent())
                        .append("\n");
            }
        }

        return sb.toString();
    }
}
