package com.paperai.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
    void testReviewComments() {
        ctx.addReviewComment("方法部分需要补充更多细节");
        ctx.addReviewComment("实验结果分析不够深入");

        assertEquals(2, ctx.getReviewComments().size());
        assertTrue(ctx.getReviewComments().get(0).contains("方法部分"));
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
    }
}
