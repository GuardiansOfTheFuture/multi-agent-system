import json
import logging
import time
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from .api import auth, agent, paper, flow, user, knowledge_graph, knowledge, chat
from .core.security import decode_token

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
log = logging.getLogger("paperai")

app = FastAPI(title="PaperAI Backend", version="1.0.0")


@app.middleware("http")
async def log_requests(request: Request, call_next):
    start = time.time()
    client_ip = request.client.host if request.client else "unknown"
    method = request.method
    path = request.url.path
    query = str(request.query_params) if request.query_params else ""

    # 跳过健康检查日志
    if path == "/api/paper/health":
        return await call_next(request)

    # 提取当前用户（不消耗 body）
    user_id = ""
    auth_header = request.headers.get("Authorization", "")
    if auth_header.startswith("Bearer "):
        payload = decode_token(auth_header[7:])
        if payload:
            user_id = payload.get("sub", "")

    log.info("→ %s %s%s user=%s client=%s",
             method, path, f"?{query}" if query else "", user_id or "-", client_ip)

    response = await call_next(request)
    ms = int((time.time() - start) * 1000)
    status = response.status_code
    icon = "✓" if 200 <= status < 400 else "✗"
    log.info("← %s %s %d %dms %s", icon, path, status, ms, user_id or "-")

    return response


app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(agent.router)
app.include_router(paper.router)
app.include_router(flow.router)
app.include_router(user.router)
app.include_router(knowledge_graph.router)
app.include_router(knowledge.router)
app.include_router(chat.router)


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    log.error("未处理异常 %s %s: %s", request.method, request.url.path, exc, exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"code": 500, "message": str(exc)[:200], "data": None},
    )


@app.on_event("startup")
async def startup():
    from .core.database import engine

    # 注册所有工具
    from .tools import tool_registry, WebSearchTool, ArxivSearchTool, DatabaseQueryTool
    from .tools import RAGSearchTool, ChartGeneratorTool, CalculatorTool, CitationFormatterTool
    tool_registry.register(WebSearchTool())
    tool_registry.register(ArxivSearchTool())
    tool_registry.register(DatabaseQueryTool())
    tool_registry.register(RAGSearchTool())
    tool_registry.register(ChartGeneratorTool())
    tool_registry.register(CalculatorTool())
    tool_registry.register(CitationFormatterTool())
    log.info("已注册 %d 个工具", len(tool_registry.list_tools()))

    log.info("========== PaperAI Python Backend 启动 ==========")
    log.info("数据库连接池已初始化")


@app.on_event("shutdown")
async def shutdown():
    from .core.database import engine
    from .core.redis_client import get_redis
    log.info("========== PaperAI Python Backend 关闭 ==========")
    await engine.dispose()
    r = await get_redis()
    if r:
        try:
            await r.aclose()
        except AttributeError:
            r.close()
    log.info("资源已释放")


@app.get("/")
async def root():
    return {"message": "PaperAI Python Backend", "docs": "/docs"}
