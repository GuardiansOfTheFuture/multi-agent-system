package com.paperai.converter;

/** Markdown → 目标格式的导出策略 */
public interface ExportStrategy {
    byte[] export(String markdown, String title);
    String format();
}
