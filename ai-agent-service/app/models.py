from typing import Any

from pydantic import BaseModel


class ChatRequest(BaseModel):
    message: str
    user_id: str
    session_id: str | None = None
    history: list[dict[str, str]] | None = []
    mode: str | None = "chat"  # "chat" 或 "strategy"
    state: dict[str, Any] | None = None
    summaries: list[str] | None = []
    memories: list[str] | None = []
    knowledge_chunks: list[str] | None = []
    memory_candidates: list[str] | None = []  # 输出用

class ChatResponse(BaseModel):
    success: bool
    answer: str
    thought_process: list[dict[str, Any]] | None = None
    execution_time: int
    memory_candidates: list[str] | None = []
    memory_candidates_typed: list[dict[str, str]] | None = []

class RAGRequest(BaseModel):
    question: str
    user_id: str
    top_k: int = 5

class RAGResponse(BaseModel):
    success: bool
    answer: str
    sources: list[str]
    retrieved_documents: int
    execution_time: int

class GraphExecuteRequest(BaseModel):
    graph_type: str  # "trading" | "rag"
    inputs: dict[str, Any]

class SyncChunksRequest(BaseModel):
    chunks: list[dict[str, Any]]  # [{"text": "...", "mysql_chunk_id": 123, "source": "...", "chunk_index": 0}]
    user_id: int = 0  # 用户ID，用于知识库按用户隔离

class MemoryRecallRequest(BaseModel):
    user_id: str | int
    query: str
    top_k: int = 5
    type: str | None = None  # 可选：preference / goal / constraint

class MemoryItem(BaseModel):
    memory_id: int  # MySQL ai_user_memories 自增主键
    user_id: str | int
    content: str
    memory_type: str = "preference"  # preference / goal / constraint

class MemorySaveRequest(BaseModel):
    memories: list[MemoryItem] = []

class MemoryDeleteRequest(BaseModel):
    user_id: str | int
    memory_ids: list[int] | None = None  # 为空时按 memory_type 或整用户清理
    memory_type: str | None = None

class SummarizeRequest(BaseModel):
    messages: list[dict[str, Any]] = []  # [{"role": "user|assistant", "content": "..."}]
