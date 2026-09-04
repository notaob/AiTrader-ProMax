"""Stage 5：MCP stdio 客户端集成验证（真实起子进程，查 Binance BTC 行情）。

与 evals 一致默认排除（addopts `-m not evals`），显式运行：
    uv run pytest -m mcp -q

对应 DoD：外部 MCP 客户端可直接查 BTC 行情；"工具层兼容 MCP"落地可复现。
"""
import json
import sys

import pytest
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


def _server_params() -> StdioServerParameters:
    # stdio 子进程复用当前解释器；pythonpath="." + cwd=包根使其可 import app.*
    # -X utf8：Windows 管道默认 GBK，MCP 协议要求 UTF-8（与 server 内 reconfigure 双保险）
    return StdioServerParameters(
        command=sys.executable,
        args=["-X", "utf8", "-m", "app.mcp_tool_server"],
    )


@pytest.mark.mcp
@pytest.mark.asyncio
async def test_mcp_list_tools_exposes_core_toolset():
    """可列出 6 个工具，schema 参数 snake_case 可读。"""
    async with stdio_client(_server_params()) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()

            listed = await session.list_tools()
            # mcp 2.x 客户端返回 ListToolsResult，工具在其 .tools 字段
            tools = getattr(listed, "tools", listed)
            names = {t.name for t in tools}
            assert {
                "get_current_price",
                "get_market_state",
                "get_technical_analysis",
                "get_trading_suggestion",
                "search_knowledge",
                "add_to_knowledge_base",
            } <= names

            price = next(t for t in tools if t.name == "get_current_price")
            assert price.input_schema["properties"]["symbol"]["type"] == "string"


@pytest.mark.mcp
@pytest.mark.asyncio
async def test_mcp_call_get_current_price_btc():
    """真实调用 get_current_price(BTC)：返回结构化 JSON，ok + 正价格。"""
    async with stdio_client(_server_params()) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()

            result = await session.call_tool(
                "get_current_price",
                arguments={"symbol": "BTC"},
            )
            data = json.loads(result.content[0].text)
            assert data["ok"] is True
            assert data["symbol"] == "BTCUSDT"
            assert data["price"] > 0


@pytest.mark.mcp
@pytest.mark.asyncio
async def test_mcp_call_technical_analysis_structured():
    """技术分析工具经 MCP 返回结构化指标（非字符串壳文本）。"""
    async with stdio_client(_server_params()) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()

            result = await session.call_tool(
                "get_technical_analysis",
                arguments={"symbol": "ETH"},
            )
            data = json.loads(result.content[0].text)
            assert data["ok"] is True
            for key in ("ma7", "ma30", "rsi", "trend", "support", "resistance"):
                assert key in data
