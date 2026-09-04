"""AiTrader MCP Server（stdio transport）。

复用 app.tools.core —— 与 LangGraph 图内工具同一实现，不双写逻辑；
LangChain 壳输出字符串，MCP 端返回结构化 JSON。

暴露工具（与图内工具同名同义，schema 参数 snake_case）：
- 行情：get_current_price / get_market_state
- 技术分析：get_technical_analysis / get_trading_suggestion
- 知识库：search_knowledge / add_to_knowledge_base

本地启动（stdio）：
    uv run python -m app.mcp_tool_server

客户端验证（任一 MCP 客户端 / Inspector / `pytest -m mcp`）：
    uv run mcp run app/mcp_tool_server.py
"""

from __future__ import annotations

import sys
from typing import Annotated

from mcp.server.mcpserver import MCPServer
from pydantic import Field

# MCP 协议经 stdio 传 UTF-8 JSON-RPC；Windows 管道默认 locale(GBK) 会写坏字节，
# 强制重配（Unix/locale=utf-8 环境无副作用）。客户端 spawn 亦可加 PYTHONUTF8=1 / -X utf8。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stdin, "reconfigure"):
    sys.stdin.reconfigure(encoding="utf-8")

from app.tools.core import analysis as core_analysis
from app.tools.core import knowledge as core_knowledge
from app.tools.core import market as core_market

#: 对外暴露的 server 名（Claude Desktop / Cursor / Inspector 以之识别）
server = MCPServer(
    "ai-trader-mcp",
    instructions=(
        "AiTrader 交易助手工具集：实时行情、市场状态、技术指标、交易建议与"
        "知识库检索。symbol 传币种代号（BTC/ETH/DOGE 等，USDT 计价）。"
    ),
)


@server.tool()
def get_current_price(
    symbol: Annotated[str, Field(description="币种代号或交易对，如 BTC / ETH / BTCUSDT / eth-usdt")],
) -> dict:
    """获取指定交易对（币种）的当前实时价格。"""
    return core_market.get_current_price(symbol)


@server.tool()
def get_market_state(
    symbol: Annotated[str, Field(description="币种代号或交易对，如 BTC / ETH / BTCUSDT")],
) -> dict:
    """获取市场状态：当前价、24h 涨跌幅、24h 最高/最低价与成交量。"""
    return core_market.get_market_state(symbol)


@server.tool()
def get_technical_analysis(
    symbol: Annotated[str, Field(description="币种代号或交易对，如 BTC / ETH / BTCUSDT")],
) -> dict:
    """技术分析指标：MA7 / MA30 / RSI(14) / 趋势 / 支撑位 / 阻力位。"""
    return core_analysis.compute_indicators(symbol).to_dict()


@server.tool()
def get_trading_suggestion(
    symbol: Annotated[str, Field(description="币种代号或交易对，如 BTC / ETH / BTCUSDT")],
) -> dict:
    """基于技术分析给出交易建议（金叉/死叉、RSI 超买超卖、买卖建议信号列表）。"""
    return core_analysis.generate_trading_suggestion(symbol)


@server.tool()
def search_knowledge(
    query: Annotated[str, Field(description="检索问句，如：止损规则、什么是 RSI")],
    user_id: Annotated[int, Field(description="目标用户 ID，仅检索其知识库")] = 0,
    top_k: Annotated[int, Field(description="返回命中条数")] = 5,
) -> dict:
    """语义检索用户知识库（交易策略/概念解释/已保存笔记），返回结构化命中列表。"""
    return core_knowledge.search_knowledge(query, user_id=user_id, top_k=top_k)


@server.tool()
def add_to_knowledge_base(
    text: Annotated[str, Field(description="要写入知识库的文本内容")],
    source: Annotated[str, Field(description="来源标识，默认 user")] = "user",
) -> dict:
    """把文本切分写入知识库，返回写入片段数。"""
    return core_knowledge.add_to_knowledge_base(text, source)


if __name__ == "__main__":
    # stdio transport：由 MCP 客户端（docker run -i / npx mcp / uv run mcp ...）spawn
    server.run()
