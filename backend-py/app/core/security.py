import jwt
import hashlib
import logging
from datetime import datetime, timedelta, timezone
from passlib.context import CryptContext
from ..config import get_settings

log = logging.getLogger("paperai.security")
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(password: str) -> str:
    return pwd_context.hash(password)


def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)


def create_token(user_id: int, username: str) -> str:
    settings = get_settings()
    payload = {
        "sub": str(user_id),
        "username": username,
        "iat": datetime.now(timezone.utc),
        "exp": datetime.now(timezone.utc) + timedelta(hours=settings.jwt_expire_hours),
    }
    token = jwt.encode(payload, settings.jwt_secret, algorithm="HS256")
    log.info("JWT 生成 userId=%d username=%s", user_id, username)
    return token


def decode_token(token: str) -> dict | None:
    try:
        settings = get_settings()
        return jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
    except jwt.PyJWTError as e:
        log.warning("JWT 解析失败: %s", e)
        return None


def get_user_id_from_token(token: str) -> int | None:
    payload = decode_token(token)
    if payload and "sub" in payload:
        return int(payload["sub"])
    return None


def md5_hex(data: str) -> str:
    return hashlib.md5(data.encode()).hexdigest()
