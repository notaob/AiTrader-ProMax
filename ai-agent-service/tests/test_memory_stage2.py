"""Stage 2 单测：语义记忆 recall/save/delete 与摘要模块（离线，不请求外部服务）。

隔离策略：
- memory_vector_store 全局单例替换为纯内存的 MemoryVectorStore，保证用例间互不污染；
- embedding_service 替换为确定性 hash 向量（同文本必同向量），验证管线与过滤逻辑。
"""

import hashlib
import math

import pytest

import app.memory.memory_service as memory_service
from app.memory.conversation_summarizer import _build_transcript, summarize_conversation
from app.memory.memory_service import delete_memories, recall_memories, save_memories
from app.rag.embedding import embedding_service
from app.rag.vector_store import MemoryVectorStore


class StubEmbedder:
    """确定性向量：同一文本得到同一向量（用于验证检索/过滤管线，不验证真实语义）。"""

    DIM = 16

    def embed(self, text: str) -> list[float]:
        return self._vector(text)

    def embed_batch(self, texts: list[str]) -> list[list[float]]:
        return [self._vector(t) for t in texts]

    @staticmethod
    def _vector(text: str) -> list[float]:
        vec = [0.0] * StubEmbedder.DIM
        for ch in text:
            h = hashlib.md5(ch.encode("utf-8")).digest()
            vec[h[0] % StubEmbedder.DIM] += 1.0 if (h[1] % 2 == 0) else -1.0
        norm = math.sqrt(sum(v * v for v in vec))
        if norm == 0:
            return vec
        return [v / norm for v in vec]


@pytest.fixture(autouse=True)
def isolated_env(monkeypatch):
    """每个用例使用独立内存向量库 + 假 embedding。"""
    stub = StubEmbedder()
    monkeypatch.setattr(embedding_service, "embed", stub.embed)
    monkeypatch.setattr(embedding_service, "embed_batch", stub.embed_batch)
    monkeypatch.setattr(memory_service, "memory_vector_store", MemoryVectorStore())
    yield


def _save_sample(user_id=1):
    saved = save_memories([
        {"user_id": user_id, "memory_id": 101, "content": "用户偏好关注比特币，喜欢做现货长线", "memory_type": "preference"},
        {"user_id": user_id, "memory_id": 102, "content": "用户目标是一年内收益翻倍", "memory_type": "goal"},
        {"user_id": user_id, "memory_id": 103, "content": "用户要求单笔亏损不超过本金5%，严格止损", "memory_type": "constraint"},
    ])
    assert saved == 3
    return saved


def test_save_recall_roundtrip():
    _save_sample(1)
    result = recall_memories(1, "用户偏好关注比特币，喜欢做现货长线", top_k=5)
    assert result, "应能召回已保存记忆"
    top = result[0]
    assert top["memory_id"] == 101
    assert top["memory_type"] == "preference"
    assert top["content"] == "用户偏好关注比特币，喜欢做现货长线"
    assert top["similarity"] > 0.99  # 相同文本 → 余弦距离≈0


def test_recall_user_isolation():
    _save_sample(1)
    save_memories([{"user_id": 2, "memory_id": 201, "content": "用户偏好关注比特币，喜欢做现货长线", "memory_type": "preference"}])
    result = recall_memories(1, "用户偏好关注比特币，喜欢做现货长线", top_k=5)
    ids = {m["memory_id"] for m in result}
    assert 101 in ids, f"应召回 user1 自身记忆: {ids}"
    assert not ids & {201}, f"user1 不应召回 user2 的记忆: {ids}"
    result2 = recall_memories(2, "用户偏好关注比特币，喜欢做现货长线", top_k=5)
    ids2 = {m["memory_id"] for m in result2}
    assert 201 in ids2 and not ids2 & {101, 102, 103}


def test_recall_type_filter():
    _save_sample(1)
    result = recall_memories(1, "用户要求单笔亏损不超过本金5%，严格止损", top_k=5, memory_type="constraint")
    assert result and all(m["memory_type"] == "constraint" for m in result)
    assert result[0]["memory_id"] == 103


def test_delete_by_ids_and_by_user():
    _save_sample(1)
    deleted = delete_memories(1, memory_ids=[101, 102])
    assert deleted == 2
    left = recall_memories(1, "用户偏好关注比特币，喜欢做现货长线", top_k=5)
    assert all(m["memory_id"] != 101 for m in left)
    # 按类型删除剩余 constraint
    assert delete_memories(1, memory_type="constraint") == 1
    # 用户清空（已空返回 0，不报错）
    assert delete_memories(1) == 0
    assert recall_memories(1, "用户偏好关注比特币", top_k=5) == []


def test_save_empty_and_bad_user_id_does_not_crash():
    assert save_memories([]) == 0
    # user_id 非法（如 "abc"）按 0 处理，不抛错
    saved = save_memories([{"user_id": "abc", "memory_id": 1, "content": "x", "memory_type": "preference"}])
    assert saved == 1


def test_summarizer_transcript_strips_think():
    msgs = [
        {"role": "user", "content": "我喜欢BTC"},
        {"role": "assistant", "content": "<think>思考过程</think>好的"},
        {"role": "user", "content": "<think>hh</think>止损5%"},
    ]
    transcript = _build_transcript(msgs)
    assert "<think>" not in transcript
    assert "思考过程" not in transcript
    assert "止损5%" in transcript
    assert "用户：" in transcript and "AI：" in transcript


def test_summarize_empty_raises():
    with pytest.raises(RuntimeError):
        summarize_conversation([])
