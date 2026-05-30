import { useNavigate } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';

export const About = () => {
  const navigate = useNavigate();

  return (
    <div style={{ padding: '20px', color: '#fff', minHeight: '100vh', background: '#121212' }}>
      <header style={{ display: 'flex', alignItems: 'center', marginBottom: '30px' }}>
        <ChevronLeft 
          onClick={() => navigate(-1)} 
          size={24} 
          style={{ cursor: 'pointer', marginRight: '15px' }} 
        />
        <h2 style={{ margin: 0 }}>关于我们</h2>
      </header>

      <div style={{ background: '#1e1e1e', padding: '20px', borderRadius: '8px' }}>
        <div style={{ textAlign: 'center', marginBottom: '20px' }}>
          <div style={{ 
            width: '80px', 
            height: '80px', 
            background: '#4CAF50', 
            borderRadius: '20px', 
            margin: '0 auto 15px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '32px',
            fontWeight: 'bold'
          }}>
            AI
          </div>
          <h3>AiTrader Pro</h3>
          <p style={{ color: '#888', fontSize: '14px' }}>Version 1.0.0</p>
        </div>

        <div style={{ borderTop: '1px solid #333', paddingTop: '20px' }}>
          <p style={{ lineHeight: '1.6', color: '#ddd' }}>
            AiTrader 是一个基于人工智能的模拟交易平台，旨在帮助用户在零风险的环境下学习加密货币交易策略。
          </p>
          <p style={{ lineHeight: '1.6', color: '#ddd', marginTop: '10px' }}>
            我们的使命是通过先进的 AI 技术，让每个人都能掌握投资技巧。
          </p>
        </div>
      </div>
      
      <div style={{ textAlign: 'center', marginTop: '40px', color: '#666', fontSize: '12px' }}>
        &copy; 2026 AiTrader Team. All rights reserved.
      </div>
    </div>
  );
};
