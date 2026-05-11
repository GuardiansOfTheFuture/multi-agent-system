package com.paperai.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 工具类
 *
 * @author: ch
 * @date 2026年05月11日
 */
public class MarkdownUtils {

    /** 提取 Markdown 标题 */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE);

    /** 提取代码块 */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:\\w+)?\\n([\\s\\S]*?)```", Pattern.MULTILINE);

    /**
     * 从 Markdown 中提取所有标题（大纲）
     */
    public static List<String> extractHeadings(String markdown) {
        List<String> headings = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(markdown);
        while (matcher.find()) {
            headings.add(matcher.group(1).trim());
        }
        return headings;
    }

    /**
     * 从 Markdown 中提取第一个代码块内容
     */
    public static String extractFirstCodeBlock(String markdown) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(markdown);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 截取指定长度的摘要
     */
    public static String summary(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    /**
     * 按章节分割 Markdown 文本
     */
    public static List<Section> splitIntoSections(String markdown) {
        List<Section> sections = new ArrayList<>();
        String[] lines = markdown.split("\n");
        StringBuilder currentContent = new StringBuilder();
        String currentTitle = null;

        for (String line : lines) {
            Matcher matcher = HEADING_PATTERN.matcher(line);
            if (matcher.matches()) {
                if (currentTitle != null) {
                    sections.add(new Section(currentTitle, currentContent.toString().trim()));
                }
                currentTitle = matcher.group(1).trim();
                currentContent = new StringBuilder();
            } else {
                if (currentContent.length() > 0) currentContent.append("\n");
                currentContent.append(line);
            }
        }
        if (currentTitle != null) {
            sections.add(new Section(currentTitle, currentContent.toString().trim()));
        }
        return sections;
    }

    public record Section(String title, String content) {}
}
