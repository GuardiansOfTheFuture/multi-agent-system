"""
LangGraph 多 Agent 编排引擎
用 LangGraph 的 StateGraph 构建论文写作流水线
"""
import json
import logging
import time
import uuid
from typing import TypedDict, Annotated
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.memory import MemorySaver
from .agent_executor import call_llm
from .agent_definitions import get_agent, SUPERVISOR, RESEARCHER, WRITER, REVIEWER, POLISHER

log = logging.getLogger("paperai.langgraph")

PRESET_FLOWS = {
    "standard": {"name": "标准流程", "steps": ["topic_eval", "research", "outline", "write", "review", "polish", "final_review"]},
    "quick_draft": {"name": "快速草稿", "steps": ["research", "outline", "write", "polish", "final_review"]},
    "deep_research": {"name": "深度研究", "steps": ["topic_eval", "research", "outline", "write", "review"] + ["polish", "final_review"], "max_review_rounds": 5},
    "write_only": {"name": "纯写作", "steps": ["write", "polish", "final_review"]},
    "review_paper": {"name": "综述论文", "steps": ["research", "outline", "write", "polish", "final_review"]},
}


class PaperState(TypedDict):
    """论文写作状态"""
    topic: str
    flow_id: str
    max_review_rounds: int
    paper_id: int | None
    current_step: str
    research_output: str
    outline: str
    sections: dict
    review_comments: list
    review_round: int
    final_draft: str
    status: str
    steps_log: list
    error: str


# ===== 节点函数 =====

_step_counter: dict[int, int] = {}
_task_id_map: dict[str, int] = {}  # key: "{paper_id}:{agent_name}" -> task_id


async def _publish_step(paper_id: int | None, step_data: dict):
    """发布步骤事件到 Redis + 写入数据库 Task 记录"""
    if paper_id is None:
        return
    try:
        from ..core.redis_client import publish_step
        await publish_step(paper_id, step_data)
    except Exception:
        pass

    # 写入数据库 Task 记录
    step_type = step_data.get("type", "")
    agent_name = step_data.get("agentName", "")
    if step_type in ("step_start", "step_complete"):
        try:
            from ..core.database import async_session
            from ..models.task import Task
            from sqlalchemy import select
            from datetime import datetime

            async with async_session() as session:
                if step_type == "step_start":
                    _step_counter[paper_id] = _step_counter.get(paper_id, 0) + 1
                    # 获取当前版本号
                    from ..models.paper import Paper
                    paper_result = await session.execute(select(Paper).where(Paper.id == paper_id))
                    paper_obj = paper_result.scalar_one_or_none()
                    ver = paper_obj.current_version if paper_obj else 0
                    task = Task(
                        paper_id=paper_id,
                        agent_role=step_data.get("agentRole", ""),
                        sort_order=_step_counter[paper_id],
                        version_no=ver,
                        description=agent_name,
                        status="IN_PROGRESS",
                    )
                    session.add(task)
                    await session.commit()
                    await session.refresh(task)
                    _task_id_map[f"{paper_id}:{agent_name}"] = task.id
                elif step_type == "step_complete":
                    task_id = _task_id_map.get(f"{paper_id}:{agent_name}")
                    if task_id:
                        result = await session.execute(select(Task).where(Task.id == task_id))
                        task = result.scalar_one_or_none()
                        if task:
                            task.status = "COMPLETED"
                            task.output_data = step_data.get("fullOutput", "")
                            task.duration_ms = step_data.get("durationMs", 0)
                            task.completed_at = datetime.now()
                            await session.commit()
                    # 清理 map
                    _task_id_map.pop(f"{paper_id}:{agent_name}", None)
        except Exception as e:
            log.warning("写入 Task 记录失败: %s", e)


async def _publish_stream(paper_id: int | None, agent_name: str, full_text: str):
    """发布流式文本到 Redis（如果 paper_id 存在）"""
    if paper_id is None:
        return
    try:
        from ..core.redis_client import publish_stream
        await publish_stream(paper_id, agent_name, full_text)
    except Exception:
        pass


async def _clear_stream(paper_id: int | None):
    """清除流式文本"""
    if paper_id is None:
        return
    try:
        from ..core.redis_client import clear_stream
        await clear_stream(paper_id)
    except Exception:
        pass


