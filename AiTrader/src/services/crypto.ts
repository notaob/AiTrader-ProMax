import type { ChartData } from "../types";

// 直接访问 Binance API
const BINANCE_API_URL = 'https://api.binance.com/api/v3';
// Binance WebSocket
const BINANCE_WS_URL = 'wss://stream.binance.com:9443/ws';

export const cryptoService = {
  // 获取比特币简单价格 (REST - 备用，主要走 WS)
  getBtcPrice: async () => {
    try {
      const response = await fetch(`${BINANCE_API_URL}/ticker/price?symbol=BTCUSDT`);
      if (!response.ok) throw new Error('Network response was not ok');
      const data = await response.json();
      return Math.floor(parseFloat(data.price));
    } catch (error) {
      console.error('Binance API call failed', error);
      throw error;
    }
  },

  // 订阅比特币实时价格 (WebSocket)
  subscribeToBtcPrice: (callback: (price: number) => void) => {
    // 订阅 1s K 线，数据更丰富且不仅限于最新成交价
    const ws = new WebSocket(`${BINANCE_WS_URL}/btcusdt@kline_1s`);
    
    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        // data.k 是 K 线数据对象
        if (data.k) {
          const price = Math.floor(parseFloat(data.k.c)); // c: Close price
          callback(price);
        }
      } catch (e) {
        console.error('WS parse error', e);
      }
    };

    return () => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.close();
      }
    };
  },

  // 获取比特币历史 K 线数据 (REST - 直接访问 Binance)
  getBtcHistory: async (): Promise<ChartData[]> => {
    try {
      // 获取最近 25 小时的数据 (1h 间隔)
      const response = await fetch(`${BINANCE_API_URL}/klines?symbol=BTCUSDT&interval=1h&limit=25`);
      
      if (!response.ok) throw new Error('Network response was not ok');
      
      // Binance 返回格式: [ [openTime, open, high, low, close, volume, closeTime, ...], ... ]
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const data: any[][] = await response.json();
      
      if (!Array.isArray(data)) {
        throw new Error('Invalid data format');
      }

      const chartData: ChartData[] = data.map(item => {
        const timestamp = item[0]; // Open time
        const price = parseFloat(item[4]); // Close price (Index 4)
        const date = new Date(timestamp);
        return {
          time: `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`,
          price: Math.floor(price),
          timestamp: timestamp
        };
      });

      return chartData;

    } catch (error) {
      console.error('Binance History API failed', error);
      throw error;
    }
  },

  // 获取多时间框架市场情绪（基于 RSI）
  getMarketSentiment: async (): Promise<SentimentItem[]> => {
    const timeframes: { label: string; interval: string }[] = [
      { label: '1小时趋势', interval: '1h' },
      { label: '4小时趋势', interval: '4h' },
      { label: '24小时趋势', interval: '1d' },
    ];

    try {
      const results = await Promise.all(
        timeframes.map(async ({ label, interval }) => {
          const resp = await fetch(
            `${BINANCE_API_URL}/klines?symbol=BTCUSDT&interval=${interval}&limit=20`
          );
          if (!resp.ok) throw new Error(`Failed to fetch ${interval} klines`);
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const klines: any[][] = await resp.json();
          const closes = klines.map((k: (string | number)[]) => parseFloat(k[4] as string));
          const rsi = calculateRSI(closes, 14);
          return { timeframe: label, rsi, signal: rsiToSignal(rsi) };
        })
      );
      return results;
    } catch (error) {
      console.error('getMarketSentiment failed', error);
      // 降级：全部返回中性
      return timeframes.map(({ label }) => ({
        timeframe: label,
        rsi: 50,
        signal: '中性',
      }));
    }
  },
};

// ========== RSI 计算 ==========

export interface SentimentItem {
  timeframe: string;
  rsi: number;
  signal: string;
}

/** Wilder 平滑 RSI(14) */
function calculateRSI(prices: number[], period: number = 14): number {
  if (prices.length <= period) return 50;

  let sumGain = 0;
  let sumLoss = 0;

  for (let i = 1; i <= period; i++) {
    const diff = prices[i] - prices[i - 1];
    if (diff >= 0) sumGain += diff;
    else sumLoss += Math.abs(diff);
  }

  let avgGain = sumGain / period;
  let avgLoss = sumLoss / period;

  // Wilder 平滑
  for (let i = period + 1; i < prices.length; i++) {
    const diff = prices[i] - prices[i - 1];
    if (diff >= 0) {
      avgGain = (avgGain * (period - 1) + diff) / period;
      avgLoss = (avgLoss * (period - 1)) / period;
    } else {
      avgGain = (avgGain * (period - 1)) / period;
      avgLoss = (avgLoss * (period - 1) + Math.abs(diff)) / period;
    }
  }

  if (avgLoss === 0) return 100;
  const rs = avgGain / avgLoss;
  return Math.round((100 - 100 / (1 + rs)) * 10) / 10; // 保留一位小数
}

/** RSI 值映射为趋势信号 */
function rsiToSignal(rsi: number): string {
  if (rsi >= 70) return '强力买入';
  if (rsi >= 55) return '买入';
  if (rsi >= 45) return '中性';
  if (rsi >= 30) return '卖出';
  return '强力卖出';
}
