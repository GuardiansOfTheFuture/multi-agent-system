import json
import logging
from fastapi import APIRouter, Depends, Query, UploadFile, File
from sqlalchemy import select, desc
from sqlalchemy.ext.asyncio import AsyncSession
from ..core.database import get_db
from ..dependencies import get_current_user_id
from ..schemas.common import ApiResult
from ..models.knowledge_graph import KnowledgeGraph

log = logging.getLogger("paperai.kg")

router = APIRouter(prefix="/api/kg", tags=["知识图谱"])


@router.get("")
async def list_kgs(paper_id: int | None = Query(None),
                   db: AsyncSession = Depends(get_db),
                   user_id: int = Depends(get_current_user_id)):
    q = select(KnowledgeGraph).where(KnowledgeGraph.user_id == user_id)
    if paper_id:
        q = q.where(KnowledgeGraph.paper_id == paper_id)
    q = q.order_by(desc(KnowledgeGraph.updated_at))
    result = await db.execute(q)
    kgs = [{k: v for k, v in kg.__dict__.items() if not k.startswith("_")} for kg in result.scalars().all()]
    return ApiResult.success(kgs)


@router.get("/{kg_id}")
async def get_kg(kg_id: int, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(KnowledgeGraph).where(KnowledgeGraph.id == kg_id))
    kg = result.scalar_one_or_none()
    if not kg:
        return ApiResult.error(404, "知识图谱不存在")
    return ApiResult.success({k: v for k, v in kg.__dict__.items() if not k.startswith("_")})


@router.post("")
async def create_kg(data: dict, db: AsyncSession = Depends(get_db),
                    user_id: int = Depends(get_current_user_id)):
    kg = KnowledgeGraph(user_id=user_id, name=data.get("name", ""), description=data.get("description"),
                        paper_id=data.get("paperId"), graph_data=data.get("graphData", {"nodes": [], "edges": []}))
    db.add(kg)
    await db.commit()
    await db.refresh(kg)
    return ApiResult.success({k: v for k, v in kg.__dict__.items() if not k.startswith("_")}, "创建成功")


@router.put("/{kg_id}")
async def update_kg(kg_id: int, data: dict, db: AsyncSession = Depends(get_db),
                    user_id: int = Depends(get_current_user_id)):
    result = await db.execute(select(KnowledgeGraph).where(KnowledgeGraph.id == kg_id, KnowledgeGraph.user_id == user_id))
    kg = result.scalar_one_or_none()
    if not kg:
        return ApiResult.error(404, "知识图谱不存在")
    for k in ["name", "description", "paperId"]:
        if k in data:
            setattr(kg, k if k != "paperId" else "paper_id", data[k])
    if "graphData" in data:
        kg.graph_data = data["graphData"]
    await db.commit()
    return ApiResult.success(message="更新成功")


@router.delete("/{kg_id}")
async def delete_kg(kg_id: int, db: AsyncSession = Depends(get_db),
                    user_id: int = Depends(get_current_user_id)):
    result = await db.execute(select(KnowledgeGraph).where(KnowledgeGraph.id == kg_id, KnowledgeGraph.user_id == user_id))
    kg = result.scalar_one_or_none()
    if not kg:
        return ApiResult.error(404, "知识图谱不存在")
    await db.delete(kg)
    await db.commit()
    return ApiResult.success(message="删除成功")


@router.post("/{kg_id}/duplicate")
async def duplicate_kg(kg_id: int, db: AsyncSession = Depends(get_db),
                       user_id: int = Depends(get_current_user_id)):
    result = await db.execute(select(KnowledgeGraph).where(KnowledgeGraph.id == kg_id))
    src = result.scalar_one_or_none()
    if not src:
        return ApiResult.error(404, "知识图谱不存在")
    copy = KnowledgeGraph(user_id=user_id, name=f"{src.name} (副本)", description=src.description,
                          paper_id=src.paper_id, graph_data=src.graph_data)
    db.add(copy)
    await db.commit()
    await db.refresh(copy)
    return ApiResult.success({k: v for k, v in copy.__dict__.items() if not k.startswith("_")}, "复制成功")


# ===== AI 抽取 =====

ENTITY_TYPE_NAMES = {
    "concept": "概念", "paper": "论文", "author": "作者", "method": "方法",
    "dataset": "数据集", "topic": "主题", "problem": "问题", "finding": "发现",
}
RELATION_TYPE_NAMES = {
    "uses": "使用", "extends": "扩展", "part_of": "属于", "contradicts": "矛盾",
    "related_to": "相关", "proposes": "提出", "evaluates": "评估", "cites": "引用",
}


