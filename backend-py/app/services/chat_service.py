"""
对话服务 — 检测用户意图，引导论文写作
"""
import json
import logging
from .agent_executor import call_llm, call_llm_agen, call_llm_with_thinking
from .agent_definitions import get_agent, AGENTS, WRITER

log = logging.getLogger("paperai.chat")

# Agent 角色描述（给用户看）
AGENT_OPTIONS = [
    {"role": "RESEARCHER", "icon": "🔬", "name": "研究员", "desc": "文献调研、资料收集、综述撰写"},
    {"role": "WRITER", "icon": "✍️", "name": "写作者", "desc": "章节撰写、论证构建、内容组织"},
    {"role": "REVIEWER", "icon": "📝", "name": "审稿人", "desc": "质量审查、问题发现、改进建议"},
    {"role": "POLISHER", "icon": "✨", "name": "润色师", "desc": "语言优化、格式规范、引用检查"},
]

# 论文写作阶段
PAPER_STAGES = [
    {"id": "topic", "label": "选题确认", "desc": "确定研究主题和方向"},
    {"id": "research", "label": "文献调研", "desc": "收集和分析相关文献"},
    {"id": "outline", "label": "大纲设计", "desc": "规划论文结构和章节"},
    {"id": "writing", "label": "内容撰写", "desc": "逐章撰写论文正文"},
    {"id": "review", "label": "审稿修改", "desc": "审查和改进论文质量"},
    {"id": "polish", "label": "润色定稿", "desc": "语言优化和格式规范"},
]

PAPER_KEYWORDS = [
    "论文", "paper", "写一篇", "撰写", "综述", "研究", "学术",
    "毕业", "课程", "期刊", "会议", "report", "thesis", "dissertation",
    "实验", "方法论", "文献", "引用", "摘要", "abstract",
    "写摘要", "写引言", "写结论", "写方法", "写实验", "写相关工作",
]


def detect_paper_intent(message: str, history: list = None) -> bool:
    """检测用户是否有写论文的意图（当前消息 + 历史上下文）"""
    msg_lower = message.lower()
    if any(kw in msg_lower for kw in PAPER_KEYWORDS):
        return True
    # 检查历史消息中是否有论文上下文
    if history:
        for h in history[-4:]:
            if any(kw in h.get("content", "").lower() for kw in PAPER_KEYWORDS):
                return True
    return False


def build_system_prompt(conversation_history: list, stage: str = None) -> str:
    """根据对话历史和阶段构建系统提示"""

    base = """你是 PaperAI，一个专业的学术论文写作助手。

## 你的职责
1. 帮助用户完成学术论文的各个阶段
2. 引导用户逐步完成论文写作
3. 根据用户需求推荐合适的 Agent 角色

## 工作方式
- 当用户想写论文时，先确认主题，然后推荐 4 条写作建议
- 每条建议对应一个写作阶段，让用户选择下一步
- 用户选择后，推荐合适的 Agent 角色来执行
- 引导用户逐步完成：选题 → 调研 → 大纲 → 撰写 → 审稿 → 润色

## 回复格式
- 使用 Markdown 格式
- 结构清晰，分点说明
- 适当使用 emoji 增加可读性"""

    if stage:
        stage_info = next((s for s in PAPER_STAGES if s["id"] == stage), None)
        if stage_info:
            base += f"\n\n## 当前阶段：{stage_info['label']}\n{stage_info['desc']}"

    return base


async def process_message(message: str, history: list, thinking: bool = False) -> dict:
    """
    处理用户消息，返回回复内容

    Returns:
        {
            "reply": "AI 回复文本",
            "thinking": "思考过程（如果开启）",
            "suggestions": [{"id": "...", "icon": "...", "title": "...", "desc": "..."}] 或 null,
            "agents": [{"role": "...", "icon": "...", "name": "...", "desc": "..."}] 或 null,
            "stage": "当前阶段"
        }
    """
    is_paper = detect_paper_intent(message, history)

    # 构建对话上下文
    context_lines = []
    for h in history[-10:]:  # 最近 10 条
        role = "用户" if h["role"] == "user" else "AI"
        context_lines.append(f"{role}: {h['content']}")

    context_lines.append(f"用户: {message}")
    prompt = "\n".join(context_lines)

    system_prompt = build_system_prompt(history)

    # 调用 LLM
    if thinking:
        reply, thinking_content = await call_llm_with_thinking(system_prompt, prompt)
    else:
        reply = await call_llm(system_prompt, prompt, thinking=False, use_cache=False)
        thinking_content = ""

    result = {
        "reply": reply,
        "thinking": thinking_content,
        "suggestions": None,
        "agents": None,
        "stage": None,
    }

    # 如果检测到论文意图，附加建议和 Agent 选项
    if is_paper:
        result["suggestions"] = [
            {"id": s["id"], "icon": "📋" if s["id"] == "topic" else
             "🔬" if s["id"] == "research" else
             "📐" if s["id"] == "outline" else
             "✍️" if s["id"] == "writing" else
             "📝" if s["id"] == "review" else "✨",
             "title": s["label"], "desc": s["desc"]}
            for s in PAPER_STAGES
        ]
        result["agents"] = AGENT_OPTIONS

    return result


