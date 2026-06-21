package com.paperai.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperai.advisor.LoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.util.*;

@Configuration
public class AiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    /** MiMo API 完整 base URL（含 /v1） */
    private String getFullBaseUrl() {
        return baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1";
    }

    /** 可用模型注册表 */
    public static final List<Map<String, Object>> MODEL_REGISTRY = List.of(
        Map.of("name", "mimo-v2.5-pro", "displayName", "MiMo V2.5 Pro", "provider", "xiaomi",
               "contextWindow", 131072, "description", "小米推理大模型，擅长代码与数学"),
        Map.of("name", "qwen3.5-flash", "displayName", "Qwen 3.5 Flash", "provider", "dashscope",
               "contextWindow", 131072, "description", "轻量极速，适合日常任务"),
        Map.of("name", "qwen-plus", "displayName", "Qwen Plus", "provider", "dashscope",
               "contextWindow", 131072, "description", "高性价比，平衡质量与速度"),
        Map.of("name", "qwen-max", "displayName", "Qwen Max", "provider", "dashscope",
               "contextWindow", 32768, "description", "旗舰模型，适合复杂推理"),
        Map.of("name", "deepseek-v3", "displayName", "DeepSeek V3", "provider", "dashscope",
               "contextWindow", 65536, "description", "深度推理模型")
    );

    private static String defaultModel = "mimo-v2.5-pro";

    public static String getDefaultModel() {
        return defaultModel;
    }

    // ===== OpenAI 兼容 API (MiMo) =====

    @Bean
    public OpenAiApi openAiApi() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);
        converter.setDefaultCharset(java.nio.charset.StandardCharsets.UTF_8);
        RestClient.Builder restClientBuilder = RestClient.builder()
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(0, converter);
                })
                .requestInterceptor((request, body, execution) -> {
                    // 给 MiMo 请求注入 thinking:disabled，防止返回 reasoning_content
                    if (request.getMethod() != null && request.getMethod().name().equals("POST")) {
                        try {
                            String uri = request.getURI().toString();
                            if (uri.contains("/chat/completions") && body instanceof byte[] bytes) {
                                String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                                if (!json.contains("\"thinking\"")) {
                                    // 在 JSON 末尾的 } 前插入 thinking 参数
                                    json = json.replaceAll("\\}\\s*$", ",\"thinking\":{\"type\":\"disabled\"}}");
                                    return execution.execute(request, new org.springframework.http.HttpEntity<>(
                                            json.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                            request.getHeaders()));
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    return execution.execute(request, body);
                });
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();
    }

    @Bean
    @Primary
    public ChatModel openAiChatModel(OpenAiApi openAiApi) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(defaultModel)
                        .temperature(0.7)
                        .build())
                .build();
    }

    @Bean
    @Primary
    public ChatClient chatClient(ChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(new LoggerAdvisor())
                .build();
    }

    /** 创建指定模型的 ChatClient */
    public ChatClient createChatClient(String modelName) {
        return createChatClient(modelName, 0.7);
    }

    /** 创建指定模型+温度的 ChatClient */
    public ChatClient createChatClient(String modelName, double temperature) {
        String model = modelName != null ? modelName : defaultModel;
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .build())
                .build();
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new LoggerAdvisor())
                .build();
    }

    // ===== 轻量 LLM 调用（分块/提取/摘要等非写作任务） =====

    @Value("${paperai.llm.light:mimo-v2.5-pro}")
    private String lightModel;

    /** 用轻量模型执行简单任务，返回纯文本 */
    public String callLightLlm(String systemPrompt, String userMessage) {
        return createChatClient(lightModel, 0.3).prompt()
                .system(systemPrompt).user(userMessage).call().content();
    }

    // Embedding 模型由 spring-ai-alibaba-autoconfigure 自动配置（DashScope）
    // 模型名通过 application-local.yml 中 spring.ai.dashscope.embedding.options.model 指定
}
