"""
LLM 调用封装 - 使用 LangChain OpenAI
"""
import logging
import time
from langchain_openai import ChatOpenAI
from langchain_core.messages import SystemMessage, HumanMessage
from ..config import get_settings
from .llm_cache import get_cached, put_cached

log = logging.getLogger("paperai.llm")

_llm_cache: dict[str, ChatOpenAI] = {}


def get_llm(model: str | None = None, temperature: float = 0.7, thinking: bool = False) -> ChatOpenAI:
    settings = get_settings()
    key = f"{model or settings.mimo_model}:{temperature}:t{int(thinking)}"
    if key not in _llm_cache:
        # 绕过系统代理
        import os
        os.environ.setdefault("NO_PROXY", "token-plan-cn.xiaomimimo.com,localhost,127.0.0.1")
        os.environ.setdefault("no_proxy", os.environ["NO_PROXY"])

        extra_body = {}
        if not thinking:
            extra_body["thinking"] = {"type": "disabled"}

        _llm_cache[key] = ChatOpenAI(
            model=model or settings.mimo_model,
            api_key=settings.mimo_api_key,
            base_url=settings.mimo_base_url,
            temperature=temperature,
            max_tokens=131072,
            timeout=180,
            extra_body=extra_body if extra_body else None,
        )
    return _llm_cache[key]


async def call_llm(system_prompt: str, user_message: str, model: str | None = None,
                   temperature: float = 0.7, max_tokens: int = 131072, thinking: bool = False,
                   use_cache: bool = True) -> str:
    log.info("[LLM] call_llm 开始 model=%s thinking=%s system_prompt=%d字 message=%d字",
             model or get_settings().mimo_model, thinking, len(system_prompt), len(user_message))

    if use_cache:
        cached = await get_cached(system_prompt, user_message)
        if cached:
            log.info("[LLM] 返回缓存结果 length=%d", len(cached))
            return cached

    llm = get_llm(model, temperature, thinking)
    log.info("[LLM] 调用 API model=%s thinking=%s", model or get_settings().mimo_model, thinking)
    t = time.time()

    messages = [SystemMessage(content=system_prompt), HumanMessage(content=user_message)]
    resp = await llm.ainvoke(messages)
    ms = int((time.time() - t) * 1000)
    result = resp.content or ""
    log.info("[LLM] API 响应完成 length=%d cost=%dms", len(result), ms)

    await put_cached(system_prompt, user_message, result)
    return result


async def call_llm_stream(system_prompt: str, user_message: str,
                          on_token=None, model: str | None = None,
                          temperature: float = 0.7) -> str:
    cached = await get_cached(system_prompt, user_message)
    if cached:
        if on_token:
            on_token(cached)
        return cached

    llm = get_llm(model, temperature)
    log.info("→ LLM 流式调用 model=%s", model or get_settings().mimo_model)
    t = time.time()

    messages = [SystemMessage(content=system_prompt), HumanMessage(content=user_message)]
    full = ""
    try:
        async for chunk in llm.astream(messages):
            if chunk.content:
                full += chunk.content
                if on_token:
                    on_token(full)
        ms = int((time.time() - t) * 1000)
        log.info("← LLM 流式响应完成 length=%d cost=%dms", len(full), ms)
        await put_cached(system_prompt, user_message, full)
        return full
    except Exception as e:
        log.warning("流式调用失败，降级同步: %s", e)
        return await call_llm(system_prompt, user_message, model, temperature)


async def call_llm_agen(system_prompt: str, user_message: str,
                        model: str | None = None, temperature: float = 0.7,
                        use_cache: bool = True):
    """
    流式 LLM 调用 — async generator，逐 chunk yield 累积文本
    """
    if use_cache:
        cached = await get_cached(system_prompt, user_message)
        if cached:
            yield cached
            return

    llm = get_llm(model, temperature)
    log.info("→ LLM agen 流式调用 model=%s", model or get_settings().mimo_model)
    t = time.time()

    messages = [SystemMessage(content=system_prompt), HumanMessage(content=user_message)]
    full = ""
    try:
        async for chunk in llm.astream(messages):
            if chunk.content:
                full += chunk.content
                yield full
        ms = int((time.time() - t) * 1000)
        log.info("← LLM agen 流式完成 length=%d cost=%dms", len(full), ms)
        await put_cached(system_prompt, user_message, full)
    except Exception as e:
        log.warning("agen 流式失败，降级同步: %s", e)
        result = await call_llm(system_prompt, user_message, model, temperature)
        yield result