async def process_message_stream(message: str, history: list, thinking: bool = False, doc_content: str = ""):
    """
    流式处理用户消息，逐 token yield 结果

    Yields:
        {"type": "thinking", "content": "..."}   — 思考过程（一次性）
        {"type": "token", "content": "..."}      — 回复文本（累积全文）
        {"type": "done", "reply": "...", "thinking": "...", "suggestions": [...], "agents": [...]}
    """
    is_paper = detect_paper_intent(message, history)

    context_lines = []
    for h in history[-10:]:
        role = "用户" if h["role"] == "user" else "AI"
        context_lines.append(f"{role}: {h['content']}")
    context_lines.append(f"用户: {message}")
    prompt = "\n".join(context_lines)
    system_prompt = build_system_prompt(history)

    # 如果有文档内容，注入上下文
    if doc_content:
        doc_excerpt = doc_content[:6000]  # 限制长度避免 token 超限
        system_prompt += f"\n\n## 当前文档内容\n用户正在编辑以下论文文档，请基于此内容回答问题：\n\n{doc_excerpt}"

    thinking_content = ""
    reply = ""

    if thinking:
        # 思考模式：先同步获取 thinking，再流式获取 reply
        reply_full, thinking_content = await call_llm_with_thinking(system_prompt, prompt)
        if thinking_content:
            yield {"type": "thinking", "content": thinking_content}
        # 用 agen 流式输出正式回复
        async for accumulated in call_llm_agen(system_prompt, prompt, use_cache=False):
            reply = accumulated
            yield {"type": "token", "content": reply}
    else:
        # 非思考模式：直接流式调用
        async for accumulated in call_llm_agen(system_prompt, prompt, use_cache=False):
            reply = accumulated
            yield {"type": "token", "content": reply}

    suggestions = None
    agents = None
    if is_paper:
        suggestions = [
            {"id": s["id"], "icon": "📋" if s["id"] == "topic" else
             "🔬" if s["id"] == "research" else
             "📐" if s["id"] == "outline" else
             "✍️" if s["id"] == "writing" else
             "📝" if s["id"] == "review" else "✨",
             "title": s["label"], "desc": s["desc"]}
            for s in PAPER_STAGES
        ]
        agents = AGENT_OPTIONS

    yield {
        "type": "done",
        "reply": reply,
        "thinking": thinking_content,
        "suggestions": suggestions,
        "agents": agents,
    }


# ===== 论文写作流（Agent → 文档面板） =====

PAPER_WRITE_KEYWORDS = [
    "写一篇", "撰写", "写论文", "写个论文", "写个", "写综述",
    "帮我写", "请帮我写", "帮写", "写一个", "请写",
    "写一篇关于", "关于", "论文", "综述", "paper",
]


async def _extract_topic_via_llm(message: str, history: list, doc_content: str = "") -> tuple[str, str]:
    """
    用 LLM 从对话上下文中提取论文主题和研究方向。
    返回 (topic, direction)。
    """
    context_lines = []
    for h in history[-6:]:
        role = "用户" if h["role"] == "user" else "AI"
        context_lines.append(f"{role}: {h['content']}")
    context_lines.append(f"用户: {message}")
    context_str = "\n".join(context_lines)

    # 如果有已有文档，提取标题作为参考
    existing_title = ""
    if doc_content:
        for line in doc_content.split("\n"):
            if line.startswith("# "):
                existing_title = line[2:].strip()
                break

    prompt = f"""根据以下对话，提取用户想要撰写的论文主题。用一行 JSON 返回。

对话：
{context_str}
{"已有文档标题：" + existing_title if existing_title else ""}

返回格式（严格 JSON，不要其他内容）：
{{"topic": "论文标题（10字以内概括）", "direction": "研究方向（一句话描述）"}}

注意：
- 如果已有文档标题，优先使用该标题作为 topic
- 如果用户没有明确主题，根据对话上下文和已有文档推断
- topic 应该是一个适合做论文标题的短语，不是用户的指令原文
- 例如用户说"帮我写一篇关于大语言模型的综述"，topic 应该是"大语言模型综述"
"""

    try:
        result = await call_llm("你是一个论文主题提取助手。只返回 JSON，不要其他内容。", prompt, use_cache=False)
        import json as _json
        data = _json.loads(result.strip().replace("```json", "").replace("```", ""))
        topic = data.get("topic", "").strip()
        direction = data.get("direction", "").strip()
        if topic:
            return topic, direction
    except Exception:
        pass

    # 降级：简单字符串提取
    topic = message.strip()
    for prefix in ["帮我写一篇关于", "写一篇关于", "帮我写", "写一篇", "关于", "写"]:
        if topic.startswith(prefix):
            topic = topic[len(prefix):]
            break
    for suffix in ["的论文", "的综述", "论文", "综述", "的学术论文"]:
        if topic.endswith(suffix):
            topic = topic[:-len(suffix)]
    topic = topic.strip()
    return (topic, "") if topic else ("学术论文研究", "")


