package com.paperai.controller;

import com.paperai.agent.base.BaseAgent;
import com.paperai.agent.AgentContext;
import com.paperai.model.enums.AgentRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent 调试接口测试
 *
 * @author: ch
 * @date 2026年05月11日
 */
@WebMvcTest(AgentController.class)
@Import(AgentControllerTest.MockAgentConfig.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testListAgents() throws Exception {
        mockMvc.perform(get("/api/agent/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.researcherAgent").value("研究员 Agent"));
    }

    @Test
    void testChatWithExistingAgent() throws Exception {
        mockMvc.perform(post("/api/agent/researcherAgent/chat")
                        .param("topic", "测试主题")
                        .param("message", "请做文献调研"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void testChatWithNonExistentAgent() throws Exception {
        mockMvc.perform(post("/api/agent/nonExistent/chat")
                        .param("topic", "测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("未找到")));
    }

    @TestConfiguration
    static class MockAgentConfig {
        @Bean
        Map<String, BaseAgent> agents() {
            BaseAgent mockAgent = new BaseAgent(AgentRole.RESEARCHER, null) {
                @Override
                protected String getSystemPrompt() {
                    return "mock prompt";
                }

                @Override
                public String executeTask(String task, AgentContext context) {
                    return "mock response for: " + task;
                }
            };
            return Map.of("researcherAgent", mockAgent);
        }
    }
}
