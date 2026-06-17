import httpx
import numpy as np
from typing import List, Union
from app.config import config

class EmbeddingService:
    """文本向量化服务（使用阿里云 DashScope）"""
    
    def __init__(self):
        self.api_key = config.DASHSCOPE_API_KEY
        self.base_url = config.DASHSCOPE_BASE_URL
        self.model = "text-embedding-v2"  # 阿里云 Embedding 模型
        self.vector_dimension = 1536
    
    def embed(self, text: str) -> List[float]:
        """单文本向量化"""
        try:
            response = httpx.post(
                "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding",
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json"
                },
                json={
                    "model": self.model,
                    "input": {
                        "texts": [text]
                    }
                },
                timeout=30.0
            )
            response.raise_for_status()
            data = response.json()

            # 提取向量
            embedding = data["output"]["embeddings"][0]["embedding"]
            return embedding
        except Exception as e:
            raise RuntimeError(f"Embedding 失败: {e}") from e
    
    def embed_batch(self, texts: List[str]) -> List[List[float]]:
        """批量文本向量化"""
        try:
            response = httpx.post(
                "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding",
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json"
                },
                json={
                    "model": self.model,
                    "input": {
                        "texts": texts
                    }
                },
                timeout=60.0
            )
            response.raise_for_status()
            data = response.json()

            # 提取所有向量
            embeddings = [item["embedding"] for item in data["output"]["embeddings"]]
            return embeddings
        except Exception as e:
            raise RuntimeError(f"批量 Embedding 失败: {e}") from e

# 单例
embedding_service = EmbeddingService()
