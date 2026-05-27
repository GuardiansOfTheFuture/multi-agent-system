package com.paperai.converter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 导出门面 — 根据 format 参数选择对应的导出策略 */
@Component
public class ExportConverter {

    @Resource private HtmlExportStrategy htmlExport;
    @Resource private PdfExportStrategy pdfExport;
    @Resource private DocxExportStrategy docxExport;
    @Resource private LatexExportStrategy latexExport;

    private Map<String, ExportStrategy> strategies;

    @PostConstruct
    void init() {
        strategies = Map.of(
                "html", htmlExport,
                "pdf", pdfExport,
                "docx", docxExport,
                "latex", latexExport
        );
    }

    public byte[] export(String format, String markdown, String title) {
        ExportStrategy strategy = strategies.get(format.toLowerCase());
        if (strategy == null) throw new IllegalArgumentException("不支持的导出格式: " + format);
        return strategy.export(markdown, title);
    }

    public String toHtml(String markdown) {
        return new String(htmlExport.export(markdown, null), java.nio.charset.StandardCharsets.UTF_8);
    }
}
