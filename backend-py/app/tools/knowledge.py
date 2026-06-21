"""
RAG 知识库检索工具 — 从知识库中检索相关内容
"""
import logging
from .base import Tool

log = logging.getLogger("paperai.tools.knowledge")


class RAGSearchTool(Tool):
    """RAG 知识库检索工具"""

    @property
    def name(self) -> str:
        return "rag_search"

    @property
    def description(self) -> str:
        return "从用户的知识库中检索相关文档片段。用于查找用户上传的论文、笔记、资料中的相关内容。返回最相关的文档片段。"

    @property
    def parameters(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "检索查询，描述你要找的内容"
                },
                "top_k": {
                    "type": "integer",
                    "description": "返回最相关的片段数量，默认 5",
                    "default": 5
                }
            },
            "required": ["query"]
        }

    async def execute(self, query: str, top_k: int = 5, **kwargs) -> str:
        """从知识库检索相关内容"""
        try:
            from ..core.database import async_session
            from sqlalchemy import text

            async with async_session() as session:
                # 关键词检索
                sql = text("""
                    SELECT kc.id, kc.content, kc.char_count, kd.filename, kd.title
                    FROM knowledge_chunk kc
                    JOIN knowledge_document kd ON kc.document_id = kd.id
                    WHERE kc.content LIKE :kw
                    ORDER BY kc.char_count DESC
                    LIMIT :limit
                """)
                result = await session.execute(sql, {"kw": f"%{query}%", "limit": top_k})
                rows = result.fetchall()

                if not rows:
                    return f"知识库中未找到与 '{query}' 相关的内容"

                chunks = []
                for i, row in enumerate(rows, 1):
                    content = row[1]
                    # 截取相关部分
                    if len(content) > 500:
                        # 尝试找到关键词附近的内容
                        idx = content.lower().find(query.lower())
                        if idx > 100:
                            start = max(0, idx - 100)
                            end = min(len(content), idx + 400)
                            content = "..." + content[start:end] + "..."
                        else:
                            content = content[:500] + "..."
                    chunks.append(f"[片段{i}] 来源: {row[3] or row[4] or '未知'}\n{content}")

                return f"从知识库找到 {len(chunks)} 个相关片段:\n\n" + "\n\n".join(chunks)
        except Exception as e:
            log.error("RAG 检索失败: %s", e)
            return f"检索失败: {str(e)[:100]}"
