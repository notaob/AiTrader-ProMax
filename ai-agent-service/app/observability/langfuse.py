"""Langfuse 可观测封装：缺 key / 初始化失败时静默降级，不影响主链路。

兼容 langfuse SDK 版本差异：
- v2/v3：``langfuse.callback.CallbackHandler(public_key, secret_key, host)``
- v4+  ：``langfuse.callback`` 移除，改用 ``langfuse.langchain.CallbackHandler``，
  它依赖全局默认 client（先 ``Langfuse(...)`` 配置一次）。

用法（在 LangGraph invoke / astream 时传入 config）:
    config = make_run_config(session_id=..., user_id=..., mode=...)
    result = await trading_graph.ainvoke(inputs, config=config)
"""

import importlib.metadata
import uuid

from app.config import config

_callback_handler = None


def langfuse_enabled() -> bool:
    """是否配置了 Langfuse（public + secret key 均非空）。"""
    return bool(config.LANGFUSE_PUBLIC_KEY and config.LANGFUSE_SECRET_KEY)


def _langfuse_major() -> int | None:
    """已安装 langfuse SDK 主版本号；未安装返回 None。"""
    try:
        return int(importlib.metadata.version("langfuse").split(".")[0])
    except Exception:  # 未安装 / 元数据异常
        return None


def get_langfuse_handler():
    """惰性返回 Langfuse CallbackHandler；未配置或初始化失败返回 None。"""
    global _callback_handler
    if not langfuse_enabled():
        return None
    if _callback_handler is not None:
        return _callback_handler
    try:
        if _langfuse_major() is not None and _langfuse_major() >= 4:
            # langfuse v4：先配置全局默认 client，再建 LangChain callback handler
            from langfuse import Langfuse
            from langfuse.langchain import CallbackHandler

            Langfuse(
                public_key=config.LANGFUSE_PUBLIC_KEY,
                secret_key=config.LANGFUSE_SECRET_KEY,
                host=config.LANGFUSE_HOST,
            )
            _callback_handler = CallbackHandler(public_key=config.LANGFUSE_PUBLIC_KEY)
        else:
            # langfuse v2/v3：callback.CallbackHandler 直接接收 key/host
            from langfuse.callback import CallbackHandler

            _callback_handler = CallbackHandler(
                public_key=config.LANGFUSE_PUBLIC_KEY,
                secret_key=config.LANGFUSE_SECRET_KEY,
                host=config.LANGFUSE_HOST,
            )
        print(f"[langfuse] callback handler ready, host={config.LANGFUSE_HOST}")
    except Exception as e:  # 依赖缺失 / 配置非法 → 降级为不观测
        print(f"[langfuse] callback disabled: {e}")
        _callback_handler = None
    return _callback_handler


def make_run_config(*, session_id: str = "default", user_id: str = "", mode: str = "chat") -> dict:
    """构造一次 LangGraph run 的 config（checkpointer + 可观测 callbacks）。

    thread_id 使用 `session_id:毫秒时间戳` 保证每次 run 独立 checkpoint：
    当前会话历史由 Java 端全量回传（每次调用重建 messages），若 thread 恒定复用，
    operator.add 会把上一轮消息再叠加一遍。待 Python 端接管会话状态后，
    可改为稳定 thread_id 并启用 interrupt（HITL），见 AI_PHASE3 Stage 1/3。
    """
    thread_id = f"{session_id or 'default'}:{uuid.uuid4().hex[:8]}"
    run_config = {"configurable": {"thread_id": thread_id}}

    handler = get_langfuse_handler()
    if handler is not None:
        run_config["callbacks"] = [handler]
        run_config["metadata"] = {
            "user_id": user_id,
            "session_id": session_id,
            "mode": mode,
            "thread_id": thread_id,
        }
        run_config["user_id"] = user_id
        run_config["session_id"] = session_id
    return run_config
