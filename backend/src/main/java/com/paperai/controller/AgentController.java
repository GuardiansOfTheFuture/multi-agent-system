package com.paperai.controller;

import com.paperai.agent.base.BaseAgent;
import com.paperai.agent.AgentContext;
import com.paperai.model.vo.ApiResultVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Agent 调试接口 — 单独测试任意 Agent
 * <p>
 * 职责：不涉及业务组装，直接调 Agent 看原始 LLM 响应，方便调试 prompt。
 *
 * @author: ch
 * @date 2026年05月11日
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Resource
    private Map<String, BaseAgent> agents;

    @PostMapping("/{agentName}/chat")
    public ApiResultVO<String> chat(
            @PathVariable String agentName,
            @RequestParam(defaultValue = "测试主题") String topic,
            @RequestParam(defaultValue = "") String message) {

        BaseAgent agent = agents.get(agentName);
        if (agent == null) {
            return ApiResultVO.error("未找到 Agent: " + agentName
                    + "，可用: " + String.join(", ", agents.keySet()));
        }

        AgentContext ctx = new AgentContext(UUID.randomUUID().toString(), topic);
        String result = agent.executeTask(message, ctx);
        return ApiResultVO.success(result);
    }

    @GetMapping("/list")
    public ApiResultVO<Map<String, String>> list() {
        Map<String, String> info = new java.util.LinkedHashMap<>();
        agents.forEach((name, agent) -> info.put(name, agent.getRole().getDisplayName()));
        return ApiResultVO.success(info);
    }
}
