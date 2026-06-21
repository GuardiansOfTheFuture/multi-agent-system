import json
import asyncio
import logging
import uuid
from fastapi import APIRouter, Depends, Query, Request
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from ..core.database import get_db, async_session
from ..dependencies import get_current_user_id
from ..schemas.common import ApiResult
from ..schemas.paper import PaperWritingRequest
from ..services import paper_service
from ..services.paper_service import to_dict
from ..services.langgraph_orchestrator import execute_langgraph_pipeline

log = logging.getLogger("paperai.paper")
router = APIRouter(prefix="/api/paper", tags=["论文"])


@router.get("/health")
async def health():
    return ApiResult.success("PaperAI Backend is running")


@router.post("/create")
async def create_paper(req: PaperWritingRequest, db: AsyncSession = Depends(get_db),
                       user_id: int = Depends(get_current_user_id)):
    paper = await paper_service.create_paper(db, req.topic, user_id, description=req.description, keywords=req.keywords)
    return ApiResult.success({"paperId": paper.id, "topic": paper.title, "status": paper.status}, "论文已创建")


@router.post("/research")
async def do_research(req: PaperWritingRequest, db: AsyncSession = Depends(get_db),
                      user_id: int = Depends(get_current_user_id)):
    from ..services.agent_definitions import RESEARCHER
    from ..services.agent_executor import call_llm
    topic = req.topic
    keywords = req.keywords or ""
    description = req.description or ""
    task = f"研究主题：{topic}"
    if keywords:
        task += f"\n关键词：{keywords}"
    if description:
        task += f"\n研究方向：{description}"
    task += "\n\n请进行文献调研，输出关键发现和研究方向。"
    result = await call_llm(RESEARCHER.system_prompt, task)
    return ApiResult.success({"topic": topic, "researchOutput": result})


@router.post("/write")
async def write_paper(req: PaperWritingRequest, db: AsyncSession = Depends(get_db),
                      user_id: int = Depends(get_current_user_id)):
    paper = await paper_service.create_paper(db, req.topic, user_id)
    result = await execute_langgraph_pipeline(req.topic, req.flowId or "standard",
                                              max_review_rounds=req.maxReviewRounds or 3,
                                              paper_id=paper.id)
    content = result.get("finalDraft", "")
    await paper_service.save_version(db, paper.id, "FINAL", "论文终稿", content, "AGENT")
    await paper_service.update_status(db, paper.id, "COMPLETED")
    result["paperId"] = paper.id
    return ApiResult.success(result)


@router.post("/write/{paper_id}/stop")
async def stop_writing(paper_id: int, user_id: int = Depends(get_current_user_id)):
    from ..core.redis_client import mark_stop
    await mark_stop(paper_id)
    return ApiResult.success({"paperId": paper_id, "status": "STOPPED"})


@router.post("/write/{paper_id}")
async def start_writing(paper_id: int, req: PaperWritingRequest, db: AsyncSession = Depends(get_db),
                        user_id: int = Depends(get_current_user_id)):
    await paper_service.check_owner(db, paper_id, user_id)
    await paper_service.update_status(db, paper_id, "WRITING")

    # 后台任务：创建独立 DB session，避免请求结束后 session 被关闭
    async def _run():
        try:
            async with async_session() as session:
                result = await execute_langgraph_pipeline(req.topic, req.flowId or "standard",
                                                         max_review_rounds=req.maxReviewRounds or 3,
                                                         paper_id=paper_id)
                content = result.get("finalDraft", "")
                await paper_service.save_version(session, paper_id, "FINAL", "论文终稿", content, "AGENT")
                await paper_service.update_status(session, paper_id, "COMPLETED")
        except Exception as e:
            log.error("后台写作任务失败 paperId=%d: %s", paper_id, e, exc_info=True)
            try:
                async with async_session() as session:
                    await paper_service.update_status(session, paper_id, "FAILED")
            except Exception:
                log.error("更新论文状态失败 paperId=%d", paper_id)

    asyncio.create_task(_run())
    return ApiResult.success({"paperId": paper_id, "taskId": str(uuid.uuid4()), "status": "QUEUED"})


