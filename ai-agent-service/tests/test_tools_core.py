"""Stage 5 Step 1 回归：tools.core 共享层与 LangChain 字符串壳一致性。

背景：工具核心逻辑抽到 app.tools.core（结构化 dict / dataclass），LangGraph 图内
工具（app.tools.*_tools）退化为薄壳。本测试锁定：
- core 返回结构化结果（字段可被 MCP server 直接消费）
- 壳文本与 core 同源一致（图内输出、Stage 4 judge/format 不回归）
- 错误 / 数据不足分支文案与重构前逐字一致

全部 mock，不触网。
"""
import pytest

from app.market_data.binance_client import binance_client
from app.rag.document_processor import document_processor
from app.tools.analysis_tools import get_technical_analysis, get_trading_suggestion
from app.tools.core import analysis as core_analysis
from app.tools.core import knowledge as core_knowledge
from app.tools.core import market as core_market
from app.tools.market_tools import get_current_price, get_market_state


def _price_series(limit=35):
    """单调上涨的收盘价序列（ma7 > ma30 → 多头；无下跌 → RSI=100）。"""
    return [float(100 + i) for i in range(limit)]


# ---------------------------------------------------------------- market core
class TestMarketCore:
    def test_core_price_structured_and_shell_consistent(self, monkeypatch):
        seen = {}

        def fake(symbol=None):
            seen["symbol"] = symbol
            return 50000.0

        monkeypatch.setattr(binance_client, "get_current_price", fake)
        data = core_market.get_current_price("ETH")
        assert data == {"ok": True, "requested": "ETH", "symbol": "ETHUSDT", "price": 50000.0}
        assert get_current_price.invoke({"symbol": "ETH"}) == "ETH 当前价格: $50000.00"
        assert seen["symbol"] == "ETH"  # 壳把入参原样透传给 core

    def test_core_price_failure_and_shell_fallback(self, monkeypatch):
        monkeypatch.setattr(binance_client, "get_current_price", lambda symbol=None: 0.0)
        data = core_market.get_current_price("ETH")
        assert data["ok"] is False
        assert get_current_price.invoke({"symbol": "ETH"}) == "暂时无法获取 ETH 价格数据"

    def test_core_market_state_structured_and_shell(self, monkeypatch):
        def fake(symbol=None):
            return {"symbol": "ETHUSDT", "currentPrice": 3000.0,
                    "priceChangePercent24h": 2.0, "high24h": 3100.0,
                    "low24h": 2900.0, "volume24h": 123.0}

        monkeypatch.setattr(binance_client, "get_market_state", fake)
        state = core_market.get_market_state("ETH")
        assert state["ok"] is True and state["currentPrice"] == 3000.0
        out = get_market_state.invoke({"symbol": "ETH"})
        assert "市场状态 (ETH)" in out and "- 当前价格: $3000.00" in out


# ---------------------------------------------------------------- analysis core
class TestAnalysisCore:
    @pytest.fixture()
    def rich_series(self, monkeypatch):
        monkeypatch.setattr(binance_client, "get_price_history", lambda symbol=None, limit=35: _price_series(limit))

    def test_core_indicators_structured(self, rich_series):
        ind = core_analysis.compute_indicators("ETH")
        assert ind.ok is True and ind.data_len == 35
        assert ind.trend == "多头" and ind.rsi == 100.0  # 单调上涨序列
        assert ind.ma7 == 131.0 and ind.ma30 == 119.5  # avg(128..134) / avg(105..134)
        assert ind.current_price == 134.0
        d = ind.to_dict()
        assert d["support"] == 115.0 and d["resistance"] == 134.0  # 近 20 根: 115..134

    def test_shell_matches_core_values(self, rich_series):
        ind = core_analysis.compute_indicators("ETH")
        out = get_technical_analysis.invoke({"symbol": "ETH"})
        assert f"- MA7: ${ind.ma7:.2f}" in out
        assert f"- RSI(14): {ind.rsi:.2f}" in out
        assert f"- 支撑位: ${ind.support:.2f}" in out

    def test_suggestion_core_signals_join_equals_shell(self, rich_series):
        core_resp = core_analysis.generate_trading_suggestion("ETH")
        assert core_resp["ok"] is True
        shell_out = get_trading_suggestion.invoke({"symbol": "ETH"})
        assert "\n".join(core_resp["signals"]) == shell_out
        assert "金叉信号" in shell_out and "RSI 超买" in shell_out  # 单调上涨 → 无止损买入建议

    def test_insufficient_data_branch_text(self, monkeypatch):
        monkeypatch.setattr(binance_client, "get_price_history", lambda symbol=None, limit=35: [])
        ind = core_analysis.compute_indicators("ETH")
        assert ind.ok is False and ind.data_len == 0
        assert get_technical_analysis.invoke({"symbol": "ETH"}) == "数据不足，无法进行技术分析"
        assert get_trading_suggestion.invoke({"symbol": "ETH"}) == "数据不足，无法给出建议"


# ---------------------------------------------------------------- knowledge core
class TestKnowledgeCore:
    def test_add_core_and_shell_success(self, monkeypatch):
        monkeypatch.setattr(document_processor, "process_text", lambda text, source: 2)
        resp = core_knowledge.add_to_knowledge_base("hello", source="user")
        assert resp == {"ok": True, "count": 2, "error": None}
        from app.tools.rag_tools import add_to_knowledge_base
        assert add_to_knowledge_base.invoke({"text": "hello"}) == "成功添加 2 个文档片段到知识库"

    def test_add_core_and_shell_failure(self, monkeypatch):
        def boom(text, source):
            raise RuntimeError("boom")

        monkeypatch.setattr(document_processor, "process_text", boom)
        resp = core_knowledge.add_to_knowledge_base("hello")
        assert resp["ok"] is False and resp["error"] == "boom"
        from app.tools.rag_tools import add_to_knowledge_base
        assert add_to_knowledge_base.invoke({"text": "hello"}) == "添加知识失败: boom"
