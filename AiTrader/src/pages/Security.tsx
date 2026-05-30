import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronLeft, Lock } from 'lucide-react';
import styles from './Security.module.css';
import { authService } from '../services/auth';
import { useAuth } from '../context/AuthContext';

export const Security = () => {
  const navigate = useNavigate();
  const { logout } = useAuth();
  
  // 修改密码的状态
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isChangingPassword, setIsChangingPassword] = useState(false);

  const handleChangePassword = async () => {
    if (!oldPassword || !newPassword || !confirmPassword) {
      alert('请填写所有密码字段');
      return;
    }

    if (newPassword !== confirmPassword) {
      alert('两次输入的新密码不一致');
      return;
    }

    if (newPassword.length < 6) {
      alert('新密码长度不能少于6位');
      return;
    }

    setIsChangingPassword(true);
    try {
      await authService.changePassword(oldPassword, newPassword);
      alert('密码修改成功，请重新登录');
      logout();
      navigate('/');
    } catch (error) {
      console.error(error);
      alert('密码修改失败，请检查旧密码是否正确');
    } finally {
      setIsChangingPassword(false);
    }
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <ChevronLeft 
          className={styles.backButton} 
          size={24} 
          onClick={() => navigate(-1)} 
        />
        <h1 className={styles.title}>安全中心</h1>
      </header>

      {/* 修改密码区块 */}
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>
          <Lock size={20} color="#007AFF" />
          修改登录密码
        </h2>
        
        <div className={styles.formGroup}>
          <label className={styles.label}>旧密码</label>
          <input
            type="password"
            className={styles.input}
            placeholder="请输入当前密码"
            value={oldPassword}
            onChange={(e) => setOldPassword(e.target.value)}
          />
        </div>

        <div className={styles.formGroup}>
          <label className={styles.label}>新密码</label>
          <input
            type="password"
            className={styles.input}
            placeholder="请输入新密码 (至少6位)"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
        </div>

        <div className={styles.formGroup}>
          <label className={styles.label}>确认新密码</label>
          <input
            type="password"
            className={styles.input}
            placeholder="请再次输入新密码"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />
        </div>

        <button 
          className={styles.button}
          onClick={handleChangePassword}
          disabled={isChangingPassword}
        >
          {isChangingPassword ? '提交中...' : '确认修改'}
        </button>
      </section>
    </div>
  );
};
