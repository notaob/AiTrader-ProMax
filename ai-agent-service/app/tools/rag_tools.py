from langchain_core.tools import tool
from app.rag.document_processor import document_processor


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
    try:
        from app.rag.embedding import embedding_service
        from app.rag.vector_store import vector_store

        query_vector = embedding_service.embed(query)
        results = vector_store.similarity_search(query_vector, top_k, user_id=user_id)

        if not results:
            return "知识库中未找到与查询相关的内容。"

        chunks_text = []
        for i, result in enumerate(results, 1):
            source = result.document.source or "未知来源"
            score = result.score
            content = result.document.content
            chunks_text.append(f"[{i}] (来源: {source}, 相关度: {score:.4f})\n{content}")

        return "知识库检索结果：\n\n" + "\n\n".join(chunks_text)
    except Exception as e:
        return f"知识库搜索出错: {str(e)}"


@tool
def add_to_knowledge_base(text: str, source: str = "user") -> str:
    """添加文本到知识库。当用户明确要求记住某些内容，或对话中产生了值得长期保存的重要信息时使用。"""
    try:
        count = document_processor.process_text(text, source)
        return f"成功添加 {count} 个文档片段到知识库"
    except Exception as e:
        return f"添加知识失败: {str(e)}"


# RAG 工具列表
rag_tools = [search_knowledge, add_to_knowledge_base]
