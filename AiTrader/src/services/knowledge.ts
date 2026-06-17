import { request } from './request';

export interface KnowledgeDoc {
  id: number;
  userId: number;
  docType: string;
  title: string;
  source: string;
  status: string;
}

export interface Chunk {
  id: number;
  chunkIndex: number;
  chunkText: string;
  keywords: string;
}

export const knowledgeService = {
  getKnowledgeDocs: (userId: number) => {
    return request<KnowledgeDoc[]>('/ai/knowledge', {
      method: 'GET',
      params: { userId: String(userId) },
    });
  },

  uploadKnowledgeFile: (file: File, userId: number) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('userId', String(userId));
    return request<void>('/ai/knowledge/upload', {
      method: 'POST',
      body: formData,
    });
  },

  getDocChunks: (docId: number) => {
    return request<Chunk[]>(`/ai/knowledge/${docId}/chunks`, {
      method: 'GET',
    });
  },

  deleteKnowledgeDoc: (docId: number, userId: number) => {
    return request<void>(`/ai/knowledge/${docId}?userId=${userId}`, {
      method: 'DELETE',
    });
  },
};
