from typing import List, Dict


def build_prompt(
    state: Dict,
    recent_messages: List[Dict],
    summaries: List[str],
    memories: List[str],
    knowledge_chunks: List[str],
    current_message: str,
    mode: str = "chat"
) -> str:
    """
    按优先级组装最终 prompt。

    组装顺序：
    1. 系统提示词
    2. session_state
    3. 最近消息
    4. 历史摘要
    5. 长期记忆
    6. 知识片段
    7. 当前用户输入
    """
    parts = []

    # 1. 系统提示词
    if mode == "strategy":
        parts.append("你是一名专业的加密货币交易专家。请根据当前市场数据，制定一份详细的交易策略报告。")
    else:
        parts.append("你是 AiTrader 的 AI 助手。")

    # 2. 会话状态
    if state:
        parts.append("\n当前会话状态：")
        for key, value in state.items():
            if key != "state_json":
                parts.append(f"- {key}: {value}")
            else:
                parts.append(f"- {key}: {value}")

    # 3. 最近消息
    if recent_messages:
        parts.append("\n最近对话：")
        for msg in recent_messages:
            role = msg.get("role", "user")
            content = msg.get("content", "")
            role_label = "用户" if role == "user" else "助手"
            parts.append(f"{role_label}: {content}")

    # 4. 历史摘要
    if summaries and len(summaries) > 0:
        parts.append("\n历史摘要：")
        for summary in summaries:
            parts.append(summary)

    # 5. 长期记忆
    if memories and len(memories) > 0:
        parts.append("\n长期记忆：")
        for idx, mem in enumerate(memories, start=1):
            parts.append(f"{idx}. {mem}")

    # 6. 知识片段
    if knowledge_chunks and len(knowledge_chunks) > 0:
        parts.append("\n相关知识：")
        for idx, chunk in enumerate(knowledge_chunks, start=1):
            parts.append(f"{idx}. {chunk}")

    # 7. 当前用户输入
    parts.append(f"\n用户当前输入：{current_message}")

    return "\n".join(parts)
