import re

from app.rag.embedding import embedding_service
from app.rag.vector_store import Document, VectorStore

# 用户长期记忆专用向量索引（与 RAG 知识库 rag_vectors 隔离；以 user_id 隔离租户）
memory_vector_store = VectorStore(index_name="mem_vectors", doc_prefix="mem:doc")


def _strip_think_blocks(text: str) -> str:
    """去除推理模型输出中的 <think>...</think> 思考块（deepseek/qwen 推理模式等），分类/摘要共用。"""
    return re.sub(r"<think>[\s\S]*?</think>", "", text).strip()


def _parse_user_id(user_id) -> int:
    """Java userId 为 Long；转 int 供向量库 user_id 数值过滤，非法时按 0（不过滤）处理。"""
    try:
        return int(user_id)
    except (TypeError, ValueError):
        return 0


def recall_memories(user_id, query: str, top_k: int = 5, memory_type: str | None = None) -> list[dict]:
    """语义召回用户记忆：embedding 查询后向量检索，支持 memory_type(preference/goal/constraint) 过滤。

    返回 [{memory_id, content, memory_type, score(越小越相关), similarity}]；embedding 失败时返回空列表。
    """
    if not query:
        return []
    uid = _parse_user_id(user_id)
    try:
        query_vector = embedding_service.embed(query)
    except Exception as e:  # embedding 不可用 → 本次不召回，不影响主链路
        print(f"[memory] recall embed 失败: {e}")
        return []
    # type 过滤是后置条件：多取一些候选再过滤，避免漏召回
    fetch_k = top_k * 4 if memory_type else top_k
    results = memory_vector_store.similarity_search(query_vector, top_k=fetch_k, user_id=uid)
    recalled = []
    for r in results:
        meta = r.document.metadata or {}
        if memory_type and meta.get("memory_type") != memory_type:
            continue
        recalled.append({
            "memory_id": meta.get("memory_id", r.document.id),
            "content": r.document.content,
            "memory_type": meta.get("memory_type", ""),
            "score": round(float(r.score), 4),
            "similarity": round(float(max(0.0, 1.0 - r.score)), 4),
        })
        if len(recalled) >= top_k:
            break
    return recalled


def save_memories(items: list[dict]) -> int:
    """把已由 Java 落库（MySQL ai_user_memories）的记忆文本 embed 后写入向量索引。

    items 元素: {user_id, memory_id, content, memory_type}；
    doc id 复用 MySQL 自增 memory_id（全局唯一），保证失效时能精确定位删除。
    """
    if not items:
        return 0
    try:
        texts = [it["content"] for it in items]
        vectors = embedding_service.embed_batch(texts)
    except Exception as e:
        print(f"[memory] save embed 失败: {e}")
        return 0
    docs = []
    for item, vector in zip(items, vectors, strict=True):
        uid = _parse_user_id(item.get("user_id"))
        memory_id = int(item["memory_id"])
        docs.append(Document(
            id=str(memory_id),
            content=item["content"],
            source="memory",
            vector=vector,
            metadata={
                "user_id": uid,
                "memory_id": memory_id,
                "memory_type": item.get("memory_type", "preference"),
            },
        ))
    saved = memory_vector_store.add_documents(docs)
    print(f"[memory] 写入 {saved} 条用户记忆向量 (user_id={items[0].get('user_id')})")
    return saved


def delete_memories(user_id, memory_ids: list | None = None, memory_type: str | None = None) -> int:
    """删除记忆向量：优先按 memory_id 列表精确删除；否则按 user(+type) 清理（与 Java 失效逻辑对应）。"""
    uid = _parse_user_id(user_id)
    if memory_ids:
        return memory_vector_store.delete_documents([str(i) for i in memory_ids])
    return memory_vector_store.delete_by_user(uid, memory_type)


