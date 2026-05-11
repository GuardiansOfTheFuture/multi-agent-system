package com.paperai.model.enums;

/**
 * Agent 角色枚举
 *
 * @author: ch
 * @date 2026年05月11日
 */
public enum AgentRole {

    SUPERVISOR("SUPERVISOR", "导师 Agent", "把控研究方向，审阅大纲，给出修改意见"),
    RESEARCHER("RESEARCHER", "研究员 Agent", "文献调研，信息收集，综述撰写"),
    WRITER("WRITER", "写手 Agent", "撰写论文各章节，组织语言"),
    REVIEWER("REVIEWER", "审稿人 Agent", "批判性审阅，找漏洞，提改进意见"),
    POLISHER("POLISHER", "润色 Agent", "语法校对，格式规范，引用检查");

    private final String code;
    private final String displayName;
    private final String description;

    AgentRole(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static AgentRole fromCode(String code) {
        for (AgentRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown AgentRole code: " + code);
    }
}
