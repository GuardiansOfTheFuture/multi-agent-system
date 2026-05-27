package com.paperai.controller;

import com.paperai.model.enums.AgentRole;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.cache.LlmCacheService;
import com.paperai.config.AiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LlmCacheService llmCacheService;

    @MockBean
    private AiConfig aiConfig;

    @MockBean
    private com.paperai.service.CustomAgentService customAgentService;

    @Test
    void testListAgents() throws Exception {
        mockMvc.perform(get("/api/agent/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(AgentRole.values().length));
    }

    @Test
    void testListModels() throws Exception {
        mockMvc.perform(get("/api/agent/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }
}
