"""技术分析共享核心（MA / RSI / 趋势 / 支撑阻力 / 交易建议）。

被 LangGraph 工具（app.tools.analysis_tools，字符串壳）与 MCP server
（app.mcp_tool_server，结构化返回）复用：计算与建议逻辑唯一来源在此，
消费方只做呈现适配，避免同一算法在两处各自维护导致分叉。
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any

from app.market_data.binance_client import binance_client

RSI_PERIOD = 14
MIN_BARS = 30  # 样本少于该值不给出可信指标（对应原工具 "数据不足" 分支）


def calculate_rsi(prices: list[float], period: int = RSI_PERIOD) -> float:
    """计算 RSI：固定窗口平均涨跌幅。样本不足返回中性 50。"""
    if len(prices) < period + 1:
        return 50.0

    gains: list[float] = []
    losses: list[float] = []
    for i in range(1, period + 1):
        change = prices[-i] - prices[-i - 1]
        gains.append(change if change > 0 else 0.0)
        losses.append(abs(change) if change < 0 else 0.0)

    avg_gain = sum(gains) / period
    avg_loss = sum(losses) / period

    if avg_loss == 0:
        return 100.0
    return 100 - (100 / (1 + avg_gain / avg_loss))


@dataclass
class IndicatorResult:
    """结构化指标结果。

    - ok=False 且 error=None：样本不足（见 data_len / MIN_BARS）
    - ok=False 且 error=str：底层 IO 异常
    """

    symbol: str
    ok: bool
    data_len: int
    current_price: float | None = None
    ma7: float | None = None
    ma30: float | None = None
    rsi: float | None = None
    trend: str | None = None
    support: float | None = None
    resistance: float | None = None
    error: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def compute_indicators(symbol: str | None = None, limit: int = 35) -> IndicatorResult:
    """抓最近 1h 收盘价并计算 MA7 / MA30 / RSI(14) / 趋势 / 近 20 根支撑阻力。

    永不抛异常：IO 失败进 error 字段，样本不足置 ok=False（data_len 反映实际数量）。
    """
    symbol = str(symbol or "")
    try:
        prices = binance_client.get_price_history(symbol=symbol, limit=limit)
    except Exception as e:  # pragma: no cover - HTTP 层已兜底，防御性留口
        return IndicatorResult(symbol=symbol, ok=False, data_len=0, error=str(e))

    if len(prices) < MIN_BARS:
        return IndicatorResult(symbol=symbol, ok=False, data_len=len(prices))

    ma7 = sum(prices[-7:]) / 7
    ma30 = sum(prices[-30:]) / 30
    rsi = calculate_rsi(prices)
    recent = prices[-20:]
    return IndicatorResult(
        symbol=symbol,
        ok=True,
        data_len=len(prices),
        current_price=prices[-1],
        ma7=ma7,
        ma30=ma30,
        rsi=rsi,
        trend="多头" if ma7 > ma30 else "空头",
        support=min(recent),
        resistance=max(recent),
    )


def build_suggestion_lines(ind: IndicatorResult) -> list[str]:
    """由指标生成交易建议行：LangGraph 壳逐行 join；MCP 端直接消费列表。"""
    lines: list[str] = []

    if ind.ma7 > ind.ma30:
        lines.append("✅ 金叉信号: 短期均线上穿长期均线，看涨")
    else:
        lines.append("⚠️ 死叉信号: 短期均线下穿长期均线，看跌")

    if ind.rsi > 70:
        lines.append("⚠️ RSI 超买: 可能回调")
    elif ind.rsi < 30:
        lines.append("✅ RSI 超卖: 可能反弹")
    else:
        lines.append("🟡 RSI 中性: 在 30-70 之间")

    if ind.ma7 > ind.ma30 and ind.rsi < 70:
        lines.append(f"💡 建议: 可考虑逢低买入，止损设置在 ${ind.current_price * 0.95:.2f}")
    elif ind.ma7 < ind.ma30 and ind.rsi > 30:
        lines.append("💡 建议: 趋势偏弱，观望或轻仓")

    return lines


def generate_trading_suggestion(symbol: str | None = None) -> dict[str, Any]:
    """交易建议（结构化入口，供 MCP / 其他消费方直接使用）。"""
    ind = compute_indicators(symbol)
    if ind.error:
        return {"ok": False, "symbol": ind.symbol, "reason": f"生成建议失败: {ind.error}", "signals": []}
    if not ind.ok:
        return {"ok": False, "symbol": ind.symbol, "reason": "数据不足，无法给出建议", "signals": []}
    return {"ok": True, "symbol": ind.symbol, "reason": None, "signals": build_suggestion_lines(ind)}
