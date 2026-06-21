import re
from .base import Exporter


class LatexExporter(Exporter):
    def export(self, markdown: str, title: str) -> bytes:
        lines = []
        lines.append(r"\documentclass[12pt,a4paper]{article}")
        lines.append(r"\usepackage{ctex}")
        lines.append(r"\usepackage[margin=2.5cm]{geometry}")
        lines.append(r"\usepackage{hyperref}")
        lines.append(r"\begin{document}")
        lines.append(f"\\title{{{self._escape(title)}}}")
        lines.append(r"\maketitle")
        for line in markdown.split("\n"):
            if line.startswith("# "):
                lines.append(f"\\section{{{self._escape(line[2:])}}}")
            elif line.startswith("## "):
                lines.append(f"\\subsection{{{self._escape(line[3:])}}}")
            elif line.startswith("### "):
                lines.append(f"\\subsubsection{{{self._escape(line[4:])}}}")
            elif line.strip().startswith("- ") or line.strip().startswith("* "):
                if not lines or lines[-1] != r"\begin{itemize}":
                    lines.append(r"\begin{itemize}")
                lines.append(f"  \\item {self._escape(line.strip()[2:])}")
            elif line.strip() == "":
                if lines and lines[-1] == r"\begin{itemize}":
                    lines.append(r"\end{itemize}")
                lines.append("")
            else:
                if lines and lines[-1] == r"\begin{itemize}":
                    lines.append(r"\end{itemize}")
                lines.append(self._escape(line))
        if lines and lines[-1] == r"\begin{itemize}":
            lines.append(r"\end{itemize}")
        lines.append(r"\end{document}")
        return "\n".join(lines).encode("utf-8")

    def _escape(self, text: str) -> str:
        for ch in ["&", "%", "#", "_", "{", "}"]:
            text = text.replace(ch, "\\" + ch)
        text = text.replace("~", "\\textasciitilde{}")
        text = text.replace("^", "\\textasciicircum{}")
        return text
