"""
数据库查询工具 — 查询已有论文和研究数据
"""
import logging
import json
from .base import Tool

log = logging.getLogger("paperai.tools.database")


class DatabaseQueryTool(Tool):
    """数据库查询工具"""

    @property
    def name(self) -> str:
        return "db_query"

    @property
    def description(self) -> str:
        return "查询系统中已有的论文、文献、研究数据。可以按主题、关键词、作者等条件搜索。返回匹配的论文列表。"

    @property
    def parameters(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "query_type": {
                    "type": "string",
                    "enum": ["papers", "references", "knowledge"],
                    "description": "查询类型: papers=论文, references=参考文献, knowledge=知识库"
                },
                "keyword": {
                    "type": "string",
                    "description": "搜索关键词"
                },
                "limit": {
                    "type": "integer",
                    "description": "返回数量限制，默认 10",
                    "default": 10
                }
            },
            "required": ["query_type", "keyword"]
        }

    async def execute(self, query_type: str, keyword: str, limit: int = 10, **kwargs) -> str:
        """执行数据库查询"""
        try:
            from ..core.database import async_session
            from sqlalchemy import text

            async with async_session() as session:
                if query_type == "papers":
                    return await self._query_papers(session, keyword, limit)
                elif query_type == "references":
                    return await self._query_references(session, keyword, limit)
                elif query_type == "knowledge":
                    return await self._query_knowledge(session, keyword, limit)
                else:
                    return f"未知查询类型: {query_type}"
        except Exception as e:
            log.error("数据库查询失败: %s", e)
            return f"查询失败: {str(e)[:100]}"

    async def _query_papers(self, session, keyword: str, limit: int) -> str:
        """查询论文"""
        from sqlalchemy import text
        sql = text("""
            SELECT id, title, keywords, status, created_at
            FROM paper
            WHERE title LIKE :kw OR keywords LIKE :kw
            ORDER BY created_at DESC
            LIMIT :limit
        """)
        result = await session.execute(sql, {"kw": f"%{keyword}%", "limit": limit})
        rows = result.fetchall()

        if not rows:
            return f"未找到包含 '{keyword}' 的论文"

        papers = []
        for row in rows:
            papers.append(f"- [{row[0]}] {row[1]} | 关键词: {row[2] or '无'} | 状态: {row[3]} | 时间: {row[4]}")

        return f"找到 {len(papers)} 篇论文:\n" + "\n".join(papers)

    async def _query_references(self, session, keyword: str, limit: int) -> str:
        """查询参考文献"""
        from sqlalchemy import text
        sql = text("""
            SELECT id, title, authors, year, journal
            FROM paper_reference
            WHERE title LIKE :kw OR authors LIKE :kw
            ORDER BY year DESC
            LIMIT :limit
        """)
        result = await session.execute(sql, {"kw": f"%{keyword}%", "limit": limit})
        rows = result.fetchall()

        if not rows:
            return f"未找到包含 '{keyword}' 的参考文献"

        refs = []
        for row in rows:
            refs.append(f"- [{row[0]}] {row[1]} | {row[2] or '未知作者'} | {row[3] or '?'}年 | {row[4] or '未知期刊'}")

        return f"找到 {len(refs)} 条参考文献:\n" + "\n".join(refs)

    async def _query_knowledge(self, session, keyword: str, limit: int) -> str:
        """查询知识库"""
        from sqlalchemy import text
        sql = text("""
            SELECT kc.id, kc.content, kd.filename
            FROM knowledge_chunk kc
            JOIN knowledge_document kd ON kc.document_id = kd.id
            WHERE kc.content LIKE :kw
            LIMIT :limit
        """)
        result = await session.execute(sql, {"kw": f"%{keyword}%", "limit": limit})
        rows = result.fetchall()

        if not rows:
            return f"未找到包含 '{keyword}' 的知识库内容"

        chunks = []
        for row in rows:
            content_preview = row[1][:200] + "..." if len(row[1]) > 200 else row[1]
            chunks.append(f"- [{row[0]}] 来源: {row[2]}\n  内容: {content_preview}")

        return f"找到 {len(chunks)} 条知识库内容:\n" + "\n".join(chunks)