# 章节名 → 写作指令映射
SECTION_TASKS = {
    "摘要": "请撰写论文的【摘要】部分。要求：150-300字，概括研究背景、方法、主要发现和结论。使用第三人称，语言精练。直接输出正文，不要标题。",
    "引言": "请撰写论文的【引言】章节。要求：包含研究背景与动机、研究问题与目标、论文结构概述。800-1500字，逻辑清晰，层层递进。直接输出正文，不要标题。",
    "相关工作": "请撰写论文的【相关工作】章节。要求：梳理相关领域文献，分类讨论现有方法，指出研究空白。800-1500字，引用规范。直接输出正文，不要标题。",
    "方法": "请撰写论文的【方法】章节。要求：详细描述技术方案、模型设计或算法流程，包含必要的公式和图表说明。800-1500字。直接输出正文，不要标题。",
    "实验": "请撰写论文的【实验】章节。要求：包含实验设置、数据集描述、评估指标、实验结果与分析。800-1500字，有数据支撑。直接输出正文，不要标题。",
    "结论": "请撰写论文的【结论】章节。要求：总结主要贡献，讨论局限性，展望未来工作。400-800字。直接输出正文，不要标题。",
}

SECTION_ALIASES = {
    "abstract": "摘要", "summary": "摘要", "概括": "摘要",
    "introduction": "引言", "intro": "引言", "背景": "引言",
    "related work": "相关工作", "related": "相关工作", "综述": "相关工作",
    "method": "方法", "methodology": "方法", "方案": "方法", "模型": "方法",
    "experiment": "实验", "experiments": "实验", "实验结果": "实验",
    "conclusion": "结论", "conclusions": "结论", "总结": "结论",
}

# 建议卡片触发词 → 这些是阶段引导，不是写章节命令
GUIDANCE_KEYWORDS = [
    "想进行", "选择", "进入", "开始", "进行",
    "文献调研", "选题确认", "大纲设计", "内容撰写", "审稿修改", "润色定稿",
    "收集和分析", "确定研究", "规划论文", "逐章撰写", "审查和改进", "语言优化",
]


def _detect_section(message: str) -> str | None:
    """检测用户是否明确要求写某个章节，返回章节名或 None"""
    msg = message.lower()

    # 先检查是否是引导/阶段请求（不是写章节命令）
    for kw in GUIDANCE_KEYWORDS:
        if kw in msg:
            return None  # 引导请求不触发写章节

    # 匹配"写XX"模式
    write_patterns = ["写", "撰写", "draft", "write"]
    for pattern in write_patterns:
        if pattern in msg:
            # 直接匹配章节名
            for sec in SECTION_TASKS:
                if sec in msg:
                    return sec
            # 别名匹配
            for alias, sec in SECTION_ALIASES.items():
                if alias in msg:
                    return sec

    # 如果消息只包含章节名（没有其他动词），也触发写
    for sec in SECTION_TASKS:
        if sec in msg and len(msg) < 20:
            return sec

    return None


def _detect_stage(message: str) -> str | None:
    """检测用户选择了哪个论文阶段，返回 stage id 或 None"""
    msg = message
    for stage in PAPER_STAGES:
        if stage["label"] in msg:
            return stage["id"]
    # 别名
    stage_aliases = {
        "选题": "topic", "确认选题": "topic", "定题": "topic",
        "调研": "research", "文献": "research", "文献调研": "research",
        "大纲": "outline", "结构": "outline", "设计大纲": "outline",
        "撰写": "writing", "写作": "writing", "写正文": "writing",
        "审稿": "review", "修改": "review", "审查": "review",
        "润色": "polish", "定稿": "polish", "优化": "polish",
    }
    for alias, stage_id in stage_aliases.items():
        if alias in msg:
            return stage_id
    return None


# ===== 文档结构解析 =====

