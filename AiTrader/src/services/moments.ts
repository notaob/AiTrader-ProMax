import { request } from './request';
import type { Post, Comment } from '../types';

export const momentsService = {
  // 获取动态列表（分页）
  getList: (page = 1, size = 10) => {
    return request<Post[]>('/moments/list', {
      method: 'GET',
      params: { page: String(page), size: String(size) },
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

  // 获取评论列表
  getComments: (momentId: number) => {
    return request<Comment[]>(`/moments/${momentId}/comments`, {
      method: 'GET',
    });
  },

  // 发表评论
  addComment: (momentId: number, content: string) => {
    return request<Comment>('/moments/comment', {
      method: 'POST',
      body: JSON.stringify({ momentId, content }),
    });
  },
};
