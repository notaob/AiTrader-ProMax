import re
from typing import List


def chunk_document(text: str, chunk_size: int = 500, overlap: int = 50) -> List[str]:
    """
    文档切片。

    按字符长度切片，优先在句子边界处切割，若无法找到合适边界则按长度硬切。
    相邻片段之间保留 overlap 长度的重叠，保证语义连续性。
    """
    if not text:
        return []

    chunks = []
    start = 0
    text_len = len(text)

    while start < text_len:
        end = min(start + chunk_size, text_len)

        # 如果不是最后一段，尝试在句子边界处截断
        if end < text_len:
            # 从 end 往前找最近的句号、问号、感叹号或换行
            search_start = max(start + chunk_size - overlap, start)
            boundary = -1
            for i in range(end - 1, search_start - 1, -1):
                if text[i] in "。！？\n":
                    boundary = i + 1
                    break
            if boundary != -1:
                end = boundary

        chunk = text[start:end].strip()
        if chunk:
            chunks.append(chunk)

        # 下一段起始位置，保留 overlap
        step = end - start
        if step <= 0:
            break
        start += max(step - overlap, 1)

    return chunks


def extract_keywords(text: str) -> List[str]:
    """
    提取关键词（简单实现）。

    基于词频和停用词过滤，返回出现频率较高的候选词。
    """
    if not text:
        return []

    # 简单停用词表
    stopwords = {
        "的", "了", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也",
        "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这",
        "那", "这些", "那些", "这个", "那个", "之", "与", "及", "等", "或", "但", "而",
        "the", "is", "and", "to", "of", "a", "in", "that", "have", "i", "it", "for",
        "not", "on", "with", "he", "as", "you", "do", "at", "this", "but", "his",
    }

    # 清洗并分词：保留中文和英文单词
    cleaned = re.sub(r"[^\u4e00-\u9fa5a-zA-Z0-9]", " ", text)
    words = cleaned.split()

    freq = {}
    for w in words:
        w = w.lower()
        if w in stopwords or len(w) < 2:
            continue
        freq[w] = freq.get(w, 0) + 1

    # 按词频排序，取前 10
    sorted_words = sorted(freq.items(), key=lambda x: x[1], reverse=True)
    return [w for w, _ in sorted_words[:10]]