def classify_user_message(user_message: str) -> dict | None:
    """
    用 AI 分类用户消息为: preference / goal / constraint / none
    返回 {"content": "提取的记忆文本", "type": "preference|goal|constraint"} 或 None
    """
    from langchain_core.messages import HumanMessage as _HumanMessage
    from langchain_openai import ChatOpenAI

    from app.config import config

    llm = ChatOpenAI(
        model=config.DASHSCOPE_MODEL,
        openai_api_key=config.DASHSCOPE_API_KEY,
        openai_api_base=config.DASHSCOPE_BASE_URL,
        temperature=0,
        max_tokens=256,
    )

    prompt = """你是一个记忆分类器。分析用户消息，判断是否包含以下信息之一：
- preference: 交易偏好（交易风格、喜欢的币种、持仓时间偏好、分析方法偏好）
- goal: 交易目标（收益目标、盈利预期、投资计划）
- constraint: 风控约束（止损规则、仓位限制、风险控制、最大回撤）
- none: 以上都不包含（如问候、提问、闲聊）

同时提取用户表达的核心内容作为记忆文本（用第一人称，简洁表述）。

只输出 JSON，不要其他内容。
格式：{"type": "preference|goal|constraint|none", "content": "记忆文本"}

用户消息：""" + user_message

    try:
        result = llm.invoke([_HumanMessage(content=prompt)])
        text = result.content.strip()
        # 去除推理模型的 <think>...</think> 标签（如 deepseek-v3/v4 等）
        text = _strip_think_blocks(text)
        # 提取 JSON（兼容 markdown code block）
        if "```" in text:
            text = re.search(r"```(?:json)?\s*(.*?)\s*```", text, re.DOTALL).group(1)
        # 兜底：如果还有非 JSON 前缀，尝试找到第一个 { 开始截取
        brace_idx = text.find("{")
        if brace_idx > 0:
            text = text[brace_idx:]
        import json
        data = json.loads(text)
        mem_type = data.get("type", "none")
        content = data.get("content", "")
        if mem_type in ("preference", "goal", "constraint") and content:
            return {"content": content, "type": mem_type}
        return None
    except Exception as e:
        print(f"[classify_user_message] error: {e}")
        return None


def extract_memory_from_dialogue(user_message: str, ai_response: str) -> list[dict]:
    """
    从对话中提取记忆候选。

    简单实现：提取用户消息中的偏好、目标、约束等关键信息。
    返回记忆候选列表，每个候选包含 text 和 source。
    """
    candidates = []
    combined = f"{user_message} {ai_response}"

    # 提取偏好类语句（喜欢/不喜欢/偏好）
    preference_patterns = [
        r"(?:我|用户).*?(?:喜欢|偏好|倾向于|更愿意|最爱).*?([。；;]|$)",
        r"(?:我|用户).*?(?:不喜欢|讨厌|反感|避免).*?([。；;]|$)",
    ]
    for pattern in preference_patterns:
        for match in re.finditer(pattern, combined):
            text = match.group(0).strip()
            if text:
                candidates.append({"text": text, "source": "preference"})

    # 提取目标类语句
    goal_patterns = [
        r"(?:我|用户).*?(?:目标|想要|希望|计划|打算|目的是).*?([。；;]|$)",
    ]
    for pattern in goal_patterns:
        for match in re.finditer(pattern, combined):
            text = match.group(0).strip()
            if text:
                candidates.append({"text": text, "source": "goal"})

    # 提取约束/规则类语句
    constraint_patterns = [
        r"(?:我|用户).*?(?:必须|一定|只能|不要|禁止|严格|最多|最少|不超过|至少).*?([。；;]|$)",
    ]
    for pattern in constraint_patterns:
        for match in re.finditer(pattern, combined):
            text = match.group(0).strip()
            if text:
                candidates.append({"text": text, "source": "constraint"})

    # 去重：按 text 去重，保留 source 的并集
    seen = {}
    for cand in candidates:
        key = cand["text"]
        if key not in seen:
            seen[key] = cand
    return list(seen.values())
