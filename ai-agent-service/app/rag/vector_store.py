import json
import numpy as np
from typing import List, Dict, Optional
from dataclasses import dataclass
import redis
from redis.commands.search.field import TextField, NumericField, VectorField
from redis.commands.search.index_definition import IndexDefinition
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
    """向量存储（基于 Redis Stack 向量索引）
    关键设计：所有写入操作使用 decode_responses=False 的二进制客户端，
    确保文本字段和向量字段在同一次 hset 中写入，
    RediSearch 才能正确索引包含 vector 的完整文档。"""

    INDEX_NAME = "rag_vectors"
    DOC_PREFIX = "rag:doc"

    def __init__(self):
        # 只用一个二进制客户端（decode_responses=False）
        # 文本字段也以 bytes 形式写入，RediSearch 正常索引
        # 向量字段以 raw FLOAT32 bytes 写入
        self.redis = redis.Redis(
            host=config.REDIS_HOST,
            port=config.REDIS_PORT,
            password=config.REDIS_PASSWORD if config.REDIS_PASSWORD else None,
            db=config.REDIS_DB,
            decode_responses=False
        )
        # 文本读取客户端（方便用字符串读取索引信息）
        self.redis_text = redis.Redis(
            host=config.REDIS_HOST,
            port=config.REDIS_PORT,
            password=config.REDIS_PASSWORD if config.REDIS_PASSWORD else None,
            db=config.REDIS_DB,
            decode_responses=True
        )
        self._create_index()

    def _create_index(self):
        """创建 HNSW 向量索引，如果已存在则跳过"""
        try:
            self.redis_text.ft(self.INDEX_NAME).info()
            return  # 索引已存在
        except Exception:
            pass

        schema = (
            TextField("content"),
            TextField("source"),
            TextField("metadata"),
            NumericField("mysql_chunk_id", sortable=True),
            NumericField("user_id", sortable=True),
            VectorField("vector",
                algorithm="HNSW",
                attributes={
                    "TYPE": "FLOAT32",
                    "DIM": 1536,
                    "DISTANCE_METRIC": "COSINE",
                    "INITIAL_CAP": 1000,
                    "M": 16,
                    "EF_CONSTRUCTION": 200,
                }
            ),
        )

        definition = IndexDefinition(prefix=[self.DOC_PREFIX + ":"])
        self.redis_text.ft(self.INDEX_NAME).create_index(
            schema, definition=definition
        )
        print(f"[VectorStore] 索引 {self.INDEX_NAME} 创建成功 (HNSW, dim=1536)")

    def _build_mapping(self, doc: Document) -> dict:
        """构建 hset mapping：文本字段编码为 bytes，向量字段保持 raw bytes，
        所有字段一次性写入，保证 RediSearch 索引完整文档"""
        vector_bytes = np.array(doc.vector, dtype=np.float32).tobytes()
        mysql_chunk_id = (doc.metadata or {}).get("mysql_chunk_id", 0)
        user_id = (doc.metadata or {}).get("user_id", 0)
        return {
            "content": doc.content.encode("utf-8"),
            "source": (doc.source or "").encode("utf-8"),
            "metadata": json.dumps(doc.metadata or {}).encode("utf-8"),
            "mysql_chunk_id": str(mysql_chunk_id).encode("utf-8"),
            "user_id": str(user_id).encode("utf-8"),
            "vector": vector_bytes,
        }

    def add_document(self, doc: Document) -> bool:
        """添加单个文档（所有字段一次性写入）"""
        try:
            key = f"{self.DOC_PREFIX}:{doc.id}"
            mapping = self._build_mapping(doc)
            self.redis.hset(key, mapping=mapping)
            return True
        except Exception as e:
            print(f"添加文档失败: {e}")
            return False

    def add_documents(self, docs: List[Document]) -> int:
        """批量添加文档（pipeline，所有字段一次性写入）"""
        try:
            pipe = self.redis.pipeline()
            for doc in docs:
                key = f"{self.DOC_PREFIX}:{doc.id}"
                mapping = self._build_mapping(doc)
                pipe.hset(key, mapping=mapping)
            pipe.execute()
            return len(docs)
        except Exception as e:
            print(f"批量添加文档失败: {e}")
            return 0

    def similarity_search(self, query_vector: List[float], top_k: int = 5, user_id: int = 0) -> List[SearchResult]:
        """用 FT.SEARCH 做 KNN 查询（HNSW 近似最近邻）
        redis-py 8.x + decode_responses=False 兼容方案：两步搜索
        1. execute_command FT.SEARCH NOCONTENT WITHSCORES 获取 doc IDs + scores
           支持 user_id 过滤：只搜索该用户的文档
        2. 用二进制客户端 HGETALL 读取每个文档的文本字段（跳过向量二进制）"""
        try:
            query_bytes = np.array(query_vector, dtype=np.float32).tobytes()

            # 构建 KNN 查询：如果有 user_id，加 @user_id 过滤
            if user_id > 0:
                query_str = f"@user_id:[{user_id} {user_id}]=>[KNN {top_k} @vector $query_vec]"
            else:
                query_str = f"*=>[KNN {top_k} @vector $query_vec]"

            # Step 1: KNN 搜索（NOCONTENT + WITHSCORES）
            raw_result = self.redis.execute_command(
                "FT.SEARCH", self.INDEX_NAME,
                query_str,
                "PARAMS", 2, "query_vec", query_bytes,
                "DIALECT", 2,
                "NOCONTENT",
                "WITHSCORES",
                "LIMIT", 0, top_k
            )

            if not raw_result or not isinstance(raw_result, dict):
                return []

            # bytes key：b'total_results' 而非字符串 "total_results"
            total_key = b"total_results" if b"total_results" in raw_result else "total_results"
            total_results = raw_result.get(total_key, 0)
            if isinstance(total_results, bytes):
                total_results = int(total_results)
            if total_results == 0:
                return []

            results_key = b"results" if b"results" in raw_result else "results"
            results_list = raw_result.get(results_key, [])
            if not results_list:
                return []

            # Step 2: 解析结果 + HGETALL 读取文本字段
            search_results = []
            id_key = b"id" if b"id" in results_list[0] else "id"
            score_key = b"score" if b"score" in results_list[0] else "score"

            for item in results_list:
                doc_id_raw = item.get(id_key, b"")
                if isinstance(doc_id_raw, bytes):
                    doc_id_raw = doc_id_raw.decode("utf-8")
                prefix = self.DOC_PREFIX + ":"
                doc_id = doc_id_raw.replace(prefix, "") if doc_id_raw.startswith(prefix) else doc_id_raw

                # 获取 KNN cosine distance score
                score_val = item.get(score_key, 0.0)
                score = float(score_val) if score_val else 0.0

                # HGETALL 读取文本字段（二进制客户端，手动解码非向量字段）
                key = f"{self.DOC_PREFIX}:{doc_id}"
                raw_fields = self.redis.hgetall(key)

                doc_fields = {}
                for field_key, field_val in raw_fields.items():
                    k = field_key.decode("utf-8") if isinstance(field_key, bytes) else field_key
                    if k == "vector":
                        continue  # 跳过向量二进制字段
                    v = field_val.decode("utf-8") if isinstance(field_val, bytes) else str(field_val)
                    doc_fields[k] = v

                # 解析 metadata
                metadata_raw = doc_fields.get("metadata", "{}")
                try:
                    metadata = json.loads(metadata_raw) if metadata_raw else {}
                except (json.JSONDecodeError, TypeError):
                    metadata = {}

                search_results.append(SearchResult(
                    document=Document(
                        id=doc_id,
                        content=doc_fields.get("content", ""),
                        source=doc_fields.get("source", ""),
                        vector=[],  # 搜索结果不需要返回向量
                        metadata=metadata,
                    ),
                    score=score,
                ))

            return search_results
        except Exception as e:
            print(f"向量搜索失败: {e}")
            import traceback
            traceback.print_exc()
            return []

    def delete_document(self, doc_id: str) -> bool:
        """删除文档"""
        try:
            key = f"{self.DOC_PREFIX}:{doc_id}"
            self.redis.delete(key)
            return True
        except Exception as e:
            print(f"删除文档失败: {e}")
            return False

    def clear_all(self) -> bool:
        """清空所有文档并重建索引"""
        try:
            self.redis_text.ft(self.INDEX_NAME).dropindex(delete_documents=True)
            self._create_index()
            return True
        except Exception as e:
            print(f"清空文档失败: {e}")
            return False

    def get_stats(self) -> Dict:
        """获取统计信息"""
        try:
            info = self.redis_text.ft(self.INDEX_NAME).info()
            return {"document_count": info.get("num_records", 0)}
        except Exception as e:
            print(f"获取统计失败: {e}")
            return {"document_count": 0}

# 单例
vector_store = VectorStore()