def _parse_doc_sections(doc_content: str) -> list[dict]:
    """
    将文档按 ## 标题拆分成章节列表
    返回: [{"title": "摘要", "content": "...", "start": 10, "end": 50}, ...]
    """
    if not doc_content:
        return []

    lines = doc_content.split("\n")
    sections = []
    current_title = None
    current_start = 0
    current_lines = []

    for i, line in enumerate(lines):
        if line.startswith("## "):
            # 保存上一个章节
            if current_title is not None:
                sections.append({
                    "title": current_title,
                    "content": "\n".join(current_lines).strip(),
                    "start": current_start,
                    "end": i,
                })
            current_title = line[3:].strip()
            current_start = i
            current_lines = []
        elif line.startswith("# ") and not line.startswith("## "):
            # 跳过文档标题（# 开头的）
            continue
        else:
            current_lines.append(line)

    # 保存最后一个章节
    if current_title is not None:
        sections.append({
            "title": current_title,
            "content": "\n".join(current_lines).strip(),
            "start": current_start,
            "end": len(lines),
        })

    return sections


def _find_target_section(message: str, sections: list[dict]) -> dict | None:
    """从用户消息中找到目标章节"""
    msg = message.lower()

    # 1. 精确匹配章节标题
    sorted_sections = sorted(sections, key=lambda s: len(s["title"]), reverse=True)
    for sec in sorted_sections:
        if sec["title"].lower() in msg:
            return sec

    # 2. 别名匹配
    for alias, sec_name in SECTION_ALIASES.items():
        if alias in msg:
            for sec in sections:
                if sec["title"] == sec_name:
                    return sec

    # 3. 位置词匹配（第一个、最后一个、上一个、下一个）
    if "第一个" in msg or "首" in msg:
        return sections[0] if sections else None
    if "最后一个" in msg or "最后" in msg or "末" in msg:
        return sections[-1] if sections else None

    # 4. 关键词匹配（从内容推断）
    content_keywords = {
        "摘要": "摘要", "abstract": "摘要",
        "背景": "引言", "动机": "引言", "问题": "引言",
        "文献": "相关工作", "综述": "相关工作", "现有": "相关工作",
        "方案": "方法", "模型": "方法", "算法": "方法", "设计": "方法",
        "实验": "实验", "结果": "实验", "数据": "实验", "评估": "实验",
        "总结": "结论", "展望": "结论", "贡献": "结论",
    }
    for kw, sec_name in content_keywords.items():
        if kw in msg:
            for sec in sections:
                if sec["title"] == sec_name:
                    return sec

    return None


def _detect_modify_intent(message: str) -> bool:
    """检测用户是否想修改已有内容（而非写新内容）"""
    modify_keywords = [
        "改", "修改", "调整", "优化", "重写", "润色", "缩短", "扩展",
        "加", "增加", "补充", "删", "删除", "去掉", "精简",
        "把", "将", "让", "使", "帮",
        "更", "变", "换",
    ]
    msg = message.lower()
    return any(kw in msg for kw in modify_keywords)


