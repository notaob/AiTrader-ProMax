from app.prompts import STRATEGY_PROMPT, SYSTEM_PROMPT


def build_prompt(
    state: dict,
    recent_messages: list[dict],
    summaries: list[str],
    memories: list[str],
    knowledge_chunks: list[str],
    current_message: str,
    mode: str = "chat",
) -> str:
    """
    按优先级组装最终 prompt。

    组装顺序：
    1. 系统提示词（mode 决定，统一取 app.prompts 单一来源）
    2. session_state
    3. 最近消息
    4. 历史摘要
    5. 长期记忆
    6. 知识片段
    7. 当前用户输入
    """
    parts: list[str] = []

    # 1. 系统提示词（chat / strategy 由 prompts 包提供，消除双写漂移）
    parts.append(STRATEGY_PROMPT if mode == "strategy" else SYSTEM_PROMPT)

    # 2. 会话状态
    if state:
        parts.append("\n当前会话状态：")
        for key, value in state.items():
            val_str = str(value).replace("{", "{{").replace("}", "}}")
            parts.append(f"- {key}: {val_str}")

    # 3. 最近消息
    if recent_messages:
        parts.append("\n最近对话：")
        for msg in recent_messages:
            role = msg.get("role", "user")
            content = msg.get("content", "").replace("{", "{{").replace("}", "}}")
            role_label = "用户" if role == "user" else "助手"
            parts.append(f"{role_label}: {content}")

    # 4. 历史摘要
    if summaries:
        parts.append("\n历史摘要：")
        for summary in summaries:
            parts.append(str(summary).replace("{", "{{").replace("}", "}}"))

    # 5. 长期记忆
    if memories:
        parts.append("\n长期记忆：")
        for idx, mem in enumerate(memories, start=1):
            parts.append(f"{idx}. {str(mem).replace('{', '{{').replace('}', '}}')}")

    # 6. 知识片段
    if knowledge_chunks:
        parts.append("\n相关知识：")
        for idx, chunk in enumerate(knowledge_chunks, start=1):
            parts.append(f"{idx}. {str(chunk).replace('{', '{{').replace('}', '}}')}")

    # 7. 当前用户输入
    safe_input = current_message.replace("{", "{{").replace("}", "}}")
    parts.append(f"\n用户当前输入：{safe_input}")

    return "\n".join(parts)
