package com.paperai.common;

/**
 * 系统常量
 *
 * @author: ch
 * @date 2026年05月11日
 */
public interface Constants {

    // ===== 日期格式 =====
    String DATE_FORMAT = "yyyy-MM-dd";
    String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // ===== 论文状态 =====
    String PAPER_STATUS_DRAFT = "DRAFT";
    String PAPER_STATUS_REVIEWING = "REVIEWING";
    String PAPER_STATUS_COMPLETED = "COMPLETED";
    String PAPER_STATUS_FAILED = "FAILED";

    // ===== Agent 默认配置 =====
    int DEFAULT_MAX_REVIEW_ROUNDS = 3;
    int LLM_TIMEOUT_MS = 60000;

    // ===== API 路径 =====
    String API_PREFIX = "/api";
}
