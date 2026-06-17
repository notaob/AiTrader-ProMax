import { request } from './request';

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
    return request<{ reply: string; conversationId: number; remainingChance?: number }>(`/ai/conversations/${conversationId}/chat`, {
      method: 'POST',
      body: JSON.stringify({ message, mode: mode || 'chat' }),
    });
  },

  getSessionState: (conversationId: number) => {
    return request<SessionState>(`/ai/conversations/${conversationId}/state`, {
      method: 'GET',
    });
  },
};
