import styles from './AssetCard.module.css';

interface AssetCardProps {
  point?: number;
  aiChance?: number;
  large?: boolean;
}


export const UserStats = ({ point = 0, aiChance = 0, large = false }: AssetCardProps) => {
    return (
      <div className={styles.container} style={large ? { gap: '40px' } : undefined}>
        <div className={styles.item} style={large ? { flexDirection: 'column', alignItems: 'center' } : undefined}>
          <span className={styles.label} style={large ? { fontSize: '14px', marginBottom: '4px' } : undefined}>积分</span>
          <span className={styles.value} style={large ? { fontSize: '28px' } : undefined}>{point}</span>
        </div>
        <div className={styles.divider} style={large ? { height: '30px', margin: '0 10px' } : undefined} />
        <div className={styles.item} style={large ? { flexDirection: 'column', alignItems: 'center' } : undefined}>
          <span className={styles.label} style={large ? { fontSize: '14px', marginBottom: '4px' } : undefined}>AI 剩余次数</span>
          <span className={styles.value} style={large ? { fontSize: '28px' } : undefined}>{aiChance}</span>
        </div>
      </div>
    );
  };
