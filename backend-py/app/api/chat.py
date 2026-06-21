import json
import logging
from fastapi import APIRouter, Depends, Query
from fastapi.responses import StreamingResponse
from sqlalchemy import select, desc, delete
from sqlalchemy.ext.asyncio import AsyncSession
from ..core.database import get_db
from ..dependencies import get_current_user_id
from ..schemas.common import ApiResult
from ..models.conversation import Conversation, ConversationMessage
from ..models.article import Article
from ..services.chat_service import (
    process_message, process_message_stream, process_paper_stream,
    process_rewrite_stream, process_section_modify_stream,
    detect_paper_intent, _detect_section, _detect_stage, _detect_modify_intent, _parse_doc_sections
)

log = logging.getLogger("paperai.chat")
router = APIRouter(prefix="/api/chat", tags=["对话"])


def _conv_to_dict(c) -> dict:
    return {
        "id": c.id,
        "title": c.title,
        "createdAt": str(c.created_at) if c.created_at else None,
        "updatedAt": str(c.updated_at) if c.updated_at else None,
    }


def _msg_to_dict(m) -> dict:
    return {
        "id": m.id,
        "role": m.role,
        "content": m.content,
        "createdAt": str(m.created_at) if m.created_at else None,
    }


@router.get("/list")
async def list_conversations(db: AsyncSession = Depends(get_db),
                             user_id: int = Depends(get_current_user_id)):
    q = select(Conversation).where(Conversation.user_id == user_id).order_by(desc(Conversation.updated_at))
    rows = (await db.execute(q)).scalars().all()
    return ApiResult.success([_conv_to_dict(c) for c in rows])


@router.post("/create")
async def create_conversation(data: dict, db: AsyncSession = Depends(get_db),
                              user_id: int = Depends(get_current_user_id)):
    title = data.get("title", "新对话")
    conv = Conversation(user_id=user_id, title=title)
    db.add(conv)
    await db.commit()
    await db.refresh(conv)
    return ApiResult.success(_conv_to_dict(conv))


@router.get("/{conv_id}/messages")
async def get_messages(conv_id: int, db: AsyncSession = Depends(get_db),
                       user_id: int = Depends(get_current_user_id)):
    conv = await db.get(Conversation, conv_id)
    if not conv or conv.user_id != user_id:
        return ApiResult.error(404, "对话不存在")
    q = select(ConversationMessage).where(
        ConversationMessage.conversation_id == conv_id
    ).order_by(ConversationMessage.created_at)
    rows = (await db.execute(q)).scalars().all()

    # 加载关联的 Article
    article_q = select(Article).where(Article.conversation_id == conv_id)
    article = (await db.execute(article_q)).scalar_one_or_none()

    return ApiResult.success({
        "messages": [_msg_to_dict(m) for m in rows],
        "article": {
            "title": article.title if article else "",
            "content": article.content if article else "",
        } if article else None,
    })


@router.post("/{conv_id}/send")
async def send_message(conv_id: int, data: dict, db: AsyncSession = Depends(get_db),
                       user_id: int = Depends(get_current_user_id)):
    conv = await db.get(Conversation, conv_id)
    if not conv or conv.user_id != user_id:
        return ApiResult.error(404, "对话不存在")

    user_msg = data.get("message", "").strip()
    thinking = data.get("thinking", False)
    if not user_msg:
        return ApiResult.error(400, "消息不能为空")

    # 保存用户消息
    db.add(ConversationMessage(conversation_id=conv_id, role="user", content=user_msg))

    # 如果是第一条消息，更新对话标题
    msg_count = len((await db.execute(
        select(ConversationMessage.id).where(ConversationMessage.conversation_id == conv_id)
    )).scalars().all())
    if msg_count <= 1:
        conv.title = user_msg[:30] + ("..." if len(user_msg) > 30 else "")

    await db.commit()

    # 获取历史消息
    history_q = select(ConversationMessage).where(
        ConversationMessage.conversation_id == conv_id
    ).order_by(ConversationMessage.created_at).limit(20)
    history_rows = (await db.execute(history_q)).scalars().all()
    history = [{"role": m.role, "content": m.content} for m in history_rows]

    # 调用对话服务
    result = await process_message(user_msg, history, thinking=thinking)

    reply = result["reply"]
    thinking_content = result.get("thinking", "")
    suggestions = result.get("suggestions")
    agents = result.get("agents")

    # 保存 AI 回复
    db.add(ConversationMessage(conversation_id=conv_id, role="agent", content=reply))
    await db.commit()

    return ApiResult.success({
        "reply": reply,
        "thinking": thinking_content,
        "suggestions": suggestions,
        "agents": agents,
    })


