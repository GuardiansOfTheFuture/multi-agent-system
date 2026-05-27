package com.paperai.agent;

import com.paperai.common.BusinessException;
import com.paperai.common.ResultCode;
import com.paperai.config.AiConfig;
import com.paperai.model.ResearchResult;
import com.paperai.model.dto.ResearchRequestDTO;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import com.paperai.cache.LlmCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 统一的 Agent 执行器 — 替代原来的 BaseAgent + 5 个 Agent 子类。
 * 职责单一：接收 AgentDefinition + 任务文本 → 调用 LLM → 返回结果。
 * RAG 由 ChatClient 的 QuestionAnswerAdvisor 自动完成。
 */
@Slf4j
@Component
public class AgentExecutor {

    private final ChatClient defaultChatClient;
    private final LlmCacheService llmCacheService;

    @Resource
    private AiConfig aiConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentExecutor(ChatClient dashScopeChatClient, LlmCacheService llmCacheService) {
        this.defaultChatClient = dashScopeChatClient;
        this.llmCacheService = llmCacheService;
    }

    // ===== 核心 API =====

    /** 同步执行，返回完整结果 */
    public String execute(AgentDefinition def, String userMessage) {
        return callLlm(def.systemPrompt(), userMessage, defaultChatClient);
    }

    /** 流式执行，逐 token 回调 */
    public String executeStream(AgentDefinition def, String userMessage,
                                Consumer<String> onToken) {
        return callLlmStream(def.systemPrompt(), userMessage, onToken, defaultChatClient);
    }

    /** 带自定义模型/System Prompt 的流式执行（FlowEngine 节点级配置） */
    public String executeWithConfig(AgentDefinition def, String userMessage,
                                     Consumer<String> onToken,
                                     ChatClient customClient, String customPrompt) {
        String systemPrompt = (customPrompt != null && !customPrompt.isBlank())
                ? customPrompt : def.systemPrompt();
        ChatClient client = customClient != null ? customClient : defaultChatClient;
        return callLlmStream(systemPrompt, userMessage, onToken, client);
    }

    // ===== 快捷方法（原 ResearcherAgent 特有） =====

    /** 结构化研究 */
    public ResearchResult executeStructuredResearch(ResearchRequestDTO request) {
        AgentContext ctx = new AgentContext(UUID.randomUUID().toString(), request.getTopic());
        ctx.setKeywords(request.getKeywords() != null
                ? Arrays.asList(request.getKeywords().split("[,，]")) : List.of());

        String task = buildResearchTask(request);
        String response = execute(AgentDefinitions.RESEARCHER, task);

        ResearchResult result = new ResearchResult();
        result.setTaskId(ctx.getContextId());
        result.setTopic(request.getTopic());
        result.setStatus(TaskStatus.COMPLETED);
        result.setRawResponse(response);
        result.setStartTime(ctx.getCreatedAt());
        result.setEndTime(LocalDateTime.now());
        extractStructuredResult(response, result);
        return result;
    }

    /** 知识图谱抽取 */
    public String extractKnowledgeGraph(String text, String topic,
                                         List<String> entityTypes,
                                         List<String> relationTypes,
                                         double confidence) {
        String prompt = buildKgExtractionPrompt(text, topic, entityTypes, relationTypes, confidence);
        String raw = execute(AgentDefinitions.RESEARCHER, prompt);
        return parseKgJson(raw);
    }

    // ===== LLM 调用 =====

