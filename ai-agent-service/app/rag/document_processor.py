import re
import uuid
from typing import List
from app.rag.vector_store import Document, vector_store
from app.rag.embedding import embedding_service

class DocumentProcessor:
    """文档处理器"""
    
    def __init__(self, chunk_size: int = 500, chunk_overlap: int = 100):
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap
    
    def process_text(self, text: str, source: str) -> int:
        """处理文本并添加到向量存储"""
        # 1. 文本预处理
        cleaned_text = self._clean_text(text)
        
        # 2. 切分文本
        chunks = self._split_text(cleaned_text)
        
        # 3. 批量向量化
        vectors = embedding_service.embed_batch(chunks)
        
        # 4. 构建文档并存储
        documents = []
        for i, (chunk, vector) in enumerate(zip(chunks, vectors)):
            doc = Document(
                id=str(uuid.uuid4()),
                content=chunk,
                source=source,
                vector=vector,
                metadata={"chunk_index": i, "total_chunks": len(chunks)}
            )
            documents.append(doc)
        
        # 5. 批量添加到向量存储
        return vector_store.add_documents(documents)
    
    def _clean_text(self, text: str) -> str:
        """清洗文本"""
        # 去除多余空白
        text = re.sub(r'\s+', ' ', text)
        # 去除特殊字符
        text = re.sub(r'[^\u4e00-\u9fa5a-zA-Z0-9\.\,\;\:\!\?\(\)\[\]\"\'\-\s]', '', text)
        return text.strip()
    
    def _split_text(self, text: str) -> List[str]:
        """切分文本（按句子切分，保持语义完整）"""
        # 按句子切分（中文句号、英文句号、问号、感叹号）
        sentences = re.split(r'([。\.\?\!？！])', text)
        
        # 合并句子和标点
        sentences = [''.join(i) for i in zip(sentences[0::2], sentences[1::2] + [''])]
        sentences = [s.strip() for s in sentences if s.strip()]
        
        chunks = []
        current_chunk = []
        current_length = 0
        
        for sentence in sentences:
            sentence_length = len(sentence)
            
            # 如果当前块加上新句子超过 chunk_size，保存当前块
            if current_length + sentence_length > self.chunk_size and current_chunk:
                chunks.append(''.join(current_chunk))
                
                # 保留重叠部分
                overlap_text = ''.join(current_chunk)
                overlap_sentences = overlap_text.split('。')
                current_chunk = []
                current_length = 0
                
                # 从后往前添加句子，直到接近 chunk_overlap
                for s in reversed(overlap_sentences):
                    if current_length + len(s) < self.chunk_overlap:
                        current_chunk.insert(0, s + '。')
                        current_length += len(s) + 1
                    else:
                        break
            
            current_chunk.append(sentence)
            current_length += sentence_length
        
        # 添加最后一个块
        if current_chunk:
            chunks.append(''.join(current_chunk))
        
        return chunks

# 单例
document_processor = DocumentProcessor()
