package com.paperai.service;

import com.paperai.PaperAiApplication;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.vo.PaperWritingVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrchestratorService 集成测试 — 真正走完写作全流程
 */
@SpringBootTest(classes = PaperAiApplication.class)
class OrchestratorServiceIntegrationTest {

    @Autowired
    private OrchestratorService orchestratorService;

    @Test
    void testFullWritingPipeline() {
        PaperWritingRequestDTO request = new PaperWritingRequestDTO();
        request.setTopic("Multi-Agent系统协作模式研究");
        request.setDescription("研究多个AI Agent如何协同完成复杂任务");
        request.setKeywords("Multi-Agent,AI协作,任务编排");
        request.setSections(List.of("引言", "相关工作", "结论"));
        request.setMaxReviewRounds(1);

        PaperWritingVO result = orchestratorService.execute(request);

        assertNotNull(result);
        assertEquals("Multi-Agent系统协作模式研究", result.getTopic());
        assertNotNull(result.getFinalDraft());
        assertTrue(result.getTotalDurationMs() > 0);
        assertNotNull(result.getSteps());
        assertTrue(result.getSteps().size() >= 6);
    }
}
