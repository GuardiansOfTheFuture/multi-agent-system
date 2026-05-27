package com.paperai.service;

import com.paperai.agent.AgentDefinition;
import com.paperai.agent.AgentDefinitions;
import com.paperai.agent.AgentExecutor;
import com.paperai.event.StepEventPublisher;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.vo.PaperWritingVO;
import com.paperai.service.FlowEngineService;
import com.paperai.service.impl.OrchestratorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    @Mock
    private AgentExecutor agentExecutor;

    @Mock
    private PaperService paperService;

    @Mock
    private AgentTaskService agentTaskService;

    @Mock
    private StepEventPublisher stepEventPublisher;

    @Mock
    private FlowDefinitionService flowDefinitionService;

    @Mock
    private FlowEngineService flowEngine;

    @Mock
    private KnowledgeGraphService knowledgeGraphService;

    @InjectMocks
    private OrchestratorServiceImpl orchestratorService;

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
    void testAgentDefinitionsCoverage() {
        // 确保 5 个预设定义都存在
        assertNotNull(AgentDefinitions.SUPERVISOR);
        assertNotNull(AgentDefinitions.RESEARCHER);
        assertNotNull(AgentDefinitions.WRITER);
        assertNotNull(AgentDefinitions.REVIEWER);
        assertNotNull(AgentDefinitions.POLISHER);

        assertEquals(AgentRole.SUPERVISOR, AgentDefinitions.SUPERVISOR.role());
        assertEquals(AgentRole.RESEARCHER, AgentDefinitions.RESEARCHER.role());
        assertEquals(AgentRole.WRITER, AgentDefinitions.WRITER.role());
        assertEquals(AgentRole.REVIEWER, AgentDefinitions.REVIEWER.role());
        assertEquals(AgentRole.POLISHER, AgentDefinitions.POLISHER.role());

        // 验证 System Prompt 非空
        for (AgentDefinition def : new AgentDefinition[]{
                AgentDefinitions.SUPERVISOR, AgentDefinitions.RESEARCHER,
                AgentDefinitions.WRITER, AgentDefinitions.REVIEWER, AgentDefinitions.POLISHER}) {
            assertNotNull(def.systemPrompt());
            assertFalse(def.systemPrompt().isBlank());
        }
    }

    @Test
    void testForRole() {
        for (AgentRole role : AgentRole.values()) {
            AgentDefinition def = AgentDefinitions.forRole(role);
            assertNotNull(def);
            assertEquals(role, def.role());
        }
    }

    @Test
    void testAgentDefinitionRecord() {
        AgentDefinition def = new AgentDefinition(AgentRole.WRITER, "test prompt");
        assertEquals(AgentRole.WRITER, def.role());
        assertEquals("test prompt", def.systemPrompt());
    }
}
