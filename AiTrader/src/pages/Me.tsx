import { useState } from 'react';
import { useNavigate } from "react-router-dom";
import { Lock, Info, ChevronRight, Edit2 } from "lucide-react"; 
import { UserStats } from '../components/AssetCard'; 
import { Avatar } from '../components/Avatar';
import type { MenuItem } from '../types';
import styles from './Me.module.css';
import { useAuth } from '../context/AuthContext';
import { authService } from '../services/auth';


export const Me = () => {
  const navigate = useNavigate();
  const { openAuthModal, user, logout, updateUser } = useAuth();
  
  const [isEditingName, setIsEditingName] = useState(false);
  const [tempName, setTempName] = useState('');

  const menuItems: MenuItem[] = [
    { icon: Lock, label: '安全中心', path: '/security' },
    { icon: Info, label: '关于我们', path: '/about' },
  ];

  const handleLogout = () => {
    // JS 知识点：window.confirm 是浏览器原生的确认框
    if (window.confirm('确定要退出当前账号吗？')) {
      logout();
      alert("退出成功");
      navigate('/');
    }
  };

  // 处理文件上传
  const handleUpload = async (file: File) => {
    if (!user) return;

    // 乐观更新 (Optimistic UI)
    const objectUrl = URL.createObjectURL(file);
    const oldIcon = user.icon;
    updateUser({ icon: objectUrl });

    try {
      const formData = new FormData();
      formData.append('file', file);
      // 真实上传
      const url = await authService.uploadAvatar(formData);
      updateUser({ icon: url });
    } catch (error) {
      console.error('Upload failed', error);
      updateUser({ icon: oldIcon }); // 回滚
      alert('上传头像失败');
    }
  };

  // 开始编辑昵称
  const startEditName = () => {
    if (!user) return;
    setTempName(user.nickName);
    setIsEditingName(true);
  };

  // 保存昵称
  const saveName = async () => {
    if (!user || !tempName.trim()) {
      setIsEditingName(false);
      return;
    }
    
    if (tempName === user.nickName) {
      setIsEditingName(false);
      return;
    }
    
    const oldName = user.nickName;
    // 乐观更新
    updateUser({ nickName: tempName });
    setIsEditingName(false);

    try {
      await authService.updateUserInfo(tempName);
    } catch (error) {
      console.error('Update name failed', error);
      updateUser({ nickName: oldName }); // 回滚
      alert('修改昵称失败');
    }
  };

  const handleNameKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      saveName();
    }
  };

  const handleMenuItemClick = (path: string) => {
    // 需要登录才能访问的页面列表
    const protectedPaths = ['/security'];
    
    if (protectedPaths.includes(path) && !user) {
      openAuthModal('login');
      return;
    }
    
    navigate(path);
  };

  return (
    // HTML 知识点：最外层通常还是用 div，但可以用语义化的 class
    <div className={styles.container}>
      {/* HTML 知识点：头部信息用 <header> */}
      <header className={styles.header}>
        {user ? (
          <>
            <Avatar 
              src={user.icon} 
              alt={user.nickName} 
              editable 
              onFileSelect={handleUpload}
              className={styles.avatarWrapper}
            />
            
            <div className={styles.userInfo}>
              {isEditingName ? (
                <input
                  type="text"
                  value={tempName}
                  onChange={(e) => setTempName(e.target.value)}
                  onBlur={saveName}
                  onKeyDown={handleNameKeyDown}
                  autoFocus
                  style={{
                    fontSize: '18px',
                    padding: '4px 8px',
                    borderRadius: '4px',
                    border: '1px solid #ddd',
                    width: '150px'
                  }}
                />
              ) : (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <div 
                    className={styles.nicknameWrapper}
                    onClick={startEditName}
                  >
                    <h2 style={{ margin: 0, fontSize: '18px' }}>
                      {user.nickName}
                    </h2>
                  </div>
                  <Edit2 
                    size={14} 
                    color="#888" 
                    style={{ cursor: 'pointer' }}
                    onClick={startEditName}
                  />
                </div>
              )}
              
              <span style={{ fontSize: '12px', color: '#888' }}>UID: {user.id}</span>
            </div>
          </>
        ) : (
          <>
            <Avatar placeholder="AI" className={styles.avatarWrapper} />
            <div className={styles.userInfo}>
              <button 
                onClick={() => openAuthModal('login')}
                className={styles.loginButton}
              >
                点击登录
              </button>
            </div>
          </>
        )}
      </header>

      {/* HTML 知识点：独立的内容区块用 <section> */}
      {user && (
        <div className={styles.assetLabel}>
          <div style={{}}>
            <UserStats point={user.point || 0} aiChance={user.aiChance || 0} large />
          </div>
        </div>
      )}

      {/* HTML 知识点：菜单列表用 <ul> (Unordered List) */}
      <ul className={styles.menuList}>
        {menuItems.map((item, i) => (
          // HTML 知识点：列表项用 <li> (List Item)
          <li key={i} className={styles.menuItem} onClick={() => handleMenuItemClick(item.path)}>
            <div className={styles.menuItemLeft}>
              <item.icon size={20} className={styles.menuIcon} />
              <span>{item.label}</span>
            </div>
            <ChevronRight size={20} className={styles.arrow} />
          </li>
        ))}
      </ul>

      {user && (
        <button className={styles.logoutButton} onClick={handleLogout}>
          退出登录
        </button>
      )}
    </div>
  );
};
