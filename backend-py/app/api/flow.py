import json
from fastapi import APIRouter, Depends
from sqlalchemy import select, desc
from sqlalchemy.ext.asyncio import AsyncSession
from ..core.database import get_db
from ..dependencies import get_current_user_id
from ..schemas.common import ApiResult
from ..models.flow_definition import FlowDefinition


def _flow_to_dict(f) -> dict:
    """FlowDefinition 转前端兼容格式"""
    d = {k: v for k, v in f.__dict__.items() if not k.startswith("_")}
    d["id"] = f"custom-{f.id}"
    d["dbId"] = f.id
    d["source"] = "custom"
    # graph_data: dict → JSON 字符串（前端做 JSON.parse）
    gd = d.pop("graph_data", {})
    d["graphData"] = json.dumps(gd, ensure_ascii=False) if isinstance(gd, dict) else (gd or "{}")
    d["isTemplate"] = d.pop("is_template", 0)
    d["createdAt"] = d.pop("created_at", None)
    d["updatedAt"] = d.pop("updated_at", None)
    return d

router = APIRouter(prefix="/api/flow", tags=["流程"])


@router.get("/list")
async def list_flows(db: AsyncSession = Depends(get_db), user_id: int = Depends(get_current_user_id)):
    result = []
    # 从数据库读取预设流程（is_template=1）
    preset_q = select(FlowDefinition).where(FlowDefinition.is_template == 1).order_by(FlowDefinition.id)
    preset_rows = (await db.execute(preset_q)).scalars().all()
    for f in preset_rows:
        d = _flow_to_dict(f)
        d["source"] = "preset"
        result.append(d)
    # 从数据库读取用户自定义流程
    custom_q = select(FlowDefinition).where(FlowDefinition.user_id == user_id, FlowDefinition.is_template == 0).order_by(desc(FlowDefinition.updated_at))
    custom_rows = (await db.execute(custom_q)).scalars().all()
    for f in custom_rows:
        result.append(_flow_to_dict(f))
    return ApiResult.success(result)


@router.get("/templates")
async def list_templates(db: AsyncSession = Depends(get_db)):
    q = select(FlowDefinition).where(FlowDefinition.is_template == 1)
    rows = (await db.execute(q)).scalars().all()
    return ApiResult.success([{k: v for k, v in f.__dict__.items() if not k.startswith("_")} for f in rows])


@router.get("/{flow_id}")
async def get_flow(flow_id: str, db: AsyncSession = Depends(get_db)):
    # 自定义流程（支持 "custom-1" 和纯数字 "1" 两种格式）
    db_id = None
    if flow_id.startswith("custom-"):
        try:
            db_id = int(flow_id[7:])
        except ValueError:
            pass
    elif flow_id.isdigit():
        db_id = int(flow_id)
    if db_id:
        result = await db.execute(select(FlowDefinition).where(FlowDefinition.id == db_id))
        f = result.scalar_one_or_none()
        if not f:
            return ApiResult.error(404, "流程不存在")
        return ApiResult.success(_flow_to_dict(f))
    return ApiResult.error(404, "流程不存在")


@router.post("")
async def create_flow(data: dict, db: AsyncSession = Depends(get_db),
                      user_id: int = Depends(get_current_user_id)):
    f = FlowDefinition(user_id=user_id, name=data.get("name", ""), description=data.get("description"),
                       category=data.get("category", "custom"), graph_data=data.get("graphData", {}),
                       is_template=data.get("isTemplate", 0))
    db.add(f)
    await db.commit()
    await db.refresh(f)
    return ApiResult.success(_flow_to_dict(f), "创建成功")


@router.put("/{flow_id}")
async def update_flow(flow_id: int, data: dict, db: AsyncSession = Depends(get_db),
                      user_id: int = Depends(get_current_user_id)):
    result = await db.execute(select(FlowDefinition).where(FlowDefinition.id == flow_id, FlowDefinition.user_id == user_id))
    f = result.scalar_one_or_none()
    if not f:
        return ApiResult.error(404, "流程不存在")
    for k in ["name", "description", "category", "is_template"]:
        if k in data:
            setattr(f, k, data[k])
    if "graphData" in data:
        f.graph_data = data["graphData"]
    await db.commit()
    return ApiResult.success(message="更新成功")


@router.delete("/{flow_id}")
async def delete_flow(flow_id: int, db: AsyncSession = Depends(get_db),
                      user_id: int = Depends(get_current_user_id)):
    result = await db.execute(select(FlowDefinition).where(FlowDefinition.id == flow_id, FlowDefinition.user_id == user_id))
    f = result.scalar_one_or_none()
    if not f:
        return ApiResult.error(404, "流程不存在")
    await db.delete(f)
    await db.commit()
    return ApiResult.success(message="删除成功")


@router.post("/{flow_id}/duplicate")
async def duplicate_flow(flow_id: int, db: AsyncSession = Depends(get_db),
                         user_id: int = Depends(get_current_user_id)):
    result = await db.execute(select(FlowDefinition).where(FlowDefinition.id == flow_id))
    src = result.scalar_one_or_none()
    if not src:
        return ApiResult.error(404, "流程不存在")
    copy = FlowDefinition(
        user_id=user_id, name=f"{src.name} (副本)", description=src.description,
        category="custom", graph_data=src.graph_data, is_template=0,
    )
    db.add(copy)
    await db.commit()
    await db.refresh(copy)
    return ApiResult.success(_flow_to_dict(copy), "复制成功")
