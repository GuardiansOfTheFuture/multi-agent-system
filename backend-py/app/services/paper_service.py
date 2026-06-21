import logging
from sqlalchemy import select, func, desc, delete
from sqlalchemy.ext.asyncio import AsyncSession
from ..models.paper import Paper
from ..models.paper_version import PaperVersion
from ..models.task import Task
from fastapi import HTTPException

log = logging.getLogger("paperai.paper")


def to_dict(obj) -> dict:
    """SQLAlchemy 对象转 dict，过滤内部字段"""
    return {k: v for k, v in obj.__dict__.items() if not k.startswith("_")}


async def create_paper(db: AsyncSession, topic: str, user_id: int, **kwargs) -> Paper:
    paper = Paper(title=topic, user_id=user_id, status="DRAFT", **kwargs)
    db.add(paper)
    await db.commit()
    await db.refresh(paper)
    log.info("创建论文: id=%d title=%s userId=%d", paper.id, paper.title, user_id)
    return paper


async def get_paper_by_id(db: AsyncSession, paper_id: int) -> Paper:
    result = await db.execute(select(Paper).where(Paper.id == paper_id))
    paper = result.scalar_one_or_none()
    if not paper:
        raise HTTPException(404, "论文不存在")
    return paper


async def list_papers(db: AsyncSession, user_id: int, page: int = 1, size: int = 10):
    count_q = select(func.count()).select_from(Paper).where(Paper.user_id == user_id)
    total = (await db.execute(count_q)).scalar() or 0
    q = select(Paper).where(Paper.user_id == user_id).order_by(desc(Paper.created_at)).offset((page - 1) * size).limit(size)
    result = await db.execute(q)
    records = [to_dict(r) for r in result.scalars().all()]
    return {"total": total, "records": records, "size": size, "page": page}


async def check_owner(db: AsyncSession, paper_id: int, user_id: int):
    paper = await get_paper_by_id(db, paper_id)
    if paper.user_id != user_id:
        raise HTTPException(403, "无权操作该论文")
    return paper


async def update_status(db: AsyncSession, paper_id: int, status: str):
    paper = await get_paper_by_id(db, paper_id)
    paper.status = status
    await db.commit()
    log.info("更新状态: paperId=%d status=%s", paper_id, status)


async def delete_paper(db: AsyncSession, paper_id: int):
    paper = await get_paper_by_id(db, paper_id)
    await db.execute(delete(PaperVersion).where(PaperVersion.paper_id == paper_id))
    await db.execute(delete(Task).where(Task.paper_id == paper_id))
    await db.delete(paper)
    await db.commit()
    log.info("删除论文: paperId=%d", paper_id)


async def save_version(db: AsyncSession, paper_id: int, stage: str, summary: str, content: str,
                       edit_type: str = "MANUAL", change_summary: str | None = None) -> PaperVersion:
    max_q = select(func.max(PaperVersion.version_no)).where(PaperVersion.paper_id == paper_id)
    next_no = ((await db.execute(max_q)).scalar() or 0) + 1
    pv = PaperVersion(
        paper_id=paper_id, version_no=next_no, stage=stage, summary=summary,
        content=content, word_count=len(content) if content else 0,
        edit_type=edit_type, change_summary=change_summary,
    )
    db.add(pv)
    # 直接更新 paper 的 current_version，不再重复查询
    result = await db.execute(select(Paper).where(Paper.id == paper_id))
    paper = result.scalar_one()
    paper.current_version = next_no
    await db.commit()
    await db.refresh(pv)
    log.info("保存版本: paperId=%d version=%d stage=%s words=%d", paper_id, next_no, stage, pv.word_count)
    return pv


async def get_versions(db: AsyncSession, paper_id: int) -> list:
    result = await db.execute(
        select(PaperVersion).where(PaperVersion.paper_id == paper_id).order_by(PaperVersion.version_no)
    )
    return list(result.scalars().all())


async def get_latest_version(db: AsyncSession, paper_id: int) -> PaperVersion | None:
    result = await db.execute(
        select(PaperVersion).where(PaperVersion.paper_id == paper_id).order_by(desc(PaperVersion.version_no)).limit(1)
    )
    return result.scalar_one_or_none()


async def get_tasks_by_paper(db: AsyncSession, paper_id: int) -> list:
    result = await db.execute(
        select(Task).where(Task.paper_id == paper_id).order_by(Task.sort_order)
    )
    return list(result.scalars().all())


