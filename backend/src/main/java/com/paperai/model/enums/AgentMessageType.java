package com.paperai.model.enums;

/**
 * Agent 消息类型枚举
 *
 * @author: ch
 * @date 2026年05月11日
 */
public enum AgentMessageType {

    TASK_ASSIGN("TASK_ASSIGN", "任务分配"),
    TASK_RESULT("TASK_RESULT", "任务结果"),
    REVIEW_COMMENT("REVIEW_COMMENT", "审阅意见"),
    REVISION_REQUEST("REVISION_REQUEST", "修改请求"),
    DEBATE_ARGUMENT("DEBATE_ARGUMENT", "辩论论点"),
    COORDINATION("COORDINATION", "协调消息");

    private final String code;
    private final String description;

    AgentMessageType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
