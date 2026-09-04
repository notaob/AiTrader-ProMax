import json
import sys
from dataclasses import dataclass

import numpy as np
import redis
from redis.commands.search.field import NumericField, TextField, VectorField
from redis.commands.search.index_definition import IndexDefinition
from redis.exceptions import ResponseError

from app.config import config


def _eprint(*args, **kwargs):
    """模块日志统一输出到 stderr：stdout 预留给 MCP stdio JSON-RPC 协议帧，禁止业务日志占用。"""
    print(*args, file=sys.stderr, **kwargs)


VALID_VECTOR_MODES = ("auto", "memory", "redis")


@dataclass
class Document:
    """文档"""
    id: str
    content: str
    source: str
    vector: list[float]
    metadata: dict | None = None


@dataclass
class SearchResult:
    """搜索结果"""
    document: Document
    score: float


class MemoryVectorStore:
    """进程内向量存储（Redis 不可用时的降级模式）。

    用途：本地开发、CI、pytest 评测——不依赖外部 Redis Stack 即可跑通
    RAG / 知识库检索链路。接口与 VectorStore 一致，数据不持久化，
    进程重启即清空（控制台会有明确提示）。
    """

    def __init__(self):
        self._docs: dict[str, Document] = {}

    def add_document(self, doc: Document) -> bool:
        self._docs[doc.id] = doc
        return True

    def add_documents(self, docs: list[Document]) -> int:
        for doc in docs:
            self._docs[doc.id] = doc
        return len(docs)

    def similarity_search(self, query_vector: list[float], top_k: int = 5, user_id: int = 0) -> list[SearchResult]:
        """余弦相似度检索。score 与 Redis COSINE 距离语义一致：越小越相关。"""
        q = np.asarray(query_vector, dtype=np.float32)
        q_norm = np.linalg.norm(q)
        if q_norm == 0 or not self._docs:
            return []
        q = q / q_norm

        scored: list = []
        for doc in self._docs.values():
            uid = (doc.metadata or {}).get("user_id", 0)
            if user_id > 0 and uid != user_id:
                continue
            v = np.asarray(doc.vector, dtype=np.float32)
            v_norm = np.linalg.norm(v)
            if v_norm == 0:
                continue
            distance = 1.0 - float(q @ v) / v_norm
            scored.append((distance, doc))

        scored.sort(key=lambda x: x[0])
        return [SearchResult(document=doc, score=d) for d, doc in scored[:top_k]]

    def delete_document(self, doc_id: str) -> bool:
        return self._docs.pop(doc_id, None) is not None

    def delete_documents(self, doc_ids: list[str]) -> int:
        count = 0
        for doc_id in doc_ids:
            if self._docs.pop(doc_id, None) is not None:
                count += 1
        return count

    def delete_by_user(self, user_id: int, memory_type: str | None = None) -> int:
        """删除指定用户（可限定记忆类型）的全部文档。"""
        doomed = []
        for doc_id, doc in self._docs.items():
            meta = doc.metadata or {}
            if meta.get("user_id", 0) != user_id:
                continue
            if memory_type is not None and meta.get("memory_type") != memory_type:
                continue
            doomed.append(doc_id)
        for doc_id in doomed:
            self._docs.pop(doc_id, None)
        return len(doomed)

    def clear_all(self) -> bool:
        self._docs.clear()
        return True

    def get_stats(self) -> dict:
        return {"document_count": len(self._docs)}


