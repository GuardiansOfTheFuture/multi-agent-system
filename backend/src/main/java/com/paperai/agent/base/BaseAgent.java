package com.paperai.agent.base;

import com.paperai.agent.AgentContext;
import com.paperai.model.enums.AgentMessageType;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import com.paperai.model.AgentMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Agent 抽象基类
 * 所有具体 Agent 都继承此类，提供：
 * - 角色定义（System Prompt）
 * - ChatClient 调用封装
 * - 消息收发协议
 * - 上下文读写
 * - 结构化输出解析
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Slf4j
public abstract class BaseAgent {


    /** 当前 Agent 的角色 */
    protected final AgentRole role;

    /** Spring AI ChatClient */
    protected final ChatClient chatClient;

    /** 共享上下文 */
    protected AgentContext context;

    protected BaseAgent(AgentRole role, ChatClient chatClient) {
        this.role = role;
        this.chatClient = chatClient;
    }

    /**
     * 获取该 Agent 的 System Prompt（角色定义）
     */
    protected abstract String getSystemPrompt();

    /**
     * 执行任务 — 子类实现具体逻辑
     *
     * @param task    任务描述
     * @param context 共享上下文
     * @return 执行结果
     */
    public abstract String executeTask(String task, AgentContext context);

    /**
     * 执行任务（流式版本）— 默认实现走同步 callLlm。
     * 子类可重写以支持逐 token 回调。
     *
     * @param task     任务描述
     * @param context  共享上下文
     * @param onToken  每个 token 的回调
     * @return 最终完整结果
     */
    public String executeTaskStream(String task, AgentContext context, java.util.function.Consumer<String> onToken) {
        // 默认实现：用流式调用 LLM
        this.context = context;
        return callLlmStream(task, onToken);
    }

    /**
     * 与 LLM 对话（同步，一次性返回完整结果）
     * 请求/响应详情由 LoggerAdvisor 统一记录，此处只记 Agent 层面的启动和完成
     */
    protected String callLlm(String userMessage) {
        String response = chatClient.prompt()
                .system(getSystemPrompt())
                .user(userMessage)
                .call()
                .content();

        log.info("[{}] LLM 响应完成，长度: {}", role.getDisplayName(),
                response != null ? response.length() : 0);

        return response;
    }

    /**
     * 与 LLM 流式对话 — 逐 token 回调，适用于实时展示
     *
     * @param userMessage 用户消息
     * @param onToken     每个 token 的回调（传入累计到当前的完整文本）
     * @return 最终的完整响应
     */
    protected String callLlmStream(String userMessage, java.util.function.Consumer<String> onToken) {
        StringBuilder full = new StringBuilder();

        String response = chatClient.prompt()
                .system(getSystemPrompt())
                .user(userMessage)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    full.append(chunk);
                    onToken.accept(full.toString());
                })
                .blockLast(); // 等待流结束，返回最后一片

        String result = full.toString();
        log.info("[{}] LLM 流式响应完成，长度: {}", role.getDisplayName(), result.length());
        return result;
    }

    /**
     * 与 LLM 对话（带上下文）
     *
     * @param userMessage  用户消息
     * @param contextText  额外上下文文本
     * @return LLM 响应内容
     */
    protected String callLlmWithContext(String userMessage, String contextText) {
        String fullPrompt = """
                【上下文信息】
                %s
                
                【任务】
                %s
                """.formatted(contextText, userMessage);

        return callLlm(fullPrompt);
    }

    /**
     * 发送消息给其他 Agent
     *
     * @param receiver 目标角色
     * @param type     消息类型
     * @param content  消息内容
     */
    protected void sendMessage(AgentRole receiver, AgentMessageType type, String content) {
        AgentMessage message = new AgentMessage(this.role, receiver, type, content);
        if (this.context != null) {
            this.context.addMessage(message);
        }
        log.info("[{}] → [{}] 发送消息: type={}", role.getDisplayName(),
                receiver.getDisplayName(), type.getCode());
    }

    /**
     * 广播消息给所有 Agent
     *
     * @param type    消息类型
     * @param content 消息内容
     */
    protected void broadcast(AgentMessageType type, String content) {
        AgentMessage message = new AgentMessage(this.role, null, type, content);
        if (this.context != null) {
            this.context.addMessage(message);
        }
        log.info("[{}] 广播消息: type={}", role.getDisplayName(), type.getCode());
    }

    /**
     * 更新任务状态
     */
    protected void updateStatus(TaskStatus status) {
        if (this.context != null) {
            this.context.updateTaskStatus(this.role.getCode(), status);
        }
    }

    // ===== Getters =====

    public AgentRole getRole() {
        return role;
    }

    public AgentContext getContext() {
        return context;
    }

    public void setContext(AgentContext context) {
        this.context = context;
    }
}
