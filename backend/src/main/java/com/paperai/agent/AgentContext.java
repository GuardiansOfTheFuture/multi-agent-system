package com.paperai.agent;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 共享上下文（黑板模式）— 论文全生命周期的数据载体。
 * 线程安全：所有可变集合使用同步包装或 ConcurrentHashMap。
 */
public class AgentContext {

    private final String contextId;
    private String topic;
    private String abstractText;
    private List<String> keywords;

    /** 研究阶段产出 */
    private String researchOutput;

    /** 论文大纲 */
    private String outline;

    /** 章节内容（标题 → 内容） */
    private final Map<String, String> sections = Collections.synchronizedMap(new LinkedHashMap<>());

    /** 审稿意见列表 */
    private final List<String> reviewComments = Collections.synchronizedList(new ArrayList<>());

    /** 最终定稿 */
    private String finalDraft;

    /** 关联知识图谱的 graphData JSON */
    private String kgGraphData;

    /** 扩展属性（方向描述、论文标题等） */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AgentContext(String contextId, String topic) {
        this.contextId = contextId;
        this.topic = topic;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    private void touch() { this.updatedAt = LocalDateTime.now(); }

    // ===== 论文数据存取 =====

    public void addSection(String title, String content) { sections.put(title, content); touch(); }
    public String getSection(String title) { return sections.get(title); }
    public void addReviewComment(String comment) { reviewComments.add(comment); touch(); }

    public void setAttribute(String key, Object value) { attributes.put(key, value); }
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) { return (T) attributes.get(key); }

    // ===== Getters & Setters =====

    public String getContextId() { return contextId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; touch(); }
    public String getAbstractText() { return abstractText; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; touch(); }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; touch(); }
    public String getResearchOutput() { return researchOutput; }
    public void setResearchOutput(String researchOutput) { this.researchOutput = researchOutput; touch(); }
    public String getOutline() { return outline; }
    public void setOutline(String outline) { this.outline = outline; touch(); }
    public Map<String, String> getSections() { return Collections.unmodifiableMap(sections); }
    public List<String> getReviewComments() { return Collections.unmodifiableList(reviewComments); }
    public String getFinalDraft() { return finalDraft; }
    public void setFinalDraft(String finalDraft) { this.finalDraft = finalDraft; touch(); }
    public String getKgGraphData() { return kgGraphData; }
    public void setKgGraphData(String kgGraphData) { this.kgGraphData = kgGraphData; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
