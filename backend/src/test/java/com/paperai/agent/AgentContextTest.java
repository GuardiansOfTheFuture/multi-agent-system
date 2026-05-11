package com.paperai.agent;

import com.paperai.model.AgentMessage;
import com.paperai.model.enums.AgentMessageType;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentContext 黑板模式测试
 *
 * @author: ch
 * @date 2026年05月11日
 */
class AgentContextTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new AgentContext("test-ctx-001", "深度学习在NLP中的应用");
    }

    @Test
    void testContextCreation() {
        assertEquals("test-ctx-001", ctx.getContextId());
        assertEquals("深度学习在NLP中的应用", ctx.getTopic());
        assertNotNull(ctx.getCreatedAt());
        assertNotNull(ctx.getUpdatedAt());
    }

    @Test
    void testTopicUpdate() {
        ctx.setTopic("新主题");
        assertEquals("新主题", ctx.getTopic());
    }

    @Test
    void testResearchOutput() {
        String output = "文献综述内容...";
        ctx.setResearchOutput(output);
        assertEquals(output, ctx.getResearchOutput());
    }

    @Test
    void testSectionOperations() {
        ctx.addSection("引言", "引言内容");
        ctx.addSection("方法", "方法内容");

        assertEquals("引言内容", ctx.getSection("引言"));
        assertEquals("方法内容", ctx.getSection("方法"));
        assertEquals(2, ctx.getSections().size());
    }

    @Test
    void testMessageCommunication() {
        AgentMessage msg = new AgentMessage(
                AgentRole.RESEARCHER,
                AgentRole.WRITER,
                AgentMessageType.TASK_RESULT,
                "研究完成"
        );
        ctx.addMessage(msg);

        List<AgentMessage> messages = ctx.getMessages();
        assertEquals(1, messages.size());

        List<AgentMessage> toWriter = ctx.getMessagesByReceiver("WRITER");
        assertEquals(1, toWriter.size());

        List<AgentMessage> fromResearcher = ctx.getMessagesBySender("RESEARCHER");
        assertEquals(1, fromResearcher.size());
    }

    @Test
    void testTaskStatusTracking() {
        ctx.updateTaskStatus("RESEARCHER", TaskStatus.IN_PROGRESS);
        assertEquals(TaskStatus.IN_PROGRESS, ctx.getTaskStatus("RESEARCHER"));
        assertFalse(ctx.isAllTasksCompleted());

        ctx.updateTaskStatus("RESEARCHER", TaskStatus.COMPLETED);
        ctx.updateTaskStatus("WRITER", TaskStatus.SKIPPED);
        assertTrue(ctx.isAllTasksCompleted());
    }

    @Test
    void testReviewComments() {
        ctx.addReviewComment("方法部分需要补充更多细节");
        ctx.addReviewComment("实验结果分析不够深入");

        List<String> comments = ctx.getReviewComments();
        assertEquals(2, comments.size());
        assertTrue(comments.get(0).contains("方法部分"));
    }

    @Test
    void testOutline() {
        String outline = "1. 引言\n2. 方法\n3. 实验\n4. 结论";
        ctx.setOutline(outline);
        assertEquals(outline, ctx.getOutline());
    }

    @Test
    void testCustomAttributes() {
        ctx.setAttribute("direction", "自然语言处理");
        ctx.setAttribute("round", 3);

        assertEquals("自然语言处理", ctx.getAttribute("direction"));
        // assertEquals(3, ctx.getAttribute("round"));
    }
}
