package com.paperai.utils;

import java.util.UUID;

/**
 * 文本工具类
 *
 * @author: ch
 * @date 2026年05月11日
 */
public class TextUtils {

    /**
     * 生成短 UUID（8位）
     */
    public static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 截断文本
     */
    public static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    /**
     * 判断是否为空
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 解析关键词列表（逗号/分号/空格分隔）
     */
    public static String[] parseKeywords(String keywords) {
        if (isBlank(keywords)) return new String[0];
        return keywords.split("[,;，；\\s]+");
    }
}