@router.get("/write/{paper_id}/stream")
async def stream_writing(paper_id: int, request: Request, user_id: int = Depends(get_current_user_id)):
    from ..core.redis_client import is_stop_requested, get_steps, get_stream

    async def event_generator():
        yield f"event: connected\ndata: {json.dumps({'paperId': paper_id})}\n\n"
        last_index = 0
        last_stream_text = ""
        async with async_session() as session:
            while True:
                if await request.is_disconnected():
                    break
                if await is_stop_requested(paper_id):
                    yield f"event: error\ndata: {json.dumps({'message': '任务已被停止'})}\n\n"
                    break

                # 从 Redis 读取流式文本（实时）
                stream_data = await get_stream(paper_id)
                if stream_data and stream_data.get("fullText", "") != last_stream_text:
                    last_stream_text = stream_data.get("fullText", "")
                    yield f"event: stream\ndata: {json.dumps(stream_data, ensure_ascii=False)}\n\n"

                # 从 Redis 读取步骤事件
                steps = await get_steps(paper_id, last_index)
                for step_data in steps:
                    event_type = step_data.get("type", "")
                    if event_type == "step_start":
                        yield f"event: step\ndata: {json.dumps({'agentName': step_data.get('agentName', ''), 'agentRole': step_data.get('agentRole', ''), 'status': 'IN_PROGRESS', 'durationMs': 0, 'summary': '进行中...', 'fullOutput': ''}, ensure_ascii=False)}\n\n"
                        last_index += 1
                    elif event_type == "step_complete":
                        yield f"event: step\ndata: {json.dumps({'agentName': step_data.get('agentName', ''), 'agentRole': step_data.get('agentRole', ''), 'status': step_data.get('status', 'COMPLETED'), 'durationMs': step_data.get('durationMs', 0), 'summary': step_data.get('summary', ''), 'fullOutput': step_data.get('fullOutput', '')}, ensure_ascii=False)}\n\n"
                        last_stream_text = ""  # 步骤完成后重置流式文本
                        last_index += 1
                    elif event_type == "complete":
                        yield f"event: complete\ndata: {json.dumps({'status': 'COMPLETED'})}\n\n"
                        return

                # 兜底：检查数据库状态
                try:
                    paper = await paper_service.get_paper_by_id(session, paper_id)
                    if paper:
                        current_status = paper.status
                        if current_status == "COMPLETED" and not steps:
                            yield f"event: complete\ndata: {json.dumps({'status': 'COMPLETED'})}\n\n"
                            break
                        elif current_status == "FAILED":
                            yield f"event: error\ndata: {json.dumps({'message': '写作失败'})}\n\n"
                            break
                except Exception:
                    pass  # 论文可能还没创建
                await asyncio.sleep(0.5)
    return StreamingResponse(event_generator(), media_type="text/event-stream")


@router.get("/list")
async def list_papers(page: int = Query(1), size: int = Query(10),
                      db: AsyncSession = Depends(get_db),
                      user_id: int = Depends(get_current_user_id)):
    result = await paper_service.list_papers(db, user_id, page, size)
    return ApiResult.success(result)


@router.get("/{paper_id}")
async def get_paper(paper_id: int, db: AsyncSession = Depends(get_db),
                    user_id: int = Depends(get_current_user_id)):
    paper = await paper_service.check_owner(db, paper_id, user_id)
    d = {k: v for k, v in paper.__dict__.items() if not k.startswith("_")}
    return ApiResult.success(d)


@router.put("/{paper_id}/content")
async def update_content(paper_id: int, data: dict, db: AsyncSession = Depends(get_db),
                         user_id: int = Depends(get_current_user_id)):
    await paper_service.check_owner(db, paper_id, user_id)
    content = data.get("content", "")
    version_no = data.get("versionNo")
    await paper_service.update_content(db, paper_id, version_no, content) if version_no else None
    return ApiResult.success(message="内容已更新")


@router.post("/{paper_id}/versions")
async def save_version(paper_id: int, data: dict, db: AsyncSession = Depends(get_db),
                       user_id: int = Depends(get_current_user_id)):
    await paper_service.check_owner(db, paper_id, user_id)
    pv = await paper_service.save_version(
        db, paper_id, data.get("stage", "MANUAL"), data.get("summary", ""),
        data.get("content", ""), data.get("editType", "MANUAL"), data.get("changeSummary")
    )
    return ApiResult.success({k: v for k, v in pv.__dict__.items() if not k.startswith("_")}, "版本已保存")


@router.get("/{paper_id}/versions/{version_no}")
async def get_version(paper_id: int, version_no: int, db: AsyncSession = Depends(get_db)):
    versions = await paper_service.get_versions(db, paper_id)
    for v in versions:
        if v.version_no == version_no:
            return ApiResult.success({k: val for k, val in v.__dict__.items() if not k.startswith("_")})
    return ApiResult.error(404, "版本不存在")


@router.get("/{paper_id}/versions")
async def get_versions(paper_id: int, db: AsyncSession = Depends(get_db)):
    versions = await paper_service.get_versions(db, paper_id)
    return ApiResult.success([{k: v for k, v in v.__dict__.items() if not k.startswith("_")} for v in versions])


@router.get("/{paper_id}/versions/latest")
async def get_latest_version(paper_id: int, db: AsyncSession = Depends(get_db)):
    v = await paper_service.get_latest_version(db, paper_id)
    if not v:
        return ApiResult.error(404, "无版本记录")
    return ApiResult.success({k: val for k, val in v.__dict__.items() if not k.startswith("_")})


