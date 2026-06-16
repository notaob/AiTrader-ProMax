import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { authService } from '../services/auth';
import type { User } from '../types';

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

type AuthView = 'login' | 'register' | 'forgot-password' | null;

interface AuthContextType {
  user: User | null;
  authModalView: AuthView;
  openAuthModal: (view: AuthView) => void;
  closeAuthModal: () => void;
  login: (email: string, codeOrPassword: string, method: 'code' | 'password') => Promise<void>;
  logout: () => void;
  updateUser: (updates: Partial<User>) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(() => {
    // 初始化时从 localStorage 读取用户信息
    const savedUser = localStorage.getItem('user');
    try {
      if (!savedUser || savedUser === 'undefined') return null;
      const parsedUser = JSON.parse(savedUser);
      return parsedUser;
    } catch (error) {
      console.error('Failed to parse user from localStorage', error);
      localStorage.removeItem('user'); // 解析失败时清除脏数据
      return null;
    }
  });
  const [authModalView, setAuthModalView] = useState<AuthView>(null);

  const logout = () => {
    // 调用退出登录 API
    // 注意：必须在清除 localStorage 之前调用，否则 request 拦截器拿不到 token
    const token = localStorage.getItem('token');
    if (token) {
      authService.logout()
        .then(() => {
          console.log('Logout successful');
        })
        .catch((error) => {
          console.error('Logout failed:', error);
        })
        .finally(() => {
          // 无论后端请求成功与否，前端都要清除状态
          localStorage.removeItem('token');
          localStorage.removeItem('user');
          setUser(null);
        });
    } else {
      // 如果没有 token，直接清除本地状态
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      setUser(null);
    }
  };

  // App 初始化时，自动刷新用户信息
  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) {
      authService.getCurrentUser()
        .then(latestUser => {
          setUser(latestUser);
          localStorage.setItem('user', JSON.stringify(latestUser));
        })
        .catch(error => {
          console.error('Failed to refresh user info:', error);
          // 如果 token 过期或无效（401），应该自动登出
          // 这里简化处理：如果是 401 错误，清除本地状态
          if (error.message && error.message.includes('401')) {
            logout();
          }
        });
    }
  }, []);

  const openAuthModal = (view: AuthView) => setAuthModalView(view);
  const closeAuthModal = () => setAuthModalView(null);

  const login = async (email: string, codeOrPassword: string, method: 'code' | 'password') => {
    // 真实后端模式：直接发起请求，错误由 UI 层捕获处理
    let data;
    if (method === 'code') {
      data = await authService.loginByCode(email, codeOrPassword);
    } else {
      data = await authService.loginByPassword(email, codeOrPassword);
    }
    
    // 登录成功，保存 token
    const { token } = data; 
    localStorage.setItem('token', token);

    // 关键修复：登录后立即调用 /user/me 获取完整的用户信息（包括余额、积分等）
    // 因为 login 接口可能只返回了基础信息，没有返回资产数据
    try {
      const fullUserInfo = await authService.getCurrentUser();
      localStorage.setItem('user', JSON.stringify(fullUserInfo));
      setUser(fullUserInfo);
    } catch (error) {
      console.error('Failed to fetch full user info after login', error);
      // 降级策略：如果拉取失败，先用登录接口返回的残缺信息凑合用
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { token: _, ...partialUserInfo } = data;
      localStorage.setItem('user', JSON.stringify(partialUserInfo));
      setUser(partialUserInfo);
    }

    closeAuthModal();
  };

  const updateUser = (updates: Partial<User>) => {
    if (!user) return;
    const newUser = { ...user, ...updates };
    setUser(newUser);
    localStorage.setItem('user', JSON.stringify(newUser));
  };

  return (
    <AuthContext.Provider value={{ 
      user, 
      authModalView, 
      openAuthModal, 
      closeAuthModal,
      login,
      logout,
      updateUser
    }}>
      {children}
    </AuthContext.Provider>
  );
};


