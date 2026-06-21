import asyncio
import logging
import redis.asyncio as redis
from ..config import get_settings

log = logging.getLogger("paperai.redis")
_redis: redis.Redis | None = None
_lock = asyncio.Lock()


async def get_redis() -> redis.Redis | None:
    global _redis
    if _redis is not None:
        return _redis
    async with _lock:
        if _redis is not None:
            return _redis
        try:
            settings = get_settings()
            _redis = redis.from_url(settings.redis_url, decode_responses=True, socket_connect_timeout=3)
            await _redis.ping()
            log.info("Redis 连接建立: %s", settings.redis_url)
            return _redis
        except Exception as e:
            log.warning("Redis 连接失败，降级为无缓存模式: %s", e)
            return None


async def cache_get(key: str) -> str | None:
    r = await get_redis()
    if not r:
        log.debug("Redis 不可用，跳过 GET key=%s", key[:20])
        return None
    try:
        val = await r.get(f"paperai:{key}")
        if val:
            log.info("[Redis GET] key=%s 命中, length=%d", key[:20], len(val))
        else:
            log.info("[Redis GET] key=%s 未命中", key[:20])
        return val
    except Exception as e:
        log.warning("[Redis GET] 失败 key=%s: %s", key[:20], e)
        return None


async def cache_set(key: str, value: str, ttl: int = 600):
    r = await get_redis()
    if not r:
        log.debug("Redis 不可用，跳过 SET key=%s", key[:20])
        return
    try:
        await r.set(f"paperai:{key}", value, ex=ttl)
        log.info("[Redis SET] key=%s 写入成功, length=%d, ttl=%ds", key[:20], len(value), ttl)
    except Exception as e:
        log.warning("[Redis SET] 失败 key=%s: %s", key[:20], e)


async def cache_delete(key: str):
    r = await get_redis()
    if not r:
        return
    try:
        await r.delete(f"paperai:{key}")
        log.info("[Redis DELETE] key=%s", key[:20])
    except Exception as e:
        log.warning("[Redis DELETE] 失败 key=%s: %s", key[:20], e)


async def is_token_blacklisted(token_hash: str) -> bool:
    r = await get_redis()
    if not r:
        log.debug("[Auth] Redis 不可用，跳过黑名单检查")
        return False
    try:
        result = await r.exists(f"paperai:jwt:blacklist:{token_hash}") > 0
        log.info("[Auth] 黑名单检查 hash=%s result=%s", token_hash[:12], result)
        return result
    except Exception as e:
        log.warning("[Auth] 黑名单检查失败: %s", e)
        return False


async def blacklist_token(token_hash: str, ttl: int):
    r = await get_redis()
    if not r:
        log.warning("[Auth] Redis 不可用，无法写入黑名单")
        return
    try:
        await r.set(f"paperai:jwt:blacklist:{token_hash}", "1", ex=ttl)
        log.info("[Auth] Token 黑名单写入 hash=%s ttl=%ds", token_hash[:12], ttl)
    except Exception as e:
        log.warning("[Auth] 黑名单写入失败: %s", e)


async def is_stop_requested(paper_id: int) -> bool:
    r = await get_redis()
    if not r:
        return False
    try:
        val = await r.hget("paperai:running:tasks", str(paper_id))
        return val == "true"
    except Exception:
        return False


async def mark_running(paper_id: int):
    r = await get_redis()
    if not r:
        return
    try:
        await r.hset("paperai:running:tasks", str(paper_id), "false")
        log.info("任务标记运行中 paperId=%d", paper_id)
    except Exception:
        pass


async def mark_stop(paper_id: int):
    r = await get_redis()
    if not r:
        return
    try:
        await r.hset("paperai:running:tasks", str(paper_id), "true")
        log.info("任务标记停止 paperId=%d", paper_id)
    except Exception:
        pass


async def remove_running(paper_id: int):
    r = await get_redis()
    if not r:
        return
    try:
        await r.hdel("paperai:running:tasks", str(paper_id))
    except Exception:
        pass


async def publish_step(paper_id: int, step_data: dict):
    """发布步骤事件到 Redis stream"""
    import json
    r = await get_redis()
    if not r:
        log.warning("[SSE] Redis 不可用，无法发布步骤 paperId=%d", paper_id)
        return
    try:
        await r.rpush(f"paperai:sse:{paper_id}", json.dumps(step_data, ensure_ascii=False))
        await r.expire(f"paperai:sse:{paper_id}", 3600)
        log.info("[SSE] 步骤已发布到 Redis paperId=%d type=%s agentName=%s",
                 paper_id, step_data.get("type", ""), step_data.get("agentName", ""))
    except Exception as e:
        log.warning("[SSE] 发布步骤失败 paperId=%d: %s", paper_id, e)


async def get_steps(paper_id: int, last_index: int = 0) -> list:
    """获取从 last_index 开始的步骤事件"""
    import json
    r = await get_redis()
    if not r:
        return []
    try:
        items = await r.lrange(f"paperai:sse:{paper_id}", last_index, -1)
        if items:
            log.info("[SSE] 获取步骤 paperId=%d from=%d count=%d", paper_id, last_index, len(items))
        return [json.loads(item) for item in items]
    except Exception as e:
        log.warning("[SSE] 获取步骤失败 paperId=%d: %s", paper_id, e)
        return []


async def publish_stream(paper_id: int, agent_name: str, full_text: str):
    """发布流式文本到 Redis（覆盖式，只保留最新）"""
    import json
    r = await get_redis()
    if not r:
        return
    try:
        data = json.dumps({"type": "stream", "agentName": agent_name, "fullText": full_text}, ensure_ascii=False)
        await r.set(f"paperai:stream:{paper_id}", data, ex=300)
        log.debug("[Stream] 流式文本已更新 paperId=%d agent=%s length=%d", paper_id, agent_name, len(full_text))
    except Exception as e:
        log.warning("[Stream] 发布流式文本失败 paperId=%d: %s", paper_id, e)


async def get_stream(paper_id: int) -> dict | None:
    """获取当前流式文本"""
    import json
    r = await get_redis()
    if not r:
        return None
    try:
        val = await r.get(f"paperai:stream:{paper_id}")
        if val:
            log.debug("[Stream] 获取流式文本 paperId=%d length=%d", paper_id, len(val))
        return json.loads(val) if val else None
    except Exception as e:
        log.warning("[Stream] 获取流式文本失败 paperId=%d: %s", paper_id, e)
        return None


async def clear_stream(paper_id: int):
    r = await get_redis()
    if not r:
        return
    try:
        await r.delete(f"paperai:stream:{paper_id}")
        log.info("[Stream] 清除流式文本 paperId=%d", paper_id)
    except Exception as e:
        log.warning("[Stream] 清除失败 paperId=%d: %s", paper_id, e)


async def clear_steps(paper_id: int):
    r = await get_redis()
    if not r:
        return
    try:
        await r.delete(f"paperai:sse:{paper_id}")
        await r.delete(f"paperai:stream:{paper_id}")
        log.info("[SSE] 清除步骤和流式文本 paperId=%d", paper_id)
    except Exception as e:
        log.warning("[SSE] 清除失败 paperId=%d: %s", paper_id, e)
