package com.paperai.utils;

public final class TextUtils {

    private TextUtils() {}

    public static String truncate(String s, int max) {
        return s == null ? "" : s.length() > max ? s.substring(0, max) + "..." : s;
    }

    public static String sanitize(String name) {
        return name == null ? "unnamed" : name.replaceAll("[\\\\/:*?\"<>|]", "_").replace(".json", "");
    }

    /** 取文本指纹用于去重：短文本直接返回，长文本取首尾各30字符 */
    public static String fingerprint(String text) {
        if (text == null) return "";
        return text.length() < 60 ? text : text.substring(0, 30) + text.substring(text.length() - 30);
    }
}
