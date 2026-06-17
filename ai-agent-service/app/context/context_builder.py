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
        parts.append("""你是一名专业的加密货币交易专家。请根据当前市场数据，制定一份详细的交易策略报告。

【格式要求 - 严格遵守】
1. 整篇返回必须是标准Markdown格式，方便前端React组件解析
2. 仅用 ## 标题和 - 列表，不要用表格（|符号）和emoji
3. 所有内容用文字段落或简单列表呈现

【报告结构 - 必须完整覆盖以下4个章节】

## 1. 市场趋势分析
- 判断当前趋势（多头/空头/震荡）
- 分析关键技术指标（MA、RSI等）

## 2. 关键价位
- 支撑位：具体价格
- 阻力位：具体价格
- 重要价格节点说明

## 3. 交易建议
- 入场点位建议
- 止损点位设置
- 止盈目标设定
- 仓位管理建议

## 4. 风险提示
- 潜在风险因素
- 需要关注的市场信号

请基于实时市场数据给出专业、客观的分析建议。确保返回的是完整Markdown格式文本，必须包含以上全部4个章节。""")
    else:
        parts.append("你是 AiTrader 的 AI 助手。")

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
    if summaries and len(summaries) > 0:
        parts.append("\n历史摘要：")
        for summary in summaries:
            parts.append(str(summary).replace("{", "{{").replace("}", "}}"))

    # 5. 长期记忆
    if memories and len(memories) > 0:
        parts.append("\n长期记忆：")
        for idx, mem in enumerate(memories, start=1):
            parts.append(f"{idx}. {str(mem).replace('{', '{{').replace('}', '}}')}")

    # 6. 知识片段
    if knowledge_chunks and len(knowledge_chunks) > 0:
        parts.append("\n相关知识：")
        for idx, chunk in enumerate(knowledge_chunks, start=1):
            parts.append(f"{idx}. {str(chunk).replace('{', '{{').replace('}', '}}')}")

    # 7. 当前用户输入
    safe_input = current_message.replace("{", "{{").replace("}", "}}")
    parts.append(f"\n用户当前输入：{safe_input}")

    return "\n".join(parts)
