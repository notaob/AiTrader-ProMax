from fastapi import FastAPI, HTTPException, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
import time

from app.models import ChatRequest, ChatResponse, RAGRequest, RAGResponse, GraphExecuteRequest, SyncChunksRequest
from app.graph.trading_graph import trading_graph
from app.config import config
from app.rag.rag_service import rag_service
from app.rag.document_processor import document_processor
from app.context.context_builder import build_prompt
from app.memory.memory_service import extract_memory_from_dialogue, classify_user_message
from langchain_core.messages import HumanMessage

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

        # 使用 context_builder 组装完整 prompt
        recent_messages = request.history or []
        system_prompt = build_prompt(
            state=request.state,
            recent_messages=recent_messages,
            summaries=request.summaries or [],
            memories=request.memories or [],
            knowledge_chunks=request.knowledge_chunks or [],
            current_message=request.message,
            mode=request.mode or "chat"
        )

        # 构建消息历史
        messages = []

        # 添加历史消息
        for msg in request.history or []:
            if msg.get("role") == "user":
                messages.append(HumanMessage(content=msg["content"]))
            elif msg.get("role") == "assistant":
                from langchain_core.messages import AIMessage
                messages.append(AIMessage(content=msg["content"]))

        # 添加当前消息
        messages.append(HumanMessage(content=request.message))

        # 调用 LangGraph
        result = await trading_graph.ainvoke({
            "messages": messages,
            "user_id": request.user_id,
            "session_id": request.session_id or "default",
            "intermediate_steps": [],
            "mode": request.mode or "chat",
            "context": {
                "state": request.state,
                "summaries": request.summaries,
                "memories": request.memories,
                "knowledge_chunks": request.knowledge_chunks,
                "system_prompt": system_prompt
            }
        })

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
        raise HTTPException(status_code=500, detail=str(e))


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
        raise HTTPException(status_code=500, detail=str(e))


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
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/rag/upload")
async def upload_file_to_knowledge_base(file: UploadFile = File(...)):
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
        raise HTTPException(status_code=500, detail=str(e))


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
        raise HTTPException(status_code=500, detail=str(e))


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
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/rag/sync")
async def sync_chunks_to_vector_store(request: SyncChunksRequest):
    """
    Java 保存 chunks 到 MySQL 后，调此端点生成 embedding 并写入 Redis Stack 向量索引
    """
    try:
        from app.rag.vector_store import vector_store, Document
        from app.rag.embedding import embedding_service

        texts = [c["text"] for c in request.chunks]
        vectors = embedding_service.embed_batch(texts)

        documents = []
        for chunk, vector in zip(request.chunks, vectors):
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
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/agent/execute")
async def execute_graph(request: GraphExecuteRequest):
    """
    通用图执行接口 - 供 Java 后端灵活调用
    """
    try:
        if request.graph_type == "trading":
            result = await trading_graph.ainvoke(request.inputs)
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
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
async def health_check():
    """健康检查"""
    return {
        "status": "healthy",
        "service": "ai-agent-langgraph",
        "version": "1.0.0",
        "llm": config.DASHSCOPE_MODEL
    }


@app.get("/tools")
async def list_tools():
    """列出可用工具"""
    from app.tools.market_tools import market_tools
    from app.tools.analysis_tools import analysis_tools
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