async def process_section_modify_stream(message: str, history: list,
                                         doc_content: str, thinking: bool = False):
    """
    修改文档内容 — AI 思考 + 任务列表驱动

    Yields:
        {"type": "think", ...}        — 思考过程
        {"type": "tasklist", ...}      — 任务列表
        {"type": "task_update", ...}   — 任务状态
        {"type": "token", ...}         — 聊天回复
        {"type": "doc", ...}           — 文档内容
        {"type": "done", ...}          — 完成
    """
    # 解析文档结构
    sections = _parse_doc_sections(doc_content)
    if not sections:
        yield {"type": "token", "content": "❌ 文档还没有内容，无法修改。"}
        yield {"type": "done", "reply": "文档为空", "doc": doc_content, "title": "", "cards": []}
        return

    # 提取论文标题
    doc_title = ""
    for line in doc_content.split("\n"):
        if line.startswith("# "):
            doc_title = line[2:].strip()
            break

    # === 第一步：AI 思考，决定要修改哪些章节 ===
    sec_names = "、".join(s["title"] for s in sections)

    think_prompt = f"""你是 PaperAI 论文修改 Agent。用户要求修改文档。

论文标题：{doc_title}
文档章节：{sec_names}
用户要求：{message}

请分析需要修改哪些章节，并用以下格式回答：
【分析】用户想要什么
【计划】需要修改的章节列表（用逗号分隔章节名）"""

    think_result = ""
    yield {"type": "think", "round": 1, "content": "", "streaming": True}
    async for accumulated in call_llm_agen("你是 PaperAI 论文修改 Agent。", think_prompt, use_cache=False):
        think_result = accumulated
        yield {"type": "think", "round": 1, "content": accumulated, "streaming": True}
    yield {"type": "think", "round": 1, "content": think_result, "streaming": False}

    # 从思考结果中提取要修改的章节
    target_sections = []
    for sec in sections:
        if sec["title"] in think_result or sec["title"] in message:
            target_sections.append(sec)

    # 如果没有匹配到，检查是否是"全文"类请求
    full_doc_keywords = ["全文", "整篇", "所有", "全部", "每一", "各个"]
    if not target_sections and any(kw in message for kw in full_doc_keywords):
        target_sections = sections  # 修改所有章节

    # 如果还是没有，让 AI 从思考结果推断
    if not target_sections:
        for sec in sections:
            for kw in [sec["title"], sec["title"][:2]]:
                if kw in think_result:
                    target_sections.append(sec)
                    break

    # 最终兜底：修改所有章节
    if not target_sections:
        target_sections = sections

    # === 第二步：生成任务列表 ===
    tasks = [{"name": f"修改「{sec['title']}」", "status": "pending"} for sec in target_sections]
    yield {"type": "tasklist", "tasks": tasks}

    # === 第三步：逐个执行修改 ===
    current_doc = doc_content

    for i, (task, sec) in enumerate(zip(tasks, target_sections)):
        yield {"type": "task_update", "index": i, "status": "running"}

        # 每个任务的思考
        task_think_prompt = f"""你要修改「{sec['title']}」章节。

用户要求：{message}
当前章节内容（前200字）：{sec['content'][:200]}

简要说明你将如何修改这个章节（一句话）："""

        task_think = ""
        yield {"type": "think", "round": f"task-{i+1}", "content": "", "streaming": True}
        async for accumulated in call_llm_agen("你是 PaperAI 修改 Agent。", task_think_prompt, use_cache=False):
            task_think = accumulated
            yield {"type": "think", "round": f"task-{i+1}", "content": accumulated, "streaming": True}
        yield {"type": "think", "round": f"task-{i+1}", "content": task_think, "streaming": False}

        yield {"type": "token", "content": f"\n\n✍️ **任务 {i+1}/{len(tasks)}**：修改「{sec['title']}」\n\n"}

        # 构建修改上下文
        context_parts = [f"论文标题：{doc_title}"]
        # 相邻章节
        for j, s in enumerate(sections):
            if s["title"] == sec["title"]:
                if j > 0:
                    context_parts.append(f"上一章节「{sections[j-1]['title']}」结尾：\n{sections[j-1]['content'][-300:]}")
                if j < len(sections) - 1:
                    context_parts.append(f"下一章节「{sections[j+1]['title']}」开头：\n{sections[j+1]['content'][:300]}")
                break

        context = "\n\n".join(context_parts)

        task_prompt = f"""{context}

当前要修改的章节「{sec['title']}」原文：
---
{sec['content']}
---

用户修改要求：{message}

请直接输出修改后的章节正文（不要输出标题）："""

        # 流式改写
        new_content = ""
        lines = current_doc.split("\n")
        prefix_lines = lines[:sec["start"]]
        prefix = "\n".join(prefix_lines)
        suffix_lines = lines[sec["end"]:]

        async for accumulated in call_llm_agen(WRITER.system_prompt, task_prompt, use_cache=False):
            new_content = accumulated
            if len(accumulated.strip()) >= 20:
                updated_doc = prefix + f"\n## {sec['title']}\n\n{accumulated.strip()}\n"
                if suffix_lines:
                    updated_doc += "\n".join(suffix_lines)
                yield {"type": "doc", "content": updated_doc}

        # 清理
        cleaned = new_content.strip()
        cl_lines = cleaned.split("\n")
        while cl_lines and (cl_lines[0].strip().startswith("# ") or cl_lines[0].strip().startswith("## ") or not cl_lines[0].strip()):
            cl_lines.pop(0)
        cleaned = "\n".join(cl_lines).strip()

        if len(cleaned) >= 20:
            current_doc = prefix + f"\n## {sec['title']}\n\n{cleaned}\n"
            if suffix_lines:
                current_doc += "\n".join(suffix_lines)
            yield {"type": "doc", "content": current_doc}
            preview = cleaned[:100].replace("\n", " ")
            yield {"type": "token", "content": f"\n> {preview}...\n"}
            yield {"type": "task_update", "index": i, "status": "done", "summary": f"{len(cleaned)}字"}
        else:
            yield {"type": "token", "content": f"\n⚠️「{sec['title']}」修改内容不足，保持原文\n"}
            yield {"type": "task_update", "index": i, "status": "failed"}

    # === 第四步：第 2 轮思考 — 回顾修改效果 ===
    yield {"type": "token", "content": "\n\n🎉 修改完成！正在检查修改效果...\n\n"}

    modified_secs = "、".join(s["title"] for s in target_sections)
    review_prompt = f"""你是 PaperAI 论文审阅 Agent。刚完成了以下章节的修改，请检查效果。

论文标题：{doc_title}
修改的章节：{modified_secs}
用户修改要求：{message}

修改后的文档：
{current_doc[:4000]}

请检查：
1. 修改是否符合用户要求
2. 修改后的内容质量如何
3. 是否还有需要进一步改进的地方"""

    review_result = ""
    yield {"type": "think", "round": 2, "content": "", "streaming": True}
    async for accumulated in call_llm_agen("你是 PaperAI 论文审阅 Agent。", review_prompt, use_cache=False):
        review_result = accumulated
        yield {"type": "think", "round": 2, "content": accumulated, "streaming": True}
    yield {"type": "think", "round": 2, "content": review_result, "streaming": False}

    yield {"type": "token", "content": f"📋 **修改效果检查**\n\n{review_result}\n\n"}

    # === 第五步：完成 ===
    yield {"type": "token", "content": f"\n\n✅ 已完成 {len(tasks)} 个修改任务。"}

    # 提取论文标题
    doc_title = ""
    for line in current_doc.split("\n"):
        if line.startswith("# "):
            doc_title = line[2:].strip()
            break

    cards = _generate_cards(current_doc, target_sections[-1]["title"] if target_sections else None)

    yield {
        "type": "done",
        "reply": f"✅ 已完成 {len(tasks)} 个修改任务。",
        "doc": current_doc,
        "title": doc_title,
        "thinking": think_result,
        "cards": cards,
    }


