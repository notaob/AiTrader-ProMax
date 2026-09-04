"""symbol 透传回归测试。

背景：judge 评测抓到 eqa-010 问 ETH 却答出 BTC 量级价格——根因是 BinanceClient
与行情/分析工具忽略 symbol 参数、全部硬编码默认 BTCUSDT。本测试锁定：
- client 层 symbol 归一化（BTC / eth / eth/usdt / btc-usdt 等 → ETHUSDT，缺省 BTCUSDT）
- client 层把归一化后的 symbol 真正放进 Binance 请求参数
- 工具层把 LLM 传入的 symbol 透传给 client

全部走 mock，不触网。
"""
import pytest

from app.market_data.binance_client import binance_client
from app.tools.analysis_tools import get_technical_analysis, get_trading_suggestion
from app.tools.market_tools import get_current_price, get_market_state


class TestNormalizeSymbol:
    @pytest.mark.parametrize("raw,expected", [
        ("BTC", "BTCUSDT"),
        ("ETH", "ETHUSDT"),
        ("eth", "ETHUSDT"),
        ("ETHUSDT", "ETHUSDT"),
        ("eth/usdt", "ETHUSDT"),
        ("btc-usdt", "BTCUSDT"),
        ("  btc  ", "BTCUSDT"),
        ("DOGE", "DOGEUSDT"),
        (None, "BTCUSDT"),
        ("", "BTCUSDT"),
        ("  ", "BTCUSDT"),
    ])
    def test_normalize(self, raw, expected):
        assert binance_client.normalize_symbol(raw) == expected


class TestClientPassesSymbol:
    def test_get_current_price_symbol(self, monkeypatch):
        captured = {}

        def fake_get(endpoint, params=None, use_cache=True):
            captured["endpoint"] = endpoint
            captured["params"] = params
            return {"price": "42000.5"}

        monkeypatch.setattr(binance_client, "_get", fake_get)
        assert binance_client.get_current_price("ETH") == 42000.5
        assert captured["endpoint"] == "/api/v3/ticker/price"
        assert captured["params"]["symbol"] == "ETHUSDT"

    def test_get_current_price_default_btc(self, monkeypatch):
        captured = {}

        def fake_get(endpoint, params=None, use_cache=True):
            captured["params"] = params
            return {"price": "1.0"}

        monkeypatch.setattr(binance_client, "_get", fake_get)
        binance_client.get_current_price()
        assert captured["params"]["symbol"] == "BTCUSDT"

    def test_get_market_state_symbol(self, monkeypatch):
        captured = {}

        def fake_get(endpoint, params=None, use_cache=True):
            captured["params"] = params
            return {"lastPrice": "100.0", "priceChange": "1.0",
                    "priceChangePercent": "1.01", "volume": "10.0",
                    "highPrice": "105.0", "lowPrice": "95.0"}

        monkeypatch.setattr(binance_client, "_get", fake_get)
        state = binance_client.get_market_state("eth/usdt")
        assert captured["params"]["symbol"] == "ETHUSDT"
        assert state["symbol"] == "ETHUSDT"
        assert state["currentPrice"] == 100.0

    def test_get_price_history_symbol(self, monkeypatch):
        captured = {}

        def fake_get(endpoint, params=None, use_cache=True):
            captured["params"] = params
            # 一根 K 线：[t, o, h, l, c, v, ...]
            return [[0, "1", "2", "3", "4", "5", 0, 0, 0, 0, 0, 0]]

        monkeypatch.setattr(binance_client, "_get", fake_get)
        history = binance_client.get_price_history("BTC")
        assert captured["params"]["symbol"] == "BTCUSDT"
        assert history == [4.0]


def _price_series(limit=35):
    return [float(100 + i) for i in range(limit)]


class TestToolsPassSymbol:
    def test_current_price_tool(self, monkeypatch):
        seen = {}

        def fake(symbol=None):
            seen["symbol"] = symbol
            return 50000.0

        monkeypatch.setattr(binance_client, "get_current_price", fake)
        out = get_current_price.invoke({"symbol": "ETH"})
        assert seen["symbol"] == "ETH"
        assert "ETH 当前价格: $50000.00" in out

    def test_market_state_tool(self, monkeypatch):
        seen = {}

        def fake(symbol=None):
            seen["symbol"] = symbol
            return {"symbol": "ETHUSDT", "currentPrice": 3000.0,
                    "priceChangePercent24h": 2.0, "high24h": 3100.0,
                    "low24h": 2900.0, "volume24h": 123.0}

        monkeypatch.setattr(binance_client, "get_market_state", fake)
        out = get_market_state.invoke({"symbol": "ETH"})
        assert seen["symbol"] == "ETH"
        assert "市场状态 (ETH)" in out

    def test_technical_analysis_tool(self, monkeypatch):
        seen = {}

        def fake(symbol=None, limit=35):
            seen["symbol"] = symbol
            return _price_series(limit)

        monkeypatch.setattr(binance_client, "get_price_history", fake)
        out = get_technical_analysis.invoke({"symbol": "ETH"})
        assert seen["symbol"] == "ETH"
        assert "技术分析 (ETH)" in out

    def test_trading_suggestion_tool(self, monkeypatch):
        seen = {}

        def fake(symbol=None, limit=35):
            seen["symbol"] = symbol
            return _price_series(limit)

        monkeypatch.setattr(binance_client, "get_price_history", fake)
        out = get_trading_suggestion.invoke({"symbol": "ETH"})
        assert seen["symbol"] == "ETH"
        assert "金叉" in out
