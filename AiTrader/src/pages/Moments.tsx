import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Heart, MessageCircle, Share2, Plus, Send, RefreshCw } from 'lucide-react';
import { useMutation } from '@tanstack/react-query';
import styles from './Moments.module.css';
import { Avatar } from '../components/Avatar';
import { useAuth } from '../context/AuthContext';
import { momentsService } from '../services/moments';
import type { Post, Comment } from '../types';

const PAGE_SIZE = 10;

/* ── Skeleton Card ── */
const SkeletonCard = () => (
  <div className={styles.skeletonCard}>
    <div className={styles.skeletonRow}>
      <div className={styles.skeletonCircle} />
      <div style={{ flex: 1 }}>
        <div className={`${styles.skeletonBar} ${styles.skeletonBarShort}`} />
        <div className={`${styles.skeletonBar} ${styles.skeletonBarThin}`} style={{ width: 120 }} />
      </div>
    </div>
    <div className={`${styles.skeletonBar} ${styles.skeletonBarLong}`} />
    <div className={`${styles.skeletonBar} ${styles.skeletonBarMedium}`} style={{ marginTop: 6 }} />
    <div className={styles.skeletonActions}>
      <div className={styles.skeletonActionDot} />
      <div className={styles.skeletonActionDot} />
      <div className={styles.skeletonActionDot} />
    </div>
  </div>
);

