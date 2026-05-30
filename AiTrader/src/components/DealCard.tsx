import styles from './DealCard.module.css';

interface DealCardProps {
  title: string;
  description: string;
  actionText: string;
  actionColor?: string;
  onAction?: () => void;
}

export const DealCard = ({ 
  title, 
  description, 
  actionText, 
  actionColor = '#4CAF50',
  onAction 
}: DealCardProps) => {
  return (
    <div className={styles.card}>
      <div>
        <h3 className={styles.title}>{title}</h3>
        <p className={styles.description}>{description}</p>
      </div>
      <button 
        className={styles.button} 
        style={{ background: actionColor }}
        onClick={onAction}
      >
        {actionText}
      </button>
    </div>
  );
};