@router.post("/{conv_id}/stream")
async def stream_message(conv_id: int, data: dict, db: AsyncSession = Depends(get_db),
                         user_id: int = Depends(get_current_user_id)):
    conv = await db.get(Conversation, conv_id)
    if not conv or conv.user_id != user_id:
        async def err():
            yield f"event: error\ndata: {json.dumps({'error': '对话不存在'})}\n\n"
        return StreamingResponse(err(), media_type="text/event-stream")

    user_msg = data.get("message", "").strip()
    thinking = data.get("thinking", False)

    # 获取或创建 Article（用独立 session，确保 SSE 期间可用）
    from ..core.database import async_session
    doc_content = ""
    async with async_session() as sess:
        article_q = select(Article).where(Article.conversation_id == conv_id)
        article = (await sess.execute(article_q)).scalar_one_or_none()
        if not article:
            article = Article(conversation_id=conv_id, title="未命名文档", content="")
            sess.add(article)
            await sess.commit()
        doc_content = article.content or ""

    if not user_msg:
        async def err():
            yield f"event: error\ndata: {json.dumps({'error': '消息不能为空'})}\n\n"
        return StreamingResponse(err(), media_type="text/event-stream")

    # 保存用户消息
    db.add(ConversationMessage(conversation_id=conv_id, role="user", content=user_msg))

    # 首条消息更新标题
    msg_count = len((await db.execute(
        select(ConversationMessage.id).where(ConversationMessage.conversation_id == conv_id)
    )).scalars().all())
    if msg_count <= 1:
        conv.title = user_msg[:30] + ("..." if len(user_msg) > 30 else "")
    await db.commit()

    # 获取历史消息
    history_q = select(ConversationMessage).where(
        ConversationMessage.conversation_id == conv_id
    ).order_by(ConversationMessage.created_at).limit(20)
    history_rows = (await db.execute(history_q)).scalars().all()
    history = [{"role": m.role, "content": m.content} for m in history_rows]

    async def event_generator():
        reply_text = ""
        is_paper = detect_paper_intent(user_msg, history)
        has_doc = bool(doc_content.strip())
        is_modify = _detect_modify_intent(user_msg)
        has_sections = bool(_parse_doc_sections(doc_content))

        # 路由逻辑（优先级从高到低）：
        # 1. 有文档 + 修改意图 + 能定位章节 → 章节修改（最高优先级）
        # 2. 有文档 + 修改意图但没定位到章节 → 普通对话（让 AI 引导用户）
        # 3. 论文意图 → 论文写作
        # 4. 其他 → 普通对话
        if has_doc and is_modify and has_sections:
            stream_fn = process_section_modify_stream
            kwargs = {"message": user_msg, "history": history, "doc_content": doc_content, "thinking": thinking}
        elif is_paper and not (has_doc and is_modify):
            # 只有在不是修改意图时才走论文写作
            stream_fn = process_paper_stream
            kwargs = {"message": user_msg, "history": history, "thinking": thinking, "doc_content": doc_content}
        else:
            stream_fn = process_message_stream
            kwargs = {"message": user_msg, "history": history, "thinking": thinking, "doc_content": doc_content}

        # 用独立 session 做即时保存（SSE 长连接不能依赖注入的 db）
        from ..core.database import async_session

        # 收集流式数据，done 时一起存库
        collected_think_rounds = []
        collected_tasks = []

        try:
            async for chunk in stream_fn(**kwargs):
                if chunk["type"] == "think":
                    round_id = chunk.get("round", 0)
                    content = chunk["content"]
                    streaming = chunk.get("streaming", False)
                    # 收集思考轮次（只在 streaming=False 时记录最终内容）
                    if not streaming:
                        collected_think_rounds.append({"round": round_id, "content": content})
                    yield f"event: think\ndata: {json.dumps({'round': round_id, 'content': content, 'streaming': streaming}, ensure_ascii=False)}\n\n"
                elif chunk["type"] == "tasklist":
                    collected_tasks = chunk["tasks"]
                    yield f"event: tasklist\ndata: {json.dumps({'tasks': chunk['tasks']}, ensure_ascii=False)}\n\n"
                elif chunk["type"] == "task_update":
                    idx = chunk["index"]
                    status = chunk["status"]
                    summary = chunk.get("summary", "")
                    if idx < len(collected_tasks):
                        collected_tasks[idx]["status"] = status
                        if summary:
                            collected_tasks[idx]["summary"] = summary
                    yield f"event: task_update\ndata: {json.dumps({'index': idx, 'status': status, 'summary': summary}, ensure_ascii=False)}\n\n"
                elif chunk["type"] == "thinking":
                    yield f"event: thinking\ndata: {json.dumps({'content': chunk['content']}, ensure_ascii=False)}\n\n"
                elif chunk["type"] == "token":
                    reply_text = chunk["content"]
                    yield f"event: token\ndata: {json.dumps({'content': chunk['content']}, ensure_ascii=False)}\n\n"
                elif chunk["type"] == "doc":
                    # 即时保存到 Article
                    doc_now = chunk["content"]
                    try:
                        async with async_session() as sess:
                            from sqlalchemy import update as sql_update
                            await sess.execute(
                                sql_update(Article).where(Article.conversation_id == conv_id).values(content=doc_now)
                            )
                            await sess.commit()
                    except Exception:
                        pass  # 保存失败不影响流式输出
                    yield f"event: doc\ndata: {json.dumps({'content': doc_now}, ensure_ascii=False)}\n\n"
                elif chunk["type"] == "done":
                    # 保存 AI 回复到消息表（存完整 JSON，包含思考、任务、卡片）
                    try:
                        async with async_session() as sess:
                            msg_data = json.dumps({
                                "reply": chunk.get("reply", ""),
                                "thinking": chunk.get("thinking", ""),
                                "cards": chunk.get("cards", []),
                                "thinkRounds": collected_think_rounds,
                                "tasks": collected_tasks,
                            }, ensure_ascii=False)
                            sess.add(ConversationMessage(
                                conversation_id=conv_id, role="agent", content=msg_data
                            ))
                            # 最终文档保存
                            doc_final = chunk.get("doc", "")
                            doc_title = chunk.get("title", "")
                            if doc_final:
                                from sqlalchemy import update as sql_update
                                values = {"content": doc_final}
                                if doc_title:
                                    values["title"] = doc_title
                                await sess.execute(
                                    sql_update(Article).where(Article.conversation_id == conv_id).values(**values)
                                )
                            await sess.commit()
                    except Exception:
                        pass
                    yield f"event: done\ndata: {json.dumps({'reply': chunk['reply'], 'thinking': chunk.get('thinking', ''), 'doc': chunk.get('doc', ''), 'cards': chunk.get('cards', [])}, ensure_ascii=False)}\n\n"
        except Exception as e:
            log.error("流式对话异常: %s", e, exc_info=True)
            yield f"event: error\ndata: {json.dumps({'error': str(e)[:200]}, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")


