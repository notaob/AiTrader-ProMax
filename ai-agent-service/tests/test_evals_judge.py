"""Stage 4 · 端到端问答 LLM-as-judge 评测 pytest 入口（marker: evals+judge）。

    uv run pytest -m judge -q

夜间性：需要本地 ai-agent :8000 在线。未启动默认 skip；
设环境变量 EVAL_LIVE_REQUIRED=1 时强制要求在线（缺服务即 fail）。
门限按实测留余量（校准后更新注释）。
"""
import os

import httpx
import pytest

from evals.judge_e2e_faithfulness import run

pytestmark = [pytest.mark.evals, pytest.mark.judge]


def _service_up() -> bool:
    try:
        return httpx.get("http://127.0.0.1:8000/health", timeout=3).status_code == 200
    except Exception:
        return False


def test_e2e_judge_faithfulness():
    if not _service_up():
        if os.environ.get("EVAL_LIVE_REQUIRED") == "1":
            pytest.fail("ai-agent :8000 未运行，但 EVAL_LIVE_REQUIRED=1")
        pytest.skip("ai-agent :8000 未运行，跳过 judge 评测（nightly 依赖在线服务）")
    # 2026-09-04 修复 symbol 透传 + 输出纪律后实测：pass_rate=1.00 / c=4.70 / f=4.40 / r=4.80 / s=5.00
    # 门限留余量以吸收 agent(temperature=0.7) 与 judge 的抖动，显著下滑才判回归。
    metrics = run(verbose=False)
    assert metrics["answered"] >= 8, f"answered 过少: {metrics}"
    assert metrics["pass_rate"] >= 0.70, f"pass_rate 过低: {metrics}"
    assert metrics["correctness"] >= 3.5, f"correctness 过低: {metrics}"
    assert metrics["faithfulness"] >= 3.3, f"faithfulness 过低: {metrics}"
    assert metrics["judge_parse_fail"] == 0, f"judge JSON 解析失败: {metrics}"
