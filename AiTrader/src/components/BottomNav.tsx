import { useNavigate, useLocation } from 'react-router-dom';
import { Home, Gift, Activity, User } from 'lucide-react';

export const BottomNav = () => {
  const navigate = useNavigate();
  const location = useLocation();

  // 在特定页面隐藏底部导航
  if (location.pathname === '/moments/new' || location.pathname === '/report') {
    return null;
  }

  const navItems = [
    { name: '首页', path: '/', icon: Home },
    { name: '特惠', path: '/deals', icon: Gift },
    { name: '动态', path: '/moments', icon: Activity },
    { name: '我的', path: '/me', icon: User },
  ];

  return (
    <div style={{
      position: 'fixed',
      bottom: 0,
      left: 0,
      right: 0,
      background: '#1a1a1a',
      borderTop: '1px solid #333',
      display: 'flex',
      justifyContent: 'space-around',
      padding: '10px 0',
      zIndex: 1000
    }}>
      {navItems.map((item) => {
        const isActive = location.pathname === item.path;
        return (
          <div 
            key={item.path}
            onClick={() => navigate(item.path)}
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              cursor: 'pointer',
              color: isActive ? '#4CAF50' : '#888'
            }}
          >
            <item.icon size={24} />
            <span style={{ fontSize: '12px', marginTop: '4px' }}>{item.name}</span>
          </div>
        );
      })}
    </div>
  );
};
