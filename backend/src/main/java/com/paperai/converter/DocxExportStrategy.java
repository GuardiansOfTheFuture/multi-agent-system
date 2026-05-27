package com.paperai.converter;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

@Slf4j
@Component
public class DocxExportStrategy implements ExportStrategy {

    @Override
    public byte[] export(String markdown, String title) {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            String[] lines = markdown != null ? markdown.split("\n") : new String[0];
            for (String line : lines) {
                if (line.startsWith("# ")) {
                    XWPFParagraph p = doc.createParagraph();
                    p.setStyle("Heading1");
                    XWPFRun r = p.createRun();
                    r.setText(line.replaceFirst("^#\\s+", ""));
                    r.setBold(true); r.setFontSize(18);
                } else if (line.startsWith("## ")) {
                    XWPFParagraph p = doc.createParagraph();
                    p.setStyle("Heading2");
                    XWPFRun r = p.createRun();
                    r.setText(line.replaceFirst("^##\\s+", ""));
                    r.setBold(true); r.setFontSize(14);
                } else if (line.startsWith("### ")) {
                    XWPFParagraph p = doc.createParagraph();
                    p.setStyle("Heading3");
                    XWPFRun r = p.createRun();
                    r.setText(line.replaceFirst("^###\\s+", ""));
                    r.setBold(true); r.setFontSize(12);
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
                    r.setText(line.trim()); r.setFontSize(11);
                } else {
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun r = p.createRun();
                    r.setText(line.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                            .replaceAll("\\*(.+?)\\*", "$1"));
                    r.setFontSize(11);
                }
            }
            doc.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.error("DOCX 导出失败", e);
            throw new RuntimeException("DOCX 导出失败: " + e.getMessage(), e);
        }
    }

    @Override public String format() { return "docx"; }
}
