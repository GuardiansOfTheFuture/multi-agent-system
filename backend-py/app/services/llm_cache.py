import hashlib
import logging
from ..core.redis_client import cache_get, cache_set
from ..config import get_settings

log = logging.getLogger("paperai.cache")


def compute_key(system_prompt: str, user_message: str) -> str:
    raw = f"{system_prompt}|||{user_message}"
    return hashlib.md5(raw.encode()).hexdigest()


async def get_cached(system_prompt: str, user_message: str) -> str | None:
    settings = get_settings()
    if not settings.cache_llm_enabled:
        log.info("[Cache] 缓存已禁用，跳过查询")
        return None
    key = compute_key(system_prompt, user_message)
    log.info("[Cache] 查询缓存 key=%s", key[:16])
    val = await cache_get(f"llm:{key}")
    if val:
        log.info("[Cache] 命中缓存 key=%s length=%d", key[:16], len(val))
    else:
        log.info("[Cache] 未命中 key=%s", key[:16])
    return val


async def put_cached(system_prompt: str, user_message: str, response: str):
    settings = get_settings()
    if not settings.cache_llm_enabled:
        log.info("[Cache] 缓存已禁用，跳过写入")
        return
    key = compute_key(system_prompt, user_message)
    log.info("[Cache] 写入缓存 key=%s length=%d ttl=%ds", key[:16], len(response), settings.cache_llm_ttl)
    await cache_set(f"llm:{key}", response, settings.cache_llm_ttl)
