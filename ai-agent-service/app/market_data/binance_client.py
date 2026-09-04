
import sys

import requests

from app.config import config


def _eprint(*args, **kwargs):
    """模块日志统一输出到 stderr：stdout 预留给 MCP stdio JSON-RPC 协议帧，禁止业务日志占用。"""
    print(*args, file=sys.stderr, **kwargs)


class BinanceClient:
    """币安 HTTP 客户端 - 按需获取市场数据（方法均支持 symbol，默认 BTCUSDT）"""

    BASE_URL = "https://data-api.binance.vision"
    DEFAULT_SYMBOL = "BTCUSDT"

    def __init__(self):
        # 简单的内存缓存，避免短时间内重复请求
        self._cache = {}
        self._cache_time = {}
        self.cache_ttl = 5  # 缓存 5 秒

        # HTTP 代理配置
        if config.PROXY_ENABLE:
            proxy_url = f"http://{config.PROXY_HOST}:{config.PROXY_PORT}"
            self._proxies = {"http": proxy_url, "https": proxy_url}
            _eprint(f"币安客户端使用代理: {proxy_url}")
        else:
            self._proxies = None

    @staticmethod
    def normalize_symbol(symbol: str | None = None) -> str:
        """把币种代号归一化为 Binance 交易对：BTC→BTCUSDT、eth/usdt→ETHUSDT；缺省 BTCUSDT。"""
        if not symbol or not str(symbol).strip():
            return BinanceClient.DEFAULT_SYMBOL
        s = str(symbol).strip().upper().replace("/", "").replace("-", "").replace(" ", "")
        if not s:
            return BinanceClient.DEFAULT_SYMBOL
        return s if s.endswith("USDT") else f"{s}USDT"

    def _get(self, endpoint: str, params: dict = None, use_cache: bool = True) -> dict:
        """发送 GET 请求，带简单缓存（cache key 含 params，按交易对隔离）"""
        cache_key = f"{endpoint}:{str(params)}"

        # 检查缓存
        if use_cache and cache_key in self._cache:
            import time
            if time.time() - self._cache_time.get(cache_key, 0) < self.cache_ttl:
                return self._cache[cache_key]

        # 发送请求
        url = f"{self.BASE_URL}{endpoint}"
        response = requests.get(url, params=params, timeout=10, proxies=self._proxies)
        response.raise_for_status()
        data = response.json()

        # 更新缓存
        if use_cache:
            import time
            self._cache[cache_key] = data
            self._cache_time[cache_key] = time.time()

        return data

    def get_current_price(self, symbol: str | None = None) -> float:
        """获取指定交易对当前价格（symbol 传 BTC/ETH/ETHUSDT 均可；缺省 BTCUSDT）"""
        try:
            data = self._get("/api/v3/ticker/price", {"symbol": self.normalize_symbol(symbol)})
            return float(data["price"])
        except Exception as e:
            _eprint(f"获取价格失败: {e}")
            return 0.0

    def get_market_state(self, symbol: str | None = None) -> dict:
        """获取指定交易对市场状态（24h 统计）"""
        pair = self.normalize_symbol(symbol)
        try:
            data = self._get("/api/v3/ticker/24hr", {"symbol": pair})
            return {
                "symbol": pair,
                "currentPrice": float(data["lastPrice"]),
                "priceChange24h": float(data["priceChange"]),
                "priceChangePercent24h": float(data["priceChangePercent"]),
                "volume24h": float(data["volume"]),
                "high24h": float(data["highPrice"]),
                "low24h": float(data["lowPrice"]),
            }
        except Exception as e:
            _eprint(f"获取市场状态失败: {e}")
            return {
                "symbol": pair,
                "currentPrice": 0.0,
                "priceChange24h": 0.0,
                "volume24h": 0.0,
            }

    def get_klines(self, symbol: str | None = None, interval: str = "1h",
                   limit: int = 35) -> list[list]:
        """获取 K 线数据

        Args:
            symbol: 交易对（BTC/ETH/ETHUSDT 均可，缺省 BTCUSDT）
            interval: K线周期 (1m, 5m, 15m, 1h, 4h, 1d)
            limit: 获取条数
        """
        try:
            data = self._get(
                "/api/v3/klines",
                {"symbol": self.normalize_symbol(symbol), "interval": interval, "limit": limit},
                use_cache=False  # K线数据不用缓存，确保实时性
            )
            return data
        except Exception as e:
            _eprint(f"获取K线数据失败: {e}")
            return []

    def get_price_history(self, symbol: str | None = None, limit: int = 35) -> list[float]:
        """获取价格历史（用于计算技术指标），返回收盘价列表"""
        klines = self.get_klines(symbol=symbol, interval="1h", limit=limit)
        if not klines:
            return []
        # K线数据格式: [开盘时间, 开盘价, 最高价, 最低价, 收盘价, 成交量, ...]
        return [float(k[4]) for k in klines]

    def start(self):
        """兼容旧接口，HTTP 方式无需启动"""
        _eprint("币安 HTTP 客户端初始化完成（无需 WebSocket 连接）")

    def stop(self):
        """兼容旧接口，HTTP 方式无需停止"""
        pass


# 单例
binance_client = BinanceClient()
