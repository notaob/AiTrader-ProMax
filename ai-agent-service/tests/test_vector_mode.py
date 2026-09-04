"""向量存储模式决策测试。

场景矩阵（不发外网、不依赖真实 Redis，用抛异常的假 Redis 类模拟）：
- REDIS_VECTOR_MODE=memory       → 永不触碰 Redis，进程内可用
- REDIS_VECTOR_MODE=auto(默认)   → Redis 连不上/无 RediSearch → 静默降级内存，可正常读写
- REDIS_VECTOR_MODE=redis        → 真 HNSW 不可用即启动报错（杜绝"带病假索引"）
- 非法值 → 按 auto 处理
"""

import pytest
import redis as redis_pkg

from app.config import config
from app.rag.vector_store import Document, VectorStore


class _RaisingRedis:
    """任何连接尝试都抛异常，模拟「没有可用 Redis」的环境。"""

    def __init__(self, *args, **kwargs):
        raise ConnectionError("simulated redis unavailable")


@pytest.fixture(autouse=True)
def _no_real_redis(monkeypatch):
    monkeypatch.setattr(redis_pkg, "Redis", _RaisingRedis)


def _make_doc():
    return Document(
        id="1",
        content="我喜欢比特币，偏好现货长线",
        source="memory",
        vector=[0.1, 0.2, 0.3],
        metadata={"user_id": 1, "memory_id": 1, "memory_type": "preference"},
    )


def test_forced_memory_never_touches_redis_and_works(monkeypatch):
    monkeypatch.setattr(config, "REDIS_VECTOR_MODE", "memory")
    store = VectorStore()
    assert store.mode == "memory"
    assert store.add_documents([_make_doc()]) == 1
    stats = store.get_stats()
    assert stats["document_count"] == 1
    assert stats["mode"] == "memory"
    assert stats["engine"] == "memory_numpy"


def test_auto_redis_unavailable_falls_back_to_memory(monkeypatch):
    monkeypatch.setattr(config, "REDIS_VECTOR_MODE", "auto")
    store = VectorStore()
    assert store.mode == "memory"
    store.add_documents([_make_doc()])
    assert store.get_stats()["document_count"] == 1


def test_forced_redis_unavailable_raises(monkeypatch):
    monkeypatch.setattr(config, "REDIS_VECTOR_MODE", "redis")
    with pytest.raises(RuntimeError, match="REDIS_VECTOR_MODE=redis"):
        VectorStore()


def test_invalid_mode_treated_as_auto(monkeypatch):
    monkeypatch.setattr(config, "REDIS_VECTOR_MODE", "hack")
    store = VectorStore()
    assert store.mode == "memory"
