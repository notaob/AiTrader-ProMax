import React, { useState, useEffect } from 'react';
import styles from './Auth.module.css';
import { useAuth } from '../context/AuthContext';
import { authService } from '../services/auth';

export const ForgotPassword = () => {
  const { openAuthModal } = useAuth();
  const [countdown, setCountdown] = useState(0);
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [step, setStep] = useState<1 | 2>(1);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

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
    if (!/^1\d{10}$/.test(phone)) {
      alert('请输入有效的手机号');
      return;
    }
    
    try {
      const code = await authService.sendCode(phone);
      alert(`验证码已发送: ${code}`);
      setCountdown(60);
    } catch (error) {
      console.error(error);
      alert('验证码发送失败，请重试');
    }
  };

  const handleNextStep = (e: React.FormEvent) => {
    e.preventDefault();
    if (!code || !/^\d{6}$/.test(code)) {
      alert('请输入有效的6位验证码');
      return;
    }
    // 前端简单校验通过，进入第二步设置密码
    // 注意：实际验证码校验通常在最后提交时由后端统一处理，或者这里可以调一个 verifyCode 接口
    setStep(2);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password !== confirmPassword) {
      alert('两次输入的密码不一致，请重新输入');
      return;
    }
    
    try {
      await authService.resetPassword(phone, code, password);
      alert('密码重置成功，请重新登录');
      openAuthModal('login');
    } catch (error) {
      console.error(error);
      const message = error instanceof Error ? error.message : '重置失败，请重试';
      alert(message);
    }
  };

  const handleLogin = () => {
    openAuthModal('login');
  };

  return (
    <div className={styles.container} style={{ minHeight: 'auto', padding: 0, width: '100%', background: 'transparent' }}>
      <h1 className={styles.title} style={{ marginTop: 0 }}>重置密码</h1>
      {
        step === 1 ? (
        <form className={`${styles.form} ${styles.tabContent}`} key="step1" onSubmit={handleNextStep}>
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

          <div className={styles.inputGroup}>
            <label className={styles.label}>验证码</label>
            <div className={styles.codeInputWrapper}>
              <input 
                type="text" 
                className={styles.input} 
                placeholder="6位数字" 
                required 
                style={{ flex: 1 }} 
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
          <button type="submit" className={styles.button}>下一步</button>
        </form>
      ) :(
        <form className={`${styles.form} ${styles.tabContent}`} key="step2" onSubmit={handleSubmit}>
          <div className={styles.inputGroup}>
            <label className={styles.label}>新密码</label>
            <input 
              type="password" 
              className={styles.input} 
              placeholder="设置新密码 (至少6位)" 
              required 
              minLength={6}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <div className={styles.inputGroup}>
            <label className={styles.label}>确认新密码</label>
            <input 
              type="password" 
              className={styles.input} 
              placeholder="请再次输入新密码" 
              required 
              minLength={6}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', gap: '10px', justifyContent: 'center' }}>
            <button 
              type="button" 
              className={styles.button} 
              style={{ background: '#333', color: '#ccc' }}
              onClick={() => setStep(1)}
            >
              上一步
            </button>
            <button type="submit" className={styles.button}>确认重置</button>
          </div>
        </form>
      )}

      <div className={styles.links} style={{ justifyContent: 'center', marginTop: '20px' }}>
        <span className={styles.link} onClick={handleLogin}>返回登录</span>
      </div>
    </div>
  );
};
