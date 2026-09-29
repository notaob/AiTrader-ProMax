from langchain_openai import ChatOpenAI

from app.config import config


def chat_model_kwargs() -> dict:
    """chat 模型连接参数：配置了 ARK_API_KEY 时走火山方舟，否则走 DashScope。

    只影响 chat 类 LLM（图内 ReAct / 记忆分类 / 摘要）；
    embedding 始终走 DashScope，失败时调用方已有降级（关键词召回 / 跳过向量化）。
    """
    if config.ARK_API_KEY:
        return {
            "model": config.ARK_MODEL,
            "openai_api_key": config.ARK_API_KEY,
            "openai_api_base": config.ARK_BASE_URL,
        }
    return {
        "model": config.DASHSCOPE_MODEL,
        "openai_api_key": config.DASHSCOPE_API_KEY,
        "openai_api_base": config.DASHSCOPE_BASE_URL,
    }


def create_llm():
    """创建 LLM 实例（供应商可切换：火山方舟 / DashScope，见 chat_model_kwargs）"""
    return ChatOpenAI(
        **chat_model_kwargs(),
        temperature=0.7,
        # 思考型模型（GLM-5.3 等）的思考 token 也计入 max_tokens，
        # 上限太低会被思考吃光导致正文为空（实测 20 轮中 2 轮 reply 为空的根因）
        max_tokens=16384
    )


def extract_llm_text(result) -> str:
    """从 LangChain AIMessage 提取正文文本，兼容思考型模型。

    GLM-5.3 flash 等思考模型的思考内容放在 additional_kwargs.reasoning_content，
    正文 content 偶发为空（尤其 max_tokens 吃紧时）——回退读取，避免下游
    json.loads 拿到空串直接崩掉。
    """
    text = result.content
    if isinstance(text, list):
        text = "".join(p.get("text", "") for p in text if isinstance(p, dict))
    text = (text or "").strip()
    if not text:
        text = (getattr(result, "additional_kwargs", None) or {}).get("reasoning_content") or ""
    return text.strip()