class VectorStore:
    """向量存储（优先 Redis Stack 向量索引；Redis 不可用时自动降级为内存模式）

    关键设计：所有写入操作使用 decode_responses=False 的二进制客户端，
    确保文本字段和向量字段在同一次 hset 中写入，
    RediSearch 才能正确索引包含 vector 的完整文档。

    可参数化 index_name / doc_prefix，供知识库(RAG)与用户记忆两个独立索引复用：
    - 知识库：vector_store（索引 rag_vectors，前缀 rag:doc）
    - 用户记忆：memory_vector_store（索引 mem_vectors，前缀 mem:doc），见 app/memory/memory_service.py
    """

    INDEX_NAME = "rag_vectors"
    DOC_PREFIX = "rag:doc"

    def __init__(self, index_name: str | None = None, doc_prefix: str | None = None):
        self.index_name = index_name or VectorStore.INDEX_NAME
        self.doc_prefix = doc_prefix or VectorStore.DOC_PREFIX
        self.mode = "memory"  # "redis"(真 HNSW) 或 "memory"(进程内)
        self.forced = "auto"  # 配置决策保留，供日志/健康检查
        self.redis = None
        self.redis_text = None
        self._memory = MemoryVectorStore()

        try:
            self.forced = (config.REDIS_VECTOR_MODE or "auto").strip().lower()
        except Exception:
            self.forced = "auto"
        if self.forced not in VALID_VECTOR_MODES:
            _eprint(f"[VectorStore] {self.index_name}: 非法 REDIS_VECTOR_MODE={self.forced!r}，"
                  f"仅支持 {VALID_VECTOR_MODES}，按 auto 处理。")
            self.forced = "auto"

        if self.forced == "memory":
            _eprint(f"[VectorStore] {self.index_name}: REDIS_VECTOR_MODE=memory → 进程内模式"
                  f"（语义检索仍用 embedding + cosine，仅不跨进程持久化）。")
            return

        try:
            self._connect_redis()  # 成功即置 redis 模式
        except Exception as e:
            if self.forced == "redis":
                # 明确要求真索引但不可用：直接暴露错误，杜绝"假 redis 模式"
                raise RuntimeError(
                    f"[VectorStore] {self.index_name}: REDIS_VECTOR_MODE=redis 但无法建立真实 HNSW 索引: {e}\n"
                    f"    请确认 Redis 是 redis/redis-stack-server（带 RediSearch 模块），"
                    f"或改回 REDIS_VECTOR_MODE=auto/memory。"
                ) from e
            # auto：静默降级为内存模式，并给出部署提示
            _eprint(f"[VectorStore] {self.index_name}: Redis 不可用/无 RediSearch"
                  f"（{type(e).__name__}: {e}）→ 自动降级为进程内内存模式。\n"
                  f"    说明：语义检索仍真实（DashScope embedding + cosine），仅索引不跨进程持久化；\n"
                  f"    部署 Redis Stack（docker-compose redis/redis-stack-server）后自动启用真实 HNSW 索引。")

    def _connect_redis(self):
        """连接 Redis 并校验 RediSearch；失败抛异常（由 __init__ 决策降级或报错）。"""
        # 只用一个二进制客户端（decode_responses=False）：文本字段也以 bytes 写入，
        # RediSearch 才能正常索引；向量字段以 raw FLOAT32 bytes 写入。
        self.redis = redis.Redis(
            host=config.REDIS_HOST,
            port=config.REDIS_PORT,
            password=config.REDIS_PASSWORD if config.REDIS_PASSWORD else None,
            db=config.REDIS_DB,
            decode_responses=False,
            socket_connect_timeout=1.0,
        )
        # 文本读取客户端（方便用字符串读取索引信息）
        self.redis_text = redis.Redis(
            host=config.REDIS_HOST,
            port=config.REDIS_PORT,
            password=config.REDIS_PASSWORD if config.REDIS_PASSWORD else None,
            db=config.REDIS_DB,
            decode_responses=True,
            socket_connect_timeout=1.0,
        )
        # 快速探测连接（失败立即抛异常进入降级/报错分支）
        if not self.redis.ping():
            raise ConnectionError("Redis ping 失败")
        # 显式探测 RediSearch 模块：普通 Redis 没有 FT.* 命令 → 明确降级，而不是带病跑"假索引"
        self._probe_redisearch()
        self._create_index()
        self.mode = "redis"
        _eprint(f"[VectorStore] Redis Stack 索引 {self.index_name} 就绪 (mode=redis, HNSW)")

    def _probe_redisearch(self):
        """用 FT._LIST 探测 RediSearch 模块是否可用；缺失时抛 RuntimeError。"""
        try:
            self.redis.execute_command("FT._LIST")
        except ResponseError as e:
            raise RuntimeError(
                "当前 Redis 未加载 RediSearch(FT.*) 模块 —— 请使用 redis/redis-stack-server"
                "（普通 redis-server 只能跑内存降级，不会得到真实 HNSW 索引）"
            ) from e

    def _create_index(self):
        """创建 HNSW 向量索引，如果已存在则跳过"""
        try:
            self.redis_text.ft(self.index_name).info()
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

        definition = IndexDefinition(prefix=[self.doc_prefix + ":"])
        self.redis_text.ft(self.index_name).create_index(
            schema, definition=definition
        )
        _eprint(f"[VectorStore] 索引 {self.index_name} 创建成功 (HNSW, dim=1536)")

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
        if self.mode == "memory":
            return self._memory.add_document(doc)
        try:
            key = f"{self.doc_prefix}:{doc.id}"
            mapping = self._build_mapping(doc)
            self.redis.hset(key, mapping=mapping)
            return True
        except Exception as e:
            _eprint(f"添加文档失败: {e}")
            return False

    def add_documents(self, docs: list[Document]) -> int:
        """批量添加文档（pipeline，所有字段一次性写入）"""
        if self.mode == "memory":
            return self._memory.add_documents(docs)
        try:
            pipe = self.redis.pipeline()
            for doc in docs:
                key = f"{self.doc_prefix}:{doc.id}"
                mapping = self._build_mapping(doc)
                pipe.hset(key, mapping=mapping)
            pipe.execute()
            return len(docs)
        except Exception as e:
            _eprint(f"批量添加文档失败: {e}")
            return 0

    def similarity_search(self, query_vector: list[float], top_k: int = 5, user_id: int = 0) -> list[SearchResult]:
        """KNN 相似度检索（redis 模式用 FT.SEARCH；memory 模式用 numpy 余弦）"""
        if self.mode == "memory":
            return self._memory.similarity_search(query_vector, top_k, user_id)
        try:
            query_bytes = np.array(query_vector, dtype=np.float32).tobytes()

            # 构建 KNN 查询：如果有 user_id，加 @user_id 过滤
            if user_id > 0:
                query_str = f"@user_id:[{user_id} {user_id}]=>[KNN {top_k} @vector $query_vec]"
            else:
                query_str = f"*=>[KNN {top_k} @vector $query_vec]"

            # Step 1: KNN 搜索（NOCONTENT + WITHSCORES）
            raw_result = self.redis.execute_command(
                "FT.SEARCH", self.index_name,
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
                prefix = self.doc_prefix + ":"
                doc_id = doc_id_raw.replace(prefix, "") if doc_id_raw.startswith(prefix) else doc_id_raw

                # 获取 KNN cosine distance score
                score_val = item.get(score_key, 0.0)
                score = float(score_val) if score_val else 0.0

                # HGETALL 读取文本字段（二进制客户端，手动解码非向量字段）
                key = f"{self.doc_prefix}:{doc_id}"
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
            _eprint(f"向量搜索失败: {e}")
            import traceback
            traceback.print_exc()
            return []

    def delete_document(self, doc_id: str) -> bool:
        """删除文档"""
        if self.mode == "memory":
            return self._memory.delete_document(doc_id)
        try:
            key = f"{self.doc_prefix}:{doc_id}"
            self.redis.delete(key)
            return True
        except Exception as e:
            _eprint(f"删除文档失败: {e}")
            return False

    def delete_documents(self, doc_ids: list[str]) -> int:
        """批量删除文档（按 doc id）"""
        if self.mode == "memory":
            return self._memory.delete_documents(doc_ids)
        try:
            if not doc_ids:
                return 0
            keys = [f"{self.doc_prefix}:{doc_id}" for doc_id in doc_ids]
            deleted = self.redis.delete(*keys)
            return int(deleted)
        except Exception as e:
            _eprint(f"批量删除文档失败: {e}")
            return 0

    def delete_by_user(self, user_id: int, memory_type: str | None = None) -> int:
        """删除指定用户（可限定记忆类型）的全部文档，redis 模式按前缀 SCAN 匹配。"""
        if self.mode == "memory":
            return self._memory.delete_by_user(user_id, memory_type)
        try:
            prefix = self.doc_prefix + ":"
            pipe = self.redis.pipeline()
            to_delete = []
            for key in self.redis_text.scan_iter(match=prefix + "*", count=200):
                meta_raw = self.redis_text.hget(key, "metadata")
                meta = {}
                if meta_raw:
                    try:
                        meta = json.loads(meta_raw)
                    except (json.JSONDecodeError, TypeError):
                        meta = {}
                if meta.get("user_id", 0) != user_id:
                    continue
                if memory_type is not None and meta.get("memory_type") != memory_type:
                    continue
                to_delete.append(key)
            for key in to_delete:
                pipe.delete(key)
            if to_delete:
                pipe.execute()
            return len(to_delete)
        except Exception as e:
            _eprint(f"按用户删除文档失败: {e}")
            return 0

    def clear_all(self) -> bool:
        """清空所有文档并重建索引"""
        if self.mode == "memory":
            return self._memory.clear_all()
        try:
            self.redis_text.ft(self.index_name).dropindex(delete_documents=True)
            self._create_index()
            return True
        except Exception as e:
            _eprint(f"清空文档失败: {e}")
            return False

    def get_stats(self) -> dict:
        """获取统计信息（含当前模式，供 /health 与运维区分内存降级 vs 真实 HNSW）"""
        try:
            if self.mode == "memory":
                stats = self._memory.get_stats()
            else:
                info = self.redis_text.ft(self.index_name).info()
                stats = {"document_count": info.get("num_records", 0)}
        except Exception as e:
            _eprint(f"获取统计失败: {e}")
            stats = {"document_count": 0}
        stats["mode"] = self.mode
        stats["engine"] = "redis_hnsw" if self.mode == "redis" else "memory_numpy"
        return stats


# 单例
vector_store = VectorStore()
