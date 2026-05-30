import { useState } from 'react';
import { BookOpen, Upload, FileText, ChevronDown, ChevronUp } from 'lucide-react';
import { knowledgeService, type KnowledgeDoc, type Chunk } from '../services/knowledge';

interface KnowledgePanelProps {
  docs: KnowledgeDoc[];
  loading: boolean;
  error: string | null;
  onRefresh: () => void;
}

export const KnowledgePanel = ({ docs, loading, error, onRefresh }: KnowledgePanelProps) => {
  const [uploading, setUploading] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [selectedDocId, setSelectedDocId] = useState<number | null>(null);
  const [chunks, setChunks] = useState<Chunk[]>([]);
  const [chunksLoading, setChunksLoading] = useState(false);

  const [form, setForm] = useState({
    title: '',
    docType: 'article',
    content: '',
  });

  const handleUpload = async () => {
    if (!form.title.trim() || !form.content.trim()) return;
    setUploading(true);
    try {
      await knowledgeService.uploadKnowledgeDoc(form);
      setForm({ title: '', docType: 'article', content: '' });
      setShowForm(false);
      onRefresh();
    } catch (error) {
      console.error('上传文档失败:', error);
    } finally {
      setUploading(false);
    }
  };

  const handleSelectDoc = async (docId: number) => {
    if (selectedDocId === docId) {
      setSelectedDocId(null);
      setChunks([]);
      return;
    }
    setSelectedDocId(docId);
    setChunksLoading(true);
    try {
      const data = await knowledgeService.getDocChunks(docId);
      setChunks(data);
    } catch (error) {
      console.error('加载文档分片失败:', error);
    } finally {
      setChunksLoading(false);
    }
  };

  return (
    <div style={{ padding: '16px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <BookOpen size={18} color="#8884d8" />
          <span style={{ fontWeight: 'bold', fontSize: '15px' }}>知识库</span>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '4px',
            padding: '6px 12px',
            borderRadius: '4px',
            border: 'none',
            background: showForm ? '#666' : '#4CAF50',
            color: '#fff',
            fontSize: '12px',
            cursor: 'pointer',
          }}
        >
          <Upload size={12} />
          {showForm ? '取消' : '上传文档'}
        </button>
      </div>

      {showForm && (
        <div style={{ background: '#2a2a2a', borderRadius: '8px', padding: '12px', marginBottom: '16px', border: '1px solid #333' }}>
          <div style={{ marginBottom: '10px' }}>
            <label style={{ display: 'block', fontSize: '12px', color: '#aaa', marginBottom: '4px' }}>标题</label>
            <input
              type="text"
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              placeholder="文档标题"
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: '4px',
                border: '1px solid #444',
                background: '#1e1e1e',
                color: '#fff',
                fontSize: '13px',
                outline: 'none',
                boxSizing: 'border-box',
              }}
            />
          </div>
          <div style={{ marginBottom: '10px' }}>
            <label style={{ display: 'block', fontSize: '12px', color: '#aaa', marginBottom: '4px' }}>类型</label>
            <select
              value={form.docType}
              onChange={(e) => setForm({ ...form, docType: e.target.value })}
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: '4px',
                border: '1px solid #444',
                background: '#1e1e1e',
                color: '#fff',
                fontSize: '13px',
                outline: 'none',
                boxSizing: 'border-box',
              }}
            >
              <option value="article">文章</option>
              <option value="report">报告</option>
              <option value="strategy">策略</option>
              <option value="note">笔记</option>
            </select>
          </div>
          <div style={{ marginBottom: '10px' }}>
            <label style={{ display: 'block', fontSize: '12px', color: '#aaa', marginBottom: '4px' }}>内容</label>
            <textarea
              value={form.content}
              onChange={(e) => setForm({ ...form, content: e.target.value })}
              placeholder="输入文档内容..."
              rows={5}
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: '4px',
                border: '1px solid #444',
                background: '#1e1e1e',
                color: '#fff',
                fontSize: '13px',
                outline: 'none',
                resize: 'vertical',
                boxSizing: 'border-box',
              }}
            />
          </div>
          <button
            onClick={handleUpload}
            disabled={uploading || !form.title.trim() || !form.content.trim()}
            style={{
              width: '100%',
              padding: '8px',
              borderRadius: '4px',
              border: 'none',
              background: uploading || !form.title.trim() || !form.content.trim() ? '#666' : '#4CAF50',
              color: '#fff',
              fontSize: '13px',
              cursor: uploading || !form.title.trim() || !form.content.trim() ? 'not-allowed' : 'pointer',
            }}
          >
            {uploading ? '上传中...' : '确认上传'}
          </button>
        </div>
      )}

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
      ) : docs.length === 0 ? (
        <div style={{ textAlign: 'center', color: '#888', padding: '20px 0', fontSize: '13px' }}>暂无知识文档</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {docs.map((doc) => (
            <div key={doc.id}>
              <div
                onClick={() => handleSelectDoc(doc.id)}
                style={{
                  background: '#2a2a2a',
                  borderRadius: '8px',
                  padding: '12px',
                  border: '1px solid #333',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px',
                }}
              >
                <FileText size={16} color="#8884d8" />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: '13px', fontWeight: 500, color: '#eee', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {doc.title}
                  </div>
                  <div style={{ fontSize: '11px', color: '#666', marginTop: '2px' }}>
                    {doc.docType} · {doc.status}
                  </div>
                </div>
                {selectedDocId === doc.id ? <ChevronUp size={14} color="#888" /> : <ChevronDown size={14} color="#888" />}
              </div>

              {selectedDocId === doc.id && (
                <div style={{ marginTop: '8px', marginLeft: '8px', paddingLeft: '12px', borderLeft: '2px solid #444' }}>
                  {chunksLoading ? (
                    <div style={{ textAlign: 'center', color: '#888', padding: '12px 0', fontSize: '12px' }}>加载分片中...</div>
                  ) : chunks.length === 0 ? (
                    <div style={{ textAlign: 'center', color: '#888', padding: '12px 0', fontSize: '12px' }}>暂无分片</div>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                      {chunks.map((chunk) => (
                        <div
                          key={chunk.id}
                          style={{
                            background: '#1e1e1e',
                            borderRadius: '6px',
                            padding: '10px',
                            border: '1px solid #333',
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
                            <span style={{ fontSize: '11px', color: '#888' }}>分片 #{chunk.chunkIndex}</span>
                          </div>
                          <div style={{ fontSize: '12px', color: '#ddd', lineHeight: '1.5', marginBottom: '6px' }}>
                            {chunk.chunkText}
                          </div>
                          {chunk.keywords && (
                            <div style={{ fontSize: '11px', color: '#666' }}>
                              关键词: {chunk.keywords}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
