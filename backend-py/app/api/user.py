from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from ..core.database import get_db
from ..dependencies import get_current_user_id
from ..schemas.common import ApiResult
from ..services import user_service

router = APIRouter(prefix="/api/user", tags=["用户"])


@router.get("/me")
async def get_me(db: AsyncSession = Depends(get_db), user_id: int = Depends(get_current_user_id)):
    user = await user_service.get_user_by_id(db, user_id)
    return ApiResult.success(user.model_dump())
