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
  }
};
