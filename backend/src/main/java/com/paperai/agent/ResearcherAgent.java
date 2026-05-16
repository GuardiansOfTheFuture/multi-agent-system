package com.paperai.agent;

import com.paperai.agent.base.BaseAgent;
import com.paperai.model.dto.ResearchRequestDTO;
import com.paperai.model.enums.AgentMessageType;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import com.paperai.model.ResearchResult;
import com.paperai.service.LlmCacheService;
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

    public ResearcherAgent(ChatClient dashScopeChatClient, LlmCacheService llmCacheService) {
        super(AgentRole.RESEARCHER, dashScopeChatClient, llmCacheService);
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
     * 从文本中抽取知识图谱实体和关系。返回 JSON 字符串，格式为 { nodes: [...], edges: [...] }。
     */
    public String extractKnowledgeGraph(String text, String topic, java.util.List<String> entityTypes, java.util.List<String> relationTypes, double confidence) {
        String prompt = buildKgExtractionPrompt(text, topic, entityTypes, relationTypes, confidence);
        String raw = callLlm(prompt);
        return parseKgJson(raw);
    }

    private String buildKgExtractionPrompt(String text, String topic, java.util.List<String> entityTypes, java.util.List<String> relationTypes, double confidence) {
        java.util.Map<String,String> entityTypeNames = java.util.Map.of(
            "concept","概念","paper","论文","author","作者","method","方法",
            "dataset","数据集","topic","主题","problem","问题","finding","发现"
        );
        java.util.Map<String,String> relationTypeNames = java.util.Map.of(
            "uses","使用","extends","扩展","part_of","属于","contradicts","矛盾",
            "related_to","相关","proposes","提出","evaluates","评估","cites","引用"
        );

        StringBuilder etList = new StringBuilder();
        if (entityTypes != null && !entityTypes.isEmpty()) {
            for (String t : entityTypes) { etList.append("- ").append(entityTypeNames.getOrDefault(t, t)).append("\n"); }
        } else { etList.append("（全部类型）\n"); }

        StringBuilder rtList = new StringBuilder();
        if (relationTypes != null && !relationTypes.isEmpty()) {
            for (String t : relationTypes) { rtList.append("- ").append(relationTypeNames.getOrDefault(t, t)).append("\n"); }
        } else { rtList.append("（全部类型）\n"); }

        return """
                你是一位知识图谱构建专家。请从以下文本中抽取关键实体和它们之间的关系。

                当前主题：%s

                文本内容：
                %s

                严格只抽取以下实体类型：
                %s
                严格只抽取以下关系类型：
                %s

                请以严格 JSON 格式输出（不要 Markdown 代码块包裹）：

                {
                  "entities": [
                    {"id":"e1","type":"method","name":"实体名称","desc":"简要描述","confidence":0.85},
                    {"id":"e2","type":"concept","name":"实体名称","desc":"简要描述","confidence":0.72}
                  ],
                  "relations": [
                    {"source":"e1","target":"e2","type":"uses","desc":"关系描述","confidence":0.80}
                  ]
                }

                置信度说明：
                - confidence 范围 0.0-1.0，表示你对该条抽取结果的把握
                - 0.9+ = 文本明确提及，确定无疑
                - 0.7-0.9 = 文本隐含表达，较高把握
                - 0.5-0.7 = 合理推断，有一定不确定性
                - 低于0.5的不应抽取
                - 至少保留 %.1f 以上置信度的结果

                关键要求：
                - 每个实体 name 简明扼要（不超过15字）
                - 只抽取文本中明确提到的实体和关系，不要杜撰
                - 输出纯 JSON，不含任何其他文字
                - 尽力保证关系密度：每个实体尽可能与至少一个其他实体建立关系
                - 寻找隐含关系：如果两个实体在同一语境中出现、共同解决问题、或属于同一主题，即使没有显式动词也要建立 related_to 关系
                - 对核心概念和方法类实体，至少为其找到1-2个关联实体
                - 如果确实没有任何关系线索，该实体可以保持孤立，但应尽量少
                """.formatted(topic != null ? topic : "未知", truncateText(text, 8000),
                    etList.toString(), rtList.toString(), confidence);
    }

    private String parseKgJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String json = raw.trim();
        // 去除可能的 Markdown 代码块包裹
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            json = json.substring(start + 1, end > 0 ? end : json.length()).trim();
        }
        // 验证是合法 JSON
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            return json;
        } catch (Exception e) {
            log.warn("KG 抽取 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String truncateText(String text, int max) {
        return text != null && text.length() > max ? text.substring(0, max) + "..." : text;
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