    private String callLlm(String systemPrompt, String userMessage, ChatClient client) {
        String cacheKey = llmCacheService.computeKey(systemPrompt, userMessage);
        String cached = llmCacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            String response = client.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            log.info("[AgentExecutor] LLM 响应完成，长度: {}",
                    response != null ? response.length() : 0);
            llmCacheService.put(cacheKey, response);
            return response;
        } catch (Exception e) {
            log.error("[AgentExecutor] LLM 调用失败", e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR.getCode(),
                    "AI 服务调用失败：" + extractShortMessage(e));
        }
    }

    private String callLlmStream(String systemPrompt, String userMessage,
                                  Consumer<String> onToken, ChatClient client) {
        String cacheKey = llmCacheService.computeKey(systemPrompt, userMessage);
        String cached = llmCacheService.get(cacheKey);
        if (cached != null) {
            if (onToken != null) onToken.accept(cached);
            return cached;
        }

        StringBuilder full = new StringBuilder();
        try {
            String response = client.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .stream()
                    .content()
                    .doOnNext(chunk -> {
                        full.append(chunk);
                        if (onToken != null) onToken.accept(full.toString());
                    })
                    .blockLast();

            String result = full.toString();
            log.info("[AgentExecutor] LLM 流式响应完成，长度: {}", result.length());
            llmCacheService.put(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.error("[AgentExecutor] LLM 流式调用失败", e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR.getCode(),
                    "AI 服务流式调用失败：" + extractShortMessage(e));
        }
    }

    // ===== 工具方法 =====

    private String extractShortMessage(Throwable e) {
        if (e == null) return "未知错误";
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String msg = cause.getMessage();
        if (msg == null) msg = e.getMessage();
        if (msg == null) return "未知错误";
        return msg.length() > 150 ? msg.substring(0, 150) + "..." : msg;
    }

    // ===== Research task building & result extraction（原 ResearcherAgent） =====

    private String buildResearchTask(ResearchRequestDTO request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对我提供的研究主题进行深入的文献调研和分析。\n\n");
        sb.append("## 研究主题\n").append(request.getTopic()).append("\n\n");
        if (request.getDescription() != null && !request.getDescription().isBlank())
            sb.append("## 研究方向描述\n").append(request.getDescription()).append("\n\n");
        if (request.getKeywords() != null && !request.getKeywords().isBlank())
            sb.append("## 关键词\n").append(request.getKeywords()).append("\n\n");
        if (request.getRequirements() != null && !request.getRequirements().isBlank())
            sb.append("## 附加要求\n").append(request.getRequirements()).append("\n\n");
        sb.append("请按照 System Prompt 中要求的格式输出研究结果。");
        return sb.toString();
    }

    private void extractStructuredResult(String response, ResearchResult result) {
        if (response == null) return;
        int findingsIdx = indexOfSection(response, "### 3. 关键发现", "### 3.", "**关键发现**");
        if (findingsIdx != -1) {
            String section = response.substring(findingsIdx);
            int next = findNextSection(section);
            if (next != -1) section = section.substring(0, next);
            List<String> findings = Arrays.stream(section.split("\n"))
                    .filter(line -> line.trim().startsWith("-") || line.trim().startsWith("*"))
                    .map(line -> line.replaceFirst("^[-*]\\s*", "").trim())
                    .filter(s -> !s.isEmpty()).toList();
            result.setKeyFindings(findings);
        }
        int dirIdx = indexOfSection(response, "### 4. 建议研究方向", "### 4.", "**建议研究方向**");
        if (dirIdx != -1) {
            String section = response.substring(dirIdx);
            int next = findNextSection(section);
            if (next != -1) section = section.substring(0, next);
            List<String> directions = Arrays.stream(section.split("\n"))
                    .filter(line -> line.trim().startsWith("-") || line.trim().startsWith("*"))
                    .map(line -> line.replaceFirst("^[-*]\\s*", "").trim())
                    .filter(s -> !s.isEmpty()).toList();
            result.setSuggestedDirections(directions);
        }
        int refIdx = indexOfSection(response, "### 5. 参考文献", "### 5.", "**参考文献**");
        if (refIdx != -1) {
            String section = response.substring(refIdx);
            List<String> refs = Arrays.stream(section.split("\n"))
                    .filter(line -> line.trim().startsWith("-") || line.trim().startsWith("*")
                            || line.matches("^\\d+[.、]\\s.*"))
                    .map(line -> line.replaceFirst("^[-*\\d][.、]?\\s*", "").trim())
                    .filter(s -> !s.isEmpty()).toList();
            result.setReferences(refs);
        }
    }

    private int indexOfSection(String text, String... markers) {
        for (String m : markers) {
            int idx = text.indexOf(m);
            if (idx != -1) return idx;
        }
        return -1;
    }

    private int findNextSection(String text) {
        for (String marker : new String[]{"### ", "## ", "---", "___"}) {
            int idx = text.indexOf(marker, 10);
            if (idx != -1) return idx;
        }
        return -1;
    }

    // ===== KG extraction（原 ResearcherAgent） =====

    private String buildKgExtractionPrompt(String text, String topic,
                                            List<String> entityTypes,
                                            List<String> relationTypes,
                                            double confidence) {
        Map<String, String> entityTypeNames = Map.of(
                "concept", "概念", "paper", "论文", "author", "作者", "method", "方法",
                "dataset", "数据集", "topic", "主题", "problem", "问题", "finding", "发现");
        Map<String, String> relationTypeNames = Map.of(
                "uses", "使用", "extends", "扩展", "part_of", "属于", "contradicts", "矛盾",
                "related_to", "相关", "proposes", "提出", "evaluates", "评估", "cites", "引用");

        StringBuilder etList = new StringBuilder();
        if (entityTypes != null && !entityTypes.isEmpty())
            for (String t : entityTypes) etList.append("- ").append(entityTypeNames.getOrDefault(t, t)).append("\n");
        else etList.append("（全部类型）\n");

        StringBuilder rtList = new StringBuilder();
        if (relationTypes != null && !relationTypes.isEmpty())
            for (String t : relationTypes) rtList.append("- ").append(relationTypeNames.getOrDefault(t, t)).append("\n");
        else rtList.append("（全部类型）\n");

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
                """.formatted(topic != null ? topic : "未知",
                truncate(text, 8000),
                etList.toString(), rtList.toString(), confidence);
    }

    private String parseKgJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String json = raw.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            json = json.substring(start + 1, end > 0 ? end : json.length()).trim();
        }
        try {
            objectMapper.readTree(json);
            return json;
        } catch (Exception e) {
            log.warn("KG 抽取 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static String truncate(String text, int max) {
        return text != null && text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
