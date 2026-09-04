import time

import uvicorn
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

from app.config import config
from app.graph.trading_graph import trading_graph
from app.memory.conversation_summarizer import summarize_conversation
from app.memory.memory_service import (
    classify_user_message,
    delete_memories,
    memory_vector_store,
    recall_memories,
    save_memories,
)
from app.models import (
    ChatRequest,
    ChatResponse,
    GraphExecuteRequest,
    MemoryDeleteRequest,
    MemoryRecallRequest,
    MemorySaveRequest,
    RAGRequest,
    RAGResponse,
    SummarizeRequest,
    SyncChunksRequest,
)
from app.observability.langfuse import make_run_config
from app.rag.document_processor import document_processor
from app.rag.rag_service import rag_service
from app.rag.vector_store import vector_store as rag_vector_store
from app.streaming import build_chat_inputs, chat_stream_frames

app = FastAPI(
    title="AI Agent Service (LangGraph)",
    description="基于 LangGraph 的 AI Agent 服务 - HTTP 模式获取市场数据",
    version="1.1.0"
)

# CORS 配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.post("/agent/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """
    ReAct 模式对话 - 使用 LangGraph 实现
    支持 state、summaries、memories 和 knowledge_chunks 上下文管理
    """
    try:
        start_time = time.time()

        # LangGraph inputs 组装与 /agent/chat/stream 单一来源（见 app/streaming.py build_chat_inputs）
        inputs = build_chat_inputs(request)
        run_config = make_run_config(
            session_id=request.session_id or "default",
            user_id=request.user_id,
            mode=request.mode or "chat",
        )
        result = await trading_graph.ainvoke(inputs, config=run_config)

        execution_time = int((time.time() - start_time) * 1000)

        # 提取最终回答
        final_message = result["messages"][-1]
        answer = final_message.content if hasattr(final_message, 'content') else str(final_message)

        # 记忆提取：chat 模式用 AI 分类用户消息，strategy 模式跳过
        memory_candidate_texts = []
        memory_candidates_typed = []

        if (request.mode or "chat") != "strategy":
            classified = classify_user_message(request.message)
            if classified:
                memory_candidate_texts.append(classified["content"])
                memory_candidates_typed.append(classified)

        return ChatResponse(
            success=True,
            answer=answer,
            thought_process=result.get("intermediate_steps", []),
            execution_time=execution_time,
            memory_candidates=memory_candidate_texts,
            memory_candidates_typed=memory_candidates_typed
        )
    except Exception as e:
        import traceback
        error_msg = f"Error: {str(e)}\n{traceback.format_exc()}"
        print(error_msg)
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.post("/agent/chat/stream")
async def chat_stream(request: ChatRequest):
    """ReAct 模式流式对话（SSE）。

    帧协议见 app/streaming.py：data: {type: token|tool|done|error, ...}
    非流式 /agent/chat 保留作 fallback（Java 断线/异常时降级）。
    连接断开由 uvicorn 侧取消生成器，langgraph run 随之取消，不额外处理。
    """
    async def _event_source():
        async for frame in chat_stream_frames(request):
            yield frame

    return StreamingResponse(
        _event_source(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",  # 若置于 nginx 等代理后，关闭其响应缓冲以保真流式
        },
    )


@app.post("/agent/rag", response_model=RAGResponse)
async def rag_chat(request: RAGRequest):
    """
    RAG 模式对话 - Python 端实现
    """
    try:
        start_time = time.time()

        # 调用 Python RAG 服务
        result = rag_service.query(request.question, request.top_k)

        execution_time = int((time.time() - start_time) * 1000)

        return RAGResponse(
            success=result["success"],
            answer=result["answer"],
            sources=result["sources"],
            retrieved_documents=result["retrieved_documents"],
            execution_time=execution_time
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.post("/rag/add-text")
async def add_text_to_knowledge_base(text: str, source: str = "user"):
    """
    添加文本到知识库
    """
    try:
        count = document_processor.process_text(text, source)
        return {
            "success": True,
            "message": f"成功添加 {count} 个文档片段",
            "chunks": count
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.post("/rag/upload")
async def upload_file_to_knowledge_base(file: UploadFile = File(...)):  # noqa: B008
    """
    上传文件到知识库（支持 txt, md）
    """
    try:
        content = await file.read()
        text = content.decode("utf-8")

        count = document_processor.process_text(text, file.filename)
        return {
            "success": True,
            "message": f"成功处理文件 {file.filename}，添加 {count} 个文档片段",
            "chunks": count
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.get("/rag/stats")
async def get_knowledge_base_stats():
    """
    获取知识库统计
    """
    try:
        stats = rag_service.get_stats()
        return {
            "success": True,
            "document_count": stats["document_count"]
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.delete("/rag/clear")
async def clear_knowledge_base():
    """
    清空知识库
    """
    try:
        success = rag_service.clear_knowledge_base()
        return {
            "success": success,
            "message": "知识库已清空" if success else "清空失败"
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.post("/rag/sync")
async def sync_chunks_to_vector_store(request: SyncChunksRequest):
    """
    Java 保存 chunks 到 MySQL 后，调此端点生成 embedding 并写入 Redis Stack 向量索引
    """
    try:
        from app.rag.embedding import embedding_service
        from app.rag.vector_store import Document, vector_store

        texts = [c["text"] for c in request.chunks]
        vectors = embedding_service.embed_batch(texts)

        documents = []
        for chunk, vector in zip(request.chunks, vectors, strict=True):
            doc = Document(
                id=str(chunk["mysql_chunk_id"]),
                content=chunk["text"],
                source=chunk.get("source", ""),
                vector=vector,
                metadata={
                    "mysql_chunk_id": chunk["mysql_chunk_id"],
                    "chunk_index": chunk.get("chunk_index", 0),
                    "user_id": request.user_id,
                }
            )
            documents.append(doc)

        synced = vector_store.add_documents(documents)
        return {"success": True, "synced_count": synced}
    except Exception as e:
        import traceback
        print(f"[sync_chunks] error: {traceback.format_exc()}")
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.post("/agent/execute")
async def execute_graph(request: GraphExecuteRequest):
    """
    通用图执行接口 - 供 Java 后端灵活调用
    """
    try:
        if request.graph_type == "trading":
            run_config = make_run_config(
                session_id=str(request.inputs.get("session_id", "default")),
                user_id=str(request.inputs.get("user_id", "")),
                mode=str(request.inputs.get("mode", "chat")),
            )
            result = await trading_graph.ainvoke(request.inputs, config=run_config)
            return {
                "success": True,
                "result": result
            }
        else:
            return {
                "success": False,
                "error": f"未知的图类型: {request.graph_type}"
            }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.post("/agent/memories/recall")
async def memories_recall(request: MemoryRecallRequest):
    """语义召回用户长期记忆：embedding 查询 → 向量检索（user_id 隔离 + 可选 type 过滤）。

    供 Java AiMemoryServiceImpl.recallMemories() 调用；embedding 不可用时降级返回空列表。
    """
    try:
        memories = recall_memories(request.user_id, request.query, request.top_k, request.type)
        return {"success": True, "memories": memories, "count": len(memories)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.post("/agent/memories/save")
async def memories_save(request: MemorySaveRequest):
    """写入用户记忆向量：Java 先把记忆落库到 MySQL(ai_user_memories)，再回调本端点生成 embedding。

    返回 success=False（embedding 失败）不抛错，Java 侧按日志降级，不影响主链路。
    """
    try:
        items = [m.model_dump() for m in request.memories]
        saved = save_memories(items)
        return {"success": saved == len(items), "saved": saved, "total": len(items)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.post("/agent/memories/delete")
async def memories_delete(request: MemoryDeleteRequest):
    """删除记忆向量（记忆失效/清除时同步清理）。

    优先级：memory_ids 精确删除 > memory_type 按类删除 > 整用户清空。
    """
    try:
        deleted = delete_memories(request.user_id, request.memory_ids, request.memory_type)
        return {"success": True, "deleted": deleted}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.post("/agent/summarize")
async def summarize_messages(request: SummarizeRequest):
    """LLM 语义摘要：把批量对话消息压成要点摘要（Java AiSummaryServiceImpl 触发时机不变，只换摘要文本来源）。"""
    try:
        if not request.messages:
            raise HTTPException(status_code=400, detail="messages 不能为空")
        summary = summarize_conversation(request.messages)
        return {"success": True, "summary": summary, "message_count": len(request.messages)}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.get("/health")
async def health_check():
    """健康检查（含向量模式：auto 探测 RediSearch，缺失自动内存降级）"""
    vector_modes = {}
    for name, store in (("rag_vectors", rag_vector_store), ("mem_vectors", memory_vector_store)):
        try:
            vector_modes[name] = {
                "mode": store.mode,
                "forced": getattr(store, "forced", "auto"),
                **store.get_stats(),
            }
        except Exception as e:
            vector_modes[name] = {"mode": "unknown", "error": str(e)}
    return {
        "status": "healthy",
        "service": "ai-agent-langgraph",
        "version": "1.0.0",
        "llm": config.DASHSCOPE_MODEL,
        "vector_modes": vector_modes,
    }


@app.get("/tools")
async def list_tools():
    """列出可用工具"""
    from app.tools.analysis_tools import analysis_tools
    from app.tools.market_tools import market_tools
    from app.tools.rag_tools import rag_tools

    all_tools = market_tools + analysis_tools + rag_tools
    return {
        "tools": [
            {
                "name": tool.name,
                "description": tool.description
            }
            for tool in all_tools
        ]
    }


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=config.PORT)
