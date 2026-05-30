import { request } from './request';

export interface KnowledgeDoc {
  id: number;
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

export interface UploadDocPayload {
  title: string;
  docType: string;
  content: string;
}

export const knowledgeService = {
  getKnowledgeDocs: () => {
    return request<KnowledgeDoc[]>('/ai/knowledge', {
      method: 'GET',
    });
  },

  uploadKnowledgeDoc: (doc: UploadDocPayload) => {
    return request<void>('/ai/knowledge/upload', {
      method: 'POST',
      body: JSON.stringify(doc),
    });
  },

  getDocChunks: (docId: number) => {
    return request<Chunk[]>(`/ai/knowledge/${docId}/chunks`, {
      method: 'GET',
    });
  },
};
