import { useState, useRef, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import { Send, Bot, User, Loader2, FileText, ChevronRight, BookOpen } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { aiService, type Message, type ProfileOption } from '../services/ai';
import { useAuth } from '../context/AuthContext';
import { MemoryPanel } from './MemoryPanel';
import { KnowledgePanel } from './KnowledgePanel';
import { memoryService, type Memory } from '../services/memory';
import { knowledgeService, type KnowledgeDoc } from '../services/knowledge';

export const AIChat = () => {
  const { user, openAuthModal, updateUser } = useAuth();
  const navigate = useNavigate();
  const [messages, setMessages] = useState<Array<{ role: string, content: string, profileOptions?: ProfileOption[] }>>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isTyping, setIsTyping] = useState(false);
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [inputMessage, setInputMessage] = useState('');
  const [chatMode, setChatMode] = useState<'chat' | 'strategy'>('chat');
  const [activeTab, setActiveTab] = useState<'chat' | 'strategy' | 'knowledge'>('chat');
  const [memories, setMemories] = useState<Memory[]>([]);
  const [memoriesLoading, setMemoriesLoading] = useState(false);
  const [memoriesError, setMemoriesError] = useState<string | null>(null);
  const [knowledgeDocs, setKnowledgeDocs] = useState<KnowledgeDoc[]>([]);
  const [knowledgeLoading, setKnowledgeLoading] = useState(false);
  const [knowledgeError, setKnowledgeError] = useState<string | null>(null);
  const [streamState, setStreamState] = useState<{ content: string; options?: ProfileOption[] }>({ content: '' });
  /** 当前正在执行的工具名（tool 帧 start 显示 / end 清除），null 时不展示 */
  const [toolStatus, setToolStatus] = useState<string | null>(null);
  const streamingRef = useRef<HTMLDivElement>(null);
  const typingTimeoutRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const hasInitialized = useRef(false);
  /** 真流式 token 累积缓冲：token 逐帧直写 DOM 不触发 re-render，结束后一次性落消息 */
  const streamAccumRef = useRef('');
  /** 当前流式请求的 AbortController（组件卸载/切换会话时中止） */
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    return () => {
      if (typingTimeoutRef.current) {
        clearInterval(typingTimeoutRef.current);
      }
      if (abortRef.current) {
        abortRef.current.abort();
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

    const isStrategy = chatMode === 'strategy';
    const messageText = isStrategy
      ? '请基于当前市场数据，生成一份详细的交易策略报告'
      : (inputMessage.trim() || '请给我AI交易策略');
    setInputMessage('');
    setToolStatus(null);

    setMessages(prev => [...prev, { role: 'user', content: messageText }]);
    setIsLoading(true);
    setIsTyping(true);

    // 立即展示气泡，等待阶段轮换状态文案（首个 token/工具帧到达后由真文本接管）
    const statusMessages = [
      '正在连接 AI 服务...',
      '正在获取市场数据...',
      '正在分析技术指标...',
      '正在生成回复...',
    ];
    let statusIdx = 0;
    setStreamState({ content: statusMessages[0] });

    const statusTimer = setInterval(() => {
      statusIdx = Math.min(statusIdx + 1, statusMessages.length - 1);
      if (streamingRef.current) {
        streamingRef.current.textContent = statusMessages[statusIdx];
      }
    }, 3000);
    const stopStatusTimer = () => clearInterval(statusTimer);

    if (isStrategy) {
      // 策略模式：Java 端整包返回（机会扣减 / 画像引导分支），整包 done 无 token 流。
      // 保留同步端点请求（Java 同步端点仍在），收到 profileOptions 即展示画像引导按钮。
      try {
        const response = await aiService.chat(currentConversationId, messageText, 'strategy');
        stopStatusTimer();
        if (response.remainingChance !== undefined) {
          updateUser({ aiChance: response.remainingChance });
        }
        animateAssistantReply(response.reply || 'AI 未返回有效内容。', response.profileOptions);
      } catch (error) {
        stopStatusTimer();
        finishWithError('策略请求失败，请稍后再试。');
        console.error('AI Strategy Error:', error);
      }
      return;
    }

    // ===== chat 模式：真流式（token 逐帧 / tool 过程 / done 收尾 / error 兜底）=====
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    streamAccumRef.current = '';

    let finalReply = '';
    let finalOptions: ProfileOption[] | undefined;
    let remainingChance: number | undefined;
    let doneReceived = false;
    let streamErrorMsg: string | null = null;
    let gotAnyFrame = false;

    const outcome = await aiService.chatStream(
      currentConversationId,
      messageText,
      'chat',
      {
        onToken: (text) => {
          gotAnyFrame = true;
          stopStatusTimer();
          if (streamingRef.current) {
            streamAccumRef.current += text;
            streamingRef.current.textContent = streamAccumRef.current;
            messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
          }
        },
        onTool: (frame) => {
          gotAnyFrame = true;
          stopStatusTimer();
          setToolStatus(frame.status === 'start' ? (frame.name || '市场分析工具') : null);
        },
        onDone: (result) => {
          gotAnyFrame = true;
          doneReceived = true;
          stopStatusTimer();
          setToolStatus(null);
          finalReply = result.reply || '';
          finalOptions = result.profileOptions;
          remainingChance = result.remainingChance;
        },
        onError: (msg) => {
          gotAnyFrame = true;
          stopStatusTimer();
          setToolStatus(null);
          streamErrorMsg = msg;
        },
      },
      ctrl.signal,
    );

    if (doneReceived) {
      // done 帧收尾：内容已由流式逐字展示，直接落库展示（markdown），不再重打
      if (remainingChance !== undefined) {
        updateUser({ aiChance: remainingChance });
      }
      if (finalReply) {
        commitStreamReply(finalReply, finalOptions);
      } else {
        finishWithError('AI 未返回有效内容，请稍后重试。');
      }
    } else if (streamErrorMsg) {
      // Java error 帧（流中断/异常兜底）：内容可能已部分落库，不重复发同步请求
      finishWithError(streamErrorMsg);
    } else if (outcome === 'broken' && !gotAnyFrame) {
      // 连接级失败（HTTP 错误/无任何帧）：Java 已清理悬挂 user 消息 → 降级同步 chat
      stopStatusTimer();
      try {
        const response = await aiService.chat(currentConversationId, messageText, 'chat');
        if (response.remainingChance !== undefined) {
          updateUser({ aiChance: response.remainingChance });
        }
        animateAssistantReply(response.reply || 'AI 未返回有效内容，请稍后重试。', response.profileOptions);
      } catch (error) {
        finishWithError('无法连接到 AI 服务器，请稍后再试。');
        console.error('AI fallback Error:', error);
      }
    } else {
      // 收到过 token/tool 但中途断开且无 done：提示（部分内容可能已落库）
      finishWithError('回答被中断，请查看历史消息或重试。');
    }
  };

  /**
   * 整包回答展示（strategy / fallback）：profileOptions 直接展示，否则 3 秒打字动画。
   */
  const animateAssistantReply = (fullText: string, options?: ProfileOption[]) => {
    // 聊天成功后静默刷新记忆列表（后台执行，不影响用户交互）
    memoryService.getMemories()
      .then(data => setMemories(data || []))
      .catch(() => {});

    if (options && options.length > 0) {
      setMessages(prev => [...prev, { role: 'assistant', content: fullText, profileOptions: options }]);
      setStreamState({ content: '' });
      setToolStatus(null);
      setIsTyping(false);
      setIsLoading(false);
      messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
      return;
    }

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
        setStreamState({ content: '' });
        setToolStatus(null);
        setIsTyping(false);
        setIsLoading(false);
      }
    }, interval);
  };

  /**
   * 真流式 done 收尾：token 已逐字展示，直接以 markdown 形式固化到消息历史。
   */
  const commitStreamReply = (text: string, options?: ProfileOption[]) => {
    memoryService.getMemories()
      .then(data => setMemories(data || []))
      .catch(() => {});

    setMessages(prev => [...prev, { role: 'assistant', content: text, profileOptions: options }]);
    setStreamState({ content: '' });
    setToolStatus(null);
    setIsTyping(false);
    setIsLoading(false);
    messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
  };

  const finishWithError = (message: string) => {
    setMessages(prev => [...prev, { role: 'assistant', content: message }]);
    setStreamState({ content: '' });
    setToolStatus(null);
    setIsTyping(false);
    setIsLoading(false);
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleOptionSelect = async (value: string) => {
    if (isLoading || isTyping) return;
    if (!user) { openAuthModal('login'); return; }

    const messageText = value;

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

    setMessages(prev => [...prev, { role: 'user', content: messageText }]);
    setIsLoading(true);
    setIsTyping(true);
    setStreamState({ content: '正在处理您的选择...' });
    setToolStatus(null);

    try {
      // 画像选项提交走 strategy 语义（机会扣减 / 画像写入）
      const response = await aiService.chat(currentConversationId, messageText, 'strategy');
      if (response.remainingChance !== undefined) {
        updateUser({ aiChance: response.remainingChance });
      }
      animateAssistantReply(response.reply || 'AI 未返回有效内容。', response.profileOptions);
    } catch (error) {
      console.error('Option select error:', error);
      finishWithError('无法连接到 AI 服务器，请稍后再试。');
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
    } catch (err) {
      setMemoriesError(err instanceof Error ? err.message : '加载记忆失败');
    } finally {
      setMemoriesLoading(false);
    }
    try {
      const docData = await knowledgeService.getKnowledgeDocs(Number(user?.id) || 0);
      setKnowledgeDocs(docData || []);
    } catch (err) {
      setKnowledgeError(err instanceof Error ? err.message : '加载知识文档失败');
    } finally {
      setKnowledgeLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'knowledge' && user) {
      loadSidePanelData();
    }
  }, [activeTab, user]);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  return (
    <>
    <style>{`
      .chat-markdown { white-space: normal; font-size: 14px; line-height: 1.6; word-break: break-word; overflow-wrap: break-word; }
      .chat-markdown p { margin: 0 0 8px; }
      .chat-markdown p:last-child { margin-bottom: 0; }
      .chat-markdown h1, .chat-markdown h2, .chat-markdown h3, .chat-markdown h4 { margin: 10px 0 6px; line-height: 1.4; }
      .chat-markdown h1 { font-size: 17px; } .chat-markdown h2 { font-size: 16px; } .chat-markdown h3 { font-size: 15px; }
      .chat-markdown ul, .chat-markdown ol { padding-left: 18px; margin: 4px 0 8px; }
      .chat-markdown li { margin-bottom: 4px; }
      .chat-markdown strong { color: #fff; }
      .chat-markdown em { color: #ddd; }
      .chat-markdown code { background: #454545; color: #ffd479; padding: 1px 5px; border-radius: 4px; font-size: 12px; font-family: Consolas, Monaco, monospace; }
      .chat-markdown pre { background: #161616; border: 1px solid #3a3a3a; padding: 10px 12px; border-radius: 6px; overflow-x: auto; margin: 8px 0; }
      .chat-markdown pre code { background: transparent; padding: 0; color: #e6e6e6; }
      .chat-markdown blockquote { border-left: 3px solid #8884d8; padding: 4px 12px; margin: 8px 0; color: #bdbdbd; background: rgba(136,132,216,0.08); border-radius: 0 6px 6px 0; }
      .chat-markdown a { color: #8fa3ff; }
      .chat-markdown table { border-collapse: collapse; margin: 8px 0; width: 100%; font-size: 13px; }
      .chat-markdown th, .chat-markdown td { border: 1px solid #4a4a4a; padding: 5px 8px; text-align: left; }
      .chat-markdown th { background: #3a3a3a; }
      .chat-markdown hr { border: none; border-top: 1px solid #3f3f3f; margin: 10px 0; }
    `}</style>
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
          <div style={{
            display: 'flex',
            gap: 0,
            background: '#2a2a2a',
            borderRadius: '20px',
            padding: '3px',
            border: '1px solid #444',
          }}>
            <button
              onClick={() => { setActiveTab('chat'); setChatMode('chat'); setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 100); }}
              style={{
                padding: '6px 16px',
                borderRadius: '16px',
                border: 'none',
                cursor: 'pointer',
                fontSize: '13px',
                fontWeight: 500,
                background: activeTab === 'chat' ? '#4CAF50' : 'transparent',
                color: activeTab === 'chat' ? '#fff' : '#888',
                transition: 'all 0.2s',
              }}
            >
              通用模式
            </button>
            <button
              onClick={() => { setActiveTab('strategy'); setChatMode('strategy'); setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 100); }}
              style={{
                padding: '6px 16px',
                borderRadius: '16px',
                border: 'none',
                cursor: 'pointer',
                fontSize: '13px',
                fontWeight: 500,
                background: activeTab === 'strategy' ? '#4CAF50' : 'transparent',
                color: activeTab === 'strategy' ? '#fff' : '#888',
                transition: 'all 0.2s',
              }}
            >
              策略报告
            </button>
            <button
              onClick={() => {
                if (!user) { openAuthModal('login'); return; }
                setActiveTab('knowledge');
              }}
              style={{
                padding: '6px 16px',
                borderRadius: '16px',
                border: 'none',
                cursor: 'pointer',
                fontSize: '13px',
                fontWeight: 500,
                display: 'flex',
                alignItems: 'center',
                gap: '5px',
                background: activeTab === 'knowledge' ? '#8884d8' : 'transparent',
                color: activeTab === 'knowledge' ? '#fff' : '#888',
                transition: 'all 0.2s',
              }}
            >
              <BookOpen size={13} />
              记忆与知识
            </button>
          </div>
        </div>
      </div>

      {/* 主体区域 */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {activeTab === 'knowledge' ? (
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
                  userId={Number(user?.id) || 0}
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
                      <div className="chat-markdown">
                        <ReactMarkdown>{msg.content}</ReactMarkdown>
                      </div>
                    )}
                    {msg.profileOptions && msg.profileOptions.length > 0 && (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '12px' }}>
                        {msg.profileOptions.map((opt, i) => (
                          <button
                            key={i}
                            onClick={() => handleOptionSelect(opt.value)}
                            disabled={isLoading || isTyping}
                            style={{
                              padding: '10px 16px',
                              borderRadius: '8px',
                              border: '1px solid #555',
                              background: (isLoading || isTyping) ? '#444' : '#2a2a2a',
                              color: '#fff',
                              fontSize: '13px',
                              cursor: (isLoading || isTyping) ? 'not-allowed' : 'pointer',
                              textAlign: 'left',
                              transition: 'background 0.2s',
                            }}
                            onMouseEnter={(e) => {
                              if (!isLoading && !isTyping) (e.currentTarget.style.background = '#4CAF50');
                            }}
                            onMouseLeave={(e) => {
                              e.currentTarget.style.background = (isLoading || isTyping) ? '#444' : '#2a2a2a';
                            }}
                          >
                            {opt.label}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              ))}

              {/* 流式打字气泡 — ref 直接操作 DOM，不触发 React re-render */}
              {streamState.content && (
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
                    <span ref={streamingRef}>{streamState.content}</span>
                    {toolStatus && (
                      <div style={{ marginTop: '8px', display: 'flex', alignItems: 'center', gap: '6px', color: '#8fa3ff', fontSize: '12px' }}>
                        <Loader2 size={13} className="animate-spin" />
                        正在调用「{toolStatus}」获取数据...
                      </div>
                    )}
                    {streamState.options && streamState.options.length > 0 && (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '12px' }}>
                        {streamState.options.map((opt, i) => (
                          <button
                            key={i}
                            onClick={() => handleOptionSelect(opt.value)}
                            disabled={isLoading || isTyping}
                            style={{
                              padding: '10px 16px',
                              borderRadius: '8px',
                              border: '1px solid #555',
                              background: (isLoading || isTyping) ? '#444' : '#2a2a2a',
                              color: '#fff',
                              fontSize: '13px',
                              cursor: (isLoading || isTyping) ? 'not-allowed' : 'pointer',
                              textAlign: 'left',
                              transition: 'background 0.2s',
                            }}
                            onMouseEnter={(e) => {
                              if (!isLoading && !isTyping) (e.currentTarget.style.background = '#4CAF50');
                            }}
                            onMouseLeave={(e) => {
                              e.currentTarget.style.background = (isLoading || isTyping) ? '#444' : '#2a2a2a';
                            }}
                          >
                            {opt.label}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}

              {isLoading && !streamState.content && (
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
    </>
  );
};
