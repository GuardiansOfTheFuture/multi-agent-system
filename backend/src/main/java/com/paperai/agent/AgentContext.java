package com.paperai.agent;

import com.paperai.model.AgentMessage;
import com.paperai.model.enums.TaskStatus;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 共享上下文（黑板模式）
 * 所有 Agent 共享读写此上下文，存储论文全生命周期的数据：
 * - 论文基本信息（标题、摘要、关键词）
 * - 各阶段产出（研究综述、大纲、各章节、审稿意见）
 * - Agent 间的消息通信记录
 * - 任务执行状态追踪
 *
 * @author: ch
 * @date 2026年05月11日
 */
public class AgentContext {

    /** 上下文ID */
    private final String contextId;

    /** 论文主题 */
    private String topic;

    /** 论文摘要 */
    private String abstractText;

    /** 关键词列表 */
    private List<String> keywords;

    /** 研究阶段产出 */
    private String researchOutput;

    /** 论文大纲 */
    private String outline;

    /** 章节内容（Map<章节标题, 内容>） */
    private final Map<String, String> sections = new LinkedHashMap<>();

    /** 审稿意见列表 */
    private final List<String> reviewComments = new ArrayList<>();

    /** 最终定稿 */
    private String finalDraft;

    /** Agent 消息通信记录 */
    private final List<AgentMessage> messages = new ArrayList<>();

    /** 任务状态追踪（Map<Agent角色, 状态>） */
    private final Map<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();

    /** 额外自定义数据 */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /** 各节点输出（nodeId → 输出文本），条件节点用此获取上一节点输出做评分判断 */
    private final Map<String, String> nodeOutputs = new ConcurrentHashMap<>();

    public void putNodeOutput(String nodeId, String output) { nodeOutputs.put(nodeId, output); }
    public String getNodeOutput(String nodeId) { return nodeOutputs.get(nodeId); }

    /** 创建时间 */
    private final LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    public AgentContext(String contextId, String topic) {
        this.contextId = contextId;
        this.topic = topic;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ===== 消息通信 =====

    public void addMessage(AgentMessage message) {
        this.messages.add(message);
        this.updatedAt = LocalDateTime.now();
    }

    public List<AgentMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public List<AgentMessage> getMessagesBySender(String roleCode) {
        return messages.stream()
                .filter(m -> m.getSender().getCode().equals(roleCode))
                .toList();
    }

    public List<AgentMessage> getMessagesByReceiver(String roleCode) {
        return messages.stream()
                .filter(m -> m.getReceiver() != null && m.getReceiver().getCode().equals(roleCode))
                .toList();
    }

    // ===== 任务状态 =====

    public void updateTaskStatus(String agentRole, TaskStatus status) {
        taskStatusMap.put(agentRole, status);
        this.updatedAt = LocalDateTime.now();
    }

    public TaskStatus getTaskStatus(String agentRole) {
        return taskStatusMap.getOrDefault(agentRole, TaskStatus.PENDING);
    }

    public Map<String, TaskStatus> getTaskStatusMap() {
        return Collections.unmodifiableMap(taskStatusMap);
    }

    public boolean isAllTasksCompleted() {
        return taskStatusMap.values().stream()
                .allMatch(s -> s == TaskStatus.COMPLETED || s == TaskStatus.SKIPPED);
    }

    // ===== 论文数据 =====

    public void addSection(String title, String content) {
        sections.put(title, content);
        this.updatedAt = LocalDateTime.now();
    }

    public String getSection(String title) {
        return sections.get(title);
    }

    // ===== Getters & Setters =====

    public String getContextId() {
        return contextId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
        this.updatedAt = LocalDateTime.now();
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
        this.updatedAt = LocalDateTime.now();
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
        this.updatedAt = LocalDateTime.now();
    }

    public String getResearchOutput() {
        return researchOutput;
    }

    public void setResearchOutput(String researchOutput) {
        this.researchOutput = researchOutput;
        this.updatedAt = LocalDateTime.now();
    }

    public String getOutline() {
        return outline;
    }

    public void setOutline(String outline) {
        this.outline = outline;
        this.updatedAt = LocalDateTime.now();
    }

    public Map<String, String> getSections() {
        return Collections.unmodifiableMap(sections);
    }

    public List<String> getReviewComments() {
        return Collections.unmodifiableList(reviewComments);
    }

    public void addReviewComment(String comment) {
        this.reviewComments.add(comment);
        this.updatedAt = LocalDateTime.now();
    }

    public String getFinalDraft() {
        return finalDraft;
    }

    public void setFinalDraft(String finalDraft) {
        this.finalDraft = finalDraft;
        this.updatedAt = LocalDateTime.now();
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
