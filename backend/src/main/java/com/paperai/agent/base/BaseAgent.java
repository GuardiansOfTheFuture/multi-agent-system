package com.paperai.agent.base;

import com.paperai.agent.AgentContext;
import com.paperai.common.BusinessException;
import com.paperai.common.ResultCode;
import com.paperai.model.enums.AgentMessageType;
import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import com.paperai.model.AgentMessage;
import com.paperai.service.LlmCacheService;
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

    /** LLM 调用异常的 ErrorCode */
    private static final int AI_CALL_ERROR_CODE = ResultCode.AI_SERVICE_ERROR.getCode();


    /** 当前 Agent 的角色 */
    protected final AgentRole role;

    /** Spring AI ChatClient（默认） */
    protected final ChatClient chatClient;

    /** LLM 响应缓存 */
    protected final LlmCacheService llmCacheService;

    /** 共享上下文 */
    protected AgentContext context;

    /** 临时覆盖 ChatClient（节点级模型配置） */
    private ChatClient customClient;

    /** 临时覆盖 System Prompt（节点级自定义提示词） */
    private String customPrompt;

    protected BaseAgent(AgentRole role, ChatClient chatClient, LlmCacheService llmCacheService) {
        this.role = role;
        this.chatClient = chatClient;
        this.llmCacheService = llmCacheService;
    }

    /** 重置节点级配置（每次 executeWithConfig 前调用） */
    private void resetOverrides() {
        this.customClient = null;
        this.customPrompt = null;
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
     * 带节点级配置的执行 — 支持动态切换模型和自定义 System Prompt。
     * FlowEngine 在节点有 config 时调用此方法而非 executeTaskStream。
     *
     * @param task         任务描述
     * @param context      共享上下文
     * @param onToken      流式回调
     * @param modelClient  节点指定的 ChatClient（可为 null，则用默认）
     * @param customPrompt 节点自定义 System Prompt（可为 null，则用默认）
     * @return 最终完整结果
     */
    public String executeWithConfig(String task, AgentContext context, java.util.function.Consumer<String> onToken,
                                     ChatClient modelClient, String customPrompt) {
        resetOverrides();
        this.customClient = modelClient;
        this.customPrompt = (customPrompt != null && !customPrompt.isBlank()) ? customPrompt : null;
        return executeTaskStream(task, context, onToken);
    }

    /** 获取当前有效 System Prompt（优先用节点级自定义） */
    private String effectiveSystemPrompt() {
        if (customPrompt != null && !customPrompt.isBlank()) return customPrompt;
        return getSystemPrompt();
    }

    /** 获取当前有效 ChatClient（优先用节点级自定义） */
    private ChatClient effectiveChatClient() {
        return customClient != null ? customClient : chatClient;
    }

    /**
     * 与 LLM 对话（同步，一次性返回完整结果）
     * 请求/响应详情由 LoggerAdvisor 统一记录，此处只记 Agent 层面的启动和完成
     */
    protected String callLlm(String userMessage) {
        String cacheKey = llmCacheService.computeKey(getSystemPrompt(), userMessage);
        String cached = llmCacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            String response = effectiveChatClient().prompt()
                    .system(effectiveSystemPrompt())
                    .user(userMessage)
                    .call()
                    .content();

            log.info("[{}] LLM 响应完成，长度: {}", role.getDisplayName(),
                    response != null ? response.length() : 0);

            llmCacheService.put(cacheKey, response);
            return response;
        } catch (Exception e) {
            log.error("[{}] LLM 调用失败", role.getDisplayName(), e);
            throw new BusinessException(AI_CALL_ERROR_CODE,
                    "AI 服务(" + role.getDisplayName() + ")调用失败：" + extractShortMessage(e));
        }
    }

    /**
     * 与 LLM 流式对话 — 逐 token 回调，适用于实时展示
     *
     * @param userMessage 用户消息
     * @param onToken     每个 token 的回调（传入累计到当前的完整文本）
     * @return 最终的完整响应
     */
    protected String callLlmStream(String userMessage, java.util.function.Consumer<String> onToken) {
        String cacheKey = llmCacheService.computeKey(getSystemPrompt(), userMessage);
        String cached = llmCacheService.get(cacheKey);
        if (cached != null) {
            onToken.accept(cached);
            return cached;
        }

        StringBuilder full = new StringBuilder();

        try {
            String response = effectiveChatClient().prompt()
                    .system(effectiveSystemPrompt())
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
            llmCacheService.put(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.error("[{}] LLM 流式调用失败", role.getDisplayName(), e);
            throw new BusinessException(AI_CALL_ERROR_CODE,
                    "AI 服务(" + role.getDisplayName() + ")流式调用失败：" + extractShortMessage(e));
        }
    }

    /**
     * 从异常堆栈提取简短信息，避免暴露原始 JSON
     */
    private String extractShortMessage(Throwable e) {
        if (e == null) return "未知错误";
        // 优先取最内层 cause 的信息
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        if (msg == null) msg = e.getMessage();
        if (msg == null) return "未知错误";
        // 截断过长消息
        return msg.length() > 150 ? msg.substring(0, 150) + "..." : msg;
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
