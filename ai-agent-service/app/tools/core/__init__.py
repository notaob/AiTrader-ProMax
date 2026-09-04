"""app.tools.core —— LangGraph 图内工具与 MCP server 共享的工具核心。

约定：core 函数只做「IO + 计算」，返回结构化 dict / dataclass，不做任何
LangChain / MCP 绑定；对外呈现（LangChain 字符串壳、MCP 结构化响应）
由消费方各自适配，保证同一逻辑只有一个实现。
"""

from app.tools.core import analysis, knowledge, market
from app.tools.core.analysis import (
    IndicatorResult,
    build_suggestion_lines,
    calculate_rsi,
    compute_indicators,
    generate_trading_suggestion,
)
from app.tools.core.knowledge import add_to_knowledge_base, search_knowledge
from app.tools.core.market import get_current_price, get_market_state

__all__ = [
    "analysis",
    "knowledge",
    "market",
    "IndicatorResult",
    "build_suggestion_lines",
    "calculate_rsi",
    "compute_indicators",
    "generate_trading_suggestion",
    "add_to_knowledge_base",
    "search_knowledge",
    "get_current_price",
    "get_market_state",
]
