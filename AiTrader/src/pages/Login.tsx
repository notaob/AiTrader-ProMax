import { useState, useEffect } from 'react';
import styles from './Auth.module.css';
import { useAuth } from '../context/AuthContext';
import { authService } from '../services/auth';

export const Login = () => {
  const { openAuthModal, login } = useAuth();
  const [loginMethod, setLoginMethod] = useState<'password' | 'code'>('password');
  const [countdown, setCountdown] = useState(0);
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let timer: number;
    if (countdown > 0) {
      timer = setInterval(() => {
        setCountdown((prev) => prev - 1);
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [countdown]);

  const sendCode = async () => {
    if (!phone) {
      alert('请输入手机号');
      return;
    }
    // 简单的手机号格式验证 (11位数字)
    if (!/^1\d{10}$/.test(phone)) {
      alert('请输入有效的手机号');
      return;
    }
    
    try {
      await authService.sendCode(phone);
      alert('验证码已发送');
    } catch (error) {
      console.error(error);
      alert('验证码发送失败，请重试');
    }
    setCountdown(60);
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (loading) return;

    setLoading(true);
    try {
      const credential = loginMethod === 'code' ? code : password;
      if (!credential) {
        alert(loginMethod === 'code' ? '请输入验证码' : '请输入密码');
        setLoading(false);
        return;
      }

      await login(phone, credential, loginMethod);
      
      // navigate('/'); // 移除这一行
      alert('登录成功！');
    } catch (error) {
      console.error(error);
      const message = error instanceof Error ? error.message : '登录失败，请重试';
      alert(message);
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = () => {
    openAuthModal('register');
  };

  const handleForgotPassword = () => {
    openAuthModal('forgot-password');
  };

  return (
    <div className={styles.container} style={{ minHeight: 'auto', padding: 0, width: '100%', background: 'transparent' }}>
      <h1 className={styles.title} style={{ marginTop: 0 }}>登录 AiTrader</h1>
      
      {/* 登录方式切换 Tab */}
      <div className={styles.tabs}>
        <span 
          className={`${styles.tab} ${loginMethod === 'password' ? styles.tabActive : ''}`}
          onClick={() => setLoginMethod('password')}
        >
          密码登录
        </span>
        <span 
          className={`${styles.tab} ${loginMethod === 'code' ? styles.tabActive : ''}`}
          onClick={() => setLoginMethod('code')}
        >
          验证码登录
        </span>
      </div>

      <form className={styles.form} onSubmit={handleLogin}>
        <div className={styles.inputGroup}>
          <label className={styles.label}>手机号码</label>
          <input 
            type="tel" 
            className={styles.input} 
            placeholder="请输入手机号" 
            required 
            pattern="[0-9]*" 
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
          />
        </div>
        
        {loginMethod === 'code' ? (
          <div className={`${styles.inputGroup} ${styles.tabContent}`} key="code">
            <label className={styles.label}>验证码</label>
            <div className={styles.codeInputWrapper}>
              <input 
                type="text" 
                className={`${styles.input} ${styles.flexInput}`} 
                placeholder="6位数字" 
                required 
                maxLength={6} 
                value={code}
                onChange={(e) => setCode(e.target.value)}
              />
              <button 
                type="button" 
                className={styles.codeButton} 
                onClick={sendCode}
                disabled={countdown > 0}
              >
                {countdown > 0 ? `${countdown}s 后重发` : '获取验证码'}
              </button>
            </div>
          </div>
        ) : (
          <div className={`${styles.inputGroup} ${styles.tabContent}`} key="password">
            <label className={styles.label}>密码</label>
            <input 
              type="password" 
              className={styles.input} 
              placeholder="请输入密码" 
              required 
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
        )}

        <button type="submit" className={styles.button} disabled={loading}>
          {loading ? '登 录 中...' : '登 录'}
        </button>

        <div className={styles.links}>
          <span className={styles.link} onClick={handleRegister}>注册新账号</span>
          <span className={styles.link} onClick={handleForgotPassword}>忘记密码？</span>
        </div>
      </form>
    </div>
  );
};