def _generate_cards(doc_content: str, just_wrote: str = None) -> list[dict]:
    """
    根据文档当前状态，动态生成建议卡片
    """
    all_sections = ["摘要", "引言", "相关工作", "方法", "实验", "结论"]
    existing = set()
    if doc_content:
        for sec in _parse_doc_sections(doc_content):
            existing.add(sec["title"])

    remaining = [s for s in all_sections if s not in existing]
    cards = []

    if just_wrote:
        # 刚写完一个章节 → 建议写下一个 + 修改当前
        if remaining:
            next_sec = remaining[0]
            cards.append({
                "title": f"✍️ 写{next_sec}",
                "desc": f"继续撰写「{next_sec}」章节",
                "action": f"写{next_sec}",
            })
        cards.append({
            "title": f"📝 修改{just_wrote}",
            "desc": f"调整或优化「{just_wrote}」的内容",
            "action": f"修改{just_wrote}",
        })
        if len(remaining) > 1:
            cards.append({
                "title": "📋 查看大纲",
                "desc": "查看论文整体结构和进度",
                "action": "帮我看下大纲",
            })
    else:
        # 规划大纲后 → 建议写各个章节
        for sec in remaining[:4]:
            cards.append({
                "title": f"✍️ 写{sec}",
                "desc": f"撰写「{sec}」章节",
                "action": f"写{sec}",
            })

    return cards


