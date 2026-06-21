from fastapi import APIRouter, Depends, Request
from sqlalchemy.ext.asyncio import AsyncSession
from ..core.database import get_db
from ..core.security import md5_hex
from ..core.redis_client import blacklist_token
from ..dependencies import get_current_user_id
from ..schemas.auth import LoginRequest, RegisterRequest
from ..schemas.common import ApiResult
from ..services import user_service

router = APIRouter(prefix="/api/auth", tags=["认证"])


@router.post("/register")
async def register(req: RegisterRequest, db: AsyncSession = Depends(get_db)):
    vo = await user_service.register(db, req)
    return ApiResult.success({"token": vo.token, "user": vo.user.model_dump()}, "注册成功")


@router.post("/login")
async def login(req: LoginRequest, db: AsyncSession = Depends(get_db)):
    vo = await user_service.login(db, req)
    return ApiResult.success({"token": vo.token, "user": vo.user.model_dump()}, "登录成功")


@router.post("/logout")
async def logout(request: Request, user_id: int = Depends(get_current_user_id)):
    auth = request.headers.get("Authorization", "")
    if auth.startswith("Bearer "):
        token = auth[7:]
        await blacklist_token(md5_hex(token), 86400)
    return ApiResult.success(message="已登出")
