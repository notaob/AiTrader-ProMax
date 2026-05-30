import { request } from './request';

export interface Promotion {
  id: number;
  title: string;
  description: string;
  actionText: string;
  actionColor: string;
  type: 'gift' | 'exchange' | 'recharge'; // 区分活动类型
  requiredPoints?: number; // 兑换所需的积分
}

export const marketService = {
  // 获取活动列表
  getPromotions: () => {
    return request<Promotion[]>(
      '/market/promotions',
      {
        method: 'GET'
      }
    );
  },

  // 领取新手礼包，10000USDT 和 10 次 AI 机会
  claimWelcomeGift: () => {
    return request<void>('/market/gift/claim', {
      method: 'POST'
    });
  },

  // 积分兑换 AI 次数，每个1000积分可兑换1次
  exchangeAiChance: (points: number) => {
    return request<void>('/market/exchange/ai', {
      method: 'POST',
      body: JSON.stringify({ points })
    });
  }
};