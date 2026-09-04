"""行情共享核心：LangGraph 工具与 MCP server 复用同一组函数（结构化返回）。"""

from __future__ import annotations

from app.market_data.binance_client import binance_client


def get_current_price(symbol: str | None = None) -> dict:
    """获取指定交易对当前价格（结构化）。

    Returns:
        {"ok": bool, "requested": 原入参, "symbol": 归一化交易对, "price": float}
    """
    price = binance_client.get_current_price(symbol=symbol)
    return {
        "ok": price > 0,
        "requested": str(symbol or "").strip(),
        "symbol": binance_client.normalize_symbol(symbol),
        "price": price,
    }


def get_market_state(symbol: str | None = None) -> dict:
    """获取指定交易对市场状态（24h 统计，结构化）。

    Returns: BinanceClient 的 24h 统计 dict，附 "ok"（currentPrice>0 才算成功）。
    """
    state = binance_client.get_market_state(symbol=symbol)
    state["ok"] = state.get("currentPrice", 0.0) > 0
    return state
