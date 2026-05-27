package com.paperai.converter;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PdfExportStrategy implements ExportStrategy {

    @Override
    public byte[] export(String markdown, String title) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            PDFont fontNormal = loadSystemFont(doc);
            float margin = 40, fontSize = 10, leading = 14;

            List<PDPage> pages = new ArrayList<>();
            PDPage firstPage = new PDPage();
            pages.add(firstPage);
            doc.addPage(firstPage);
            float pageWidth = firstPage.getMediaBox().getWidth() - 2 * margin;
            float y = firstPage.getMediaBox().getHeight() - margin;

            // 标题
            try (PDPageContentStream cs = new PDPageContentStream(doc, firstPage,
                    PDPageContentStream.AppendMode.APPEND, true)) {
                cs.beginText();
                cs.setFont(fontNormal, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText(title != null ? title : "Paper");
                cs.endText();
            }
            y -= 30;

            String plain = stripMarkdown(markdown);
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
                            csBody.endText(); csBody.close();
                            curPage = newPage(doc, pages);
                            csBody = new PDPageContentStream(doc, curPage,
                                    PDPageContentStream.AppendMode.APPEND, true);
                            csBody.beginText(); csBody.setFont(fontNormal, fontSize);
                            y = curPage.getMediaBox().getHeight() - margin;
                            csBody.newLineAtOffset(margin, y); y -= leading;
                        }
                        continue;
                    }
                    for (String line : wrapText(trimmed, fontSize, pageWidth)) {
                        y -= leading;
                        if (y < margin) {
                            csBody.endText(); csBody.close();
                            curPage = newPage(doc, pages);
                            csBody = new PDPageContentStream(doc, curPage,
                                    PDPageContentStream.AppendMode.APPEND, true);
                            csBody.beginText(); csBody.setFont(fontNormal, fontSize);
                            y = curPage.getMediaBox().getHeight() - margin;
                            csBody.newLineAtOffset(margin, y); y -= leading;
                        }
                        csBody.showText(line);
                        csBody.newLineAtOffset(0, -leading);
                    }
                    y -= leading / 2;
                    csBody.newLineAtOffset(0, -leading / 2);
                }
                csBody.endText();
            } finally { csBody.close(); }
            doc.save(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.error("PDF 导出失败", e);
            throw new RuntimeException("PDF 导出失败: " + e.getMessage(), e);
        }
    }

    private PDFont loadSystemFont(PDDocument doc) throws java.io.IOException {
        String[][] paths = {
            {"C:/Windows/Fonts/simhei.ttf"},
            {"C:/Windows/Fonts/simsun.ttc", "C:/Windows/Fonts/simsunb.ttf"},
            {"C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/msyhbd.ttc"},
            {"C:/Windows/Fonts/msyh.ttf", "C:/Windows/Fonts/msyhbd.ttf"},
            {"C:/Windows/Fonts/arial.ttf"},
            {"/usr/share/fonts/truetype/wqy/wqy-microhei.ttc"},
            {"/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"},
            {"/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"},
            {"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"},
            {"/System/Library/Fonts/PingFang.ttc"},
            {"/System/Library/Fonts/STHeiti Light.ttc"},
            {"/System/Library/Fonts/Helvetica.ttc"},
        };
        for (String[] candidates : paths) {
            for (String p : candidates) {
                File f = new File(p);
                if (f.exists()) { log.info("PDF 字体: {}", p); return PDType0Font.load(doc, f); }
            }
        }
        throw new java.io.IOException("未找到可用中文字体");
    }

    private PDPage newPage(PDDocument doc, List<PDPage> pages) {
        PDPage p = new PDPage(); pages.add(p); doc.addPage(p); return p;
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
                .replaceAll("\\\\\\[|\\\\]", "")
                .replaceAll("\\\\\\(|\\\\)", "")
                .replaceAll("(?s)\\$\\$(.+?)\\$\\$", " [$1] ")
                .replaceAll("(?<!\\$)\\$(?!\\$)(.+?)\\$(?!\\$)", " $1 ");
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

    @Override public String format() { return "pdf"; }
}
