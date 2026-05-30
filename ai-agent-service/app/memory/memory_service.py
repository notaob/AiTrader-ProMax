import re
import hashlib
import math
from typing import List, Dict


def extract_memory_from_dialogue(user_message: str, ai_response: str) -> List[Dict]:
    """
    从对话中提取记忆候选。

    简单实现：提取用户消息中的偏好、目标、约束等关键信息。
    返回记忆候选列表，每个候选包含 text 和 source。
    """
    candidates = []
    combined = f"{user_message} {ai_response}"

    # 提取偏好类语句（喜欢/不喜欢/偏好）
    preference_patterns = [
        r"(?:我|用户).*?(?:喜欢|偏好|倾向于|更愿意|最爱).*?([。；;]|$)",
        r"(?:我|用户).*?(?:不喜欢|讨厌|反感|避免).*?([。；;]|$)",
    ]
    for pattern in preference_patterns:
        for match in re.finditer(pattern, combined):
            text = match.group(0).strip()
            if text:
                candidates.append({"text": text, "source": "preference"})

    # 提取目标类语句
    goal_patterns = [
        r"(?:我|用户).*?(?:目标|想要|希望|计划|打算|目的是).*?([。；;]|$)",
    ]
    for pattern in goal_patterns:
        for match in re.finditer(pattern, combined):
            text = match.group(0).strip()
            if text:
                candidates.append({"text": text, "source": "goal"})

    # 提取约束/规则类语句
    constraint_patterns = [
        r"(?:我|用户).*?(?:必须|一定|只能|不要|禁止|严格|最多|最少|不超过|至少).*?([。；;]|$)",
    ]
    for pattern in constraint_patterns:
        for match in re.finditer(pattern, combined):
            text = match.group(0).strip()
            if text:
                candidates.append({"text": text, "source": "constraint"})

    # 去重：按 text 去重，保留 source 的并集
    seen = {}
    for cand in candidates:
        key = cand["text"]
        if key not in seen:
            seen[key] = cand
    return list(seen.values())


def generate_embedding(text: str) -> List[float]:
    """
    生成文本向量（简单实现，用 hash 模拟）。

    将文本分词后，基于哈希值生成固定维度的稀疏向量，
    再归一化为单位向量，用于余弦相似度计算。
    """
    dim = 128
    vec = [0.0] * dim

    # 按字符滑动窗口生成 n-gram 特征
    tokens = list(text)
    for i in range(len(tokens)):
        for n in range(1, 4):
            if i + n > len(tokens):
                break
            gram = "".join(tokens[i:i + n])
            h = int(hashlib.md5(gram.encode("utf-8")).hexdigest(), 16)
            idx = h % dim
            # 使用另一个哈希位作为符号，使分布更均匀
            sign = 1 if ((h >> 7) & 1) == 0 else -1
            vec[idx] += sign * (1.0 / (n * n))

    # L2 归一化
    norm = math.sqrt(sum(v * v for v in vec))
    if norm > 0:
        vec = [v / norm for v in vec]
    return vec


def search_memories(query: str, memories: List[str], top_k: int = 3) -> List[str]:
    """
    基于关键词召回记忆。

    简单实现：将 query 分词后与每条 memory 计算关键词重叠度，
    返回得分最高的 top_k 条记忆。
    """
    if not memories or not query:
        return []

    query_tokens = set(_tokenize(query))
    if not query_tokens:
        return memories[:top_k]

    scored = []
    for mem in memories:
        mem_tokens = set(_tokenize(mem))
        if not mem_tokens:
            continue
        # Jaccard 相似度作为得分
        intersection = len(query_tokens & mem_tokens)
        union = len(query_tokens | mem_tokens)
        score = intersection / union if union > 0 else 0.0
        scored.append((score, mem))

    scored.sort(key=lambda x: x[0], reverse=True)
    return [mem for _, mem in scored[:top_k]]


def _tokenize(text: str) -> List[str]:
    """简单中文/英文分词：保留长度 >= 2 的词组。"""
    text = re.sub(r"[^\u4e00-\u9fa5a-zA-Z0-9]", " ", text)
    tokens = []
    words = text.split()
    for w in words:
        if len(w) >= 2:
            tokens.append(w.lower())
        # 对纯中文进一步拆成单字，增强匹配
        if re.match(r"^[\u4e00-\u9fa5]+$", w):
            tokens.extend(list(w))
    return tokens
