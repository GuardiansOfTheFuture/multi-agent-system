package com.paperai.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.paperai.advisor.LoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class AiConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    /** 可用模型注册表 */
    public static final List<Map<String, Object>> MODEL_REGISTRY = List.of(
        Map.of("name", "qwen3.5-flash", "displayName", "Qwen 3.5 Flash", "provider", "dashscope",
               "contextWindow", 131072, "description", "轻量极速，适合日常任务"),
        Map.of("name", "qwen-plus", "displayName", "Qwen Plus", "provider", "dashscope",
               "contextWindow", 131072, "description", "高性价比，平衡质量与速度"),
        Map.of("name", "qwen-max", "displayName", "Qwen Max", "provider", "dashscope",
               "contextWindow", 32768, "description", "旗舰模型，适合复杂推理"),
        Map.of("name", "qwen-turbo", "displayName", "Qwen Turbo", "provider", "dashscope",
               "contextWindow", 131072, "description", "超快响应"),
        Map.of("name", "deepseek-v3", "displayName", "DeepSeek V3", "provider", "dashscope",
               "contextWindow", 65536, "description", "深度推理模型"),
        Map.of("name", "deepseek-r1", "displayName", "DeepSeek R1", "provider", "dashscope",
               "contextWindow", 65536, "description", "复杂推理与深度思考")
    );

    private static String defaultModel = null;

    public static String getDefaultModel() {
        if (defaultModel == null) defaultModel = "qwen3.5-flash";
        return defaultModel;
    }

    // ===== Chat =====

    @Bean
    public DashScopeChatModel dashScopeChatModel() {
        return new DashScopeChatModel(new DashScopeApi(apiKey));
    }

    @Bean
    public ChatClient dashScopeChatClient(DashScopeChatModel dashScopeChatModel) {
        return ChatClient.builder(dashScopeChatModel)
                .defaultAdvisors(new LoggerAdvisor())
                .build();
    }

    public ChatClient createChatClient(String modelName) {
        return createChatClient(modelName, 0.7);
    }

    public ChatClient createChatClient(String modelName, double temperature) {
        DashScopeChatModel model = new DashScopeChatModel(new DashScopeApi(apiKey));
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(modelName != null ? modelName : getDefaultModel())
                .withTemperature(temperature)
                .build();
        return ChatClient.builder(model)
                .defaultOptions(options)
                .defaultAdvisors(new LoggerAdvisor())
                .build();
    }

    // ===== Embedding =====

    @Bean
    public DashScopeEmbeddingModel embeddingModel() {
        return new DashScopeEmbeddingModel(new DashScopeApi(apiKey));
    }
}
