import logging
from fastapi import Depends, HTTPException, Request
from sqlalchemy.ext.asyncio import AsyncSession
from .core.database import get_db
from .core.security import get_user_id_from_token, md5_hex
from .core.redis_client import is_token_blacklisted

log = logging.getLogger("paperai.auth")


async def get_current_user_id(request: Request) -> int:
    token = None
    auth_header = request.headers.get("Authorization")
    if auth_header and auth_header.startswith("Bearer "):
        token = auth_header[7:]
    if not token:
        token = request.query_params.get("token")
    if not token:
        raise HTTPException(status_code=401, detail="未提供认证令牌")
    payload = get_user_id_from_token(token)
    if payload is None:
        raise HTTPException(status_code=401, detail="令牌无效或已过期")
    if await is_token_blacklisted(md5_hex(token)):
        log.warning("令牌已失效 userId=%s", payload)
        raise HTTPException(status_code=401, detail="令牌已失效")
    return payload
