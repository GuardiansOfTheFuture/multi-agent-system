package com.paperai.agent;

import com.paperai.PaperAiApplication;
import com.paperai.model.dto.ResearchRequestDTO;
import com.paperai.model.ResearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentExecutor 集成测试 — 真正调 DashScope API
 */
@SpringBootTest(classes = PaperAiApplication.class)
class ResearcherAgentIntegrationTest {

    @Autowired
    private AgentExecutor agentExecutor;

    @Test
    void testExecuteStructuredResearch() {
        ResearchRequestDTO request = new ResearchRequestDTO();
        request.setTopic("深度学习在医疗影像中的应用");
        request.setKeywords("深度学习,医疗影像,图像分割");
        request.setDescription("探索CNN在医学图像分割中的应用");

        ResearchResult result = agentExecutor.executeStructuredResearch(request);

        assertNotNull(result);
        assertEquals("深度学习在医疗影像中的应用", result.getTopic());
        assertNotNull(result.getRawResponse());
        assertTrue(result.getRawResponse().length() > 50);
        assertNotNull(result.getKeyFindings());
        assertNotNull(result.getSuggestedDirections());
        assertNotNull(result.getReferences());
        assertTrue(result.getDurationMs() > 0);
    }

    @Test
    void testExecuteStream() {
        String result = agentExecutor.executeStream(AgentDefinitions.RESEARCHER,
                "请简要介绍深度学习的基本概念", null);

        assertNotNull(result);
        assertTrue(result.length() > 50);
    }
}
