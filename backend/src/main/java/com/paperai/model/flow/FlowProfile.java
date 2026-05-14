package com.paperai.model.flow;

import java.util.List;

/**
 * 预设写作流程定义 — 控制 runSteps() 中哪些步骤启用
 *
 * @author ch
 */
public enum FlowProfile {

    STANDARD(
        "standard",
        "标准流程",
        "7步完整流程：选题评估→文献调研→大纲审阅→逐章写作→审稿迭代→润色定稿→最终审核",
        true, true, true, true, true, true, true, null
    ),

    QUICK_DRAFT(
        "quick_draft",
        "快速草稿",
        "跳过选题评估和审稿，快速生成初稿。适合赶时间、需要快速出稿的场景",
        false, true, true, true, false, true, true, null
    ),

    DEEP_RESEARCH(
        "deep_research",
        "深度研究",
        "强化文献调研和审稿（强制5轮）。适合高质量期刊/学位论文",
        true, true, true, true, true, true, true, 5
    ),

    WRITE_ONLY(
        "write_only",
        "纯写作",
        "跳过调研和审稿，直接按大纲写作。适合已有充足素材、只需组织成文的场景",
        false, false, false, true, false, true, true, null
    ),

    REVIEW_PAPER(
        "review_paper",
        "综述论文",
        "侧重文献调研和综合分析，无审稿迭代。适合撰写文献综述类论文",
        false, true, true, true, false, true, true, null
    );

    private final String id;
    private final String name;
    private final String description;
    private final boolean topicEvaluation;
    private final boolean literatureResearch;
    private final boolean outlineReview;
    private final boolean writeSections;
    private final boolean reviewIteration;
    private final boolean polish;
    private final boolean finalReview;
    private final Integer forceReviewRounds;

    FlowProfile(String id, String name, String description,
                boolean topicEvaluation, boolean literatureResearch,
                boolean outlineReview, boolean writeSections,
                boolean reviewIteration, boolean polish, boolean finalReview,
                Integer forceReviewRounds) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.topicEvaluation = topicEvaluation;
        this.literatureResearch = literatureResearch;
        this.outlineReview = outlineReview;
        this.writeSections = writeSections;
        this.reviewIteration = reviewIteration;
        this.polish = polish;
        this.finalReview = finalReview;
        this.forceReviewRounds = forceReviewRounds;
    }

    public static FlowProfile fromId(String id) {
        if (id == null) return STANDARD;
        for (FlowProfile f : values()) {
            if (f.id.equals(id)) return f;
        }
        return STANDARD;
    }

    public static List<FlowProfile> listAll() {
        return List.of(values());
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isTopicEvaluation() { return topicEvaluation; }
    public boolean isLiteratureResearch() { return literatureResearch; }
    public boolean isOutlineReview() { return outlineReview; }
    public boolean isWriteSections() { return writeSections; }
    public boolean isReviewIteration() { return reviewIteration; }
    public boolean isPolish() { return polish; }
    public boolean isFinalReview() { return finalReview; }
    public Integer getForceReviewRounds() { return forceReviewRounds; }
}
