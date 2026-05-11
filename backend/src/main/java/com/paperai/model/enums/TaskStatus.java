package com.paperai.model.enums;

/**
 * 任务执行状态枚举
 *
 * @author: ch
 * @date 2026年05月11日
 */
public enum TaskStatus {

    PENDING("PENDING", "等待执行"),
    IN_PROGRESS("IN_PROGRESS", "正在执行"),
    COMPLETED("COMPLETED", "执行完成"),
    FAILED("FAILED", "执行失败"),
    SKIPPED("SKIPPED", "已跳过");

    private final String code;
    private final String description;

    TaskStatus(String code, String description) {
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
