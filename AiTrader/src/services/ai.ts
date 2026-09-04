import { request, API_BASE_URL } from './request';

export interface Message {
  id: number;
  role: string;
  content: string;
  messageIndex: number;
  createdAt: string;
}

export interface Conversation {
  id: number;
  userId: number;
  title: string;
  sceneType: string;
  status: string;
  lastMessageAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProfileOption {
  label: string;
  value: string;
}

/** Python `/agent/chat/stream` tool 事件帧（经 Java SseEmitter 原样转发）。 */
export interface StreamToolFrame {
  type: 'tool';
  status: 'start' | 'end';
  name?: string;
  input?: unknown;
  output?: unknown;
}

/** SSE 事件帧的受支持字段（解析后据此分发）。 */
interface StreamFrame {
  type?: string;
  content?: string;
  answer?: string;
  conversationId?: number;
  remainingChance?: number;
  profileOptions?: ProfileOption[];
  message?: string;
}

export interface ChatResult {
  reply: string;
  conversationId: number;
  remainingChance?: number;
  profileOptions?: ProfileOption[];
}

export interface SessionState {
  id: number;
  conversationId: number;
  currentIntent: string;
  currentMode: string;
  currentStep: string;
  stateJson: string;
  updatedAt: string;
}

export const aiService = {
  createConversation: (title?: string, sceneType?: string) => {
    return request<Conversation>('/ai/conversations', {
      method: 'POST',
      body: JSON.stringify({ title: title || '新对话', sceneType: sceneType || 'chat' }),
    });
  },

  getConversations: () => {
    return request<Conversation[]>('/ai/conversations', {
      method: 'GET',
    });
  },

  getMessages: (conversationId: number) => {
    return request<Message[]>(`/ai/conversations/${conversationId}/messages`, {
      method: 'GET',
    });
  },

  chat: (conversationId: number, message: string, mode?: string) => {
    return request<ChatResult>(`/ai/conversations/${conversationId}/chat`, {
      method: 'POST',
      body: JSON.stringify({ message, mode: mode || 'chat' }),
    });
  },

  getSessionState: (conversationId: number) => {
    return request<SessionState>(`/ai/conversations/${conversationId}/state`, {
      method: 'GET',
    });
  },

  /**
   * Stage 3 真流式对话（POST SSE）。
   * 解析后端 SseEmitter 转发的 `data:{...}` 帧，经 handlers 逐帧回调。
   * @returns 'done'（收到终态 done 帧）| 'error'（收到 error 帧）| 'broken'（连接级失败/中途断开，可降级同步）
   */
  chatStream: async (
    conversationId: number,
    message: string,
    mode: string,
    handlers: {
      onToken?: (text: string) => void;
      onTool?: (frame: StreamToolFrame) => void;
      onDone?: (result: ChatResult) => void;
      onError?: (msg: string) => void;
    },
    signal?: AbortSignal,
  ): Promise<'done' | 'error' | 'broken'> => {
    const token = localStorage.getItem('token');
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    let response: Response;
    try {
      response = await fetch(`${API_BASE_URL}/ai/conversations/${conversationId}/chat/stream`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ message, mode }),
        signal,
      });
    } catch {
      return 'broken';
    }
    if (!response.ok || !response.body) return 'broken';

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let outcome: 'done' | 'error' | 'broken' = 'broken';

    const consumeEventLine = (line: string): 'done' | 'error' | null => {
      const text = line.trim();
      if (!text.startsWith('data:')) return null;
      let frame: StreamFrame;
      try {
        frame = JSON.parse(text.slice(5).trim()) as StreamFrame;
      } catch {
        return null;
      }
      switch (frame.type) {
        case 'token':
          if (frame.content) handlers.onToken?.(frame.content);
          return null;
        case 'tool':
          handlers.onTool?.(frame as unknown as StreamToolFrame);
          return null;
        case 'done': {
          const result: ChatResult = {
            reply: frame.answer ?? '',
            conversationId: Number(frame.conversationId ?? conversationId),
            remainingChance: frame.remainingChance,
            profileOptions: frame.profileOptions,
          };
          handlers.onDone?.(result);
          return 'done';
        }
        case 'error':
          handlers.onError?.(frame.message ?? 'AI 服务返回异常，请稍后再试。');
          return 'error';
        default:
          return null;
      }
    };

    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        // Java SseEmitter 每帧以空行结束（\n\n 或 \r\n\r\n）
        let sep = buffer.indexOf('\n\n');
        while (sep >= 0) {
          const frameLine = buffer.slice(0, sep);
          buffer = buffer.slice(sep + 2);
          const mark = consumeEventLine(frameLine.replace(/\r/g, ''));
          if (mark) outcome = mark;
          sep = buffer.indexOf('\n\n');
        }
      }
      // 流结束残留的一帧
      if (buffer.trim()) {
        const mark = consumeEventLine(buffer.replace(/\r/g, ''));
        if (mark) outcome = mark;
      }
    } catch {
      return 'broken';
    } finally {
      try { reader.releaseLock(); } catch { /* noop */ }
    }
    return outcome;
  },
};
