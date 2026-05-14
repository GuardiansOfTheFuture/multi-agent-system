package com.paperai.demo;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring AI + DashScope (通义千问) 集成测试
 * 验证 AI 调用链路是否正常
 *
 * 前提条件：
 * 1. 环境变量 DASHSCOPE_API_KEY 已配置
 * 2. pom.xml 中 spring-ai-alibaba-dashscope 依赖已添加
 *
 * @author: ch
 * @date 2026年05月13日
 */
@SpringBootTest
class AiDemoTest {

    @Resource
    private ChatClient dashScopeChatClient;

    @Test
    void testSimpleChat() {
        // 发送一条简单消息，验证 AI 能正常回复
        String response = dashScopeChatClient.prompt()
                .user("你好，请用一句话介绍你自己")
                .call()
                .content();

        System.out.println("===== AI 回复 =====");
        System.out.println(response);
        System.out.println("==================");

        // 断言：AI 必须有回复
        assert response != null && !response.isBlank()
                : "AI 回复为空，DashScope 调用失败";
    }

    @Test
    void testAgentSystemPrompt() {
        // 模拟 SupervisorAgent 的角色系统提示词 + 用户消息
        String systemPrompt = """
                你是一位资深的学术论文导师（Supervisor）。
                你的职责是：
                1. 评估论文选题的可行性
                2. 审阅论文大纲结构的合理性
                3. 给出建设性的改进方向
                请保持专业、严谨、有建设性。
                """;

        String userMessage = "我想写一篇关于「基于大语言模型的代码自动生成技术综述」的论文，这个选题怎么样？";

        String response = dashScopeChatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

        System.out.println("===== System Prompt 测试 =====");
        System.out.println("回复内容：");
        System.out.println(response);
        System.out.println("==============================");

        assert response != null && response.length() > 50
                : "AI 回复过短，可能未按预期工作";
    }
}
