"""LangGraph 行情工具（字符串壳）。

真实逻辑唯一来源：app.tools.core.market —— 与 MCP server 复用同一函数，
这里只负责把结构化结果格式化为模型友好文本（输出与原实现逐字一致）。
"""

from langchain_core.tools import tool

from app.tools.core import market as _core_market


@tool
def get_current_price(symbol: str) -> str:
    """获取指定交易对（币种）的当前实时价格。symbol 传币种代号即可，如 BTC、ETH、DOGE，也支持 BTCUSDT/eth/usdt。"""
    data = _core_market.get_current_price(symbol)
    if data["ok"]:
        return f"{symbol} 当前价格: ${data['price']:.2f}"
    return f"暂时无法获取 {symbol} 价格数据"


@tool
def get_market_state(symbol: str) -> str:
    """获取指定交易对（币种）的市场状态（价格、24h 涨跌、成交量等）。symbol 传币种代号，如 BTC、ETH。"""
    state = _core_market.get_market_state(symbol)
    if state["currentPrice"] > 0:
        return f"""市场状态 ({symbol}):
- 当前价格: ${state['currentPrice']:.2f}
- 24h 涨跌: {state.get('priceChangePercent24h', 0):.2f}%
- 24h 最高: ${state.get('high24h', 0):.2f}
- 24h 最低: ${state.get('low24h', 0):.2f}
- 24h 成交量: {state['volume24h']:.4f}
"""
    return f"暂时无法获取 {symbol} 市场数据"


# 工具列表
market_tools = [get_current_price, get_market_state]
