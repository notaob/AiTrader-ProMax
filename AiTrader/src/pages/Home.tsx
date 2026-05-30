import { useState, useEffect} from 'react';
import { useNavigate } from 'react-router-dom';
import { TradingChart } from '../components/TradingChart';
import { AIChat } from '../components/AIChat';
import { Avatar } from '../components/Avatar';
import { useAuth } from '../context/AuthContext';
import { useInterval } from '../hooks/useInterval';
import { cryptoService } from '../services/crypto';
import type { ChartData } from '../types';

import { UserStats } from '../components/AssetCard';
import { MarketSentiment } from '../components/MarketSentiment';

export const Home = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [currentPrice, setCurrentPrice] = useState(0);
  const [chartData, setChartData] = useState<ChartData[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // 初始加载
  useEffect(() => {
    // 提取数据获取逻辑，方便复用
    const fetchAllData = async (isFirstLoad = false) => {
      try {
        // 并行请求
        const [history, price] = await Promise.allSettled([
          cryptoService.getBtcHistory(),
          cryptoService.getBtcPrice()
        ]);

        let hasSuccess = false;

        // 处理 K 线数据
        if (history.status === 'fulfilled') {
          console.log('Raw history data:', history.value); // Debug log
          // 过滤无效数据，防止图表渲染失败
          const validData = history.value.filter(item => 
            item && 
            typeof item.price === 'number' && 
            !isNaN(item.price)
          );
          console.log('Valid chart data:', validData); // Debug log
          
          if (validData.length > 0) {
            setChartData(validData);
            hasSuccess = true;
          } else {
            console.warn('Fetched history data is empty or invalid');
          }
        } else {
          console.warn('History fetch failed:', history.reason);
        }
        
        // 处理实时价格
        if (price.status === 'fulfilled') {
          const p = price.value;
          if (typeof p === 'number' && !isNaN(p)) {
            setCurrentPrice(p);
            hasSuccess = true;
          }
        } else {
          console.warn('Price fetch failed:', price.reason);
          // 兜底：如果实时价格失败，尝试用 K 线最新数据
          if (history.status === 'fulfilled' && history.value.length > 0) {
            const lastPrice = history.value[history.value.length - 1].price;
            if (typeof lastPrice === 'number' && !isNaN(lastPrice)) {
              setCurrentPrice(lastPrice);
            }
          }
        }

        if (isFirstLoad && hasSuccess) {
          setIsLoading(false);
        }

      } catch (error) {
        console.error('Data fetch error:', error);
      }
    };

    // 这里的调用是异步的，符合 React 规范
    fetchAllData(true);
  }, []);


  // 订阅 WebSocket 实时价格
  useEffect(() => {
    // 初始获取一次
    const initFetch = async () => {
      try {
        const price = await cryptoService.getBtcPrice();
        if (price > 0) setCurrentPrice(price);
      } catch (e) {
        console.warn('Initial price fetch failed', e);
      }
    };
    initFetch();

    // 建立 WebSocket 连接
    const unsubscribe = cryptoService.subscribeToBtcPrice((price) => {
      setCurrentPrice(price);
      setIsLoading(false);
    });

    // 组件卸载时断开连接
    return () => {
      unsubscribe();
    };
  }, []);

  // 轮询：K 线图 (改为 60s，因为历史数据更新不需要太频繁)
  useInterval(async () => {
    try {
      const history = await cryptoService.getBtcHistory();
      // 简单去重和校验
      const validData = history.filter(item => typeof item.price === 'number' && !isNaN(item.price));
      if (validData.length > 0) {
        setChartData(validData);
      }
    } catch (error) {
      console.warn('Auto-refresh chart failed:', error);
    }
  }, 60000);
  
  return (
    <div style={{ paddingBottom: '60px' }}>
      <header className="header" style={{ padding: '20px 40px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1 className="header-title" style={{ margin: 0 }}>AI Trader Pro</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          {user && (
            <div style={{ marginRight: '20px' }}>
               <UserStats point={user.point || 0} aiChance={user.aiChance || 0} />
            </div>
          )}
          <div onClick={() => navigate('/me')} style={{ cursor: 'pointer' }}>
            <Avatar src={user?.icon} size={40} placeholder="AI" />
          </div>
        </div>
      </header>

      <div className="main-content">
        <div className="chart-section">
          {isLoading ? (
            <div style={{ height: '300px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#888' }}>
              加载行情数据中...
            </div>
          ) : chartData.length > 0 ? (
            <TradingChart data={chartData} />
          ) : (
            <div style={{ height: '300px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', color: '#888', textAlign: 'center' }}>
              <p>暂无 K 线数据</p>
              <p style={{ fontSize: '12px', color: '#666' }}>
                无法加载数据，请检查网络连接或 CORS 设置。
              </p>
            </div>
          )}
          
          <div style={{ marginTop: '20px', padding: '20px', background: '#1e1e1e', borderRadius: '8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <div style={{ color: '#888', marginBottom: '5px' }}>实时价格</div>
              <div style={{ fontSize: '24px', fontWeight: 'bold' }}>
                {currentPrice > 0 ? `$${currentPrice.toLocaleString()}` : '---'}
              </div>
            </div>
          </div>

          <MarketSentiment />
        </div>
        
        <div className="chat-section">
          <AIChat />
        </div>
      </div>
    </div>
  );
};