def _build_kg_prompt(text: str, topic: str, entity_types: list, relation_types: list, confidence: float) -> str:
    et_list = "\n".join(f"- {ENTITY_TYPE_NAMES.get(t, t)}" for t in entity_types) if entity_types else "（全部类型）"
    rt_list = "\n".join(f"- {RELATION_TYPE_NAMES.get(t, t)}" for t in relation_types) if relation_types else "（全部类型）"
    return f"""你是一位知识图谱构建专家。请从以下文本中抽取关键实体和它们之间的关系。

当前主题：{topic or '未知'}

文本内容：
{text[:100000]}

严格只抽取以下实体类型：
{et_list}
严格只抽取以下关系类型：
{rt_list}

请以严格 JSON 格式输出（不要 Markdown 代码块包裹）：

{{
  "entities": [
    {{"id":"e1","type":"method","name":"实体名称","desc":"简要描述","confidence":0.85}},
    {{"id":"e2","type":"concept","name":"实体名称","desc":"简要描述","confidence":0.72}}
  ],
  "relations": [
    {{"source":"e1","target":"e2","type":"uses","desc":"关系描述","confidence":0.80}}
  ]
}}

置信度说明：
- confidence 范围 0.0-1.0，表示你对该条抽取结果的把握
- 0.9+ = 文本明确提及，确定无疑
- 0.7-0.9 = 文本隐含表达，较高把握
- 0.5-0.7 = 合理推断，有一定不确定性
- 低于0.5的不应抽取
- 至少保留 {confidence:.1f} 以上置信度的结果

关键要求：
- 每个实体 name 简明扼要（不超过15字）
- 只抽取文本中明确提到的实体和关系，不要杜撰
- 输出纯 JSON，不含任何其他文字
- 尽力保证关系密度：每个实体尽可能与至少一个其他实体建立关系
- 寻找隐含关系：如果两个实体在同一语境中出现、共同解决问题、或属于同一主题，即使没有显式动词也要建立 related_to 关系
- 对核心概念和方法类实体，至少为其找到1-2个关联实体
- 如果确实没有任何关系线索，该实体可以保持孤立，但应尽量少"""


def _parse_kg_json(raw: str) -> str | None:
    if not raw or not raw.strip():
        return None
    json_str = raw.strip()

    # 去掉 markdown 代码块包裹
    if "```" in json_str:
        # 找到第一个 ``` 和最后一个 ```
        first = json_str.find("```")
        # 跳过 ```json 或 ``` 这一行
        first_nl = json_str.find("\n", first)
        if first_nl != -1:
            json_str = json_str[first_nl + 1:]
        last = json_str.rfind("```")
        if last != -1:
            json_str = json_str[:last]
        json_str = json_str.strip()

    # 尝试直接解析
    try:
        json.loads(json_str)
        return json_str
    except Exception:
        pass

    # 尝试找到第一个 { 和最后一个 } 之间的内容
    start = json_str.find("{")
    end = json_str.rfind("}")
    if start != -1 and end != -1 and end > start:
        candidate = json_str[start:end + 1]
        try:
            json.loads(candidate)
            return candidate
        except Exception:
            pass

    log.warning("KG 抽取 JSON 解析失败，原始长度=%d，前500字: %s", len(raw), raw[:500])
    log.warning("KG 抽取 JSON 解析失败，后200字: %s", raw[-200:] if len(raw) > 200 else raw)
    return None


@router.post("/extract")
async def extract_kg(data: dict, user_id: int = Depends(get_current_user_id)):
    from ..services.agent_executor import call_llm
    text = data.get("text", "")
    if not text.strip():
        return ApiResult.error(400, "请输入文本")
    topic = data.get("topic", "")
    entity_types = data.get("entityTypes", [])
    relation_types = data.get("relationTypes", [])
    confidence = data.get("confidence", 0.5)

    prompt = _build_kg_prompt(text, topic, entity_types, relation_types, confidence)
    # 抽取任务需要更长的输出
    raw = await call_llm("你是一位知识图谱构建专家。", prompt, max_tokens=32768)
    result = _parse_kg_json(raw)
    if not result:
        return ApiResult.error(500, "AI 抽取失败，请缩短文本后重试")
    return ApiResult.success(result)


@router.post("/extract-file")
async def extract_kg_from_file(file: UploadFile = File(...), user_id: int = Depends(get_current_user_id)):
    content = await file.read()
    filename = file.filename or "unknown"
    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""

    text = ""
    page_count = None

    try:
        if ext == "pdf":
            import io
            try:
                from pypdf import PdfReader
                reader = PdfReader(io.BytesIO(content))
                page_count = len(reader.pages)
                parts = []
                for page in reader.pages:
                    t = page.extract_text()
                    if t and t.strip():
                        parts.append(t.strip())
                text = "\n\n".join(parts)
                if not text.strip():
                    return ApiResult.error(400, "PDF 无法提取文本（可能是扫描件/图片PDF），请尝试粘贴文本内容")
            except ImportError:
                try:
                    import pdfplumber
                    with pdfplumber.open(io.BytesIO(content)) as pdf:
                        page_count = len(pdf.pages)
                        parts = []
                        for page in pdf.pages:
                            t = page.extract_text()
                            if t and t.strip():
                                parts.append(t.strip())
                        text = "\n\n".join(parts)
                except ImportError:
                    return ApiResult.error(500, "服务器未安装 PDF 解析库，请安装 pypdf 或 pdfplumber")
        elif ext in ("docx", "doc"):
            import io
            try:
                from docx import Document
                doc = Document(io.BytesIO(content))
                text = "\n".join(p.text for p in doc.paragraphs if p.text.strip())
            except ImportError:
                return ApiResult.error(500, "服务器未安装 python-docx")
        elif ext in ("md", "markdown", "txt"):
            text = content.decode("utf-8", errors="ignore")
        else:
            text = content.decode("utf-8", errors="ignore")
    except Exception as e:
        log.error("文件解析失败: %s", e)
        return ApiResult.error(500, f"文件解析失败: {str(e)[:100]}")

    if not text.strip():
        return ApiResult.error(400, "文件内容为空或无法解析")

    preview = text[:500] + ("..." if len(text) > 500 else "")
    return ApiResult.success({
        "filename": filename,
        "text": text,
        "preview": preview,
        "charCount": len(text),
        "pageCount": page_count,
    })
