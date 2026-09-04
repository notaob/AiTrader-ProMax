"""Stage 4 · 离线检索评测 pytest 入口（默认被排除，需显式 `-m evals`）。

    uv run pytest -m "evals and offline" -q
"""
import pytest

from evals.offline_retrieval import MRR_K, RECALL_KS, run

pytestmark = [pytest.mark.evals, pytest.mark.offline]


def test_kb_offline_retrieval_metrics():
    metrics = run(verbose=False)
    assert len(RECALL_KS) == 3
    assert MRR_K == 10
    # 门限取实测值留余量（2026-09-04 首跑：recall@5=0.96 / mrr@10=0.842）：
    # 显著低于即说明语料/标注或检索回归，而非模型抖动
    assert metrics["recall@5"] >= 0.90
    assert metrics["mrr"] >= 0.80
