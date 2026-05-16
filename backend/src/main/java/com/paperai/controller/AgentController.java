package com.paperai.controller;

import com.paperai.model.enums.AgentRole;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.service.LlmCacheService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
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

    @GetMapping("/list")
    public ApiResultVO<List<Map<String, String>>> list() {
        List<Map<String, String>> agents = Stream.of(AgentRole.values())
                .map(r -> Map.of("code", r.getCode(), "name", r.getDisplayName(), "desc", r.getDescription()))
                .collect(Collectors.toList());
        return ApiResultVO.success(agents);
    }

    @PostMapping("/{agentName}/chat")
    public ApiResultVO<String> chat(
            @PathVariable String agentName,
            @RequestParam(defaultValue = "测试") String topic,
            @RequestParam(defaultValue = "你好") String message) {
        String prompt = "主题：" + topic + "\n用户提问：" + message;
        String cacheKey = llmCacheService.computeKey(null, prompt);
        String cached = llmCacheService.get(cacheKey);
        String response = cached != null ? cached : dashScopeChatClient.prompt().user(prompt).call().content();
        if (cached == null && response != null) llmCacheService.put(cacheKey, response);
        return ApiResultVO.success(response);
    }
}
