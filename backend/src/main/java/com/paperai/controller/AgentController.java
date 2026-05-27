package com.paperai.controller;

import com.paperai.model.enums.AgentRole;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.cache.LlmCacheService;
import com.paperai.config.AiConfig;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Resource private ChatClient dashScopeChatClient;
    @Resource private LlmCacheService llmCacheService;
    @Resource private AiConfig aiConfig;
    @Resource private com.paperai.service.CustomAgentService customAgentService;

    @GetMapping("/list")
    public ApiResultVO<List<Map<String, String>>> list() {
        List<Map<String, String>> agents = Stream.of(AgentRole.values())
                .map(r -> Map.of("code", r.getCode(), "name", r.getDisplayName(), "desc", r.getDescription()))
                .collect(Collectors.toList());
        return ApiResultVO.success(agents);
    }

    @GetMapping("/models")
    public ApiResultVO<List<Map<String, Object>>> models() {
        return ApiResultVO.success(AiConfig.MODEL_REGISTRY);
    }

    @PostMapping("/{agentName}/chat")
    public ApiResultVO<String> chat(
            @PathVariable String agentName,
            @RequestParam(defaultValue = "测试") String topic,
            @RequestParam(defaultValue = "你好") String message,
            @RequestParam(required = false) String model) {
        String prompt = "主题：" + topic + "\n用户提问：" + message;
        String cacheKey = llmCacheService.computeKey(null, prompt);
        String cached = llmCacheService.get(cacheKey);
        String response;
        if (cached != null) {
            response = cached;
        } else if (model != null && !model.isBlank()) {
            ChatClient client = aiConfig.createChatClient(model);
            response = client.prompt().user(prompt).call().content();
        } else {
            response = dashScopeChatClient.prompt().user(prompt).call().content();
        }
        if (cached == null && response != null) llmCacheService.put(cacheKey, response);
        return ApiResultVO.success(response);
    }

    // ===== 自定义 Agent =====

    @GetMapping("/custom")
    public ApiResultVO<List<com.paperai.model.entity.CustomAgent>> listCustom(Authentication auth) {
        return ApiResultVO.success(customAgentService.listByUser(userId(auth)));
    }

    @PostMapping("/custom")
    public ApiResultVO<com.paperai.model.entity.CustomAgent> createCustom(
            @RequestBody com.paperai.model.entity.CustomAgent agent, Authentication auth) {
        agent.setUserId(userId(auth));
        return ApiResultVO.success("创建成功", customAgentService.create(agent));
    }

    @PutMapping("/custom/{id}")
    public ApiResultVO<com.paperai.model.entity.CustomAgent> updateCustom(
            @PathVariable Long id,
            @RequestBody com.paperai.model.entity.CustomAgent agent,
            Authentication auth) {
        return ApiResultVO.success("更新成功", customAgentService.update(id, agent, userId(auth)));
    }

    @DeleteMapping("/custom/{id}")
    public ApiResultVO<String> deleteCustom(@PathVariable Long id, Authentication auth) {
        customAgentService.delete(id, userId(auth));
        return ApiResultVO.success("已删除");
    }

    private Long userId(Authentication auth) {
        return auth != null ? (Long) auth.getPrincipal() : 0L;
    }
}
