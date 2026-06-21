from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from ..core.database import get_db
from ..dependencies import get_current_user_id
from ..schemas.common import ApiResult
from ..services.agent_definitions import list_agents, get_agent, AGENTS
from ..services.agent_executor import call_llm
from ..models.custom_agent import CustomAgent
from sqlalchemy import select

router = APIRouter(prefix="/api/agent", tags=["Agent"])

MODEL_REGISTRY = [
    {"name": "mimo-v2.5-pro", "displayName": "MiMo V2.5 Pro", "provider": "xiaomi", "contextWindow": 131072, "description": "小米推理大模型"},
]


@router.get("/list")
async def agent_list():
    return ApiResult.success(list_agents())


@router.get("/models")
async def models():
    return ApiResult.success(MODEL_REGISTRY)


@router.post("/{agent_name}/chat")
async def chat(agent_name: str, topic: str = "test", message: str = "hello",
               model: str | None = None):
    agent = get_agent(agent_name)
    result = await call_llm(agent.system_prompt, message, model=model)
    return ApiResult.success(result)


@router.get("/custom")
async def list_custom(db: AsyncSession = Depends(get_db), user_id: int = Depends(get_current_user_id)):
    result = await db.execute(select(CustomAgent).where(CustomAgent.user_id == user_id))
    agents = [dict(a.__dict__) for a in result.scalars().all()]
    for a in agents:
        a.pop("_sa_instance_state", None)
    return ApiResult.success(agents)


@router.post("/custom")
async def create_custom(data: dict, db: AsyncSession = Depends(get_db),
                        user_id: int = Depends(get_current_user_id)):
    agent = CustomAgent(user_id=user_id, **{k: v for k, v in data.items() if hasattr(CustomAgent, k)})
    db.add(agent)
    await db.commit()
    await db.refresh(agent)
    d = {k: v for k, v in agent.__dict__.items() if not k.startswith("_")}
    return ApiResult.success(d, "创建成功")


@router.put("/custom/{agent_id}")
async def update_custom(agent_id: int, data: dict, db: AsyncSession = Depends(get_db),
                        user_id: int = Depends(get_current_user_id)):
    result = await db.execute(select(CustomAgent).where(CustomAgent.id == agent_id, CustomAgent.user_id == user_id))
    agent = result.scalar_one_or_none()
    if not agent:
        return ApiResult.error(404, "Agent不存在")
    for k, v in data.items():
        if hasattr(agent, k) and v is not None:
            setattr(agent, k, v)
    await db.commit()
    return ApiResult.success(message="更新成功")


@router.delete("/custom/{agent_id}")
async def delete_custom(agent_id: int, db: AsyncSession = Depends(get_db),
                        user_id: int = Depends(get_current_user_id)):
    result = await db.execute(select(CustomAgent).where(CustomAgent.id == agent_id, CustomAgent.user_id == user_id))
    agent = result.scalar_one_or_none()
    if not agent:
        return ApiResult.error(404, "Agent不存在")
    await db.delete(agent)
    await db.commit()
    return ApiResult.success(message="删除成功")
