import { useNavigate } from 'react-router-dom';
import { Heart, MessageCircle, Share2, Plus } from 'lucide-react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import styles from './Moments.module.css';
import { Avatar } from '../components/Avatar';
import { useAuth } from '../context/AuthContext';
import { momentsService } from '../services/moments';
import type { Post } from '../types';

export const Moments = () => {
  const navigate = useNavigate();
  const { user, openAuthModal } = useAuth();
  const queryClient = useQueryClient();

  // 1. 使用 useQuery 获取动态列表
  const { data: posts = [], isLoading, error } = useQuery({
    queryKey: ['moments'],
    queryFn: momentsService.getList,
    staleTime: 1000 * 60, // 1分钟缓存
  });

  // 2. 使用 useMutation 处理点赞
  const likePostMutation = useMutation({
    mutationFn: momentsService.toggleLike,
    onMutate: async (id) => {
      // 乐观更新
      await queryClient.cancelQueries({ queryKey: ['moments'] });
      const previousPosts = queryClient.getQueryData<Post[]>(['moments']);

      queryClient.setQueryData(['moments'], (old: Post[] | undefined) => {
        return old?.map((post) => {
          if (post.id === id) {
            return {
              ...post,
              isLiked: !post.isLiked,
              likes: post.isLiked ? post.likes - 1 : post.likes + 1,
            };
          }
          return post;
        });
      });

      return { previousPosts };
    },
    onError: (_err, _id, context) => {
      if (context?.previousPosts) {
        queryClient.setQueryData(['moments'], context.previousPosts);
      }
    },
    onSettled: () => {
      // queryClient.invalidateQueries({ queryKey: ['moments'] });
    },
  });

  const handleAddPost = () => {
    if (!user) {
      openAuthModal('login');
      return;
    }
    navigate('/moments/new');
  };

  const handleLike = (id: number) => {
    if (!user) {
      openAuthModal('login');
      return;
    }
    likePostMutation.mutate(id);
  };

  if (error) {
    return <div style={{ padding: '20px', textAlign: 'center', color: 'red' }}>加载失败，请重试</div>;
  }

  return (
    <div className={styles.container}>
      <h2 className={styles.header}>
        市场动态
        <button className={styles.addMomentButton} onClick={handleAddPost}>
          <Plus size={16} style={{ marginRight: '4px' }} />
          发布动态
        </button>
      </h2>
      
      {isLoading ? (
        <div style={{ padding: '20px', textAlign: 'center', color: '#888' }}>
          加载中...
        </div>
      ) : (
        posts.map(post => (
          <div key={post.id} className={styles.postCard}>
          {/* 用户信息区 */}
          <div className={styles.userInfo}>
            <Avatar src={post.userAvatar} size={40} placeholder={post.userName[0]} className={styles.userAvatar} />
            <div>
              <div className={styles.userName}>{post.userName}</div>
              <div className={styles.postTime}>{post.time}</div>
            </div>
          </div>

          {/* 内容区 */}
          <div className={styles.content}>
            {post.content}
          </div>

          {/* 底部操作栏 */}
          <div className={styles.actions}>
            {/* 点赞按钮 */}
            <div 
              className={`${styles.actionItem} ${post.isLiked ? styles.liked : ''}`}
              onClick={() => handleLike(post.id)}
            >
              <Heart  
                size={18} 
                fill={post.isLiked ? "currentColor" : "none"} // 点赞后填充颜色
              />
              <span>{post.likes}</span>
            </div>

            {/* 评论按钮 */}
            <div className={styles.actionItem}>
              <MessageCircle size={18} />
              <span>{post.comments}</span>
            </div>

            {/* 分享按钮 */}
            <div className={styles.actionItem}>
              <Share2 size={18} />
              <span>分享</span>
            </div>
          </div>
        </div>
      ))
      )}
    </div>
  );
};
