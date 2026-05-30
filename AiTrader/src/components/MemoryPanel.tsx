import { useState } from 'react';
import { Brain, RefreshCw, Star, Shield, Target } from 'lucide-react';
import { memoryService, type Memory } from '../services/memory';

interface MemoryPanelProps {
  memories: Memory[];
  loading: boolean;
  error: string | null;
  onRefresh: () => void;
}

export const MemoryPanel = ({ memories, loading, error, onRefresh }: MemoryPanelProps) => {
  const [rebuilding, setRebuilding] = useState(false);

  const handleRebuild = async () => {
    setRebuilding(true);
    try {
      await memoryService.rebuildMemories();
      onRefresh();
    } catch (error) {
      console.error('重建记忆失败:', error);
    } finally {
      setRebuilding(false);
    }
  };

  const getMemoryIcon = (type: string) => {
    switch (type) {
      case 'preference':
        return <Star size={14} color="#FFD700" />;
      case 'constraint':
        return <Shield size={14} color="#FF6B6B" />;
      case 'goal':
        return <Target size={14} color="#4ECDC4" />;
      default:
        return <Brain size={14} color="#8884d8" />;
    }
  };

  const getMemoryTypeLabel = (type: string) => {
    switch (type) {
      case 'preference':
        return '偏好';
      case 'constraint':
        return '约束';
      case 'goal':
        return '目标';
      default:
        return type;
    }
  };

  return (
    <div style={{ padding: '16px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Brain size={18} color="#8884d8" />
          <span style={{ fontWeight: 'bold', fontSize: '15px' }}>长期记忆</span>
        </div>
        <button
          onClick={handleRebuild}
          disabled={rebuilding}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '4px',
            padding: '6px 12px',
            borderRadius: '4px',
            border: 'none',
            background: rebuilding ? '#666' : '#4CAF50',
            color: '#fff',
            fontSize: '12px',
            cursor: rebuilding ? 'not-allowed' : 'pointer',
          }}
        >
          <RefreshCw size={12} className={rebuilding ? 'animate-spin' : ''} />
          {rebuilding ? '重建中...' : '重建记忆'}
        </button>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', color: '#888', padding: '20px 0', fontSize: '13px' }}>加载中...</div>
      ) : error ? (
        <div style={{ textAlign: 'center', color: '#FF6B6B', padding: '20px 0', fontSize: '13px' }}>
          <div>{error}</div>
          <button
            onClick={onRefresh}
            style={{
              marginTop: '10px',
              padding: '6px 14px',
              borderRadius: '4px',
              border: 'none',
              background: '#4CAF50',
              color: '#fff',
              fontSize: '12px',
              cursor: 'pointer',
            }}
          >
            重试
          </button>
        </div>
      ) : memories.length === 0 ? (
        <div style={{ textAlign: 'center', color: '#888', padding: '20px 0', fontSize: '13px' }}>暂无记忆</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {memories.map((memory) => (
            <div
              key={memory.id}
              style={{
                background: '#2a2a2a',
                borderRadius: '8px',
                padding: '12px',
                border: '1px solid #333',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '8px' }}>
                {getMemoryIcon(memory.memoryType)}
                <span style={{ fontSize: '12px', color: '#aaa', fontWeight: 500 }}>
                  {getMemoryTypeLabel(memory.memoryType)}
                </span>
                <span style={{ marginLeft: 'auto', fontSize: '11px', color: '#666' }}>
                  重要性: {memory.importanceScore.toFixed(1)}
                </span>
              </div>
              <div style={{ fontSize: '13px', lineHeight: '1.5', color: '#eee', marginBottom: '8px' }}>
                {memory.content}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '11px', color: '#666' }}>
                <span>置信度: {memory.confidenceScore.toFixed(1)}</span>
                <span>来源: {memory.source}</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
