package com.paperai.service;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.data.MutableDataSet;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.*;

import java.io.File;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ExportService {

    @Resource private PaperService paperService;

    private static final Parser MARKDOWN_PARSER;
    private static final HtmlRenderer HTML_RENDERER;

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, java.util.Arrays.asList(
                com.vladsch.flexmark.ext.tables.TablesExtension.create(),
                com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
                com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension.create(),
                com.vladsch.flexmark.ext.autolink.AutolinkExtension.create(),
                com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension.create()
        ));
        MARKDOWN_PARSER = Parser.builder(options).build();
        HTML_RENDERER = HtmlRenderer.builder(options).build();
    }

    /** 获取论文内容 Markdown 文本 */
    private String getContent(Long paperId, Integer versionNo) {
        if (versionNo != null) {
            var pv = paperService.getVersion(paperId, versionNo);
            return pv != null ? pv.getContent() : "";
        }
        var pv = paperService.getLatestVersion(paperId);
        return pv != null ? pv.getContent() : "";
    }

    // ==================== HTML ====================

    public String toHtml(String markdown) {
        Document doc = MARKDOWN_PARSER.parse(markdown != null ? markdown : "");
        return wrapHtml(HTML_RENDERER.render(doc));
    }

    private String wrapHtml(String body) {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head><meta charset="UTF-8">
            <style>
              body { font-family: "Noto Serif CJK SC", "SimSun", serif; max-width: 780px; margin: 40px auto; padding: 20px; line-height: 1.8; color: #222; }
              h1 { text-align: center; font-size: 1.6em; border-bottom: 2px solid #222; padding-bottom: 8px; }
              h2 { font-size: 1.3em; margin-top: 28px; border-bottom: 1px solid #ccc; padding-bottom: 4px; }
              h3 { font-size: 1.1em; }
              table { border-collapse: collapse; width: 100%%; margin: 12px 0; }
              th, td { border: 1px solid #666; padding: 6px 10px; text-align: left; }
              th { background: #f0f0f0; }
              code { background: #f5f5f5; padding: 1px 4px; border-radius: 3px; font-size: 0.9em; }
              pre { background: #f5f5f5; padding: 12px; border-radius: 4px; overflow-x: auto; }
              blockquote { border-left: 3px solid #888; padding-left: 16px; color: #555; margin-left: 0; }
            </style></head>
            <body>%s</body></html>
            """.formatted(body);
    }

    // ==================== PDF ====================

    /** 跨平台加载系统字体（优先 CJK 字体以支持中文） */
    private PDFont loadSystemFont(PDDocument doc, boolean bold) throws java.io.IOException {
        String[][] paths = {
            // Windows CJK 字体（支持中文）
            {"C:/Windows/Fonts/simhei.ttf"},                                          // 黑体 (SimHei)
            {"C:/Windows/Fonts/simsun.ttc", "C:/Windows/Fonts/simsunb.ttf"},          // 宋体 (SimSun)
            {"C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/msyhbd.ttc"},             // 微软雅黑
            {"C:/Windows/Fonts/msyh.ttf", "C:/Windows/Fonts/msyhbd.ttf"},             // 微软雅黑 TT
            // Windows 英文 fallback
            {"C:/Windows/Fonts/arial.ttf"},
            {"C:/Windows/Fonts/arialbd.ttf"},
            // Linux CJK 字体
            {"/usr/share/fonts/truetype/wqy/wqy-microhei.ttc"},                       // 文泉驿微米黑
            {"/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"},                         // 文泉驿正黑
            {"/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"},                // Noto Sans CJK
            {"/usr/share/fonts/opentype/noto/NotoSansSC-Regular.otf"},                 // Noto Sans SC
            // Linux 英文 fallback
            {"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"},
            // macOS CJK
            {"/System/Library/Fonts/PingFang.ttc"},                                    // 苹方
            {"/System/Library/Fonts/STHeiti Light.ttc"},                               // 华文黑体
            {"/System/Library/Fonts/Hiragino Sans GB.ttc"},                            // 冬青黑体
            // macOS 英文 fallback
            {"/System/Library/Fonts/Helvetica.ttc"},
        };
        for (String[] candidates : paths) {
            for (String p : candidates) {
                File f = new File(p);
                if (f.exists()) {
                    log.info("PDF 字体: {}", p);
                    return PDType0Font.load(doc, f);
                }
            }
        }
        throw new java.io.IOException("未找到可用中文字体，请安装 SimHei / 微软雅黑 / WQY Micro Hei");
    }

    public byte[] toPdf(Long paperId, Integer versionNo) {
        String md = getContent(paperId, versionNo);
        String title = paperService.getPaperById(paperId).getTitle();

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            PDFont fontNormal = loadSystemFont(doc, false);
            PDFont fontBold = loadSystemFont(doc, true);
            if (fontBold == null) fontBold = fontNormal;

            float margin = 40;
            float fontSize = 10;
            float leading = 14;
            float pageWidth;

            // 所有页面先创建好，逐个写入
            List<PDPage> pages = new ArrayList<>();
            PDPage firstPage = new PDPage();
            pages.add(firstPage);
            doc.addPage(firstPage);
            pageWidth = firstPage.getMediaBox().getWidth() - 2 * margin;
            float y = firstPage.getMediaBox().getHeight() - margin;

            // 先写标题
            try (PDPageContentStream cs = new PDPageContentStream(doc, firstPage,
                    PDPageContentStream.AppendMode.APPEND, true)) {
                cs.beginText();
                cs.setFont(fontBold, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText(title != null ? title : "Paper");
                cs.endText();
            }
            y -= 30;

            // 逐段逐行写正文
            String plain = stripMarkdown(md);
            int pageIdx = 0;
            PDPage curPage = firstPage;
            PDPageContentStream csBody = new PDPageContentStream(doc, curPage,
                    PDPageContentStream.AppendMode.APPEND, true);
            try {
                csBody.beginText();
                csBody.setFont(fontNormal, fontSize);
                csBody.newLineAtOffset(margin, y);

                for (String para : plain.split("\n")) {
                    String trimmed = para.trim();
                    if (trimmed.isEmpty()) {
                        y -= leading;
                        if (y < margin) {
                            csBody.endText();
                            csBody.close();
                            curPage = newPage(doc, pages);
                            csBody = new PDPageContentStream(doc, curPage,
                                    PDPageContentStream.AppendMode.APPEND, true);
                            csBody.beginText();
                            csBody.setFont(fontNormal, fontSize);
                            y = curPage.getMediaBox().getHeight() - margin;
                            csBody.newLineAtOffset(margin, y);
                            y -= leading;
                        }
                        continue;
                    }
                    List<String> lines = wrapText(trimmed, fontSize, pageWidth);
                    for (String line : lines) {
                        y -= leading;
                        if (y < margin) {
                            csBody.endText();
                            csBody.close();
                            curPage = newPage(doc, pages);
                            csBody = new PDPageContentStream(doc, curPage,
                                    PDPageContentStream.AppendMode.APPEND, true);
                            csBody.beginText();
                            csBody.setFont(fontNormal, fontSize);
                            y = curPage.getMediaBox().getHeight() - margin;
                            csBody.newLineAtOffset(margin, y);
                            y -= leading;
                        }
                        csBody.showText(line);
                        csBody.newLineAtOffset(0, -leading);
                    }
                    y -= leading / 2;
                    csBody.newLineAtOffset(0, -leading / 2);
                }
                csBody.endText();
            } finally {
                csBody.close();
            }

            doc.save(bos);
            return bos.toByteArray();

        } catch (Exception e) {
            log.error("PDF 导出失败: paperId={}", paperId, e);
            throw new RuntimeException("PDF 导出失败: " + e.getMessage(), e);
        }
    }

    private PDPage newPage(PDDocument doc, List<PDPage> pages) {
        PDPage p = new PDPage();
        pages.add(p);
        doc.addPage(p);
        return p;
    }

    private String stripMarkdown(String md) {
        if (md == null) return "";
        return md
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*(.+?)\\*", "$1")
                .replaceAll("`(.+?)`", "$1")
                .replaceAll("!?\\[.*?]\\(.*?\\)", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "  • ")
                .replaceAll("(?m)^\\s*\\d+\\.\\s+", "")
                .replaceAll("(?m)^>\\s+", "  ")
                .replaceAll("\\|.*\\|", "")
                .replaceAll(":?---+:?", "")
                // 处理 LaTeX 公式分隔符
                .replaceAll("\\\\\\[|\\\\]", "")  // \[ ... \]
                .replaceAll("\\\\\\(|\\\\)", "")  // \( ... \)
                .replaceAll("(?s)\\$\\$(.+?)\\$\\$", " [$1] ")  // $$...$$ → plain
                .replaceAll("(?<!\\$)\\$(?!\\$)(.+?)\\$(?!\\$)", " $1 ");  // $...$ → plain
    }

    private List<String> wrapText(String text, float fontSize, float maxWidth) {
        float charWidth = fontSize * 0.6f;
        int maxChars = (int) (maxWidth / charWidth);
        List<String> lines = new ArrayList<>();
        String remaining = text;
        while (remaining.length() > maxChars) {
            int cut = remaining.lastIndexOf(' ', maxChars);
            if (cut < 0 || cut > maxChars + 10) cut = maxChars;
            lines.add(remaining.substring(0, cut).trim());
            remaining = remaining.substring(cut).trim();
        }
        lines.add(remaining);
        return lines;
    }

    // ==================== DOCX ====================

    public byte[] toDocx(Long paperId, Integer versionNo) {
        String md = getContent(paperId, versionNo);

        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            String[] lines = md != null ? md.split("\n") : new String[0];
            for (String line : lines) {
                if (line.startsWith("# ")) {
                    XWPFParagraph p = doc.createParagraph();
                    p.setStyle("Heading1");
                    XWPFRun r = p.createRun();
                    r.setText(line.replaceFirst("^#\\s+", ""));
                    r.setBold(true);
                    r.setFontSize(18);
                } else if (line.startsWith("## ")) {
                    XWPFParagraph p = doc.createParagraph();
                    p.setStyle("Heading2");
                    XWPFRun r = p.createRun();
                    r.setText(line.replaceFirst("^##\\s+", ""));
                    r.setBold(true);
                    r.setFontSize(14);
                } else if (line.startsWith("### ")) {
                    XWPFParagraph p = doc.createParagraph();
                    p.setStyle("Heading3");
                    XWPFRun r = p.createRun();
                    r.setText(line.replaceFirst("^###\\s+", ""));
                    r.setBold(true);
                    r.setFontSize(12);
                } else if (line.trim().isEmpty()) {
                    doc.createParagraph();
                } else if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                    XWPFParagraph p = doc.createParagraph();
                    p.setIndentationLeft(400);
                    XWPFRun r = p.createRun();
                    r.setText(line.replaceFirst("^\\s*[-*]\\s+", "• "));
                    r.setFontSize(11);
                } else if (line.trim().matches("^\\d+\\.\\s.*")) {
                    XWPFParagraph p = doc.createParagraph();
                    p.setIndentationLeft(400);
                    XWPFRun r = p.createRun();
                    r.setText(line.trim());
                    r.setFontSize(11);
                } else {
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun r = p.createRun();
                    String text = line
                            .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                            .replaceAll("\\*(.+?)\\*", "$1");
                    r.setText(text);
                    r.setFontSize(11);
                }
            }

            doc.write(bos);
            return bos.toByteArray();

        } catch (Exception e) {
            log.error("DOCX 导出失败: paperId={}", paperId, e);
            throw new RuntimeException("DOCX 导出失败: " + e.getMessage(), e);
        }
    }

    // ==================== LaTeX ====================

    public byte[] toLatex(Long paperId, Integer versionNo) {
        String md = getContent(paperId, versionNo);
        String title = paperService.getPaperById(paperId).getTitle();
        StringBuilder sb = new StringBuilder();

        sb.append("\\documentclass[12pt,a4paper]{article}\n");
        sb.append("\\usepackage[UTF8]{ctex}\n");
        sb.append("\\usepackage{hyperref}\n");
        sb.append("\\usepackage{geometry}\n");
        sb.append("\\geometry{margin=1in}\n");
        sb.append("\\title{").append(escapeLatex(title != null ? title : "Untitled")).append("}\n");
        sb.append("\\author{}\n");
        sb.append("\\date{}\n\n");
        sb.append("\\begin{document}\n");
        sb.append("\\maketitle\n\n");

        if (md != null) {
            for (String line : md.split("\n")) {
                if (line.startsWith("# ")) {
                    sb.append("\\section{").append(escapeLatex(line.replaceFirst("^#\\s+", ""))).append("}\n");
                } else if (line.startsWith("## ")) {
                    sb.append("\\subsection{").append(escapeLatex(line.replaceFirst("^##\\s+", ""))).append("}\n");
                } else if (line.startsWith("### ")) {
                    sb.append("\\subsubsection{").append(escapeLatex(line.replaceFirst("^###\\s+", ""))).append("}\n");
                } else if (line.trim().isEmpty()) {
                    sb.append("\n");
                } else if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                    sb.append("\\begin{itemize}\n");
                    sb.append("\\item ").append(escapeLatex(line.replaceFirst("^\\s*[-*]\\s+", ""))).append("\n");
                    sb.append("\\end{itemize}\n");
                } else if (line.trim().matches("^\\d+\\.\\s.*")) {
                    sb.append("\\begin{enumerate}\n");
                    sb.append("\\item ").append(escapeLatex(line.replaceFirst("^\\d+\\.\\s+", ""))).append("\n");
                    sb.append("\\end{enumerate}\n");
                } else if (line.startsWith("> ")) {
                    sb.append("\\begin{quote}\n");
                    sb.append(escapeLatex(line.replaceFirst("^>\\s*", ""))).append("\n");
                    sb.append("\\end{quote}\n");
                } else {
                    String text = line
                            .replaceAll("\\*\\*(.+?)\\*\\*", "\\\\textbf{$1}")
                            .replaceAll("\\*(.+?)\\*", "\\\\textit{$1}")
                            .replaceAll("`(.+?)`", "\\\\texttt{$1}");
                    sb.append(escapeLatex(text)).append("\n\n");
                }
            }
        }

        sb.append("\\end{document}\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeLatex(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\textbackslash ")
                .replace("&", "\\&")
                .replace("%", "\\%")
                .replace("$", "\\$")
                .replace("#", "\\#")
                .replace("_", "\\_")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("~", "\\textasciitilde ")
                .replace("^", "\\textasciicircum ");
    }
}
