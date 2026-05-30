import { request } from './request';

export interface Memory {
  id: number;
  memoryType: string;
  content: string;
  importanceScore: number;
  confidenceScore: number;
  source: string;
}

export const memoryService = {
  getMemories: () => {
    return request<Memory[]>('/ai/memories', {
      method: 'GET',
    });
  },

  rebuildMemories: () => {
    return request<void>('/ai/memories/rebuild', {
      method: 'POST',
    });
  },
};
