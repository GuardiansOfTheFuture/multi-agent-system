"""
引用格式化工具 — 格式化学术引用和参考文献
"""
import logging
import json
from .base import Tool

log = logging.getLogger("paperai.tools.citation")


class CitationFormatterTool(Tool):
    """引用格式化工具"""

    @property
    def name(self) -> str:
        return "format_citation"

    @property
    def description(self) -> str:
        return "格式化学术引用和参考文献。支持 APA、MLA、IEEE、GB/T 7714 等格式。用于论文参考文献的规范化。"

    @property
    def parameters(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "format_style": {
                    "type": "string",
                    "enum": ["apa", "mla", "ieee", "gbt7714", "chicago"],
                    "description": "引用格式: apa=APA, mla=MLA, ieee=IEEE, gbt7714=GB/T 7714(中文国标), chicago=芝加哥"
                },
                "references": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "title": {"type": "string"},
                            "authors": {"type": "string"},
                            "year": {"type": "integer"},
                            "journal": {"type": "string"},
                            "volume": {"type": "string"},
                            "issue": {"type": "string"},
                            "pages": {"type": "string"},
                            "doi": {"type": "string"},
                            "url": {"type": "string"}
                        }
                    },
                    "description": "参考文献列表，每条包含 title, authors, year, journal 等字段"
                }
            },
            "required": ["format_style", "references"]
        }

    async def execute(self, format_style: str, references: list, **kwargs) -> str:
        """格式化引用"""
        try:
            if not references:
                return "请提供参考文献"

            formatter = getattr(self, f"_format_{format_style}", None)
            if not formatter:
                return f"不支持的格式: {format_style}"

            formatted = []
            for i, ref in enumerate(references, 1):
                formatted.append(formatter(i, ref))

            header = f"参考文献 ({format_style.upper()} 格式):\n\n"
            return header + "\n\n".join(formatted)
        except Exception as e:
            log.error("引用格式化失败: %s", e)
            return f"格式化失败: {str(e)[:100]}"

    def _format_apa(self, idx: int, ref: dict) -> str:
        """APA 格式"""
        authors = ref.get("authors", "Unknown")
        year = ref.get("year", "n.d.")
        title = ref.get("title", "Untitled")
        journal = ref.get("journal", "")
        volume = ref.get("volume", "")
        issue = ref.get("issue", "")
        pages = ref.get("pages", "")
        doi = ref.get("doi", "")

        # APA: Author, A. A. (Year). Title of article. Journal, Volume(Issue), Pages. https://doi.org/xxx
        parts = [f"{authors} ({year}). {title}."]
        if journal:
            journal_part = f" {journal}"
            if volume:
                journal_part += f", {volume}"
            if issue:
                journal_part += f"({issue})"
            if pages:
                journal_part += f", {pages}"
            journal_part += "."
            parts.append(journal_part)
        if doi:
            parts.append(f" https://doi.org/{doi}")

        return f"[{idx}] " + "".join(parts)

    def _format_mla(self, idx: int, ref: dict) -> str:
        """MLA 格式"""
        authors = ref.get("authors", "Unknown")
        title = ref.get("title", "Untitled")
        journal = ref.get("journal", "")
        volume = ref.get("volume", "")
        year = ref.get("year", "")
        pages = ref.get("pages", "")

        # MLA: Author. "Title." Journal, vol. Volume, no. Issue, Year, pp. Pages.
        parts = [f"{authors}. \"{title}.\""]
        if journal:
            journal_part = f" {journal}"
            if volume:
                journal_part += f", vol. {volume}"
            if year:
                journal_part += f", {year}"
            if pages:
                journal_part += f", pp. {pages}"
            journal_part += "."
            parts.append(journal_part)

        return f"[{idx}] " + "".join(parts)

    def _format_ieee(self, idx: int, ref: dict) -> str:
        """IEEE 格式"""
        authors = ref.get("authors", "Unknown")
        title = ref.get("title", "Untitled")
        journal = ref.get("journal", "")
        volume = ref.get("volume", "")
        issue = ref.get("issue", "")
        pages = ref.get("pages", "")
        year = ref.get("year", "")

        # IEEE: Author, "Title," Journal, vol. X, no. Y, pp. Z, Year.
        parts = [f"{authors}, \"{title}\""]
        if journal:
            journal_part = f", {journal}"
            if volume:
                journal_part += f", vol. {volume}"
            if issue:
                journal_part += f", no. {issue}"
            if pages:
                journal_part += f", pp. {pages}"
            if year:
                journal_part += f", {year}"
            journal_part += "."
            parts.append(journal_part)

        return f"[{idx}] " + "".join(parts)

    def _format_gbt7714(self, idx: int, ref: dict) -> str:
        """GB/T 7714 格式（中文国标）"""
        authors = ref.get("authors", "佚名")
        title = ref.get("title", "无题")
        journal = ref.get("journal", "")
        year = ref.get("year", "")
        volume = ref.get("volume", "")
        issue = ref.get("issue", "")
        pages = ref.get("pages", "")

        # GB/T 7714: 作者. 题名[J]. 刊名, 年, 卷(期): 页码.
        parts = [f"{authors}."]
        if title:
            parts.append(f" {title}[J].")
        if journal:
            parts.append(f" {journal},")
        if year:
            parts.append(f" {year}")
        if volume:
            parts.append(f", {volume}")
        if issue:
            parts.append(f"({issue})")
        if pages:
            parts.append(f": {pages}")
        parts.append(".")

        return f"[{idx}] " + "".join(parts)

    def _format_chicago(self, idx: int, ref: dict) -> str:
        """Chicago 格式"""
        authors = ref.get("authors", "Unknown")
        title = ref.get("title", "Untitled")
        journal = ref.get("journal", "")
        volume = ref.get("volume", "")
        issue = ref.get("issue", "")
        year = ref.get("year", "")
        pages = ref.get("pages", "")

        # Chicago: Author. "Title." Journal Volume, no. Issue (Year): Pages.
        parts = [f"{authors}. \"{title}.\""]
        if journal:
            journal_part = f" {journal}"
            if volume:
                journal_part += f" {volume}"
            if issue:
                journal_part += f", no. {issue}"
            if year:
                journal_part += f" ({year})"
            if pages:
                journal_part += f": {pages}"
            journal_part += "."
            parts.append(journal_part)

        return f"[{idx}] " + "".join(parts)
