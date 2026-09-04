"""Stage 0 冒烟测试：chat / strategy / rag 核心端点可路由、可序列化。

不触发真实 LLM / 工具调用（mock trading_graph 与记忆分类），保证快速、离线、稳定。
"""

import pytest
from fastapi.testclient import TestClient

import app.main as main

FAKE_ANSWER = "mock answer from smoke test"


class _FakeMessage:
    content = FAKE_ANSWER


class _FakeGraph:
    async def ainvoke(self, inputs: dict, config: dict | None = None) -> dict:
        return {"messages": [_FakeMessage()], "intermediate_steps": []}


@pytest.fixture()
def client(monkeypatch):
    # 替换真实图与记忆分类，避免任何外部调用
    monkeypatch.setattr(main, "trading_graph", _FakeGraph())
    monkeypatch.setattr(main, "classify_user_message", lambda msg: None)
    return TestClient(main.app)


def _chat_payload(mode: str = "chat") -> dict:
    return {
        "message": "hi",
        "user_id": "1",
        "session_id": "s_test",
        "mode": mode,
        "state": {},
        "history": [],
        "summaries": [],
        "memories": [],
        "knowledge_chunks": [],
    }


def test_health_ok(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "healthy"
    assert body["llm"]  # 非空即说明配置已加载


def test_chat_mode_smoke(client):
    resp = client.post("/agent/chat", json=_chat_payload(mode="chat"))
    assert resp.status_code == 200
    body = resp.json()
    assert body["success"] is True
    assert body["answer"] == FAKE_ANSWER
    assert isinstance(body["execution_time"], int)


def test_chat_strategy_mode_smoke(client):
    resp = client.post("/agent/chat", json=_chat_payload(mode="strategy"))
    assert resp.status_code == 200
    body = resp.json()
    assert body["success"] is True
    assert body["answer"] == FAKE_ANSWER


def test_tools_list_ok(client):
    resp = client.get("/tools")
    assert resp.status_code == 200
    tools = resp.json()["tools"]
    names = {t["name"] for t in tools}
    assert {"get_current_price", "get_technical_analysis"} <= names


def test_rag_stats_ok(client):
    resp = client.get("/rag/stats")
    assert resp.status_code == 200
    assert resp.json()["success"] is True