async def _call_llm_with_stream(system_prompt: str, user_message: str,
                                 paper_id: int | None, agent_name: str) -> str:
    """调用 LLM 并实时推送流式文本到 Redis"""
    from .agent_executor import get_llm, get_cached, put_cached
    from langchain_core.messages import SystemMessage, HumanMessage
    import time as _time

    # 检查缓存
    cached = await get_cached(system_prompt, user_message)
    if cached:
        if paper_id:
            await _publish_stream(paper_id, agent_name, cached)
        return cached

    llm = get_llm()
    messages = [SystemMessage(content=system_prompt), HumanMessage(content=user_message)]
    full = ""
    try:
        async for chunk in llm.astream(messages):
            if chunk.content:
                full += chunk.content
                if paper_id:
                    await _publish_stream(paper_id, agent_name, full)
        await put_cached(system_prompt, user_message, full)
        return full
    except Exception as e:
        log.warning("流式调用失败，降级同步: %s", e)
        from .agent_executor import call_llm
        result = await call_llm(system_prompt, user_message)
        if paper_id:
            await _publish_stream(paper_id, agent_name, result)
        return result


async def topic_eval(state: PaperState) -> dict:
    """选题评估节点"""
    log.info("→ [LangGraph] 选题评估节点")
    paper_id = state.get("paper_id")
    await _publish_step(paper_id, {"type": "step_start", "agentName": "选题评估", "agentRole": "SUPERVISOR"})
    t = time.time()
    agent = SUPERVISOR
    task = f"请评估以下研究选题的学术价值和可行性：\n\n研究主题: {state['topic']}\n\n请从创新性、可行性、学术价值三个维度评价。"
    result = await _call_llm_with_stream(agent.system_prompt, task, paper_id, "选题评估")
    await _clear_stream(paper_id)
    ms = int((time.time() - t) * 1000)
    log.info("← [LangGraph] 选题评估完成 %dms", ms)
    await _publish_step(paper_id, {
        "type": "step_complete", "agentName": "选题评估", "agentRole": "SUPERVISOR",
        "status": "COMPLETED", "durationMs": ms, "summary": result[:200], "fullOutput": result,
    })
    return {
        "current_step": "topic_eval",
        "steps_log": state["steps_log"] + [{"step": "选题评估", "role": "SUPERVISOR", "ms": ms, "output_len": len(result)}],
    }


async def research(state: PaperState) -> dict:
    """文献调研节点"""
    log.info("→ [LangGraph] 文献调研节点")
    paper_id = state.get("paper_id")
    await _publish_step(paper_id, {"type": "step_start", "agentName": "文献调研", "agentRole": "RESEARCHER"})
    t = time.time()
    agent = RESEARCHER
    task = f"研究主题：{state['topic']}\n\n请进行文献调研，输出关键发现和研究方向。"
    result = await _call_llm_with_stream(agent.system_prompt, task, paper_id, "文献调研")
    await _clear_stream(paper_id)
    ms = int((time.time() - t) * 1000)
    log.info("← [LangGraph] 文献调研完成 %dms", ms)
    await _publish_step(paper_id, {
        "type": "step_complete", "agentName": "文献调研", "agentRole": "RESEARCHER",
        "status": "COMPLETED", "durationMs": ms, "summary": result[:200], "fullOutput": result,
    })
    return {
        "research_output": result,
        "current_step": "research",
        "steps_log": state["steps_log"] + [{"step": "文献调研", "role": "RESEARCHER", "ms": ms, "output_len": len(result)}],
    }


async def outline(state: PaperState) -> dict:
    """大纲生成节点"""
    log.info("→ [LangGraph] 大纲生成节点")
    outline_text = "1. 引言\n2. 相关工作\n3. 方法\n4. 实验\n5. 结论"
    return {
        "outline": outline_text,
        "current_step": "outline",
        "sections": {},
    }


async def write_sections(state: PaperState) -> dict:
    """逐章撰写节点"""
    log.info("→ [LangGraph] 逐章撰写节点")
    paper_id = state.get("paper_id")
    sections = {}
    section_names = ["引言", "相关工作", "方法", "实验", "结论"]
    steps_log = list(state["steps_log"])

    for sec in section_names:
        await _publish_step(paper_id, {"type": "step_start", "agentName": sec, "agentRole": "WRITER"})
        t = time.time()
        agent = WRITER
        task = f"请撰写论文的【{sec}】章节。\n基于已有大纲和研究材料展开。\n\n研究材料：{state.get('research_output', '')[:50000]}"
        result = await _call_llm_with_stream(agent.system_prompt, task, paper_id, sec)
        await _clear_stream(paper_id)
        ms = int((time.time() - t) * 1000)
        sections[sec] = result
        steps_log.append({"step": sec, "role": "WRITER", "ms": ms, "output_len": len(result)})
        log.info("← [LangGraph] %s 完成 %dms", sec, ms)
        await _publish_step(paper_id, {
            "type": "step_complete", "agentName": sec, "agentRole": "WRITER",
            "status": "COMPLETED", "durationMs": ms, "summary": result[:200], "fullOutput": result,
        })

    return {
        "sections": sections,
        "current_step": "write",
        "steps_log": steps_log,
    }


