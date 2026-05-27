package com.paperai.converter;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

@Component
public class HtmlExportStrategy implements ExportStrategy {

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, java.util.Arrays.asList(
                com.vladsch.flexmark.ext.tables.TablesExtension.create(),
                com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
                com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension.create(),
                com.vladsch.flexmark.ext.autolink.AutolinkExtension.create(),
                com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension.create()
        ));
        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
    }

    @Override
    public byte[] export(String markdown, String title) {
        Document doc = PARSER.parse(markdown != null ? markdown : "");
        String body = RENDERER.render(doc);
        String html = String.format("""
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head><meta charset="UTF-8"><title>%s</title>
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
            """, title != null ? escapeHtml(title) : "Paper", body);
        return html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override public String format() { return "html"; }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
