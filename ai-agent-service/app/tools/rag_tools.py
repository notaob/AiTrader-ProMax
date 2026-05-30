from langchain_core.tools import tool
from app.rag.rag_service import rag_service
from app.rag.document_processor import document_processor

@tool
def query_knowledge_base(question: str, top_k: int = 3) -> str:
    """查询知识库获取相关信息"""
    try:
        result = rag_service.query(question, top_k)
        
        if result["success"] and result["retrieved_documents"] > 0:
            return f"知识库查询结果:\n{result['answer']}\n\n来源: {', '.join(result['sources'])}"
        
        return "知识库中没有找到相关信息。"
    except Exception as e:
        return f"查询知识库失败: {str(e)}"

@tool
def add_to_knowledge_base(text: str, source: str = "user") -> str:
    """添加文本到知识库"""
    try:
        count = document_processor.process_text(text, source)
        return f"成功添加 {count} 个文档片段到知识库"
    except Exception as e:
        return f"添加知识失败: {str(e)}"

# RAG 工具列表
rag_tools = [query_knowledge_base, add_to_knowledge_base]
