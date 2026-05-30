import json
import numpy as np
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass
import redis
from app.config import config

@dataclass
class Document:
    """文档"""
    id: str
    content: str
    source: str
    vector: List[float]
    metadata: Optional[Dict] = None

@dataclass
class SearchResult:
    """搜索结果"""
    document: Document
    score: float

class VectorStore:
    """向量存储（基于 Redis）"""
    
    def __init__(self):
        self.redis_client = redis.Redis(
            host=config.REDIS_HOST,
            port=config.REDIS_PORT,
            password=config.REDIS_PASSWORD if config.REDIS_PASSWORD else None,
            db=config.REDIS_DB,
            decode_responses=True
        )
        self.doc_prefix = "rag:doc:"
        self.vector_prefix = "rag:vector:"
        self.index_key = "rag:doc:index"
    
    def add_document(self, doc: Document) -> bool:
        """添加文档"""
        try:
            # 存储文档内容
            doc_key = f"{self.doc_prefix}{doc.id}"
            self.redis_client.hset(doc_key, mapping={
                "content": doc.content,
                "source": doc.source,
                "metadata": json.dumps(doc.metadata or {})
            })
            
            # 存储向量
            vector_key = f"{self.vector_prefix}{doc.id}"
            self.redis_client.set(vector_key, json.dumps(doc.vector))
            
            # 添加到索引
            self.redis_client.sadd(self.index_key, doc.id)
            
            return True
        except Exception as e:
            print(f"添加文档失败: {e}")
            return False
    
    def add_documents(self, docs: List[Document]) -> int:
        """批量添加文档"""
        success_count = 0
        for doc in docs:
            if self.add_document(doc):
                success_count += 1
        return success_count
    
    def similarity_search(self, query_vector: List[float], top_k: int = 5) -> List[SearchResult]:
        """相似度搜索"""
        try:
            # 获取所有文档ID
            doc_ids = self.redis_client.smembers(self.index_key)
            if not doc_ids:
                return []
            
            results = []
            
            for doc_id in doc_ids:
                # 获取向量
                vector_key = f"{self.vector_prefix}{doc_id}"
                vector_json = self.redis_client.get(vector_key)
                if not vector_json:
                    continue
                
                doc_vector = json.loads(vector_json)
                
                # 计算余弦相似度
                similarity = self._cosine_similarity(query_vector, doc_vector)
                
                # 获取文档内容
                doc_key = f"{self.doc_prefix}{doc_id}"
                doc_data = self.redis_client.hgetall(doc_key)
                
                if doc_data:
                    doc = Document(
                        id=doc_id,
                        content=doc_data.get("content", ""),
                        source=doc_data.get("source", ""),
                        vector=doc_vector,
                        metadata=json.loads(doc_data.get("metadata", "{}"))
                    )
                    results.append(SearchResult(document=doc, score=similarity))
            
            # 按相似度排序，返回 TopK
            results.sort(key=lambda x: x.score, reverse=True)
            return results[:top_k]
            
        except Exception as e:
            print(f"相似度搜索失败: {e}")
            return []
    
    def delete_document(self, doc_id: str) -> bool:
        """删除文档"""
        try:
            self.redis_client.delete(f"{self.doc_prefix}{doc_id}")
            self.redis_client.delete(f"{self.vector_prefix}{doc_id}")
            self.redis_client.srem(self.index_key, doc_id)
            return True
        except Exception as e:
            print(f"删除文档失败: {e}")
            return False
    
    def clear_all(self) -> bool:
        """清空所有文档"""
        try:
            doc_ids = self.redis_client.smembers(self.index_key)
            for doc_id in doc_ids:
                self.redis_client.delete(f"{self.doc_prefix}{doc_id}")
                self.redis_client.delete(f"{self.vector_prefix}{doc_id}")
            self.redis_client.delete(self.index_key)
            return True
        except Exception as e:
            print(f"清空文档失败: {e}")
            return False
    
    def get_stats(self) -> Dict:
        """获取统计信息"""
        try:
            count = self.redis_client.scard(self.index_key)
            return {"document_count": count}
        except Exception as e:
            print(f"获取统计失败: {e}")
            return {"document_count": 0}
    
    def _cosine_similarity(self, vec1: List[float], vec2: List[float]) -> float:
        """计算余弦相似度"""
        vec1 = np.array(vec1)
        vec2 = np.array(vec2)
        
        dot_product = np.dot(vec1, vec2)
        norm1 = np.linalg.norm(vec1)
        norm2 = np.linalg.norm(vec2)
        
        if norm1 == 0 or norm2 == 0:
            return 0.0
        
        return float(dot_product / (norm1 * norm2))

# 单例
vector_store = VectorStore()
