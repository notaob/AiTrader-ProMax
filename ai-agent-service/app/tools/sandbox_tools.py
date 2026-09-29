"""LangGraph 沙箱工具（字符串壳）。

真实逻辑唯一来源：app.tools.core.sandbox。沙箱在隔离 Docker 容器内执行
LLM 生成的代码；容器无网络，行情数据由本层在边界外获取后注入 DATA 变量
——数据在边界外获取，代码在边界内计算。
"""

from typing import Annotated

from langchain_core.tools import InjectedToolArg, tool

from app.market_data.binance_client import binance_client
from app.tools.core import sandbox as _core_sandbox


@tool
def run_backtest_code(code: str, symbol: str = "BTCUSDT",
                      attempts_used: Annotated[int, InjectedToolArg] = 0) -> str:
    """在隔离沙箱中执行你编写的 Python 代码，完成内置工具无法完成的定制计算（如策略回测、自定义指标、仓位公式验证）。

    沙箱内无网络；已内置变量 DATA = {"symbol": ..., "candles": [[开盘时间戳ms, 开盘价, 最高价, 最低价, 收盘价, 成交量], ...]}，
    为该 symbol 最近 30 天日线数据。代码必须把最终结论 print() 到 stdout。
    执行失败时你会收到完整 traceback，请修改代码后重试（最多 3 次）。
    """
    # 数据在边界外获取：沙箱无网络，K 线由本层拉取后序列化注入
    klines = binance_client.get_klines(symbol=symbol, interval="1d", limit=30)
    if not klines:
        return "暂时无法获取行情数据，沙箱执行取消。请改用内置工具回答。"
    data = {
        "symbol": binance_client.normalize_symbol(symbol),
        "interval": "1d",
        "candles": klines,
    }

    result = _core_sandbox.run_backtest_code(code, data, attempts_used=attempts_used)

    err = result.get("error")
    if err == "max_attempts_reached":
        return (f"沙箱执行次数已达上限（{_core_sandbox.MAX_ATTEMPTS} 次）。"
                "请改用内置工具回答，或向用户说明该计算暂时无法完成。")
    if err == "timeout":
        return f"沙箱执行超时（{_core_sandbox.TIMEOUT_SECONDS}s），疑似死循环。请简化代码后重试。"
    if err == "docker_unavailable":
        return "沙箱环境不可用。请改用内置工具回答。"
    if err == "code_too_large":
        return "代码超过大小上限（8KB）。请精简代码后重试。"
    if err == "exit_nonzero":
        return (f"代码执行失败（exit={result['exit_code']}，耗时 {result['elapsed_ms']}ms）。"
                f"traceback：\n{result['stderr']}\n请根据报错修改代码后重试。")

    return f"执行成功（{result['elapsed_ms']}ms），stdout 输出：\n{result['stdout']}"


sandbox_tools = [run_backtest_code]