async def process_paper_stream(message: str, history: list, thinking: bool = False, doc_content: str = ""):
    """
    论文写作 Agent — 任务列表驱动

    Yields:
        {"type": "think", "round": N, "content": "..."}   — 流式思考
        {"type": "tasklist", "tasks": [...]}               — 任务列表
        {"type": "task_update", "index": N, "status": "..."} — 任务状态更新
        {"type": "token", "content": "..."}                — 聊天回复
        {"type": "doc", "content": "..."}                  — 论文正文
        {"type": "done", "reply": "...", "doc": "...", "cards": [...]}
    """

    # === 提取主题 ===
    topic, direction = await _extract_topic_via_llm(message, history, doc_content)
    existing_title = topic
    if doc_content:
        for line in doc_content.split("\n"):
            if line.startswith("# "):
                existing_title = line[2:].strip()
                break

    current_doc = doc_content or ""

    # 构基础上下文
    base_context = f"论文标题：{existing_title}"
    if direction:
        base_context += f"\n研究方向：{direction}"
    if current_doc:
        base_context += f"\n\n当前文档内容：\n{current_doc[:5000]}"
    if history:
        recent = history[-5:]
        history_str = "\n".join(f"{'用户' if h['role']=='user' else 'AI'}: {h['content'][:200]}" for h in recent)
        base_context += f"\n\n对话历史：\n{history_str}"

    # === 第一步：AI 思考 + 生成任务列表 ===
    all_secs = ["摘要", "引言", "相关工作", "方法", "实验", "结论"]
    existing_secs = set()
    if current_doc:
        for s in _parse_doc_sections(current_doc):
            existing_secs.add(s["title"])
    remaining = [s for s in all_secs if s not in existing_secs]

    # 检测用户是否指定了特定章节
    specified_section = _detect_section(message)
    stage = _detect_stage(message)
    stage_to_section = {"research": "相关工作"}
    if not specified_section and stage:
        mapped = stage_to_section.get(stage)
        if mapped:
            specified_section = mapped

    # 生成任务列表
    if specified_section:
        tasks = [{"name": f"撰写「{specified_section}」", "status": "pending"}]
    elif remaining:
        tasks = [{"name": f"撰写「{sec}」", "status": "pending"} for sec in remaining]
    else:
        tasks = [{"name": "审阅文档", "status": "pending"}]

    # 流式思考：分析 + 展示任务列表
    think_prompt = f"""{base_context}

用户消息：{message}

你是 PaperAI 论文写作 Agent。请分析当前状态。

已写章节：{'、'.join(existing_secs) if existing_secs else '无'}
待写章节：{'、'.join(remaining) if remaining else '无'}
用户指定：{specified_section or '无（自动推断）'}

请用以下格式回答（简洁）：
【分析】当前状态一句话
【计划】将要执行的任务列表"""

    think_result = ""
    yield {"type": "think", "round": 1, "content": "", "streaming": True}
    async for accumulated in call_llm_agen("你是 PaperAI 论文写作决策 Agent。", think_prompt, use_cache=False):
        think_result = accumulated
        yield {"type": "think", "round": 1, "content": accumulated, "streaming": True}
    yield {"type": "think", "round": 1, "content": think_result, "streaming": False}

    # 发送任务列表
    yield {"type": "tasklist", "tasks": tasks}

    # === 第二步：逐个执行任务 ===
    for i, task in enumerate(tasks):
        task_name = task["name"]
        # 从任务名提取章节名
        target_section = None
        for sec in all_secs:
            if sec in task_name:
                target_section = sec
                break

        # 标记任务开始
        yield {"type": "task_update", "index": i, "status": "running"}

        # 每个任务的思考
        task_think_prompt = f"""你要撰写「{target_section or task_name}」章节。

论文标题：{existing_title}
已写章节：{'、'.join(existing_secs) if existing_secs else '无'}

简要说明你将如何写这个章节（一句话）："""

        task_think = ""
        yield {"type": "think", "round": f"task-{i+1}", "content": "", "streaming": True}
        async for accumulated in call_llm_agen("你是 PaperAI 写作 Agent。", task_think_prompt, use_cache=False):
            task_think = accumulated
            yield {"type": "think", "round": f"task-{i+1}", "content": accumulated, "streaming": True}
        yield {"type": "think", "round": f"task-{i+1}", "content": task_think, "streaming": False}

        if target_section and target_section in SECTION_TASKS:
            sec_task = SECTION_TASKS[target_section]

            yield {"type": "token", "content": f"\n\n✍️ **任务 {i+1}/{len(tasks)}**：{task_name}\n\n"}

            # 构建写作上下文
            write_context = f"论文标题：{existing_title}\n研究方向：{direction or existing_title}"
            if current_doc:
                body_lines = [l for l in current_doc.split("\n") if not l.startswith("# ")]
                body = "\n".join(body_lines).strip()
                if body:
                    write_context += f"\n\n已完成的章节（保持风格一致）：\n{body[:4000]}"

            task_prompt = f"""{write_context}

当前任务：{sec_task}

写作约束：
- 直接输出正文，不要输出章节标题
- 禁止编造文献、数据
- 保持与已有章节的风格一致"""

            doc_prefix = current_doc.rstrip() if current_doc else ""
            if not doc_prefix:
                doc_prefix = f"# {existing_title}"
            doc_prefix += f"\n\n## {target_section}\n\n"

            section_content = ""
            async for accumulated in call_llm_agen(WRITER.system_prompt, task_prompt, use_cache=False):
                section_content = accumulated
                if len(accumulated.strip()) >= 20:
                    yield {"type": "doc", "content": doc_prefix + accumulated}

            # 清理标题行
            cleaned = section_content.strip()
            lines = cleaned.split("\n")
            while lines and (lines[0].strip().startswith("# ") or lines[0].strip().startswith("## ") or not lines[0].strip()):
                lines.pop(0)
            cleaned = "\n".join(lines).strip()

            if len(cleaned) >= 20:
                current_doc = doc_prefix + cleaned + "\n"
                yield {"type": "doc", "content": current_doc}
                # 展示预览
                preview = cleaned[:100].replace("\n", " ")
                yield {"type": "token", "content": f"\n> {preview}...\n"}
                # 标记任务完成
                yield {"type": "task_update", "index": i, "status": "done", "summary": f"{len(cleaned)}字"}
            else:
                yield {"type": "token", "content": f"\n⚠️「{target_section}」生成内容不足\n"}
                yield {"type": "task_update", "index": i, "status": "failed"}

        elif "审阅" in task_name:
            # 审阅任务
            yield {"type": "token", "content": "\n\n📝 正在审阅文档...\n\n"}
            review_prompt = f"请审阅以下论文文档，指出问题和改进建议：\n\n{current_doc[:3000]}"
            review_result = await call_llm("你是 PaperAI 论文审阅 Agent。", review_prompt, use_cache=False)
            yield {"type": "token", "content": review_result}
            yield {"type": "task_update", "index": i, "status": "done", "summary": "审阅完成"}

    # === 第三步：回顾检查 ===
    existing_secs_final = set()
    if current_doc:
        for s in _parse_doc_sections(current_doc):
            existing_secs_final.add(s["title"])
    remaining_final = [s for s in all_secs if s not in existing_secs_final]

    if remaining_final:
        status_msg = f"\n\n📊 **进度**：已完成 {len(existing_secs_final)}/{len(all_secs)} 章节，待写：{'、'.join(remaining_final)}"
        yield {"type": "token", "content": status_msg}
    else:
        yield {"type": "token", "content": "\n\n🎉 **全部完成**！正在进行质量检查...\n\n"}

    # === 第 2 轮思考：全面回顾检查 ===
    existing_secs_str = "、".join(existing_secs_final) if existing_secs_final else "无"
    review_prompt = f"""你是 PaperAI 论文审阅 Agent。所有章节已撰写完毕，请进行全面检查。

论文标题：{existing_title}
已完成章节：{existing_secs_str}
用户原始需求：{message}

文档内容：
{current_doc[:4000]}

请从以下维度逐一检查：
1. 【结构】各章节是否齐全、顺序是否合理
2. 【内容】每章节的论证是否充分、数据是否可信
3. 【逻辑】章节之间是否有良好的衔接和过渡
4. 【问题】最需要改进的 1-2 个具体问题
5. 【建议】下一步可以做什么（润色/补充/修改某个章节）"""

    review_result = ""
    yield {"type": "think", "round": 2, "content": "", "streaming": True}
    async for accumulated in call_llm_agen("你是 PaperAI 论文审阅 Agent。", review_prompt, use_cache=False):
        review_result = accumulated
        yield {"type": "think", "round": 2, "content": accumulated, "streaming": True}
    yield {"type": "think", "round": 2, "content": review_result, "streaming": False}

    # 展示检查结果
    yield {"type": "token", "content": f"\n\n📋 **第 2 轮思考 — 质量检查报告**\n\n{review_result}\n\n"}

    last_section = target_section if 'target_section' in dir() and target_section else None
    cards = _generate_cards(current_doc, last_section)

    # 汇总
    task_summary = f"✅ 已完成 {len(tasks)} 个任务"
    if not remaining_final:
        task_summary += "，所有章节已就绪"

    yield {
        "type": "done",
        "reply": task_summary,
        "doc": current_doc,
        "title": existing_title,
        "thinking": think_result,
        "cards": cards,
    }


