from langchain_core.tools import tool
from app.market_data.binance_client import binance_client


@tool
def get_current_price(symbol: str) -> str:
    """获取指定交易对的当前价格"""
    price = binance_client.get_current_price()
    if price > 0:
        return f"{symbol} 当前价格: ${price:.2f}"
    return "暂时无法获取价格数据"


@tool
def get_market_state(symbol: str) -> str:
    """获取市场状态（价格、成交量等）"""
    state = binance_client.get_market_state()
    if state["currentPrice"] > 0:
        return f"""市场状态 ({symbol}):
- 当前价格: ${state['currentPrice']:.2f}
- 24h 涨跌: {state.get('priceChangePercent24h', 0):.2f}%
- 24h 最高: ${state.get('high24h', 0):.2f}
- 24h 最低: ${state.get('low24h', 0):.2f}
- 24h 成交量: {state['volume24h']:.4f}
"""
    return "暂时无法获取市场数据"


# 工具列表
market_tools = [get_current_price, get_market_state]