async def update_content(db: AsyncSession, paper_id: int, version_no: int, content: str):
    if version_no:
        result = await db.execute(
            select(PaperVersion).where(PaperVersion.paper_id == paper_id, PaperVersion.version_no == version_no)
        )
        pv = result.scalar_one_or_none()
        if pv:
            pv.content = content
            pv.word_count = len(content) if content else 0
            await db.commit()
    else:
        paper = await get_paper_by_id(db, paper_id)
        if paper.current_version:
            result = await db.execute(
                select(PaperVersion).where(PaperVersion.paper_id == paper_id, PaperVersion.version_no == paper.current_version)
            )
            pv = result.scalar_one_or_none()
            if pv:
                pv.content = content
                pv.word_count = len(content) if content else 0
                await db.commit()
    log.info("更新内容: paperId=%d versionNo=%s", paper_id, version_no)


async def get_content_for_export(db: AsyncSession, paper_id: int, version_no: int | None = None) -> tuple:
    paper = await get_paper_by_id(db, paper_id)
    if version_no:
        result = await db.execute(
            select(PaperVersion).where(PaperVersion.paper_id == paper_id, PaperVersion.version_no == version_no)
        )
        pv = result.scalar_one_or_none()
        if pv:
            return pv.content or "", paper.title
    latest = await get_latest_version(db, paper_id)
    if latest:
        return latest.content or "", paper.title
    return "", paper.title


# ===== 参考文献 =====

from ..models.paper_reference import Reference


async def get_references(db: AsyncSession, paper_id: int) -> list:
    result = await db.execute(
        select(Reference).where(Reference.paper_id == paper_id).order_by(Reference.id)
    )
    return [{k: v for k, v in r.__dict__.items() if not k.startswith("_")} for r in result.scalars().all()]


async def add_reference(db: AsyncSession, paper_id: int, data: dict) -> dict:
    ref = Reference(paper_id=paper_id, **{k: v for k, v in data.items() if hasattr(Reference, k)})
    db.add(ref)
    await db.commit()
    await db.refresh(ref)
    log.info("添加文献: paperId=%d title=%s", paper_id, ref.title)
    return {k: v for k, v in ref.__dict__.items() if not k.startswith("_")}


async def update_reference(db: AsyncSession, ref_id: int, data: dict) -> dict:
    result = await db.execute(select(Reference).where(Reference.id == ref_id))
    ref = result.scalar_one_or_none()
    if not ref:
        raise HTTPException(404, "文献不存在")
    for k, v in data.items():
        if hasattr(ref, k) and v is not None:
            setattr(ref, k, v)
    await db.commit()
    return {k: v for k, v in ref.__dict__.items() if not k.startswith("_")}


async def delete_reference(db: AsyncSession, ref_id: int):
    result = await db.execute(select(Reference).where(Reference.id == ref_id))
    ref = result.scalar_one_or_none()
    if not ref:
        raise HTTPException(404, "文献不存在")
    await db.delete(ref)
    await db.commit()
    log.info("删除文献: refId=%d", ref_id)


async def import_bibtex(db: AsyncSession, paper_id: int, bibtex: str) -> int:
    count = 0
    import re
    entries = re.split(r"@(\w+)\{", bibtex)
    for i in range(1, len(entries), 2):
        if i + 1 < len(entries):
            entry_type = entries[i]
            entry_body = entries[i + 1]
            title_match = re.search(r"title\s*=\s*[{(](.+?)[})]", entry_body)
            authors_match = re.search(r"author\s*=\s*[{(](.+?)[})]", entry_body)
            year_match = re.search(r"year\s*=\s*(\d{4})", entry_body)
            journal_match = re.search(r"journal\s*=\s*[{(](.+?)[})]", entry_body)
            ref = Reference(
                paper_id=paper_id,
                title=title_match.group(1) if title_match else "",
                authors=authors_match.group(1) if authors_match else "",
                year=int(year_match.group(1)) if year_match else None,
                journal=journal_match.group(1) if journal_match else "",
                type=entry_type.lower() if entry_type else "other",
                raw_text=entries[i - 1] + "@" + entries[i] + "{" + entry_body,
            )
            db.add(ref)
            count += 1
    await db.commit()
    log.info("导入 BibTeX: paperId=%d count=%d", paper_id, count)
    return count


async def extract_references(db: AsyncSession, paper_id: int) -> list:
    latest = await get_latest_version(db, paper_id)
    if not latest or not latest.content:
        return []
    from ..services.agent_executor import call_llm
    prompt = f"请从以下论文内容中提取所有参考文献，以 JSON 数组格式输出，每条包含 title, authors, year, journal 字段：\n\n{latest.content[:50000]}"
    result = await call_llm("你是一位学术文献提取专家。", prompt)
    import json
    try:
        refs = json.loads(result)
        if isinstance(refs, list):
            for r in refs:
                ref = Reference(paper_id=paper_id, **{k: v for k, v in r.items() if hasattr(Reference, k)})
                db.add(ref)
            await db.commit()
            log.info("AI 提取文献: paperId=%d count=%d", paper_id, len(refs))
            return refs
    except Exception:
        pass
    return []