@router.post("/{conv_id}/rewrite")
async def rewrite_text(conv_id: int, data: dict, db: AsyncSession = Depends(get_db),
                       user_id: int = Depends(get_current_user_id)):
    conv = await db.get(Conversation, conv_id)
    if not conv or conv.user_id != user_id:
        async def err():
            yield f"event: error\ndata: {json.dumps({'error': '对话不存在'})}\n\n"
        return StreamingResponse(err(), media_type="text/event-stream")

    selected_text = data.get("selectedText", "").strip()
    instruction = data.get("instruction", "").strip()
    # 从 Article 获取文档内容
    article_q = select(Article).where(Article.conversation_id == conv_id)
    article = (await db.execute(article_q)).scalar_one_or_none()
    doc_content = article.content if article else ""
    if not selected_text:
        async def err():
            yield f"event: error\ndata: {json.dumps({'error': '未选中文本'})}\n\n"
        return StreamingResponse(err(), media_type="text/event-stream")
    if not instruction:
        instruction = "润色改写这段文字"

    # 获取历史消息作为上下文
    history_q = select(ConversationMessage).where(
        ConversationMessage.conversation_id == conv_id
    ).order_by(ConversationMessage.created_at).limit(10)
    history_rows = (await db.execute(history_q)).scalars().all()
    history = [{"role": m.role, "content": m.content} for m in history_rows]

    async def event_generator():
        try:
            async for chunk in process_rewrite_stream(selected_text, instruction, doc_content=doc_content, history=history):
                if chunk["type"] == "token":
                    yield f"event: token\ndata: {json.dumps({'content': chunk['content']}, ensure_ascii=False)}\n\n"
                elif chunk["type"] == "done":
                    # 将改写结果替换回 Article
                    rewritten = chunk["content"]
                    if article and selected_text in (article.content or ""):
                        article.content = article.content.replace(selected_text, rewritten)
                        await db.commit()
                    yield f"event: done\ndata: {json.dumps({'content': rewritten}, ensure_ascii=False)}\n\n"
        except Exception as e:
            log.error("改写异常: %s", e, exc_info=True)
            yield f"event: error\ndata: {json.dumps({'error': str(e)[:200]}, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")


