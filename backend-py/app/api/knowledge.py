from fastapi import APIRouter, Depends, Query, UploadFile, File
from sqlalchemy import select, desc, delete, func
from sqlalchemy.ext.asyncio import AsyncSession
from ..core.database import get_db
from ..dependencies import get_current_user_id
from ..schemas.common import ApiResult
from ..models.knowledge_document import KnowledgeDocument
from ..models.knowledge_chunk import KnowledgeChunk

router = APIRouter(prefix="/api/knowledge", tags=["知识库"])


def to_dict(obj) -> dict:
    return {k: v for k, v in obj.__dict__.items() if not k.startswith("_")}


def doc_to_camel(d: dict) -> dict:
    """将 KnowledgeDocument 的 snake_case 字段转为 camelCase（兼容前端）"""
    d["fileType"] = d.pop("file_type", "")
    d["totalChunks"] = d.pop("total_chunks", 0)
    d["totalChars"] = d.pop("total_chars", 0)
    d["createdAt"] = d.pop("created_at", None)
    d["storePath"] = d.pop("store_path", None)
    d["embedDim"] = d.pop("embed_dim", None)
    return d


@router.get("/my")
async def list_my_docs(db: AsyncSession = Depends(get_db),
                       user_id: int = Depends(get_current_user_id)):
    result = await db.execute(
        select(KnowledgeDocument).where(KnowledgeDocument.user_id == user_id).order_by(desc(KnowledgeDocument.created_at))
    )
    docs = [doc_to_camel(to_dict(d)) for d in result.scalars().all()]
    return ApiResult.success(docs)


@router.get("/shared")
async def list_shared_docs(db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(KnowledgeDocument).where(KnowledgeDocument.scope == "SHARED").order_by(desc(KnowledgeDocument.created_at))
    )
    docs = [doc_to_camel(to_dict(d)) for d in result.scalars().all()]
    return ApiResult.success(docs)


@router.get("/{doc_id}")
async def get_doc(doc_id: int, db: AsyncSession = Depends(get_db),
                  user_id: int = Depends(get_current_user_id)):
    result = await db.execute(
        select(KnowledgeDocument).where(KnowledgeDocument.id == doc_id, KnowledgeDocument.user_id == user_id)
    )
    doc = result.scalar_one_or_none()
    if not doc:
        return ApiResult.error(404, "文档不存在")
    return ApiResult.success(doc_to_camel(to_dict(doc)))


@router.get("/search")
async def search_docs(q: str = Query(...), k: int = Query(5),
                      db: AsyncSession = Depends(get_db),
                      user_id: int = Depends(get_current_user_id)):
    # 转义 LIKE 通配符，防止注入
    escaped = q.replace("%", "\\%").replace("_", "\\_")
    result = await db.execute(
        select(KnowledgeChunk).where(KnowledgeChunk.content.like(f"%{escaped}%", escape="\\")).limit(k)
    )
    chunks = [to_dict(c) for c in result.scalars().all()]
    return ApiResult.success(chunks)


@router.get("/{doc_id}/chunks")
async def get_doc_chunks(doc_id: int, db: AsyncSession = Depends(get_db),
                         user_id: int = Depends(get_current_user_id)):
    # 权限检查
    doc_result = await db.execute(
        select(KnowledgeDocument).where(KnowledgeDocument.id == doc_id, KnowledgeDocument.user_id == user_id)
    )
    if not doc_result.scalar_one_or_none():
        return ApiResult.error(404, "文档不存在")
    result = await db.execute(
        select(KnowledgeChunk).where(KnowledgeChunk.document_id == doc_id).order_by(KnowledgeChunk.chunk_index)
    )
    chunks = []
    for c in result.scalars().all():
        d = to_dict(c)
        # 兼容前端字段名（camelCase）
        d["chunkIndex"] = d.pop("chunk_index", 0)
        d["charCount"] = d.pop("char_count", 0)
        d["text"] = d.pop("content", "")
        chunks.append(d)
    return ApiResult.success(chunks)


@router.post("/upload")
async def upload_doc(file: UploadFile = File(...), scope: str = Query("PRIVATE"),
                     db: AsyncSession = Depends(get_db),
                     user_id: int = Depends(get_current_user_id)):
    content = await file.read()
    text = content.decode("utf-8", errors="ignore")
    # 原子操作：一次 commit
    doc = KnowledgeDocument(user_id=user_id, filename=file.filename, file_type=file.content_type or "text/plain",
                            scope=scope, status="COMPLETED", total_chunks=1, total_chars=len(text))
    db.add(doc)
    await db.flush()  # 获取 ID 但不提交
    chunk = KnowledgeChunk(document_id=doc.id, chunk_index=0, content=text, char_count=len(text))
    db.add(chunk)
    await db.commit()
    return ApiResult.success({"docId": doc.id, "filename": file.filename}, "上传成功")


@router.delete("/{doc_id}")
async def delete_doc(doc_id: int, db: AsyncSession = Depends(get_db),
                     user_id: int = Depends(get_current_user_id)):
    result = await db.execute(select(KnowledgeDocument).where(KnowledgeDocument.id == doc_id, KnowledgeDocument.user_id == user_id))
    doc = result.scalar_one_or_none()
    if not doc:
        return ApiResult.error(404, "文档不存在")
    # 级联删除 chunks
    await db.execute(delete(KnowledgeChunk).where(KnowledgeChunk.document_id == doc_id))
    await db.delete(doc)
    await db.commit()
    return ApiResult.success(message="删除成功")
