"""Stage 4 · 离线检索评测（recall@k / MRR，无需 LLM，最扎实的基线指标）。

评测对象：知识库向量检索。固定语料 `evals/data/kb_corpus.jsonl`（单条 = 一个
独立检索单元/文档），查询集 `evals/data/kb_queries.jsonl` 标注每条 query 应命中
的语料文档 id（1~2 个，含多命中用例以拉开 recall 区分度）。

与生产链路一致的部分：
- embedding：DashScope `text-embedding-v2`（1536 维），与 app/rag/embedding 同源；
- 相似度：余弦（1-cos 距离升序），与 Redis HNSW COSINE / 内存 numpy 语义一致。

隔离性：评测在**进程内新建 MemoryVectorStore** 建索引，不触碰运行中服务的
`rag_vectors` / `mem_vectors`，可重复、无副作用。

用法：
    uv run python -m evals.offline_retrieval        # 命令行输出指标表
    uv run pytest -m "evals and offline" -q         # pytest 入口（tests/test_evals_retrieval.py）
"""
from __future__ import annotations

import json
from pathlib import Path

from app.rag.embedding import embedding_service
from app.rag.vector_store import Document, MemoryVectorStore

DATA_DIR = Path(__file__).resolve().parent / "data"
RECALL_KS = (1, 3, 5)
MRR_K = 10
# 语料单条作为独立检索单元；防御性地限制长度，保证不会在事实内部被切块打断
MAX_FACT_CHARS = 450


def _load(name: str) -> list[dict]:
    rows = []
    for line in (DATA_DIR / name).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        rows.append(json.loads(line))
    return rows


def build_index() -> tuple[MemoryVectorStore, list[dict]]:
    """全量 embed 语料 → 隔离内存索引。返回 (store, corpus)。"""
    corpus = _load("kb_corpus.jsonl")
    ids = [d["id"] for d in corpus]
    if len(set(ids)) != len(ids):
        raise ValueError("kb_corpus.jsonl 存在重复 id")
    for d in corpus:
        if len(d["content"]) > MAX_FACT_CHARS:
            raise ValueError(f"{d['id']} 超过 {MAX_FACT_CHARS} 字，单条不再是原子检索单元")

    store = MemoryVectorStore()
    vectors = embedding_service.embed_batch([d["content"] for d in corpus])
    for doc, vec in zip(corpus, vectors, strict=True):
        store.add_document(Document(
            id=doc["id"],
            content=doc["content"],
            source=f"kb:{doc['id']}",
            vector=vec,
            metadata={"source_doc": doc["id"], "index": "kb-eval"},
        ))
    return store, corpus


def evaluate(store: MemoryVectorStore) -> tuple[dict, list[dict]]:
    queries = _load("kb_queries.jsonl")
    agg = {f"recall@{k}": [] for k in RECALL_KS}
    agg["mrr"] = []
    details: list[dict] = []

    for q in queries:
        expected = set(q["expected"])
        query_vec = embedding_service.embed(q["query"])
        results = store.similarity_search(query_vec, MRR_K)
        top_ids = [r.document.metadata.get("source_doc") for r in results]
        recall = {k: len(expected & set(top_ids[:k])) / len(expected) for k in RECALL_KS}
        rr = 0.0
        for rank, doc_id in enumerate(top_ids, 1):
            if doc_id in expected:
                rr = 1.0 / rank
                break
        for k in RECALL_KS:
            agg[f"recall@{k}"].append(recall[k])
        agg["mrr"].append(rr)
        details.append({
            "id": q["id"],
            "query": q["query"],
            "expected": sorted(expected),
            "top5": [d for d in top_ids[:5] if d],
            "recall@5": recall[5],
            "rr": rr,
        })

    n = len(queries)
    metrics = {key: (sum(v) / n if n else 0.0) for key, v in agg.items()}
    return metrics, details


def format_table(metrics: dict, corpus_n: int, query_n: int) -> str:
    lines = [
        "Stage 4 离线检索评测（kb 知识库固定语料）",
        f"  语料文档数: {corpus_n}    查询数: {query_n}",
    ]
    for k in RECALL_KS:
        lines.append(f"  recall@{k:<2}= {metrics[f'recall@{k}']:.3f}")
    lines.append(f"  MRR@{MRR_K:<2}  = {metrics['mrr']:.3f}")
    return "\n".join(lines)


def run(verbose: bool = True) -> dict:
    store, corpus = build_index()
    metrics, details = evaluate(store)
    if verbose:
        print(format_table(metrics, len(corpus), len(details)))
        misses = [d for d in details if d["recall@5"] < 1.0]
        if misses:
            print(f"\n  recall@5 < 1.0 的查询（{len(misses)} 条，用于定位语料/标注缺口）:")
            for d in misses:
                print(f"    {d['id']} {d['query'][:28]}...  expect={d['expected']} "
                      f"top5={d['top5']} r@5={d['recall@5']:.2f}")
    return metrics


if __name__ == "__main__":
    run(verbose=True)
