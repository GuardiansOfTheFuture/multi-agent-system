package com.paperai.agent;

import com.paperai.PaperAiApplication;
import com.paperai.model.dto.ResearchRequestDTO;
import com.paperai.model.ResearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResearcherAgent 集成测试 — 真正调 DashScope API
 */
@SpringBootTest(classes = PaperAiApplication.class)
class ResearcherAgentIntegrationTest {

    @Autowired
    private ResearcherAgent researcherAgent;

    @Test
    void testExecuteStructuredResearch() {
        ResearchRequestDTO request = new ResearchRequestDTO();
        request.setTopic("深度学习在医疗影像中的应用");
        request.setKeywords("深度学习,医疗影像,图像分割");
        request.setDescription("探索CNN在医学图像分割中的应用");

        ResearchResult result = researcherAgent.executeStructuredResearch(request);

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
    void testExecuteTask() {
        AgentContext ctx = new AgentContext("test-001", "AI Agent协作模式");
        String result = researcherAgent.executeTask("调研AI Agent的研究现状", ctx);

        assertNotNull(result);
        assertTrue(result.length() > 100);
        assertNotNull(ctx.getResearchOutput());
    }
}