async def review(state: PaperState) -> dict:
    """审稿节点"""
    log.info("→ [LangGraph] 审稿节点 round=%d", state.get("review_round", 0) + 1)
    paper_id = state.get("paper_id")
    round_num = state.get("review_round", 0) + 1
    step_name = f"审稿迭代#{round_num}"
    await _publish_step(paper_id, {"type": "step_start", "agentName": step_name, "agentRole": "REVIEWER"})
    t = time.time()
    agent = REVIEWER
    paper_content = "\n".join(f"### {k}\n{v}" for k, v in state.get("sections", {}).items())
    task = f"请对以下论文内容进行全面审阅，判断是否有严重问题：\n\n{paper_content}"
    result = await _call_llm_with_stream(agent.system_prompt, task, paper_id, step_name)
    await _clear_stream(paper_id)
    ms = int((time.time() - t) * 1000)
    log.info("← [LangGraph] 审稿完成 round=%d %dms", round_num, ms)
    await _publish_step(paper_id, {
        "type": "step_complete", "agentName": step_name, "agentRole": "REVIEWER",
        "status": "COMPLETED", "durationMs": ms, "summary": result[:200], "fullOutput": result,
    })
    return {
        "review_comments": state.get("review_comments", []) + [result[:200]],
        "review_round": round_num,
        "current_step": "review",
        "steps_log": state["steps_log"] + [{"step": step_name, "role": "REVIEWER", "ms": ms, "output_len": len(result)}],
    }


async def revise(state: PaperState) -> dict:
    """修改节点"""
    log.info("→ [LangGraph] 修改节点")
    paper_id = state.get("paper_id")
    await _publish_step(paper_id, {"type": "step_start", "agentName": "修改", "agentRole": "WRITER"})
    t = time.time()
    agent = WRITER
    last_review = state.get("review_comments", [""])[-1] if state.get("review_comments") else ""
    task = f"请根据审稿意见修改论文：\n\n审稿意见：{last_review}"
    result = await _call_llm_with_stream(agent.system_prompt, task, paper_id, "修改")
    await _clear_stream(paper_id)
    ms = int((time.time() - t) * 1000)
    log.info("← [LangGraph] 修改完成 %dms", ms)
    await _publish_step(paper_id, {
        "type": "step_complete", "agentName": "修改", "agentRole": "WRITER",
        "status": "COMPLETED", "durationMs": ms, "summary": result[:200], "fullOutput": result,
    })

    sections = dict(state.get("sections", {}))
    sections["修改稿"] = result
    return {
        "sections": sections,
        "current_step": "revise",
        "steps_log": state["steps_log"] + [{"step": "修改", "role": "WRITER", "ms": ms, "output_len": len(result)}],
    }


async def polish(state: PaperState) -> dict:
    """润色节点"""
    log.info("→ [LangGraph] 润色节点")
    paper_id = state.get("paper_id")
    await _publish_step(paper_id, {"type": "step_start", "agentName": "润色定稿", "agentRole": "POLISHER"})
    t = time.time()
    agent = POLISHER
    full_text = "\n".join(f"## {k}\n{v}" for k, v in state.get("sections", {}).items())
    task = f"请对以下论文全文进行润色：\n\n{full_text}"
    result = await _call_llm_with_stream(agent.system_prompt, task, paper_id, "润色定稿")
    await _clear_stream(paper_id)
    ms = int((time.time() - t) * 1000)
    log.info("← [LangGraph] 润色完成 %dms", ms)
    await _publish_step(paper_id, {
        "type": "step_complete", "agentName": "润色定稿", "agentRole": "POLISHER",
        "status": "COMPLETED", "durationMs": ms, "summary": result[:200], "fullOutput": result,
    })
    return {
        "current_step": "polish",
        "steps_log": state["steps_log"] + [{"step": "润色", "role": "POLISHER", "ms": ms, "output_len": len(result)}],
    }


