import type { ChartData } from "../types";

// 行情数据通过后端代理转发，解决中国大陆用户无法直连 Binance 的问题
const PROXY_API_URL = '/api/market/btc';

export const cryptoService = {
  // 获取比特币当前价格（通过后端代理）
  getBtcPrice: async (): Promise<number> => {
    try {
      const response = await fetch(`${PROXY_API_URL}/price`);
      if (!response.ok) throw new Error('Network response was not ok');
      const data = await response.json();
      if (data.code === 1 && data.price) {
        return Math.floor(parseFloat(data.price));
      }
      throw new Error(data.msg || 'Invalid response');
    } catch (error) {
      console.error('BTC price fetch failed', error);
      throw error;
    }
  },

  // 订阅比特币实时价格（3秒轮询替代 WebSocket）
  subscribeToBtcPrice: (callback: (price: number) => void): (() => void) => {
    let active = true;

    const poll = async () => {
      if (!active) return;
      try {
        const price = await cryptoService.getBtcPrice();
        if (active && price > 0) {
          callback(price);
        }
      } catch (e) {
        console.warn('Price poll failed', e);
      }
      if (active) {
        setTimeout(poll, 3000);
      }
    };

    poll();

    return () => {
      active = false;
    };
  },

  // 获取比特币历史 K 线数据（通过后端代理）
  getBtcHistory: async (): Promise<ChartData[]> => {
    try {
      const response = await fetch(`${PROXY_API_URL}/klines?interval=1h&limit=25`);
      if (!response.ok) throw new Error('Network response was not ok');

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
      console.error('BTC History fetch failed', error);
      throw error;
    }
  },

  // 获取多时间框架市场情绪（基于 RSI，通过后端代理）
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
            `${PROXY_API_URL}/klines?interval=${interval}&limit=20`
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