async def call_light_llm(system_prompt: str, user_message: str) -> str:
    settings = get_settings()
    return await call_llm(system_prompt, user_message, model=settings.mimo_light_model, temperature=0.3)


async def call_llm_with_thinking(system_prompt: str, user_message: str, model: str | None = None,
                                  temperature: float = 0.7) -> tuple[str, str]:
    """
    带思考模式的 LLM 调用，返回 (reply, thinking_content)
    """
    log.info("[LLM] call_llm_with_thinking 开始 model=%s", model or get_settings().mimo_model)

    llm = get_llm(model, temperature, thinking=True)
    messages = [SystemMessage(content=system_prompt), HumanMessage(content=user_message)]

    t = time.time()
    resp = await llm.ainvoke(messages)
    ms = int((time.time() - t) * 1000)

    # 提取思考内容和回复
    thinking_content = ""
    reply = resp.content or ""

    # MiMo 返回的 thinking 内容可能在 additional_kwargs 中
    if hasattr(resp, 'additional_kwargs') and resp.additional_kwargs:
        thinking_content = resp.additional_kwargs.get("reasoning_content", "")

    # 如果有 usage 信息，记录 token 消耗
    if hasattr(resp, 'usage_metadata') and resp.usage_metadata:
        usage = resp.usage_metadata
        log.info("[LLM] thinking 模式完成 input=%d output=%d thinking=%d cost=%dms",
                 usage.get('input_tokens', 0), usage.get('output_tokens', 0),
                 usage.get('reasoning_tokens', 0), ms)
    else:
        log.info("[LLM] thinking 模式完成 length=%d cost=%dms", len(reply), ms)

    return reply, thinking_content


async def call_llm_with_tools(
    system_prompt: str,
    user_message: str,
    tools: list[dict] = None,
    tool_handler=None,
    model: str | None = None,
    temperature: float = 0.7,
    max_iterations: int = 5,
) -> str:
    """
    带工具调用的 LLM 调用
    - tools: 工具 JSON Schema 列表（OpenAI function calling 格式）
    - tool_handler: 异步函数 async (tool_name, tool_args) -> str
    - max_iterations: 最大工具调用轮数
    """
    from langchain_core.messages import AIMessage, ToolMessage

    log.info("[LLM] call_llm_with_tools 开始 model=%s tools=%d",
             model or get_settings().mimo_model, len(tools) if tools else 0)

    llm = get_llm(model, temperature)

    # 如果有工具，绑定到 LLM
    if tools:
        llm = llm.bind_tools(tools)

    messages = [SystemMessage(content=system_prompt), HumanMessage(content=user_message)]

    for iteration in range(max_iterations):
        log.info("[LLM] 工具调用轮次 %d/%d", iteration + 1, max_iterations)
        t = time.time()

        resp = await llm.ainvoke(messages)
        ms = int((time.time() - t) * 1000)

        # 检查是否有工具调用
        if not hasattr(resp, 'tool_calls') or not resp.tool_calls:
            # 没有工具调用，返回结果
            result = resp.content or ""
            log.info("[LLM] 工具调用完成（无工具调用）length=%d cost=%dms", len(result), ms)
            return result

        # 有工具调用，执行工具
        messages.append(resp)  # 添加 AI 的响应（包含 tool_calls）

        for tool_call in resp.tool_calls:
            tool_name = tool_call["name"]
            tool_args = tool_call["args"]
            tool_id = tool_call["id"]

            log.info("[LLM] 调用工具: %s args=%s", tool_name, list(tool_args.keys()))

            # 执行工具
            if tool_handler:
                tool_result = await tool_handler(tool_name, tool_args)
            else:
                tool_result = f"工具 {tool_name} 未配置处理器"

            log.info("[LLM] 工具 %s 返回 %d 字", tool_name, len(tool_result) if tool_result else 0)

            # 添加工具结果到消息
            messages.append(ToolMessage(
                content=tool_result,
                tool_call_id=tool_id,
            ))

    # 达到最大轮数，返回最后的响应
    log.warning("[LLM] 达到最大工具调用轮数 %d", max_iterations)
    resp = await llm.ainvoke(messages)
    return resp.content or ""