async def final_review(state: PaperState) -> dict:
    """终审节点"""
    log.info("→ [LangGraph] 终审节点")
    paper_id = state.get("paper_id")
    await _publish_step(paper_id, {"type": "step_start", "agentName": "最终审核", "agentRole": "SUPERVISOR"})
    t = time.time()
    agent = SUPERVISOR
    summary = f"论文标题: {state['topic']}\n章节: {', '.join(state.get('sections', {}).keys())}"
    task = f"请对以下论文进行最终审核：\n\n{summary}"
    result = await _call_llm_with_stream(agent.system_prompt, task, paper_id, "最终审核")
    await _clear_stream(paper_id)
    ms = int((time.time() - t) * 1000)
    log.info("← [LangGraph] 终审完成 %dms", ms)
    await _publish_step(paper_id, {
        "type": "step_complete", "agentName": "最终审核", "agentRole": "SUPERVISOR",
        "status": "COMPLETED", "durationMs": ms, "summary": result[:200], "fullOutput": result,
    })

    final_draft = f"# {state['topic']}\n\n" + "\n\n".join(f"## {k}\n{v}" for k, v in state.get("sections", {}).items())
    return {
        "final_draft": final_draft,
        "status": "COMPLETED",
        "current_step": "final_review",
        "steps_log": state["steps_log"] + [{"step": "终审", "role": "SUPERVISOR", "ms": ms, "output_len": len(result)}],
    }


# ===== 条件边函数 =====

def should_review(state: PaperState) -> str:
    """判断是否需要继续审稿"""
    max_rounds = state.get("max_review_rounds", 3)
    current_round = state.get("review_round", 0)
    last_review = state.get("review_comments", [""])[-1] if state.get("review_comments") else ""

    if current_round >= max_rounds:
        log.info("审稿达到最大轮次 %d，进入润色", max_rounds)
        return "polish"
    if "严重问题" not in last_review and current_round > 0:
        log.info("审稿通过，进入润色")
        return "polish"
    log.info("需要修改，进入修改节点")
    return "revise"


# ===== 构建图 =====

def build_writing_graph() -> StateGraph:
    """构建论文写作状态图"""
    workflow = StateGraph(PaperState)

    # 添加节点
    workflow.add_node("topic_eval", topic_eval)
    workflow.add_node("research", research)
    workflow.add_node("outline", outline)
    workflow.add_node("write", write_sections)
    workflow.add_node("review", review)
    workflow.add_node("revise", revise)
    workflow.add_node("polish", polish)
    workflow.add_node("final_review", final_review)

    # 设置入口
    workflow.set_entry_point("topic_eval")

    # 线性边
    workflow.add_edge("topic_eval", "research")
    workflow.add_edge("research", "outline")
    workflow.add_edge("outline", "write")
    workflow.add_edge("write", "review")

    # 条件边：审稿后判断
    workflow.add_conditional_edges(
        "review",
        should_review,
        {"revise": "revise", "polish": "polish"},
    )

    # 修改后回到审稿
    workflow.add_edge("revise", "review")

    # 润色后终审
    workflow.add_edge("polish", "final_review")
    workflow.add_edge("final_review", END)

    return workflow


# 编译图（带内存检查点）
_memory = MemorySaver()
_graph = None


def get_graph() -> StateGraph:
    global _graph
    if _graph is None:
        _graph = build_writing_graph().compile(checkpointer=_memory)
    return _graph


async def execute_langgraph_pipeline(topic: str, flow_id: str = "standard",
                                     max_review_rounds: int = 3,
                                     paper_id: int | None = None,
                                     on_step=None) -> dict:
    """执行 LangGraph 论文写作流水线"""
    graph = get_graph()
    initial_state: PaperState = {
        "topic": topic,
        "flow_id": flow_id,
        "max_review_rounds": max_review_rounds,
        "paper_id": paper_id,
        "current_step": "",
        "research_output": "",
        "outline": "",
        "sections": {},
        "review_comments": [],
        "review_round": 0,
        "final_draft": "",
        "status": "RUNNING",
        "steps_log": [],
        "error": "",
    }

    config = {"configurable": {"thread_id": str(uuid.uuid4())}}
    start = time.time()
    log.info("===== LangGraph 写作开始 topic=%s paper_id=%s =====", topic, paper_id)

    final_state = await graph.ainvoke(initial_state, config)

    total_ms = int((time.time() - start) * 1000)
    log.info("===== LangGraph 写作完成 topic=%s total=%dms steps=%d =====",
             topic, total_ms, len(final_state.get("steps_log", [])))

    # 发布完成事件
    if paper_id:
        from ..core.redis_client import publish_step
        await publish_step(paper_id, {"type": "complete", "status": "COMPLETED"})

    return {
        "contextId": config["configurable"]["thread_id"],
        "topic": topic,
        "finalDraft": final_state.get("final_draft", ""),
        "sections": [{"title": k, "length": len(v)} for k, v in final_state.get("sections", {}).items()],
        "reviewComments": final_state.get("review_comments", []),
        "steps": final_state.get("steps_log", []),
        "status": final_state.get("status", "COMPLETED"),
        "totalDurationMs": total_ms,
    }
