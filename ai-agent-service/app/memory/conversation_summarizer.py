"""会话摘要：把批量对话消息用 LLM 压缩成语义摘要。

配合 Java 端 AiSummaryServiceImpl：Java 判断"何时触发"，Python 负责"摘要文本"。
输入消息先做 <think> 清洗（与记忆分类器共用 helper），再交给 LLM 压成要点摘要。
"""

from langchain_core.messages import HumanMessage
from langchain_openai import ChatOpenAI

from app.config import config
from app.memory.memory_service import _strip_think_blocks

SYSTEM_PROMPT = (
    "你是一个经验丰富的 AI 交易助手。请把下面的对话记录压缩成一段面向该用户的"
    "第一人称语义摘要，供后续对话作为长期上下文使用。"
    "必须保留：用户的交易偏好/风格、目标、风控约束、关注或持有的品种、明确结论与已做决策；"
    "可省略：寒暄、重复表达、具体行情数字罗列。"
    "要求：信息密度高、保留关键原话措辞、不超过 500 字，只输出摘要正文，不要标题与解释。"
)


# 摘要输入边界：防止超长对话把上下文撑爆（Java 侧滚动摘要是前缀全量，这里截取最近窗口）
_MAX_MESSAGES = 60
_MAX_CONTENT_CHARS = 2000


def _build_transcript(messages: list[dict]) -> str:
    """把 {role, content} 消息列表转成 用户/AI 对话文本，并清洗 <think>、限制规模。"""
    lines = []
    for msg in messages[-_MAX_MESSAGES:]:
        role = str(msg.get("role") or "").strip().lower()
        content = _strip_think_blocks(str(msg.get("content") or ""))[:_MAX_CONTENT_CHARS]
        if not content:
            continue
        if role in ("user", "human"):
            lines.append(f"用户：{content}")
        elif role in ("assistant", "ai"):
            lines.append(f"AI：{content}")
    return "\n".join(lines)


def summarize_conversation(messages: list[dict]) -> str:
    """生成语义摘要文本；输入异常或 LLM 失败时抛 RuntimeError，由调用方决定降级。"""
    transcript = _build_transcript(messages)
    if not transcript.strip():
        raise RuntimeError("无可摘要的消息内容")

    llm = ChatOpenAI(
        model=config.DASHSCOPE_MODEL,
        openai_api_key=config.DASHSCOPE_API_KEY,
        openai_api_base=config.DASHSCOPE_BASE_URL,
        temperature=0.0,
        max_tokens=1200,
    )
    prompt = f"{SYSTEM_PROMPT}\n\n对话记录：\n{transcript}"
    try:
        result = llm.invoke([HumanMessage(content=prompt)])
    except Exception as e:  # noqa: BLE001
        raise RuntimeError(f"摘要生成失败: {e}") from e

    summary = _strip_think_blocks(result.content if hasattr(result, "content") else str(result))
    if not summary:
        raise RuntimeError("摘要生成为空")
    return summary
