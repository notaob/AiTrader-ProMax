import { request } from './request';
import type { Post } from '../types';

// 定义 API 返回的列表结构，假设是分页的或者直接是数组
// 根据 request.ts，request<T> 返回的是 data 字段
// 这里假设 GET /moments/list 返回的是 Post[]
// 如果是分页结构 { list: Post[], total: number }，需要调整

export const momentsService = {
  // 获取动态列表
  getList: () => {
    return request<Post[]>('/moments/list', {
      method: 'GET',
    });
  },

  // 发布动态
  create: (content: string) => {
    return request<Post>('/moments/create', {
      method: 'POST',
      body: JSON.stringify({ content }),
    });
  },

  // 点赞/取消点赞
  toggleLike: (id: number) => {
    return request<{ isLiked: boolean; likes: number }>('/moments/like', {
      method: 'POST',
      body: JSON.stringify({ id }),
    });
  },
};
