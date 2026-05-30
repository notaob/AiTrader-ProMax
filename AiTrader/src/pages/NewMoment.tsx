import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronLeft, Image, Hash, AtSign } from 'lucide-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import styles from './NewMoment.module.css';
import { momentsService } from '../services/moments';
import type { Post } from '../types';

export const NewMoment = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [content, setContent] = useState('');

  // 使用 useMutation 处理发帖
  const addPostMutation = useMutation({
    mutationFn: momentsService.create,
    onSuccess: (newPost) => {
      // 这里的逻辑很关键：我们直接更新 'moments' 这个 Query Key 的缓存
      // 这样用户跳转回列表页时，无需重新请求网络，就能看到新发的帖子
      queryClient.setQueryData(['moments'], (old: Post[] | undefined) => {
        return [newPost, ...(old || [])];
      });
      
      alert('发布成功！');
      navigate('/moments');
    },
    onError: (error) => {
      alert('发布失败，请重试');
      console.error(error);
    }
  });

  const handlePublish = async () => {
    if (!content.trim() || addPostMutation.isPending) return;
    addPostMutation.mutate(content);
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div onClick={() => navigate(-1)} style={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
          <ChevronLeft className={styles.backButton} size={24} />
          <span style={{ marginLeft: '4px', fontSize: '16px', fontWeight: 'bold' }}>取消</span>
        </div>
        <button 
          className={styles.publishButton}
          disabled={!content.trim() || addPostMutation.isPending}
          onClick={handlePublish}
        >
          {addPostMutation.isPending ? '发布中...' : '发布'}
        </button>
      </header>

      <div className={styles.editorArea}>
        <textarea
          className={styles.textarea}
          placeholder="分享你的交易心得、市场分析或生活点滴..."
          value={content}
          onChange={(e) => setContent(e.target.value)}
          autoFocus
        />
        
        <div className={styles.toolbar}>
          <div className={styles.toolItem}>
            <Image size={20} />
            <span>图片</span>
          </div>
          <div className={styles.toolItem}>
            <Hash size={20} />
            <span>话题</span>
          </div>
          <div className={styles.toolItem}>
            <AtSign size={20} />
            <span>提及</span>
          </div>
        </div>
      </div>
    </div>
  );
};
