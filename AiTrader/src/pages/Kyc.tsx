import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronLeft, Check, ShieldCheck } from 'lucide-react';
import styles from './Kyc.module.css';

export const Kyc = () => {
  const navigate = useNavigate();
  
  // 状态管理：表单数据
  const [formData, setFormData] = useState({
    realName: '',
    idNumber: ''
  });

  // 状态管理：认证状态 (unverified | processing | verified)
  const [status, setStatus] = useState<'unverified' | 'processing' | 'verified'>('unverified');

  // 处理输入框变化
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  // 处理表单提交
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault(); // 阻止默认的表单提交刷新页面行为
    
    if (!formData.realName || !formData.idNumber) {
      alert('请填写完整信息');
      return;
    }

    setStatus('processing');

    // 模拟网络请求
    setTimeout(() => {
      setStatus('verified');
      alert('认证成功！');
    }, 1500);
  };

  return (
    <div className={styles.container}>
      {/* 头部导航 */}
      <header className={styles.header}>
        <ChevronLeft 
          className={styles.backButton}
          onClick={() => navigate(-1)} 
          size={24} 
        />
        <h2 style={{ margin: 0 }}>身份认证</h2>
      </header>

      {/* 根据状态显示不同内容 */}
      {status === 'verified' ? (
        <div className={styles.statusCard}>
          <div className={styles.verifiedIcon}>
            <Check size={32} />
          </div>
          <h3>已完成认证</h3>
          <p style={{ color: '#888', marginBottom: '20px' }}>您已通过实名认证，享有所有交易权限。</p>
          
          <div style={{ background: '#2c2c2c', padding: '15px', borderRadius: '8px', textAlign: 'left' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
              <span style={{ color: '#888' }}>姓名</span>
              <span>{formData.realName}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: '#888' }}>证件号</span>
              <span>{formData.idNumber.replace(/^(.{4})(?:\d+)(.{4})$/, "$1******$2")}</span>
            </div>
          </div>

          <button 
            className={styles.submitButton} 
            onClick={() => navigate('/me')}
            style={{ background: '#333', marginTop: '30px' }}
          >
            返回个人中心
          </button>
        </div>
      ) : (
        <div className={styles.form}>
          <div style={{ textAlign: 'center', marginBottom: '20px' }}>
            <ShieldCheck size={48} color="#4CAF50" style={{ marginBottom: '10px' }} />
            <p style={{ color: '#888', fontSize: '14px' }}>为了保障您的资金安全，请完成实名认证</p>
          </div>

          <form onSubmit={handleSubmit}>
            <div className={styles.formGroup}>
              <label className={styles.label}>真实姓名</label>
              <input 
                type="text" 
                name="realName"
                className={styles.input}
                placeholder="请输入您的真实姓名"
                value={formData.realName}
                onChange={handleChange}
                disabled={status === 'processing'}
              />
            </div>

            <div className={styles.formGroup}>
              <label className={styles.label}>身份证号</label>
              <input 
                type="text" 
                name="idNumber"
                className={styles.input}
                placeholder="请输入18位身份证号码"
                value={formData.idNumber}
                onChange={handleChange}
                disabled={status === 'processing'}
              />
            </div>

            <button 
              type="submit" 
              className={styles.submitButton}
              disabled={status === 'processing'}
              style={{ opacity: status === 'processing' ? 0.7 : 1 }}
            >
              {status === 'processing' ? '认证中...' : '立即认证'}
            </button>
          </form>
        </div>
      )}
    </div>
  );
};
