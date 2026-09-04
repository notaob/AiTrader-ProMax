"""知识库检索 / 入库共享核心：LangGraph 工具与 MCP server 复用（结构化返回）。

embedding_service / vector_store 保持延迟导入（与原 LangChain 工具行为一致，
避免模块导入期就建立 embedding 依赖）。
"""

from __future__ import annotations

from app.rag.document_processor import document_processor


def search_knowledge(query: str, user_id: int = 0, top_k: int = 5) -> dict:
    """语义检索指定用户的知识库。

    Returns:
        {"ok": bool, "error": str|None,
         "results": [{"index", "source", "score", "content"}, ...]}
    """
    try:
        from app.rag.embedding import embedding_service
        from app.rag.vector_store import vector_store

        query_vector = embedding_service.embed(query)
        results = vector_store.similarity_search(query_vector, top_k, user_id=user_id)

        items = [
            {
                "index": i,
                "source": r.document.source or "未知来源",
                "score": r.score,
                "content": r.document.content,
            }
            for i, r in enumerate(results, 1)
        ]
        return {"ok": True, "error": None, "results": items}
    except Exception as e:
        return {"ok": False, "error": str(e), "results": []}


def add_to_knowledge_base(text: str, source: str = "user") -> dict:
    """切分并写入知识库。

    Returns:
        {"ok": bool, "count": 写入片段数, "error": str|None}
    """
    try:
        count = document_processor.process_text(text, source)
        return {"ok": True, "count": int(count), "error": None}
    except Exception as e:
        return {"ok": False, "count": 0, "error": str(e)}
