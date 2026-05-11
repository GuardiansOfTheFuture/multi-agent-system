package com.paperai.controller;

import com.paperai.agent.ResearcherAgent;
import com.paperai.model.ResearchResult;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.dto.ResearchRequestDTO;
import com.paperai.model.enums.TaskStatus;
import com.paperai.model.vo.PaperWritingVO;
import com.paperai.service.OrchestratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 论文写作 API 控制器测试
 *
 * @author: ch
 * @date 2026年05月11日
 */
@WebMvcTest(PaperController.class)
class PaperControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ResearcherAgent mockResearcherAgent;

    @MockBean
    private OrchestratorService mockOrchestratorService;

    // ==================== 健康检查 ====================

    @Test
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/paper/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("PaperAI Backend is running"));
    }

    // ==================== /research ====================

    @Test
    void testResearchSuccess() throws Exception {
        ResearchRequestDTO dto = new ResearchRequestDTO();
        dto.setTopic("深度学习");
        dto.setKeywords("AI,ML");

        ResearchResult mockResult = createMockResult("深度学习");
        when(mockResearcherAgent.executeStructuredResearch(any())).thenReturn(mockResult);

        mockMvc.perform(post("/api/paper/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("研究完成"))
                .andExpect(jsonPath("$.data.topic").value("深度学习"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.keyFindings[0]").value("发现1"))
                .andExpect(jsonPath("$.data.references[0]").value("Ref1"));
    }

    @Test
    void testResearchWithAllFields() throws Exception {
        ResearchRequestDTO dto = new ResearchRequestDTO();
        dto.setTopic("NLP");
        dto.setDescription("自然语言处理研究");
        dto.setKeywords("NLP,Transformer");
        dto.setRequirements("需要近三年文献");

        when(mockResearcherAgent.executeStructuredResearch(any())).thenReturn(createMockResult("NLP"));

        mockMvc.perform(post("/api/paper/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.durationMs").isNumber());
    }

    // ==================== /research/stream ====================

    @Test
    void testResearchStream() throws Exception {
        ResearchRequestDTO dto = new ResearchRequestDTO();
        dto.setTopic("流式测试");

        when(mockResearcherAgent.executeStructuredResearch(any())).thenReturn(createMockResult("流式测试"));

        mockMvc.perform(post("/api/paper/research/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    // ==================== /write ====================

    @Test
    void testWritePaper() throws Exception {
        PaperWritingRequestDTO dto = new PaperWritingRequestDTO();
        dto.setTopic("Multi-Agent系统");
        dto.setSections(List.of("引言", "方法"));

        PaperWritingVO mockVo = createMockWritingVO("Multi-Agent系统");
        when(mockOrchestratorService.execute(any())).thenReturn(mockVo);

        mockMvc.perform(post("/api/paper/write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("论文写作完成"))
                .andExpect(jsonPath("$.data.topic").value("Multi-Agent系统"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalDurationMs").isNumber())
                .andExpect(jsonPath("$.data.steps").isArray());
    }

    @Test
    void testWritePaperWithEmptySections() throws Exception {
        PaperWritingRequestDTO dto = new PaperWritingRequestDTO();
        dto.setTopic("测试论文");

        when(mockOrchestratorService.execute(any())).thenReturn(createMockWritingVO("测试论文"));

        mockMvc.perform(post("/api/paper/write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 参数校验场景 ====================

    @Test
    void testResearchWithEmptyTopic() throws Exception {
        ResearchRequestDTO dto = new ResearchRequestDTO();
        dto.setTopic("");

        when(mockResearcherAgent.executeStructuredResearch(any())).thenReturn(createMockResult(""));

        mockMvc.perform(post("/api/paper/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void testResearchWithInvalidJson() throws Exception {
        mockMvc.perform(post("/api/paper/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid json"))
                .andExpect(status().is4xxClientError());
    }

    // ==================== Mock 工厂方法 ====================

    private ResearchResult createMockResult(String topic) {
        ResearchResult r = new ResearchResult();
        r.setTaskId(UUID.randomUUID().toString());
        r.setTopic(topic);
        r.setStatus(TaskStatus.COMPLETED);
        r.setKeyFindings(List.of("发现1", "发现2"));
        r.setSuggestedDirections(List.of("方向1"));
        r.setReferences(List.of("Ref1", "Ref2"));
        r.setRawResponse("原始响应内容");
        r.setStartTime(LocalDateTime.now());
        r.setEndTime(LocalDateTime.now());
        r.setDurationMs(1500L);
        return r;
    }

    private PaperWritingVO createMockWritingVO(String topic) {
        PaperWritingVO vo = new PaperWritingVO();
        vo.setContextId(UUID.randomUUID().toString());
        vo.setTopic(topic);
        vo.setStatus("COMPLETED");
        vo.setTotalDurationMs(5000L);
        vo.setCreatedAt(LocalDateTime.now());

        PaperWritingVO.SectionVO s1 = new PaperWritingVO.SectionVO();
        s1.setTitle("引言");
        s1.setLength(500);
        PaperWritingVO.SectionVO s2 = new PaperWritingVO.SectionVO();
        s2.setTitle("方法");
        s2.setLength(800);
        vo.setSections(List.of(s1, s2));

        PaperWritingVO.StepRecordVO step = new PaperWritingVO.StepRecordVO();
        step.setAgentName("选题评估");
        step.setAgentRole(com.paperai.model.enums.AgentRole.SUPERVISOR);
        step.setStatus(TaskStatus.COMPLETED);
        step.setDurationMs(800L);
        step.setSummary("选题可行");
        vo.setSteps(List.of(step));

        vo.setFinalDraft("# " + topic + "\n\n全文内容...");
        return vo;
    }
}
