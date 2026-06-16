import { useRef } from 'react';
import { Camera } from 'lucide-react';
import styles from './Avatar.module.css';

interface AvatarProps {
  src?: string;
  alt?: string;
  size?: number;
  editable?: boolean;
  onFileSelect?: (file: File) => void;
  className?: string;
  placeholder?: string;
}

export const Avatar = ({ 
  src, 
  alt = 'Avatar', 
  size = 60, 
  editable = false, 
  onFileSelect,
  className = '',
  placeholder
}: AvatarProps) => {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const getImageUrl = (path?: string) => {
    if (!path) return null;
    if (path.startsWith('http') || path.startsWith('blob:')) return path;
    return `/api${path}`;
  };

  const imageSrc = getImageUrl(src);
  // 如果没有图片源，且没有 placeholder 文字，使用默认头像
  const finalSrc = imageSrc || (!placeholder ? 'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix' : null);

  const handleClick = () => {
    if (editable && fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && onFileSelect) {
      onFileSelect(file);
    }
    // 重置 input，允许重复选择同一文件
    if (e.target) {
        e.target.value = '';
    }
  };

  return (
    <div 
      className={`${styles.container} ${className}`} 
      style={{ width: size, height: size, cursor: editable ? 'pointer' : 'default' }}
      onClick={handleClick}
    >
      {editable && (
        <input 
          type="file" 
          ref={fileInputRef} 
          className={styles.fileInput} 
          accept="image/*"
          onChange={handleFileChange}
        />
      )}
      
      {finalSrc ? (
        <img 
          src={finalSrc} 
          alt={alt} 
          className={styles.image} 
          style={{ width: size, height: size }}
        />
      ) : (
        <div 
          className={styles.placeholder}
          style={{ width: size, height: size, fontSize: size * 0.4 }}
        >
          {placeholder}
        </div>
      )}

      {editable && (
        <div className={styles.cameraIcon}>
          <Camera size={Math.max(12, size * 0.25)} color="#fff" />
        </div>
      )}
    </div>
  );
};
