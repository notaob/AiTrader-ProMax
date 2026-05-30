import styles from './MarketSentiment.module.css';

export const MarketSentiment = () => {
  const getSignalClass = (signal: string) => {
    if (signal === '强力买入') return styles.buy;
    if (signal === '卖出') return styles.sell;
    return styles.neutral;
  };

  return (
    <div className={styles.container}>
      {['强力买入', '中性', '卖出'].map((signal, i) => (
        <div key={i} className={styles.card}>
          <div className={styles.label}>
            {['1小时趋势', '4小时趋势', '24小时趋势'][i]}
          </div>
          <div className={`${styles.value} ${getSignalClass(signal)}`}>
            {signal}
          </div>
        </div>
      ))}
    </div>
  );
};
