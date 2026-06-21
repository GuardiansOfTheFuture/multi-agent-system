"""
学术搜索工具 — 搜索学术文献和资料
"""
import logging
import httpx
from .base import Tool

log = logging.getLogger("paperai.tools.search")


class WebSearchTool(Tool):
    """学术搜索工具"""

    @property
    def name(self) -> str:
        return "web_search"

    @property
    def description(self) -> str:
        return "搜索学术文献和网络资料。用于查找论文、研究数据、技术文档等。返回搜索结果摘要列表。"

    @property
    def parameters(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "搜索关键词，建议使用英文"
                },
                "num_results": {
                    "type": "integer",
                    "description": "返回结果数量，默认 5",
                    "default": 5
                }
            },
            "required": ["query"]
        }

    async def execute(self, query: str, num_results: int = 5, **kwargs) -> str:
        """执行搜索（使用 DuckDuckGo 免费 API）"""
        try:
            return await self._search_ddg(query, num_results)
        except Exception as e:
            log.warning("搜索失败: %s", e)
            return f"搜索失败: {str(e)[:100]}"

    async def _search_ddg(self, query: str, num_results: int) -> str:
        """DuckDuckGo 搜索"""
        url = "https://api.duckduckgo.com/"
        params = {
            "q": f"{query} site:arxiv.org OR site:scholar.google.com OR site:semanticscholar.org",
            "format": "json",
            "no_html": 1,
            "skip_disambig": 1,
        }
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.get(url, params=params)
            data = resp.json()

        results = []
        # Abstract
        if data.get("Abstract"):
            results.append(f"摘要: {data['Abstract']}")
        # Related topics
        for topic in data.get("RelatedTopics", [])[:num_results]:
            if isinstance(topic, dict) and topic.get("Text"):
                results.append(f"- {topic['Text']}")
                if topic.get("FirstURL"):
                    results.append(f"  链接: {topic['FirstURL']}")

        if not results:
            # Fallback: 返回搜索建议
            return f"未找到直接结果。建议在以下平台搜索：\n- Google Scholar: https://scholar.google.com/scholar?q={query}\n- arXiv: https://arxiv.org/search/?query={query}\n- Semantic Scholar: https://www.semanticscholar.org/search?q={query}"

        return "搜索结果:\n" + "\n".join(results)


class ArxivSearchTool(Tool):
    """arXiv 论文搜索"""

    @property
    def name(self) -> str:
        return "arxiv_search"

    @property
    def description(self) -> str:
        return "在 arXiv 上搜索学术论文。返回论文标题、作者、摘要。适合查找最新研究论文。"

    @property
    def parameters(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "搜索关键词"
                },
                "max_results": {
                    "type": "integer",
                    "description": "最大结果数，默认 5",
                    "default": 5
                }
            },
            "required": ["query"]
        }

    async def execute(self, query: str, max_results: int = 5, **kwargs) -> str:
        """搜索 arXiv"""
        url = "http://export.arxiv.org/api/query"
        params = {
            "search_query": f"all:{query}",
            "start": 0,
            "max_results": max_results,
            "sortBy": "relevance",
            "sortOrder": "descending",
        }
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                resp = await client.get(url, params=params)
                return self._parse_arxiv_xml(resp.text, max_results)
        except Exception as e:
            log.warning("arXiv 搜索失败: %s", e)
            return f"arXiv 搜索失败: {str(e)[:100]}"

    def _parse_arxiv_xml(self, xml_text: str, max_results: int) -> str:
        """解析 arXiv API 返回的 XML"""
        try:
            import xml.etree.ElementTree as ET
            root = ET.fromstring(xml_text)
            ns = {"atom": "http://www.w3.org/2005/Atom"}

            entries = root.findall("atom:entry", ns)[:max_results]
            if not entries:
                return "未找到相关论文"

            results = ["arXiv 搜索结果:\n"]
            for i, entry in enumerate(entries, 1):
                title = entry.find("atom:title", ns)
                title_text = title.text.strip().replace("\n", " ") if title is not None else "无标题"

                summary = entry.find("atom:summary", ns)
                summary_text = summary.text.strip()[:300] if summary is not None else "无摘要"

                authors = entry.findall("atom:author", ns)
                author_names = [a.find("atom:name", ns).text for a in authors if a.find("atom:name", ns) is not None]
                authors_str = ", ".join(author_names[:3])
                if len(author_names) > 3:
                    authors_str += f" 等{len(author_names)}人"

                link = entry.find("atom:id", ns)
                link_text = link.text if link is not None else ""

                published = entry.find("atom:published", ns)
                pub_text = published.text[:10] if published is not None else ""

                results.append(f"{i}. {title_text}")
                results.append(f"   作者: {authors_str}")
                results.append(f"   日期: {pub_text}")
                results.append(f"   摘要: {summary_text}...")
                results.append(f"   链接: {link_text}\n")

            return "\n".join(results)
        except Exception as e:
            return f"解析失败: {str(e)[:100]}"
