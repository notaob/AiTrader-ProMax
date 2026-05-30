from typing import List, Dict
from app.rag.embedding import embedding_service
from app.rag.vector_store import vector_store
from app.llm import create_llm
from langchain_core.messages import HumanMessage, SystemMessage

class RAGService:
    """RAG 服务"""
    
    def __init__(self):
        self.llm = create_llm()
    
    def query(self, question: str, top_k: int = 5) -> Dict:
        """RAG 查询"""
        try:
            # 1. 向量化查询
            query_vector = embedding_service.embed(question)
            
            # 2. 检索相关文档
            search_results = vector_store.similarity_search(query_vector, top_k)
            
            if not search_results:
                return {
                    "success": True,
                    "answer": "知识库中没有找到相关信息。",
                    "sources": [],
                    "retrieved_documents": 0
                }
            
            # 3. 构建上下文
            context = self._build_context(search_results)
            
            # 4. 构建 Prompt
            messages = [
                SystemMessage(content="""你是一个专业的知识助手。请基于以下知识库内容回答用户问题。
如果知识库中没有相关信息，请明确告知用户。
请给出准确、简洁的回答。"""),
                HumanMessage(content=f"""知识库内容：
{context}

用户问题：{question}

请基于以上知识库内容回答问题：""")
            ]
            
            # 5. 调用 LLM 生成答案
            response = self.llm.invoke(messages)
            
            # 6. 提取来源
            sources = list(set([r.document.source for r in search_results]))
            
            return {
                "success": True,
                "answer": response.content,
                "sources": sources,
                "retrieved_documents": len(search_results)
            }
            
        except Exception as e:
            print(f"RAG 查询失败: {e}")
            return {
                "success": False,
                "answer": f"查询失败: {str(e)}",
                "sources": [],
                "retrieved_documents": 0
            }
    
    def _build_context(self, search_results: List) -> str:
        """构建上下文"""
        context_parts = []
        for i, result in enumerate(search_results, 1):
            doc = result.document
            context_parts.append(f"[文档 {i}] (相关度: {result.score:.2f})\n{doc.content}\n")
        
        return "\n".join(context_parts)
    
    def get_stats(self) -> Dict:
        """获取知识库统计"""
        return vector_store.get_stats()
    
    def clear_knowledge_base(self) -> bool:
        """清空知识库"""
        return vector_store.clear_all()

# 单例
rag_service = RAGService()
