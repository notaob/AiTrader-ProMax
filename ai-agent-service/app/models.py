from pydantic import BaseModel
from typing import List, Optional, Dict, Any

class ChatRequest(BaseModel):
    message: str
    user_id: str
    session_id: Optional[str] = None
    history: Optional[List[Dict[str, str]]] = []
    mode: Optional[str] = "chat"  # "chat" 或 "strategy"
    state: Optional[Dict[str, Any]] = None
    summaries: Optional[List[str]] = []
    memories: Optional[List[str]] = []
    knowledge_chunks: Optional[List[str]] = []
    memory_candidates: Optional[List[str]] = []  # 输出用

class ChatResponse(BaseModel):
    success: bool
    answer: str
    thought_process: Optional[List[Dict[str, Any]]] = None
    execution_time: int
    memory_candidates: Optional[List[str]] = []

class RAGRequest(BaseModel):
    question: str
    user_id: str
    top_k: int = 5

class RAGResponse(BaseModel):
    success: bool
    answer: str
    sources: List[str]
    retrieved_documents: int
    execution_time: int

class GraphExecuteRequest(BaseModel):
    graph_type: str  # "trading" | "rag"
    inputs: Dict[str, Any]
