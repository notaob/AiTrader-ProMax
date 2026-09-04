"""Stage 1 单测：图节点行为 + 提示词单一来源 + Langfuse 降级。

不触发真实 LLM / 工具网络调用。
"""

import pytest
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage

from app.context.context_builder import build_prompt
from app.graph import trading_graph as tg
from app.prompts import STRATEGY_PROMPT, SYSTEM_PROMPT


# ---------------------------------------------------------------- should_continue
def test_should_continue_tools_when_tool_calls():
    msg = AIMessage(content="", tool_calls=[{"name": "x", "args": {}, "id": "1", "type": "tool_call"}])
    assert tg.should_continue({"messages": [msg]}) == "tools"


def test_should_continue_end_without_tool_calls():
    assert tg.should_continue({"messages": [HumanMessage(content="hi")]}) == "end"
    assert tg.should_continue({"messages": []}) == "end"


# ---------------------------------------------------------------- tools_node
class _EchoTool:
    name = "echo_tool"

    async def ainvoke(self, args: dict) -> str:
        return "echo:ok"


class _CaptureTool:
    name = "search_knowledge"

    def __init__(self):
        self.seen = None

    async def ainvoke(self, args: dict) -> str:
        self.seen = args
        return "searched"


@pytest.mark.asyncio
async def test_tools_node_runs_and_records_normalized_steps(monkeypatch):
    monkeypatch.setitem(tg.tool_map, "echo_tool", _EchoTool())
    aim = AIMessage(content="", tool_calls=[{"name": "echo_tool", "args": {"v": 1}, "id": "t1", "type": "tool_call"}])
    out = await tg.tools_node({"messages": [aim], "intermediate_steps": [], "user_id": "7"})

    assert len(out["messages"]) == 1
    assert isinstance(out["messages"][0], ToolMessage)
    assert out["messages"][0].content == "echo:ok"
    assert out["messages"][0].tool_call_id == "t1"

    steps = out["intermediate_steps"]
    assert [s["type"] for s in steps] == ["action", "observation"]
    assert [s["step"] for s in steps] == [1, 2]
    assert steps[0]["tool"] == "echo_tool"
    assert steps[1]["output"] == "echo:ok"


@pytest.mark.asyncio
async def test_tools_node_injects_user_id_for_rag_tools(monkeypatch):
    cap = _CaptureTool()
    monkeypatch.setitem(tg.tool_map, "search_knowledge", cap)
    aim = AIMessage(content="", tool_calls=[{"name": "search_knowledge", "args": {}, "id": "t2", "type": "tool_call"}])
    await tg.tools_node({"messages": [aim], "intermediate_steps": [], "user_id": "7"})
    assert cap.seen == {"user_id": 7}


@pytest.mark.asyncio
async def test_tools_node_empty_without_tool_calls():
    out = await tg.tools_node({"messages": [HumanMessage(content="hi")], "intermediate_steps": []})
    assert out["messages"] == []


# ---------------------------------------------------------------- prompts 单一来源
def test_graph_uses_canonical_prompts():
    # graph 模块引用的就是 prompts 包常量（同一对象，杜绝双写漂移）
    assert tg.SYSTEM_PROMPT is SYSTEM_PROMPT
    assert tg.STRATEGY_PROMPT is STRATEGY_PROMPT


def test_build_prompt_chat_sections_ordered():
    p = build_prompt(
        state={"symbol": "BTCUSDT"},
        recent_messages=[{"role": "user", "content": "hi"}],
        summaries=["summary1"],
        memories=["memory1"],
        knowledge_chunks=["chunk1"],
        current_message="q?",
        mode="chat",
    )
    # 用带换行前缀的唯一片段做定位（profile 正文也含"长期记忆"字样，不能只匹配裸词）
    markers = [
        "你是 AiTrader 的 AI 交易助手",  # 系统提示词
        "【重要任务 - 了解你的用户】",  # 用户画像引导（chat 专属）
        "\n当前会话状态：",
        "\n最近对话：",
        "\n历史摘要：",
        "\n长期记忆：",
        "\n相关知识：",
        "\n用户当前输入：q?",
    ]
    positions = [p.index(m) for m in markers]
    assert positions == sorted(positions)


def test_build_prompt_strategy_uses_canonical_prompt():
    p = build_prompt({}, [], [], [], [], "分析一下", mode="strategy")
    assert p.startswith(STRATEGY_PROMPT)
    assert "## 1. 市场趋势分析" in p
    # strategy 不含 chat 专用画像引导
    assert "了解你的用户" not in p


def test_build_prompt_escapes_braces():
    p = build_prompt({}, [], [], ["含 {占位符} 的记忆"], [], "{input}", mode="chat")
    assert "{{" in p and "}}" in p  # 原样文本已被转义，供 ChatPromptTemplate 使用