@router.post("/{conv_id}/agent-review")
async def agent_review(conv_id: int, data: dict, db: AsyncSession = Depends(get_db),
                       user_id: int = Depends(get_current_user_id)):
    """Agent 审阅文档 — 独立 SSE 流"""
    conv = await db.get(Conversation, conv_id)
    if not conv or conv.user_id != user_id:
        async def err():
            yield f"event: error\ndata: {json.dumps({'error': '对话不存在'})}\n\n"
        return StreamingResponse(err(), media_type="text/event-stream")

    agent_role = data.get("agentRole", "REVIEWER")

    # 获取文档内容
    from ..models.article import Article
    article_q = select(Article).where(Article.conversation_id == conv_id)
    article = (await db.execute(article_q)).scalar_one_or_none()
    doc_content = article.content if article else ""

    if not doc_content:
        async def err():
            yield f"event: error\ndata: {json.dumps({'error': '文档为空，无法审阅'})}\n\n"
        return StreamingResponse(err(), media_type="text/event-stream")

    # Agent 角色配置
    agent_configs = {
        "SUPERVISOR": {
            "name": "导师",
            "system": "你是博士生导师，负责从学术方向、研究价值角度审阅论文。给出专业、严谨的指导意见。",
            "prompt": "请从导师角度审阅这篇论文，关注：\n1. 研究方向是否正确\n2. 论文结构是否合理\n3. 学术规范是否达标\n4. 总体评价和改进建议"
        },
        "REVIEWER": {
            "name": "审稿人",
            "system": "你是严格的学术审稿人，负责从论文质量、逻辑性、创新性角度审阅。",
            "prompt": "请从审稿人角度审阅这篇论文，关注：\n1. 创新性：是否有新贡献\n2. 方法论：研究方法是否合理\n3. 逻辑性：论证是否严密\n4. 表达质量：写作是否规范\n5. 给出评分(0-10)和具体改进建议"
        },
        "POLISHER": {
            "name": "润色师",
            "system": "你是学术写作润色专家，负责从语言、格式、引用角度审阅论文。",
            "prompt": "请从润色师角度审阅这篇论文，关注：\n1. 语言表达是否规范、流畅\n2. 格式是否符合学术规范\n3. 引用是否完整、准确\n4. 有哪些具体的润色建议"
        },
        "RESEARCHER": {
            "name": "研究员",
            "system": "你是学术研究员，负责从文献调研、研究现状角度审阅论文。",
            "prompt": "请从研究员角度审阅这篇论文，关注：\n1. 文献综述是否全面\n2. 是否遗漏了重要相关工作\n3. 研究定位是否准确\n4. 有哪些需要补充的文献方向"
        },
    }

    config = agent_configs.get(agent_role, agent_configs["REVIEWER"])

    from ..core.database import async_session

    async def event_generator():
        # 发送开始事件
        yield f"event: start\ndata: {json.dumps({'agentName': config['name'], 'agentRole': agent_role}, ensure_ascii=False)}\n\n"

        full_prompt = f"""{config['prompt']}

论文内容：
{doc_content[:6000]}

请用 Markdown 格式输出审阅意见，结构清晰，分点说明。"""

        review_text = ""
        try:
            async with async_session() as sess:
                from ..services.agent_executor import call_llm_agen
                async for accumulated in call_llm_agen(config["system"], full_prompt, use_cache=False):
                    review_text = accumulated
                    yield f"event: token\ndata: {json.dumps({'content': accumulated}, ensure_ascii=False)}\n\n"

            yield f"event: done\ndata: {json.dumps({'content': review_text}, ensure_ascii=False)}\n\n"
        except Exception as e:
            log.error("Agent 审阅异常: %s", e, exc_info=True)
            yield f"event: error\ndata: {json.dumps({'error': str(e)[:200]}, ensure_ascii=False)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")


@router.delete("/{conv_id}")
async def delete_conversation(conv_id: int, db: AsyncSession = Depends(get_db),
                              user_id: int = Depends(get_current_user_id)):
    conv = await db.get(Conversation, conv_id)
    if not conv or conv.user_id != user_id:
        return ApiResult.error(404, "对话不存在")
    await db.execute(delete(ConversationMessage).where(ConversationMessage.conversation_id == conv_id))
    await db.delete(conv)
    await db.commit()
    return ApiResult.success(message="已删除")
