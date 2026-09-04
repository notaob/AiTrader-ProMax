"""LangGraph 技术分析工具（字符串壳）。

真实逻辑唯一来源：app.tools.core.analysis —— 与 MCP server 复用同一函数，
这里只负责把结构化结果格式化为模型友好文本（输出与原实现逐字一致）。
"""

from langchain_core.tools import tool

from app.tools.core import analysis as _core_analysis


@tool
def get_technical_analysis(symbol: str) -> str:
    """获取指定交易对（币种）的技术分析指标（MA、RSI、趋势判断）。symbol 传币种代号，如 BTC、ETH。"""
    result = _core_analysis.compute_indicators(symbol)
    if result.error:
        return f"技术分析失败: {result.error}"
    if not result.ok:
        return "数据不足，无法进行技术分析"
    return (
        f"技术分析 ({symbol}):\n"
        f"- MA7: ${result.ma7:.2f}\n"
        f"- MA30: ${result.ma30:.2f}\n"
        f"- RSI(14): {result.rsi:.2f}\n"
        f"- 趋势: {result.trend}\n"
        f"- 支撑位: ${result.support:.2f}\n"
        f"- 阻力位: ${result.resistance:.2f}\n"
    )


@tool
def get_trading_suggestion(symbol: str) -> str:
    """基于技术分析给出指定交易对（币种）的交易建议。symbol 传币种代号，如 BTC、ETH。"""
    result = _core_analysis.compute_indicators(symbol)
    if result.error:
        return f"生成建议失败: {result.error}"
    if not result.ok:
        return "数据不足，无法给出建议"
    return "\n".join(_core_analysis.build_suggestion_lines(result))


# 工具列表
analysis_tools = [get_technical_analysis, get_trading_suggestion]
