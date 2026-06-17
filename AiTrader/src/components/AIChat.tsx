import { useState, useRef, useEffect } from 'react';
import { Send, Bot, User, Loader2, FileText, ChevronRight, BookOpen } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { aiService, type Message } from '../services/ai';
import { useAuth } from '../context/AuthContext';
import { MemoryPanel } from './MemoryPanel';
import { KnowledgePanel } from './KnowledgePanel';
import { memoryService, type Memory } from '../services/memory';
import { knowledgeService, type KnowledgeDoc } from '../services/knowledge';

export const AIChat = () => {
  const { user, openAuthModal } = useAuth();
  const navigate = useNavigate();
  const [messages, setMessages] = useState<Array<{ role: string, content: string }>>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isTyping, setIsTyping] = useState(false);
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [inputMessage, setInputMessage] = useState('');
  const [chatMode, setChatMode] = useState<'chat' | 'strategy'>('chat');
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [memories, setMemories] = useState<Memory[]>([]);
  const [memoriesLoading, setMemoriesLoading] = useState(false);
  const [memoriesError, setMemoriesError] = useState<string | null>(null);
  const [knowledgeDocs, setKnowledgeDocs] = useState<KnowledgeDoc[]>([]);
  const [knowledgeLoading, setKnowledgeLoading] = useState(false);
  const [knowledgeError, setKnowledgeError] = useState<string | null>(null);
  const [streamingContent, setStreamingContent] = useState('');
  const streamingRef = useRef<HTMLDivElement>(null);
  const typingTimeoutRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const hasInitialized = useRef(false);

  useEffect(() => {
    return () => {
      if (typingTimeoutRef.current) {
        clearInterval(typingTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (!hasInitialized.current && user) {
      hasInitialized.current = true;
      initConversation();
    }
  }, [user]);

  const initConversation = async () => {
    try {
      // 获取用户的会话列表，找到最新的活跃会话
      const conversations = await aiService.getConversations();
      const activeConversation = conversations.find(c => c.status === 'active');

      if (activeConversation) {
        setConversationId(activeConversation.id);
        await loadMessages(activeConversation.id);
      } else {
        const conversation = await aiService.createConversation('AI交易对话', 'chat');
        setConversationId(conversation.id);
      }
    } catch (error) {
      console.error('初始化会话失败:', error);
    }
  };

  const loadMessages = async (id: number) => {
    try {
      const historyMessages = await aiService.getMessages(id);
      const formattedMessages = historyMessages.map((msg: Message) => ({
        role: msg.role,
        content: msg.content,
      }));
      setMessages(formattedMessages);
    } catch (error) {
      console.error('加载消息失败:', error);
    }
  };

  const handleSend = async () => {
    if (isLoading || isTyping) return;

    if (!user) {
      openAuthModal('login');
      return;
    }

    let currentConversationId = conversationId;

    if (!currentConversationId) {
      try {
        const conversation = await aiService.createConversation('AI交易对话', 'chat');
        currentConversationId = conversation.id;
        setConversationId(currentConversationId);
      } catch (error) {
        console.error('创建会话失败:', error);
        return;
      }
    }

    const messageText = chatMode === 'strategy'
      ? '请基于当前市场数据，生成一份详细的交易策略报告'
      : (inputMessage.trim() || '请给我AI交易策略');
    setInputMessage('');

    setMessages(prev => [...prev, { role: 'user', content: messageText }]);
    setIsLoading(true);
    setIsTyping(true);

    // 立即展示流式气泡，用轮换状态文案减少等待焦虑
    const statusMessages = [
      '正在连接 AI 服务...',
      '正在获取市场数据...',
      '正在分析技术指标...',
      '正在生成回复...',
    ];
    let statusIdx = 0;
    setStreamingContent(statusMessages[0]);

    const statusTimer = setInterval(() => {
      statusIdx = Math.min(statusIdx + 1, statusMessages.length - 1);
      if (streamingRef.current) {
        streamingRef.current.textContent = statusMessages[statusIdx];
      }
    }, 3000);

    try {
      const response = await aiService.chat(currentConversationId, messageText, chatMode);
      clearInterval(statusTimer);
      setIsLoading(false);

      const fullText = response.reply || 'AI 未返回有效内容，请稍后重试。';

      // 自适应打字速度：总时长控制在 ~3 秒，内容越长每次追加越多
      const maxDuration = 3000;
      const interval = 40;
      const totalTicks = maxDuration / interval;
      const chunkSize = Math.max(2, Math.ceil(fullText.length / totalTicks));
      let index = 0;

      typingTimeoutRef.current = setInterval(() => {
        index = Math.min(index + chunkSize, fullText.length);
        if (streamingRef.current) {
          streamingRef.current.textContent = fullText.slice(0, index);
        }
        messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });

        if (index >= fullText.length) {
          if (typingTimeoutRef.current) clearInterval(typingTimeoutRef.current);
          setMessages(prev => [...prev, { role: 'assistant', content: fullText }]);
          setStreamingContent('');
          setIsTyping(false);
        }
      }, interval);

    } catch (error) {
      clearInterval(statusTimer);
      console.error('AI Chat Error:', error);
      setIsLoading(false);
      setIsTyping(false);

      setMessages(prev => [...prev, { role: 'assistant', content: '无法连接到 AI 服务器，请稍后再试。' }]);
      setStreamingContent('');
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const loadSidePanelData = async () => {
    setMemoriesLoading(true);
    setKnowledgeLoading(true);
    setMemoriesError(null);
    setKnowledgeError(null);
    try {
      const memoryData = await memoryService.getMemories();
      setMemories(memoryData || []);
    } catch (err: any) {
      setMemoriesError(err?.message || '加载记忆失败');
    } finally {
      setMemoriesLoading(false);
    }
    try {
      const docData = await knowledgeService.getKnowledgeDocs();
      setKnowledgeDocs(docData || []);
    } catch (err: any) {
      setKnowledgeError(err?.message || '加载知识文档失败');
    } finally {
      setKnowledgeLoading(false);
    }
  };

  useEffect(() => {
    if (sidebarOpen) {
      loadSidePanelData();
    }
  }, [sidebarOpen]);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  return (
    <div style={{
      background: '#1e1e1e',
      borderRadius: '8px',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      color: '#fff',
    }}>
      {/* 顶部标题栏 */}
      <div style={{ padding: '20px', borderBottom: '1px solid #333', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Bot size={20} color="#8884d8" />
          <span style={{ fontWeight: 'bold' }}>AI Trader</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 12px',
              borderRadius: '16px',
              border: 'none',
              cursor: 'pointer',
              fontSize: '13px',
              fontWeight: 500,
              background: sidebarOpen ? '#8884d8' : '#333',
              color: '#fff',
              transition: 'all 0.2s',
            }}
          >
            <BookOpen size={14} />
            记忆与知识
          </button>
          <div style={{ display: 'flex', gap: '8px', background: '#333', borderRadius: '20px', padding: '4px' }}>
            <button
              onClick={() => setChatMode('chat')}
              style={{
                padding: '6px 16px',
                borderRadius: '16px',
                border: 'none',
                cursor: 'pointer',
                fontSize: '13px',
                fontWeight: 500,
                background: chatMode === 'chat' ? '#4CAF50' : 'transparent',
                color: chatMode === 'chat' ? '#fff' : '#aaa',
                transition: 'all 0.2s',
              }}
            >
              通用模式
            </button>
            <button
              onClick={() => setChatMode('strategy')}
              style={{
                padding: '6px 16px',
                borderRadius: '16px',
                border: 'none',
                cursor: 'pointer',
                fontSize: '13px',
                fontWeight: 500,
                background: chatMode === 'strategy' ? '#4CAF50' : 'transparent',
                color: chatMode === 'strategy' ? '#fff' : '#aaa',
                transition: 'all 0.2s',
              }}
            >
              策略报告
            </button>
          </div>
        </div>
      </div>

      {/* 主体区域 */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {sidebarOpen ? (
          /* 侧边栏模式：全宽显示记忆与知识 */
          <div style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}>
            <div style={{
              flex: 1,
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
            }}>
              <MemoryPanel
                memories={memories}
                loading={memoriesLoading}
                error={memoriesError}
                onRefresh={loadSidePanelData}
              />
              <div style={{ borderTop: '1px solid #333' }}>
                <KnowledgePanel
                  docs={knowledgeDocs}
                  loading={knowledgeLoading}
                  error={knowledgeError}
                  onRefresh={loadSidePanelData}
                />
              </div>
            </div>
          </div>
        ) : (
          /* 聊天模式：消息列表 + 输入框 */
          <div style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}>
            <div translate="no" style={{
              flex: 1,
              overflowY: 'auto',
              padding: '20px',
              display: 'flex',
              flexDirection: 'column',
              gap: '15px',
            }}>
              {messages.length === 0 && (
                <div style={{ textAlign: 'center', color: '#888', padding: '40px 0' }}>
                  <Bot size={48} color="#8884d8" style={{ marginBottom: '16px' }} />
                  <p style={{ fontSize: '16px', marginBottom: '8px' }}>您好！我是您的AI交易助手</p>
                  <p style={{ fontSize: '14px' }}>有什么我可以帮您的吗？比如分析当前市场趋势。</p>
                </div>
              )}
              {messages.map((msg, idx) => (
                <div key={idx} style={{
                  alignSelf: msg.role === 'user' ? 'flex-end' : 'flex-start',
                  maxWidth: '80%',
                  display: 'flex',
                  gap: '10px',
                  flexDirection: msg.role === 'user' ? 'row-reverse' : 'row'
                }}>
                  <div style={{
                    width: '30px',
                    height: '30px',
                    borderRadius: '50%',
                    background: msg.role === 'user' ? '#4CAF50' : '#8884d8',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0
                  }}>
                    {msg.role === 'user' ? <User size={16} /> : <Bot size={16} />}
                  </div>
                  <div style={{
                    background: '#333',
                    padding: '10px 15px',
                    borderRadius: '12px',
                    fontSize: '14px',
                    lineHeight: '1.4',
                    maxWidth: '100%',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    overflowWrap: 'break-word'
                  }}>
                    {msg.role === 'assistant' && (msg.content.trim().startsWith('#') || msg.content.includes('## 1.') || msg.content.includes('交易策略')) && (!isTyping || idx !== messages.length - 1) ? (
                      <div
                        onClick={() => navigate('/report', { state: { answer: msg.content } })}
                        style={{ cursor: 'pointer', width: '200px' }}
                      >
                        <div style={{ fontWeight: 'bold', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '8px', borderBottom: '1px solid rgba(255,255,255,0.1)', paddingBottom: '8px' }}>
                          <FileText size={18} color="#ffffffff" />
                          <span>策略报告已生成</span>
                        </div>
                        <div style={{ fontSize: '13px', color: '#eee', marginBottom: '12px', fontWeight: '500' }}>
                          {msg.content.split('\n').find(line => line.trim().startsWith('#'))?.replace(/#+\s*/, '').trim() || '交易策略分析'}
                        </div>
                        <div style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          background: '#4CAF50',
                          color: 'white',
                          padding: '8px 12px',
                          borderRadius: '6px',
                          fontSize: '12px',
                          fontWeight: '500'
                        }}>
                          <span>点击查看详情</span>
                          <ChevronRight size={14} />
                        </div>
                      </div>
                    ) : (
                      <span>{msg.content}</span>
                    )}
                  </div>
                </div>
              ))}

              {/* 流式打字气泡 — ref 直接操作 DOM，不触发 React re-render */}
              {streamingContent && (
                <div style={{
                  alignSelf: 'flex-start',
                  maxWidth: '80%',
                  display: 'flex',
                  gap: '10px',
                }}>
                  <div style={{
                    width: '30px',
                    height: '30px',
                    borderRadius: '50%',
                    background: '#8884d8',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0
                  }}>
                    <Bot size={16} />
                  </div>
                  <div style={{
                    background: '#333',
                    padding: '10px 15px',
                    borderRadius: '12px',
                    fontSize: '14px',
                    lineHeight: '1.4',
                    maxWidth: '100%',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                  }}>
                    <span ref={streamingRef}>{streamingContent}</span>
                  </div>
                </div>
              )}

              {isLoading && !streamingContent && (
                <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                  <div style={{
                    width: '30px',
                    height: '30px',
                    borderRadius: '50%',
                    background: '#8884d8',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0
                  }}>
                    <Loader2 size={16} className="animate-spin" />
                  </div>
                  <div style={{ color: '#888', fontSize: '12px' }}>AI 正在思考...</div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* 输入区域 */}
            <div style={{ padding: '20px', borderTop: '1px solid #333', display: 'flex', gap: '10px' }}>
              {chatMode === 'strategy' ? (
                <button
                  onClick={handleSend}
                  disabled={isLoading || isTyping}
                  style={{
                    flex: 1,
                    background: (isLoading || isTyping) ? '#666' : '#4CAF50',
                    border: 'none',
                    borderRadius: '4px',
                    padding: '12px 20px',
                    cursor: (isLoading || isTyping) ? 'not-allowed' : 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#fff',
                    fontSize: '14px',
                    fontWeight: 'bold',
                    gap: '8px',
                  }}
                >
                  {isLoading ? <Loader2 size={18} className="animate-spin" /> : <FileText size={18} />}
                  {isLoading ? '生成中...' : '获取策略报告'}
                </button>
              ) : (
                <>
                  <input
                    type="text"
                    value={inputMessage}
                    onChange={(e) => setInputMessage(e.target.value)}
                    onKeyPress={handleKeyPress}
                    placeholder='输入您的问题...'
                    style={{
                      flex: 1,
                      background: '#333',
                      border: 'none',
                      borderRadius: '4px',
                      padding: '10px 15px',
                      color: '#fff',
                      fontSize: '14px',
                      outline: 'none',
                    }}
                  />
                  <button
                    onClick={handleSend}
                    disabled={isLoading || isTyping}
                    style={{
                      background: (isLoading || isTyping) ? '#666' : '#4CAF50',
                      border: 'none',
                      borderRadius: '4px',
                      padding: '10px 20px',
                      cursor: (isLoading || isTyping) ? 'not-allowed' : 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: '#fff'
                    }}
                  >
                    {isLoading ? <Loader2 size={18} className="animate-spin" /> : <Send size={18} />}
                  </button>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
