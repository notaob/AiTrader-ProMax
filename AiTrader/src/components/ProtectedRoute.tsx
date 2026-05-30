import { useEffect, type JSX } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

interface ProtectedRouteProps {
  children: JSX.Element;
}

export const ProtectedRoute = ({ children }: ProtectedRouteProps) => {
  const { user, openAuthModal } = useAuth();
  const location = useLocation();

  useEffect(() => {
    // 如果用户未登录，自动打开登录弹窗
    if (!user) {
      openAuthModal('login');
    }
  }, [user, openAuthModal]);

  if (!user) {
    // 如果未登录，重定向到首页，并保存当前尝试访问的路径（以便登录后跳回，这里先简化处理）
    // 使用 replace 避免用户点击后退时死循环
    return <Navigate to="/" replace state={{ from: location }} />;
  }

  // 如果已登录，正常渲染子组件
  return children;
};
