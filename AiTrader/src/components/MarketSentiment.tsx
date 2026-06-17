import { useEffect, useState } from 'react';
import { cryptoService } from '../services/crypto';
import type { SentimentItem } from '../services/crypto';
import styles from './MarketSentiment.module.css';

export const MarketSentiment = () => {
  const [data, setData] = useState<SentimentItem[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = async () => {
    try {
      const result = await cryptoService.getMarketSentiment();
      setData(result);
    } catch {
      // getMarketSentiment 内部已做降级处理
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    const timer = setInterval(fetchData, 60000); // 每 60 秒刷新
    return () => clearInterval(timer);
  }, []);

  const getSignalClass = (signal: string) => {
    switch (signal) {
      case '强力买入': return styles.strongBuy;
      case '买入': return styles.buy;
      case '卖出': return styles.sell;
      case '强力卖出': return styles.strongSell;
      default: return styles.neutral;
    }
  };

  return (
    <div className={styles.container}>
      {(data.length > 0 ? data : [
        { timeframe: '1小时趋势', rsi: 0, signal: '' },
        { timeframe: '4小时趋势', rsi: 0, signal: '' },
        { timeframe: '24小时趋势', rsi: 0, signal: '' },
      ]).map((item, i) => (
        <div key={i} className={styles.card}>
          <div className={styles.label}>{item.timeframe}</div>
          {loading && !item.signal ? (
            <div className={styles.placeholder}>--</div>
          ) : (
            <>
              <div className={`${styles.value} ${getSignalClass(item.signal)}`}>
                {item.signal}
              </div>
              <div className={styles.rsi}>RSI(14): {item.rsi}</div>
            </>
          )}
        </div>
      ))}
    </div>
  );
};