export const Moments = () => {
  const navigate = useNavigate();
  const { user, openAuthModal } = useAuth();

  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const pageRef = useRef(1);

  // 评论展开状态
  const [expandedPostId, setExpandedPostId] = useState<number | null>(null);
  const [commentsMap, setCommentsMap] = useState<Map<number, Comment[]>>(new Map());
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [commentInput, setCommentInput] = useState('');
  const [sendingComment, setSendingComment] = useState(false);

  // 点赞动画状态
  const [likeAnimId, setLikeAnimId] = useState<number | null>(null);
  const [likeParticles, setLikeParticles] = useState<{ id: number; postId: number; x: number; y: number }[]>([]);
  const [commentBounceId, setCommentBounceId] = useState<number | null>(null);

  // 刷新动画
  const [refreshing, setRefreshing] = useState(false);

  const sentinelRef = useRef<HTMLDivElement>(null);
  const commentInputRef = useRef<HTMLInputElement>(null);
  const particleIdRef = useRef(0);

  // 加载首页
  useEffect(() => {
    loadPage(1, false);
  }, []);

  const loadPage = useCallback(async (page: number, append: boolean) => {
    if (append) setLoadingMore(true);
    else setLoading(true);

    try {
      const data = await momentsService.getList(page, PAGE_SIZE);
      if (append) {
        setPosts(prev => [...prev, ...data]);
      } else {
        setPosts(data);
      }
      setHasMore(data.length >= PAGE_SIZE);
      pageRef.current = page;
    } catch (err) {
      console.error('加载动态失败:', err);
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, []);

  // 刷新
  const handleRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    setExpandedPostId(null);
    setCommentsMap(new Map());
    await loadPage(1, false);
    setTimeout(() => setRefreshing(false), 600);
  };

  // 无限滚动 — IntersectionObserver
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !loadingMore && !loading) {
          loadPage(pageRef.current + 1, true);
        }
      },
      { rootMargin: '200px' }
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMore, loadingMore, loading, loadPage]);

  // 点赞 mutation（乐观更新）
  const likeMutation = useMutation({
    mutationFn: momentsService.toggleLike,
    onMutate: async (id) => {
      setPosts(prev => prev.map(p =>
        p.id === id ? { ...p, isLiked: !p.isLiked, likes: p.isLiked ? p.likes - 1 : p.likes + 1 } : p
      ));
    },
    onError: () => {
      // 静默失败
    },
  });

  const handleLike = (id: number, e: React.MouseEvent) => {
    if (!user) { openAuthModal('login'); return; }

    setLikeAnimId(id);
    setTimeout(() => setLikeAnimId(null), 400);

    // 粒子飞散效果
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const particles = Array.from({ length: 6 }, () => ({
      id: particleIdRef.current++,
      postId: id,
      x: rect.left + rect.width / 2 + (Math.random() - 0.5) * 20,
      y: rect.top + (Math.random() - 0.5) * 10,
    }));
    setLikeParticles(prev => [...prev, ...particles]);
    setTimeout(() => {
      setLikeParticles(prev => prev.filter(p => !particles.some(pp => pp.id === p.id)));
    }, 300);

    likeMutation.mutate(id);
  };

  // 展开/收起评论
  const toggleComments = async (postId: number) => {
    if (expandedPostId === postId) {
      setExpandedPostId(null);
      setCommentInput('');
      return;
    }

    setExpandedPostId(postId);
    setCommentInput('');

    // 评论图标弹跳动画
    setCommentBounceId(postId);
    setTimeout(() => setCommentBounceId(null), 300);

    // 首次加载评论
    if (!commentsMap.has(postId)) {
      setCommentsLoading(true);
      try {
        const comments = await momentsService.getComments(postId);
        setCommentsMap(prev => new Map(prev).set(postId, comments));
      } catch (err) {
        console.error('加载评论失败:', err);
      } finally {
        setCommentsLoading(false);
      }
    }

    setTimeout(() => commentInputRef.current?.focus(), 350);
  };

  // 发送评论（乐观更新）
  const handleSendComment = async () => {
    if (!commentInput.trim() || !expandedPostId || sendingComment) return;
    if (!user) { openAuthModal('login'); return; }

    const text = commentInput.trim();
    setCommentInput('');
    setSendingComment(true);

    const optimisticComment: Comment = {
      id: Date.now(),
      userName: user.nickName || '我',
      userAvatar: user.icon,
      content: text,
      time: '刚刚',
    };

    setCommentsMap(prev => {
      const next = new Map(prev);
      const existing = next.get(expandedPostId) || [];
      next.set(expandedPostId, [...existing, optimisticComment]);
      return next;
    });

    setPosts(prev => prev.map(p =>
      p.id === expandedPostId ? { ...p, comments: p.comments + 1 } : p
    ));

    try {
      const realComment = await momentsService.addComment(expandedPostId, text);
      setCommentsMap(prev => {
        const next = new Map(prev);
        const list = next.get(expandedPostId) || [];
        next.set(expandedPostId, list.map(c => c.id === optimisticComment.id ? realComment : c));
        return next;
      });
    } catch (err) {
      setCommentsMap(prev => {
        const next = new Map(prev);
        const list = next.get(expandedPostId) || [];
        next.set(expandedPostId, list.filter(c => c.id !== optimisticComment.id));
        return next;
      });
      setPosts(prev => prev.map(p =>
        p.id === expandedPostId ? { ...p, comments: Math.max(0, p.comments - 1) } : p
      ));
      setCommentInput(text);
    } finally {
      setSendingComment(false);
      setTimeout(() => commentInputRef.current?.focus(), 100);
    }
  };

  const handleAddPost = () => {
    if (!user) { openAuthModal('login'); return; }
    navigate('/moments/new');
  };

  // 空状态
  if (!loading && posts.length === 0) {
    return (
      <div className={styles.container}>
        <div className={styles.header}>
          <span className={styles.headerTitle}>市场动态</span>
        </div>

        <div className={styles.emptyState}>
          <div className={styles.emptyBubble}>
            <div className={styles.emptyBubbleInner}>💬</div>
          </div>
          <p className={styles.emptyText}>还没有动态，来发第一条吧</p>
          <p className={styles.emptySubtext}>分享你的交易心得和看法</p>
        </div>

        <button className={styles.fab} onClick={handleAddPost}>
          <Plus size={24} />
        </button>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      {/* Header */}
      <div className={styles.header}>
        <div className={styles.headerClickable} onClick={handleRefresh}>
          <span className={styles.headerTitle}>市场动态</span>
          <RefreshCw
            size={16}
            className={`${styles.refreshIcon} ${refreshing ? styles.refreshIconSpinning : ''}`}
          />
        </div>
      </div>

      {/* 骨架屏 */}
      {loading && (
        <>
          <SkeletonCard />
          <SkeletonCard />
          <SkeletonCard />
        </>
      )}

      {/* 帖子列表 */}
      {!loading && posts.map((post, index) => (
        <div
          key={post.id}
          className={styles.postCard}
          style={{ animationDelay: `${index * 80}ms` }}
        >
          {/* 用户信息区 */}
          <div className={styles.userInfo}>
            <Avatar
              src={post.userAvatar}
              size={40}
              placeholder={post.userName[0]}
              className={styles.userAvatar}
            />
            <div>
              <div className={styles.userName}>{post.userName}</div>
              <div className={styles.postTime}>{post.time}</div>
            </div>
          </div>

          {/* 内容区 */}
          <div className={styles.content}>{post.content}</div>

          {/* 底部操作栏 */}
          <div className={styles.actions}>
            <div
              className={`${styles.actionItem} ${post.isLiked ? styles.liked : ''}`}
              onClick={(e) => handleLike(post.id, e)}
            >
              <Heart
                size={18}
                fill={post.isLiked ? 'currentColor' : 'none'}
                className={likeAnimId === post.id ? styles.heartbeat : ''}
              />
              <span>{post.likes}</span>
            </div>

            <div
              className={`${styles.actionItem} ${expandedPostId === post.id ? styles.active : ''}`}
              onClick={() => toggleComments(post.id)}
            >
              <MessageCircle
                size={18}
                className={commentBounceId === post.id ? styles.commentBounce : ''}
              />
              <span>{post.comments}</span>
              {post.comments > 0 && expandedPostId !== post.id && (
                <span className={styles.commentDot} />
              )}
            </div>

            <div className={styles.actionItem}>
              <Share2 size={18} />
              <span>分享</span>
            </div>
          </div>

          {/* 评论区 */}
          <div className={`${styles.commentsSection} ${expandedPostId === post.id ? styles.commentsOpen : ''}`}>
            <div className={styles.commentsInner}>
              {commentsLoading && expandedPostId === post.id ? (
                <div className={styles.commentsLoading}>
                  加载评论...
                </div>
              ) : (
                (commentsMap.get(post.id) || []).map((comment, ci) => (
                  <div
                    key={comment.id}
                    className={styles.commentItem}
                    style={{ animationDelay: `${ci * 40}ms` }}
                  >
                    <Avatar
                      src={comment.userAvatar}
                      size={28}
                      placeholder={comment.userName[0]}
                      className={styles.commentAvatar}
                    />
                    <div className={styles.commentBody}>
                      <span className={styles.commentUser}>{comment.userName}</span>
                      <span className={styles.commentText}>{comment.content}</span>
                    </div>
                  </div>
                ))
              )}

              {/* 评论输入区 */}
              <div className={styles.commentInputRow}>
                <div className={styles.commentInputWrapper}>
                  <input
                    ref={expandedPostId === post.id ? commentInputRef : null}
                    type="text"
                    className={styles.commentInput}
                    placeholder="写评论..."
                    value={expandedPostId === post.id ? commentInput : ''}
                    onChange={(e) => setCommentInput(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSendComment()}
                    maxLength={500}
                  />
                </div>
                <button
                  className={`${styles.sendButton} ${!sendingComment && commentInput.trim() && expandedPostId === post.id ? styles.sendConfirm : ''}`}
                  onClick={handleSendComment}
                  disabled={!commentInput.trim() || sendingComment}
                >
                  {sendingComment ? '...' : <Send size={16} />}
                </button>
              </div>
            </div>
          </div>
        </div>
      ))}

      {/* 粒子飞散 */}
      {likeParticles.map(p => (
        <span
          key={p.id}
          className={styles.likeParticle}
          style={{ left: p.x, top: p.y }}
        />
      ))}

      {/* 无限滚动哨兵 */}
      <div ref={sentinelRef} className={styles.sentinel}>
        {loadingMore && (
          <div className={styles.dotLoader}>
            <div className={styles.dotLoaderDot} />
            <div className={styles.dotLoaderDot} />
            <div className={styles.dotLoaderDot} />
          </div>
        )}
        {!hasMore && posts.length > 0 && (
          <div className={styles.endText}>已经到底了</div>
        )}
      </div>

      {/* FAB 悬浮发布按钮 */}
      <button className={styles.fab} onClick={handleAddPost}>
        <Plus size={24} />
      </button>
    </div>
  );
};