# ===== 文本改写流 =====

async def process_rewrite_stream(selected_text: str, instruction: str,
                                  doc_content: str = "", history: list = None):
    """
    改写选中文本，流式返回结果

    Yields:
        {"type": "token", "content": "..."}  — 改写过程（累积全文）
        {"type": "done", "content": "..."}   — 最终改写结果
    """
    # 构建上下文：论文主题 + 对话历史
    context_parts = []
    if doc_content:
        # 提取文档标题
        for line in doc_content.split("\n"):
            if line.startswith("# "):
                context_parts.append(f"论文标题：{line[2:].strip()}")
                break
        # 提取选中文本的前后文（帮助理解上下文）
        idx = doc_content.find(selected_text)
        if idx >= 0:
            before = doc_content[max(0, idx - 200):idx].strip()
            after = doc_content[idx + len(selected_text):idx + len(selected_text) + 200].strip()
            if before:
                context_parts.append(f"前文：...{before}")
            if after:
                context_parts.append(f"后文：{after}...")

    if history:
        recent = history[-3:]
        for h in recent:
            role = "用户" if h["role"] == "user" else "AI"
            context_parts.append(f"{role}: {h['content'][:200]}")

    context_str = "\n".join(context_parts) if context_parts else "无"

    system_prompt = """你是一位学术写作润色助手。用户在论文文档中选中了一段文字并给出改写要求。
请根据要求改写文本，保持学术规范，与论文整体风格一致。
要求：
- 只输出改写后的文本，不要加任何解释、前缀或后缀
- 保持原文的核心意思
- 根据用户要求调整风格、长度或内容
- 确保改写后的文本与上下文连贯"""

    user_prompt = f"""上下文信息：
{context_str}

选中的原文：
---
{selected_text}
---

改写要求：{instruction}

请直接输出改写后的文本："""

    result = ""
    async for accumulated in call_llm_agen(system_prompt, user_prompt, use_cache=False):
        result = accumulated
        yield {"type": "token", "content": result}

    yield {"type": "done", "content": result}
