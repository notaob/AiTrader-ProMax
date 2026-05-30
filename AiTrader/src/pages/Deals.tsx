import { useQuery, useMutation } from '@tanstack/react-query';
import { DealCard } from '../components/DealCard';
import { marketService, type Promotion } from '../services/market';
import { useAuth } from '../context/AuthContext';
import { authService } from '../services/auth';

export const Deals = () => {
  const { user, openAuthModal, updateUser } = useAuth();

  // 1. 使用 useQuery 获取活动列表
  const { data: promotions = [], isLoading, error } = useQuery({
    queryKey: ['promotions'],
    queryFn: marketService.getPromotions,
    staleTime: 1000 * 60 * 5, // 5分钟缓存
  });

  // 2. 封装刷新用户信息的逻辑
  const refreshUser = async () => {
    const updatedUser = await authService.getCurrentUser();
    updateUser(updatedUser);
  };

  // 3. 使用 useMutation 处理领取礼包
  const claimGiftMutation = useMutation({
    mutationFn: marketService.claimWelcomeGift,
    onSuccess: async () => {
      alert('领取成功！已获得10 次 AI 咨询机会');
      await refreshUser();
    },
    onError: (err) => {
      console.error('Claim gift failed', err);
      alert(err instanceof Error ? err.message : '领取失败，请重试');
    },
  });

  // 4. 使用 useMutation 处理积分兑换
  const exchangeMutation = useMutation({
    mutationFn: (points: number) => marketService.exchangeAiChance(points),
    onSuccess: async () => {
      alert('兑换成功！');
      await refreshUser();
    },
    onError: (err) => {
      console.error('Exchange failed', err);
      alert(err instanceof Error ? err.message : '兑换失败，请重试');
    },
  });

  const handleAction = async (promo: Promotion) => {
    if (!user) {
      openAuthModal('login');
      return;
    }

    if (promo.type === 'gift') {
      // 领取新手礼包
      if (claimGiftMutation.isPending) return; // 防止重复点击
      claimGiftMutation.mutate();
    } else if (promo.type === 'exchange') {
      // 积分兑换
      if (!promo.requiredPoints) return;

      if ((user.point || 0) < promo.requiredPoints) {
        alert(`积分不足！需要 ${promo.requiredPoints} 积分`);
        return;
      }

      if (window.confirm(`确定消耗 ${promo.requiredPoints} 积分兑换 1 次 AI 机会吗？`)) {
        if (exchangeMutation.isPending) return;
        exchangeMutation.mutate(promo.requiredPoints);
      }
    } else {
      alert('该功能开发中...');
    }
  };

  if (error) {
    return <div style={{ color: 'red', padding: '20px' }}>加载活动失败，请稍后重试</div>;
  }

  return (
    <div style={{ padding: '20px', color: '#fff', paddingBottom: '80px' }}>
      <h2>特惠活动</h2>
      {isLoading ? (
        <div style={{ color: '#888', textAlign: 'center', marginTop: '40px' }}>加载活动中...</div>
      ) : (
        promotions.map((promo) => (
          <DealCard
            key={promo.id}
            title={promo.title}
            description={promo.description}
            actionText={
              // 如果正在操作这个卡片，显示 loading 状态 (简单的实现)
              (promo.type === 'gift' && claimGiftMutation.isPending) ||
              (promo.type === 'exchange' && exchangeMutation.isPending)
                ? '处理中...'
                : promo.actionText
            }
            actionColor={promo.actionColor}
            onAction={() => handleAction(promo)}
          />
        ))
      )}
    </div>
  );
};
