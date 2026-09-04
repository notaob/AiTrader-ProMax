"""Stage 1 单测：Langfuse 可观测封装（无 key 静默降级）。

注意：本地 .env 可能已配置真实 LANGFUSE key（验收用），因此用 autouse fixture
强制清空 key 与 handler 缓存，保证「降级路径」不依赖外部环境。
"""

import pytest

from app.config import config
from app.observability import langfuse


@pytest.fixture(autouse=True)
def force_no_langfuse_keys():
    """测试期间屏蔽 LANGFUSE key，隔离 handler 全局缓存。"""
    saved = (config.LANGFUSE_PUBLIC_KEY, config.LANGFUSE_SECRET_KEY, config.LANGFUSE_HOST)
    langfuse._callback_handler = None
    config.LANGFUSE_PUBLIC_KEY = ""
    config.LANGFUSE_SECRET_KEY = ""
    yield
    langfuse._callback_handler = None
    config.LANGFUSE_PUBLIC_KEY, config.LANGFUSE_SECRET_KEY, config.LANGFUSE_HOST = saved


def test_langfuse_disabled_without_keys():
    # 未配置 LANGFUSE key → 不启用、不抛错
    assert langfuse.langfuse_enabled() is False
    assert langfuse.get_langfuse_handler() is None


def test_make_run_config_without_keys_returns_checkpoint_only():
    cfg = langfuse.make_run_config(session_id="s1", user_id="u1", mode="chat")
    assert "callbacks" not in cfg  # 未配置 → 不注入观测回调
    assert cfg["configurable"]["thread_id"].startswith("s1:")


def test_make_run_config_thread_unique_per_run():
    a = langfuse.make_run_config(session_id="s1")
    b = langfuse.make_run_config(session_id="s1")
    assert a["configurable"]["thread_id"] != b["configurable"]["thread_id"]
