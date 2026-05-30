from langchain_openai import ChatOpenAI
from app.config import config

def create_llm():
    """创建 LLM 实例（使用阿里云 DashScope）"""
    return ChatOpenAI(
        model=config.DASHSCOPE_MODEL,
        openai_api_key=config.DASHSCOPE_API_KEY,
        openai_api_base=config.DASHSCOPE_BASE_URL,
        temperature=0.7,
        max_tokens=2000
    )
