"""Stage 4 · 策略报告格式合规评测 pytest 入口（marker: evals+format）。

    uv run pytest -m format -q

夜间性：需要本地 ai-agent :8000 在线 + LLM。未启动默认 skip；
设 EVAL_LIVE_REQUIRED=1 时强制要求在线（缺服务即 fail）。
门限按实测校准（format_pass_rate≥0.75 留抖动余量）。
"""
import os

import httpx
import pytest

from evals.format_compliance import run

pytestmark = [pytest.mark.evals, pytest.mark.format]


def _service_up() -> bool:
    try:
        return httpx.get("http://127.0.0.1:8000/health", timeout=3).status_code == 200
    except Exception:
        return False


def test_strategy_format_compliance():
    if not _service_up():
        if os.environ.get("EVAL_LIVE_REQUIRED") == "1":
            pytest.fail("ai-agent :8000 未运行，但 EVAL_LIVE_REQUIRED=1")
        pytest.skip("ai-agent :8000 未运行，跳过 format 评测（nightly 依赖在线服务）")
    metrics = run(verbose=False)
    assert metrics["answered"] >= 3, f"answered 过少: {metrics}"
    assert metrics["format_pass_rate"] >= 0.75, f"format_pass_rate 过低: {metrics}"
