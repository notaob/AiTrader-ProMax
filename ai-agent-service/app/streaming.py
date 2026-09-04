"""Stage 3 流式对话支撑：与 /agent/chat 共用输入组装，把 LangGraph 事件流映射为 SSE 事件帧。

帧协议（SSE，每帧 data: {json}\\n\\n，utf-8，无 BOM）:
    {type: "token", content}                            最终回答的增量文本
    {type: "tool",  status: "start"|"end", name, input/output}
    {type: "done",  answer, memory_candidates_typed, execution_time_ms}
    {type: "error", message}

实现说明:
- 用 graph.astream_events(v2) 而非 graph.astream：后者只给节点级结果，无法逐字输出。
- token 文本只出现在"最终回答轮"（bind_tools 下 ReAct 中间轮只产出 tool_call，无文本），
  空 content 帧（tool_call 参数流 / usage 段）按 extract_chunk_text 过滤，故前端收到的
  文本帧顺序即最终回答，无需区分 run。
- 工具调用帧来自 on_tool_start/on_tool_end，前端可展示"正在调用 XX 工具…"。
- done 帧在流结束后补跑记忆分类（与 /agent/chat 完全一致），Java 收到 done 后才触发
  记忆/摘要持久化 → Stage 2 链路零回归。
- 错误在流内以 error 帧返回（SSE 流已建立，不能用 HTTP 错误码）；连接断开由
  CancelledError 自然传播，不吞。
"""
import json
import time

from langchain_core.messages import HumanMessage

from app.context.context_builder import build_prompt
from app.graph.trading_graph import trading_graph
from app.memory.memory_service import classify_user_message
from app.models import ChatRequest
from app.observability.langfuse import make_run_config


def extract_chunk_text(chunk) -> str:
    """AIMessageChunk → 增量文本。兼容 content 为 str 或 list[dict]（含 text/tool_call 段），空帧返回 ''。"""
    content = getattr(chunk, "content", None)
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for piece in content:
            if isinstance(piece, dict):
                parts.append(piece.get("text") or piece.get("content") or "")
            elif isinstance(piece, str):
                parts.append(piece)
        return "".join(parts)
    return ""


def _short(text: str, limit: int = 200) -> str:
    """工具输出截断（与 tools_node 的展示截断对齐），避免超大 observation 占满事件帧。"""
    text = text or ""
    return text if len(text) <= limit else text[:limit] + "..."


def build_chat_inputs(request: ChatRequest) -> dict:
    """LangGraph inputs 组装 —— 与 /agent/chat 单一来源，杜绝流式/非流式双写漂移。"""
    recent_messages = request.history or []
    system_prompt = build_prompt(
        state=request.state,
        recent_messages=recent_messages,
        summaries=request.summaries or [],
        memories=request.memories or [],
        knowledge_chunks=request.knowledge_chunks or [],
        current_message=request.message,
        mode=request.mode or "chat",
    )

    messages = []
    for msg in recent_messages:
        if msg.get("role") == "user":
            messages.append(HumanMessage(content=msg["content"]))
        elif msg.get("role") == "assistant":
            from langchain_core.messages import AIMessage

            messages.append(AIMessage(content=msg["content"]))
    messages.append(HumanMessage(content=request.message))

    return {
        "messages": messages,
        "current_message": request.message,
        "user_id": request.user_id,
        "session_id": request.session_id or "default",
        "intermediate_steps": [],
        "mode": request.mode or "chat",
        "context": {
            "state": request.state,
            "summaries": request.summaries,
            "memories": request.memories,
            "knowledge_chunks": request.knowledge_chunks,
            "system_prompt": system_prompt,
        },
    }


def _sse_frame(payload: dict) -> str:
    """单条 SSE data 帧。ensure_ascii=False → 中文直接 utf-8 输出，规避跨端编码/半包。"""
    return "data: " + json.dumps(payload, ensure_ascii=False) + "\n\n"


async def chat_stream_frames(request: ChatRequest):
    """产出 SSE 帧字符串；异常降级为 error 帧而不是抛出让连接中断。

    Java/前端只依赖三种语义：token（增量）、tool（过程）、done（终态 + 记忆候选）、error（兜底）。
    """
    start = time.time()
    try:
        inputs = build_chat_inputs(request)
        run_config = make_run_config(
            session_id=request.session_id or "default",
            user_id=request.user_id,
            mode=request.mode or "chat",
        )

        token_texts: list[str] = []
        last_llm_message = None

        async for ev in trading_graph.astream_events(inputs, config=run_config, version="v2"):
            kind = ev.get("event") or ""
            data = ev.get("data") or {}
            if kind == "on_chat_model_stream":
                txt = extract_chunk_text(data.get("chunk"))
                if txt:
                    token_texts.append(txt)
                    yield _sse_frame({"type": "token", "content": txt})
            elif kind == "on_tool_start":
                yield _sse_frame(
                    {
                        "type": "tool",
                        "status": "start",
                        "name": ev.get("name") or "",
                        "input": data.get("input"),
                    }
                )
            elif kind == "on_tool_end":
                yield _sse_frame(
                    {
                        "type": "tool",
                        "status": "end",
                        "name": ev.get("name") or "",
                        "output": _short(str(data.get("output"))),
                    }
                )
            elif kind == "on_chat_model_end":
                # 每次 LLM run 结束的完整消息；最后一次即最终回答（ReAct 图以 agent 无工具调用收尾）
                last_llm_message = data.get("output")

        answer = extract_chunk_text(last_llm_message) or "".join(token_texts)

        # 记忆分类与 /agent/chat 保持一致：strategy 模式跳过
        memory_candidates_typed: list[dict] = []
        if (request.mode or "chat") != "strategy":
            classified = classify_user_message(request.message)
            if classified:
                memory_candidates_typed.append(classified)

        yield _sse_frame(
            {
                "type": "done",
                "answer": answer,
                "execution_time_ms": int((time.time() - start) * 1000),
                "memory_candidates_typed": memory_candidates_typed,
            }
        )
    except Exception as e:
        import traceback

        traceback.print_exc()
        yield _sse_frame({"type": "error", "message": str(e)})
