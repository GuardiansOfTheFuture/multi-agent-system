import logging
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from ..models.user import User
from ..core.security import hash_password, verify_password, create_token
from ..schemas.auth import LoginRequest, RegisterRequest, LoginVO, UserVO
from fastapi import HTTPException

log = logging.getLogger("paperai.user")


async def register(db: AsyncSession, req: RegisterRequest) -> LoginVO:
    existing = await db.execute(select(User).where(User.username == req.username))
    if existing.scalar_one_or_none():
        raise HTTPException(400, "用户名已存在")
    user = User(username=req.username, password=hash_password(req.password), email=req.email)
    db.add(user)
    await db.commit()
    await db.refresh(user)
    token = create_token(user.id, user.username)
    log.info("用户注册: id=%d username=%s", user.id, user.username)
    return LoginVO(token=token, user=UserVO.model_validate(user))


async def login(db: AsyncSession, req: LoginRequest) -> LoginVO:
    result = await db.execute(select(User).where(User.username == req.username))
    user = result.scalar_one_or_none()
    if not user or not verify_password(req.password, user.password):
        log.warning("登录失败: username=%s", req.username)
        raise HTTPException(400, "用户名或密码错误")
    token = create_token(user.id, user.username)
    log.info("用户登录: id=%d username=%s", user.id, user.username)
    return LoginVO(token=token, user=UserVO.model_validate(user))


async def get_user_by_id(db: AsyncSession, user_id: int) -> UserVO:
    result = await db.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    if not user:
        raise HTTPException(404, "用户不存在")
    return UserVO.model_validate(user)
