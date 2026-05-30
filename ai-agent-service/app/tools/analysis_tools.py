from langchain_core.tools import tool
from app.market_data.binance_client import binance_client
from typing import List

@tool
def get_technical_analysis(symbol: str) -> str:
    """获取技术分析指标（MA、RSI、趋势判断）"""
    try:
        # 获取历史价格数据
        prices = binance_client.get_price_history(limit=35)
        if len(prices) < 30:
            return "数据不足，无法进行技术分析"
        
        # 计算 MA
        ma7 = sum(prices[-7:]) / 7
        ma30 = sum(prices[-30:]) / 30
        
        # 计算 RSI
        rsi = calculate_rsi(prices)
        
        # 判断趋势
        trend = "多头" if ma7 > ma30 else "空头"
        
        # 计算支撑阻力位（简单实现）
        recent_prices = prices[-20:]
        support = min(recent_prices)
        resistance = max(recent_prices)
        
        return f"""技术分析 ({symbol}):
- MA7: ${ma7:.2f}
- MA30: ${ma30:.2f}
- RSI(14): {rsi:.2f}
- 趋势: {trend}
- 支撑位: ${support:.2f}
- 阻力位: ${resistance:.2f}
"""
    except Exception as e:
        return f"技术分析失败: {str(e)}"

@tool
def get_trading_suggestion(symbol: str) -> str:
    """基于技术分析给出交易建议"""
    try:
        prices = binance_client.get_price_history(limit=35)
        if len(prices) < 30:
            return "数据不足，无法给出建议"
        
        current_price = prices[-1]
        ma7 = sum(prices[-7:]) / 7
        ma30 = sum(prices[-30:]) / 30
        rsi = calculate_rsi(prices)
        
        # 生成建议
        suggestion = []
        
        # 趋势判断
        if ma7 > ma30:
            suggestion.append("✅ 金叉信号: 短期均线上穿长期均线，看涨")
        else:
            suggestion.append("⚠️ 死叉信号: 短期均线下穿长期均线，看跌")
        
        # RSI 判断
        if rsi > 70:
            suggestion.append("⚠️ RSI 超买: 可能回调")
        elif rsi < 30:
            suggestion.append("✅ RSI 超卖: 可能反弹")
        else:
            suggestion.append("🟡 RSI 中性: 在 30-70 之间")
        
        # 入场建议
        if ma7 > ma30 and rsi < 70:
            suggestion.append(f"💡 建议: 可考虑逢低买入，止损设置在 ${current_price * 0.95:.2f}")
        elif ma7 < ma30 and rsi > 30:
            suggestion.append(f"💡 建议: 趋势偏弱，观望或轻仓")
        
        return "\n".join(suggestion)
    except Exception as e:
        return f"生成建议失败: {str(e)}"

def calculate_rsi(prices: List[float], period: int = 14) -> float:
    """计算 RSI 指标"""
    if len(prices) < period + 1:
        return 50.0
    
    gains = []
    losses = []
    
    for i in range(1, period + 1):
        change = prices[-i] - prices[-i-1]
        if change > 0:
            gains.append(change)
            losses.append(0)
        else:
            gains.append(0)
            losses.append(abs(change))
    
    avg_gain = sum(gains) / period
    avg_loss = sum(losses) / period
    
    if avg_loss == 0:
        return 100.0
    
    rs = avg_gain / avg_loss
    rsi = 100 - (100 / (1 + rs))
    
    return rsi

# 工具列表
analysis_tools = [get_technical_analysis, get_trading_suggestion]
