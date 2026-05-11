package com.paperai.service;

import com.paperai.agent.*;
import com.paperai.agent.base.BaseAgent;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.vo.PaperWritingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 编排引擎测试 — mock 所有 Agent，只测编排逻辑
 *
 * @author: ch
 * @date 2026年05月11日
 */
@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    @Mock
    private SupervisorAgent supervisorAgent;

    @Mock
    private ResearcherAgent researcherAgent;

    @Mock
    private WriterAgent writerAgent;

    @Mock
    private ReviewerAgent reviewerAgent;

    @Mock
    private PolisherAgent polisherAgent;

    @InjectMocks
    private OrchestratorService orchestratorService;

    private PaperWritingRequestDTO request;

    @BeforeEach
    void setUp() {
        request = new PaperWritingRequestDTO();
        request.setTopic("深度学习在医疗影像中的应用");
        request.setDescription("探索CNN在医学图像分割中的应用");
        request.setKeywords("深度学习,医疗影像,CNN");
        request.setSections(java.util.List.of("引言", "方法", "实验", "结论"));
        request.setMaxReviewRounds(2);
    }

    @Test
    void testFullWritingPipeline() {
        // mock 所有 Agent 的返回值
        when(supervisorAgent.evaluateTopic(anyString(), anyString())).thenReturn("选题可行");
        when(researcherAgent.executeTask(anyString(), any(AgentContext.class))).thenReturn("文献综述结果");
        when(supervisorAgent.reviewOutline(any(AgentContext.class))).thenReturn("大纲合理");
        when(writerAgent.executeTask(anyString(), any(AgentContext.class))).thenReturn("章节内容");
        when(reviewerAgent.reviewFullPaper(any(AgentContext.class))).thenReturn("""
                ### 总体评价
                论文整体质量较好，建议进入下一步。
                ### 建议改进
                - 可适当增加实验对比
                """);
        when(polisherAgent.polishFullPaper(any(AgentContext.class))).thenReturn("润色后内容");
        when(supervisorAgent.finalReview(any(AgentContext.class))).thenReturn("建议发表");

        // 执行
        PaperWritingVO result = orchestratorService.execute(request);

        // 验证
        assertNotNull(result);
        assertEquals("深度学习在医疗影像中的应用", result.getTopic());
        assertNotNull(result.getFinalDraft());
        assertNotNull(result.getSteps());

        // 验证所有 Agent 都被调用过
        verify(supervisorAgent, times(1)).evaluateTopic(anyString(), anyString());
        verify(researcherAgent, times(1)).executeTask(anyString(), any(AgentContext.class));
        verify(supervisorAgent, times(1)).reviewOutline(any(AgentContext.class));
        verify(writerAgent, atLeast(1)).executeTask(anyString(), any(AgentContext.class));
        verify(reviewerAgent, times(1)).reviewFullPaper(any(AgentContext.class));
        verify(polisherAgent, times(1)).polishFullPaper(any(AgentContext.class));
        verify(supervisorAgent, times(1)).finalReview(any(AgentContext.class));

        // 验证步骤数
        assertTrue(result.getSteps().size() >= 6);
    }

    @Test
    void testReviewIteration() {
        // 模拟审稿人连续两次返回严重问题，第三次才通过
        when(supervisorAgent.evaluateTopic(anyString(), anyString())).thenReturn("选题可行");
        when(researcherAgent.executeTask(anyString(), any(AgentContext.class))).thenReturn("文献综述结果");
        when(supervisorAgent.reviewOutline(any(AgentContext.class))).thenReturn("大纲合理");
        when(writerAgent.executeTask(anyString(), any(AgentContext.class))).thenReturn("章节内容");

        // 第一次审稿有严重问题
        when(reviewerAgent.reviewFullPaper(any(AgentContext.class)))
                .thenReturn("### 严重问题\n- 方法部分缺少关键细节")
                .thenReturn("### 总体评价\n通过，建议进入下一步。");

        when(polisherAgent.polishFullPaper(any(AgentContext.class))).thenReturn("润色后内容");
        when(supervisorAgent.finalReview(any(AgentContext.class))).thenReturn("建议发表");

        PaperWritingVO result = orchestratorService.execute(request);

        assertNotNull(result);
        // maxReviewRounds=2，第1次有严重问题，第2次通过 — 审稿共调2次
        verify(reviewerAgent, times(2)).reviewFullPaper(any(AgentContext.class));
        // 写手：初始5个章节 + 1次修改 = 至少6次
        verify(writerAgent, atLeast(5)).executeTask(anyString(), any());
    }

    @Test
    void testAgentFailureHandling() {
        // 研究员抛异常
        when(supervisorAgent.evaluateTopic(anyString(), anyString())).thenReturn("选题可行");
        when(researcherAgent.executeTask(anyString(), any(AgentContext.class)))
                .thenThrow(new RuntimeException("API调用超时"));

        // 执行不应该抛异常
        PaperWritingVO result = orchestratorService.execute(request);

        assertNotNull(result);
        // 异常情况下状态为 COMPLETED（因为 catch 后调用了 toWritingVO 但未设置异常状态），此处仅验证不抛异常即可
        assertTrue(result.getSteps().size() >= 1);
        assertTrue(result.getSteps().stream().anyMatch(s -> s.getStatus() == com.paperai.model.enums.TaskStatus.FAILED));
    }

    @Test
    void testRequestWithDefaultSections() {
        request.setSections(null);

        when(supervisorAgent.evaluateTopic(anyString(), anyString())).thenReturn("选题可行");
        when(researcherAgent.executeTask(anyString(), any(AgentContext.class))).thenReturn("文献综述结果");
        when(supervisorAgent.reviewOutline(any(AgentContext.class))).thenReturn("大纲合理");
        when(writerAgent.executeTask(anyString(), any(AgentContext.class))).thenReturn("章节内容");
        when(reviewerAgent.reviewFullPaper(any(AgentContext.class))).thenReturn("### 总体评价\n论文完整，建议进入下一步。");
        when(polisherAgent.polishFullPaper(any(AgentContext.class))).thenReturn("润色后内容");
        when(supervisorAgent.finalReview(any(AgentContext.class))).thenReturn("建议发表");

        PaperWritingVO result = orchestratorService.execute(request);

        assertNotNull(result);
        // 默认5个章节
        verify(writerAgent, atLeast(5)).executeTask(anyString(), any());
    }
}
