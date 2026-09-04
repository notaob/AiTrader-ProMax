"""LangGraph 知识库工具（字符串壳）。

真实逻辑唯一来源：app.tools.core.knowledge —— 与 MCP server 复用同一函数，
这里只负责把结构化结果格式化为模型友好文本（输出与原实现逐字一致）。
"""

from langchain_core.tools import tool

from app.tools.core import knowledge as _core_knowledge


def _format_search_hits(results: list[dict]) -> str:
    chunks = []
    for item in results:
        chunks.append(
            f"[{item['index']}] (来源: {item['source']}, 相关度: {item['score']:.4f})\n{item['content']}"
        )
    return "知识库检索结果：\n\n" + "\n\n".join(chunks)


@tool
def search_knowledge(query: str, user_id: int, top_k: int = 5) -> str:
    """搜索加密货币交易知识库。当用户提出以下类型的问题时使用此工具：
    - 交易策略和方法论（如止损规则、仓位管理方法）
    - 市场概念和术语解释（如什么是RSI、支撑阻力位）
    - 已保存的用户笔记和文档内容
    - 任何需要从知识库获取而非实时行情的问题

    注意：实时价格和行情数据请用 get_current_price / get_market_state，
    技术分析请用 get_technical_analysis / get_trading_suggestion。
    user_id参数表示当前用户ID，搜索结果仅返回该用户的知识库内容。"""
    resp = _core_knowledge.search_knowledge(query, user_id=user_id, top_k=top_k)
    if not resp["ok"]:
        return f"知识库搜索出错: {resp['error']}"
    if not resp["results"]:
        return "知识库中未找到与查询相关的内容。"
    return _format_search_hits(resp["results"])


@tool
def add_to_knowledge_base(text: str, source: str = "user") -> str:
    """添加文本到知识库。当用户明确要求记住某些内容，或对话中产生了值得长期保存的重要信息时使用。"""
    resp = _core_knowledge.add_to_knowledge_base(text, source)
    if resp["ok"]:
        return f"成功添加 {resp['count']} 个文档片段到知识库"
    return f"添加知识失败: {resp['error']}"


# RAG 工具列表
rag_tools = [search_knowledge, add_to_knowledge_base]
