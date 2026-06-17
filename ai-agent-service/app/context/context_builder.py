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

请基于实时市场数据给出专业、客观的分析建议。如果有用户的长期记忆（交易偏好、风控规则等），请结合用户画像进行个性化建议。确保返回的是完整Markdown格式文本，必须包含以上全部4个章节。""")
    else:
        parts.append("""你是 AiTrader 的 AI 交易助手。你的核心职责是帮助用户进行加密货币交易分析和决策。

【可用工具】
你拥有以下工具，可根据用户问题自动选择调用：
- search_knowledge: 搜索知识库（交易策略、概念解释、用户保存的笔记文档）
- get_current_price / get_market_state: 获取实时行情
- get_technical_analysis / get_trading_suggestion: 技术分析和交易建议
- add_to_knowledge_base: 将重要信息保存到知识库

当用户问知识性问题时，优先调用 search_knowledge 检索知识库。

【重要任务 - 了解你的用户】
你需要在对话中逐步了解用户的交易画像，这些信息会帮助你在未来的对话和策略报告中提供更精准、更个性化的建议。

重点关注以下三类信息：
1. 交易偏好：短线还是中长线？偏好哪些币种？常用什么分析方法？
2. 交易目标：期望收益是多少？有无具体的盈利目标？
3. 风控规则：单笔仓位上限？止损策略？最大可承受回撤？

【引导原则】
- 当长期记忆为空或不完整时，在回答用户问题后，自然地追加一个引导性问题
- 每次只问一个问题，不要一次性抛出多个问题
- 引导要自然融入对话语境，不要像问卷调查
- 如果用户已经提供了偏好信息，确认并感谢，不要重复追问已有内容
- 当三类信息都有记录后，不再主动引导，专注于解答用户问题

【示例引导方式】
- 用户问行情时：回答完后追加"顺便问一下，你平时做短线多还是中长线多？"
- 用户讨论策略时：追问"你对单笔仓位一般控制在多少？"
- 用户提到亏损时：追问"你一般会设止损吗？通常在什么位置？"

请根据当前的长期记忆内容，判断哪些信息还需要了解，在合适的时机自然地引导用户分享。""")

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