@router.get("/{paper_id}/tasks")
async def get_tasks(paper_id: int, db: AsyncSession = Depends(get_db),
                    user_id: int = Depends(get_current_user_id)):
    paper = await paper_service.check_owner(db, paper_id, user_id)
    tasks = await paper_service.get_tasks_by_paper(db, paper_id)
    paper_dict = {k: v for k, v in paper.__dict__.items() if not k.startswith("_")}
    tasks_list = [{k: v for k, v in t.__dict__.items() if not k.startswith("_")} for t in tasks]
    return ApiResult.success({"paper": paper_dict, "tasks": tasks_list})


@router.delete("/{paper_id}")
async def delete_paper(paper_id: int, db: AsyncSession = Depends(get_db),
                       user_id: int = Depends(get_current_user_id)):
    await paper_service.check_owner(db, paper_id, user_id)
    await paper_service.delete_paper(db, paper_id)
    return ApiResult.success(message="删除成功")


# ===== 参考文献 =====

@router.get("/{paper_id}/references")
async def list_references(paper_id: int, db: AsyncSession = Depends(get_db),
                          user_id: int = Depends(get_current_user_id)):
    await paper_service.check_owner(db, paper_id, user_id)
    refs = await paper_service.get_references(db, paper_id)
    return ApiResult.success(refs)


@router.post("/{paper_id}/references")
async def add_reference(paper_id: int, data: dict, db: AsyncSession = Depends(get_db),
                        user_id: int = Depends(get_current_user_id)):
    await paper_service.check_owner(db, paper_id, user_id)
    ref = await paper_service.add_reference(db, paper_id, data)
    return ApiResult.success(ref, "添加成功")


@router.put("/{paper_id}/references/{ref_id}")
async def update_reference(paper_id: int, ref_id: int, data: dict, db: AsyncSession = Depends(get_db),
                           user_id: int = Depends(get_current_user_id)):
    await paper_service.check_owner(db, paper_id, user_id)
    ref = await paper_service.update_reference(db, ref_id, data)
    return ApiResult.success(ref, "更新成功")


@router.delete("/{paper_id}/references/{ref_id}")
async def delete_reference(paper_id: int, ref_id: int, db: AsyncSession = Depends(get_db),
                           user_id: int = Depends(get_current_user_id)):
    await paper_service.check_owner(db, paper_id, user_id)
    await paper_service.delete_reference(db, ref_id)
    return ApiResult.success(message="删除成功")


@router.post("/{paper_id}/references/import-bibtex")
async def import_bibtex(paper_id: int, data: dict, db: AsyncSession = Depends(get_db),
                        user_id: int = Depends(get_current_user_id)):
    await paper_service.check_owner(db, paper_id, user_id)
    count = await paper_service.import_bibtex(db, paper_id, data.get("bibtex", ""))
    return ApiResult.success({"imported": count}, f"成功导入 {count} 条文献")


@router.post("/{paper_id}/references/extract")
async def extract_references(paper_id: int, db: AsyncSession = Depends(get_db),
                             user_id: int = Depends(get_current_user_id)):
    await paper_service.check_owner(db, paper_id, user_id)
    refs = await paper_service.extract_references(db, paper_id)
    return ApiResult.success(refs)


# ===== 导出 =====

@router.get("/{paper_id}/export")
async def export_paper(paper_id: int, format: str = Query("docx"), version_no: int | None = Query(None),
                       db: AsyncSession = Depends(get_db),
                       user_id: int = Depends(get_current_user_id)):
    await paper_service.check_owner(db, paper_id, user_id)
    from ..services.paper_service import get_content_for_export
    content, title = await get_content_for_export(db, paper_id, version_no)
    from ..exporters.docx_exporter import DocxExporter
    from ..exporters.html_exporter import HtmlExporter
    from ..exporters.latex_exporter import LatexExporter
    exporters = {"docx": DocxExporter, "html": HtmlExporter, "latex": LatexExporter}
    exporter_cls = exporters.get(format, DocxExporter)
    data = exporter_cls().export(content, title)
    media_types = {"docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                   "html": "text/html", "latex": "application/x-latex"}
    return StreamingResponse(
        iter([data]),
        media_type=media_types.get(format, "application/octet-stream"),
        headers={"Content-Disposition": f'attachment; filename="{title}.{format}"'}
    )


@router.post("/{paper_id}/agent-edit")
async def agent_edit(paper_id: int, data: dict, db: AsyncSession = Depends(get_db),
                     user_id: int = Depends(get_current_user_id)):
    await paper_service.check_owner(db, paper_id, user_id)
    from ..services.agent_executor import call_llm
    selected_text = data.get("selectedText", "")
    instruction = data.get("instruction", "")
    prompt = f"请根据以下指令修改选中的文本：\n\n选中文本：{selected_text}\n\n指令：{instruction}\n\n请直接输出修改后的文本，不要解释。"
    result = await call_llm("你是一位学术论文编辑专家。", prompt)
    return ApiResult.success({"originalText": selected_text, "modifiedText": result})
