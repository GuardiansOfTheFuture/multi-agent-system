package com.paperai.converter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class LatexExportStrategy implements ExportStrategy {

    @Override
    public byte[] export(String markdown, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("\\documentclass[12pt,a4paper]{article}\n");
        sb.append("\\usepackage[UTF8]{ctex}\n");
        sb.append("\\usepackage{hyperref}\n");
        sb.append("\\usepackage{geometry}\n");
        sb.append("\\geometry{margin=1in}\n");
        sb.append("\\title{").append(escapeLatex(title != null ? title : "Untitled")).append("}\n");
        sb.append("\\author{}\n\\date{}\n\n");
        sb.append("\\begin{document}\n\\maketitle\n\n");

        if (markdown != null) {
            for (String line : markdown.split("\n")) {
                if (line.startsWith("# ")) {
                    sb.append("\\section{").append(escapeLatex(line.replaceFirst("^#\\s+", ""))).append("}\n");
                } else if (line.startsWith("## ")) {
                    sb.append("\\subsection{").append(escapeLatex(line.replaceFirst("^##\\s+", ""))).append("}\n");
                } else if (line.startsWith("### ")) {
                    sb.append("\\subsubsection{").append(escapeLatex(line.replaceFirst("^###\\s+", ""))).append("}\n");
                } else if (line.trim().isEmpty()) {
                    sb.append("\n");
                } else if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                    sb.append("\\begin{itemize}\n\\item ")
                            .append(escapeLatex(line.replaceFirst("^\\s*[-*]\\s+", "")))
                            .append("\n\\end{itemize}\n");
                } else if (line.trim().matches("^\\d+\\.\\s.*")) {
                    sb.append("\\begin{enumerate}\n\\item ")
                            .append(escapeLatex(line.replaceFirst("^\\d+\\.\\s+", "")))
                            .append("\n\\end{enumerate}\n");
                } else if (line.startsWith("> ")) {
                    sb.append("\\begin{quote}\n")
                            .append(escapeLatex(line.replaceFirst("^>\\s*", "")))
                            .append("\n\\end{quote}\n");
                } else {
                    sb.append(escapeLatex(line
                            .replaceAll("\\*\\*(.+?)\\*\\*", "\\\\textbf{$1}")
                            .replaceAll("\\*(.+?)\\*", "\\\\textit{$1}")
                            .replaceAll("`(.+?)`", "\\\\texttt{$1}")))
                            .append("\n\n");
                }
            }
        }
        sb.append("\\end{document}\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override public String format() { return "latex"; }

    private String escapeLatex(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\textbackslash ")
                .replace("&", "\\&").replace("%", "\\%")
                .replace("$", "\\$").replace("#", "\\#")
                .replace("_", "\\_").replace("{", "\\{")
                .replace("}", "\\}").replace("~", "\\textasciitilde ")
                .replace("^", "\\textasciicircum ");
    }
}
