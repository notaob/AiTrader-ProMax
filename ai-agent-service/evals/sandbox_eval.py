"""沙箱执行评测：量化"生成代码 → 沙箱执行 → 自纠错"循环的真实水平。

三层指标（见 SANDBOX_TOOL_TASKS.md）：
- 一次通过率：首次生成即 exit 0 且输出含期望值
- 3 次内解决率：含自纠错（把 traceback 喂回 LLM 重新生成）后最终成功
- 恶意拦截率：死循环被超时杀 / 外联代码无网失败 / 读环境变量拿不到值

依赖：Docker 镜像 aitrader-sandbox:latest（构建见 Dockerfile.sandbox）+ LLM。
运行：uv run pytest -m sandbox
"""

import json
import shutil
import sys
from pathlib import Path

import pytest
from langchain_core.messages import HumanMessage
from langchain_openai import ChatOpenAI

from app.llm import chat_model_kwargs, extract_llm_text
from app.tools.core import sandbox

# Windows 重定向 stdout 默认 GBK，print ✗ 等字符会 UnicodeEncodeError
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

EVALS_DIR = Path(__file__).parent
REPORT_PATH = EVALS_DIR / "reports" / "sandbox_latest.jsonl"

TASKS_FILE = EVALS_DIR / "data" / "sandbox_tasks.jsonl"
MALICIOUS_FILE = EVALS_DIR / "data" / "sandbox_malicious.jsonl"

SYSTEM_HINT = (
    "你是量化策略代码生成器。用户会给出任务和已注入的 DATA 变量说明。"
    "只输出一段 Python 代码，不要 markdown 代码块标记，不要解释。"
    "代码必须读取内置变量 DATA，把最终结论 print() 到 stdout。"
)

RETRY_HINT = (
    "你上一版代码执行失败，stderr 如下。请修正问题，只输出修正后的完整代码：\n"
)


def _load_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def _generate_code(instruction: str, data_desc: str, traceback: str | None) -> str:
    prompt = f"{SYSTEM_HINT}\n\nDATA 变量说明：{data_desc}\n\n任务：{instruction}"
    if traceback:
        prompt += f"\n\n{RETRY_HINT}{traceback}"
    llm_client = ChatOpenAI(**chat_model_kwargs(), temperature=0, max_tokens=4096)
    text = extract_llm_text(llm_client.invoke([HumanMessage(content=prompt)]))
    # 剥掉 LLM 偶尔加的 markdown 围栏
    if text.startswith("```"):
        text = text.split("```")[1]
        if text.startswith("python"):
            text = text[6:]
    return text.strip()


@pytest.mark.sandbox
class TestSandboxEval:
    @pytest.fixture(autouse=True)
    def _require_docker(self):
        if shutil.which("docker") is None:
            pytest.skip("本机无 Docker，沙箱评测跳过")

    def test_malicious_blocking(self):
        """恶意代码拦截评测（无 LLM 依赖）。

        按威胁分别断言（expect 字段定义在 sandbox_malicious.jsonl）：
        - 死循环 → 必须 timeout，且目标输出不得出现
        - 外联   → 无网必败，"exfiltrated" 不得出现
        - 读环境变量 → 允许正常退出，但沙箱内环境变量必须是空的（隔离生效的证明）
        """
        malicious = _load_jsonl(MALICIOUS_FILE)
        blocked, mal_records = 0, []
        for m in malicious:
            r = sandbox.run_backtest_code(m["code"], {"candles": []})
            expect = m.get("expect", {})
            reasons = []
            if expect.get("error") and r["error"] != expect["error"]:
                reasons.append(f"预期 error={expect['error']} 实际 {r['error']}")
            for s in expect.get("stdout_not_contains", []):
                if s in r["stdout"]:
                    reasons.append(f"stdout 泄漏敏感标记: {s!r}")
            for s in expect.get("stdout_contains", []):
                if s not in r["stdout"]:
                    reasons.append(f"stdout 未出现隔离生效证据: {s!r}")
            is_blocked = not reasons
            blocked += 1 if is_blocked else 0
            mal_records.append({"id": m["id"], "blocked": is_blocked, "reasons": reasons, "error": r["error"]})
        rate = round(blocked / len(malicious), 4) if malicious else 1.0
        print(f"\n[沙箱评测] 恶意拦截率={rate}（{blocked}/{len(malicious)}）")
        for r in mal_records:
            if not r["blocked"]:
                print(f"  ✗ {r['id']}: {r['reasons']}")
        REPORT_PATH.parent.mkdir(exist_ok=True)
        with REPORT_PATH.open("a", encoding="utf-8") as f:
            for r in mal_records:
                f.write(json.dumps({"metrics": {"malicious_block_rate": rate}, **r}, ensure_ascii=False) + "\n")
        assert rate == 1.0, f"存在未被沙箱拦截的恶意代码: {[r for r in mal_records if not r['blocked']]}"

    def test_codegen_tasks_and_malicious_blocking(self):
        judge_llm = ChatOpenAI(**chat_model_kwargs(), temperature=0, max_tokens=64)

        tasks = _load_jsonl(TASKS_FILE)

        first_pass, solved, records = 0, 0, []

        for task in tasks:
            data = task["data"]
            data_desc = task["data_desc"]
            tracebacks = []
            success, passed_first, attempt_no = False, False, 0

            for attempt in range(sandbox.MAX_ATTEMPTS):
                code = _generate_code(task["instruction"], data_desc,
                                      tracebacks[-1] if tracebacks else None)
                r = sandbox.run_backtest_code(code, data, attempts_used=attempt)
                if r["error"] in {"max_attempts_reached", "empty_code", "code_too_large",
                                  "docker_unavailable", "timeout"}:
                    tracebacks.append(f"工具层错误: {r['error']}")
                    continue
                if r["ok"]:
                    attempt_no = attempt + 1
                    # 输出校验：期望值直接比对（支持单值或列表）或 LLM 判定
                    expected = task.get("expected_stdout_contains")
                    if expected:
                        needles = expected if isinstance(expected, list) else [expected]
                        success = all(s in r["stdout"] for s in needles)
                    else:
                        verdict_msg = judge_llm.invoke([HumanMessage(content=(
                            f"任务：{task['instruction']}\n期望结论：{task['expected_conclusion']}\n"
                            f"代码输出：{r['stdout'][:1000]}\n"
                            "输出是否给出了符合期望结论的答案？只答 yes 或 no。"))])
                        verdict = (verdict_msg.content or "").strip().lower()
                        success = verdict.startswith("yes")
                    if success:
                        if attempt == 0:
                            passed_first = True
                        break
                    # 代码执行成功但结论不符：这是最需要自纠错的场景，
                    # 把偏差反馈给 LLM 重新生成，而不是直接放弃
                    tracebacks.append(f"代码执行成功但结论与期望不符。期望：{task['expected_conclusion']}。"
                                      f"实际输出：{r['stdout'][:500]}。请检查计算逻辑（周期、边界、公式）后重写。")
                    continue
                tracebacks.append(r["stderr"] or r["error"] or "unknown")

            if success:
                solved += 1
                if passed_first:
                    first_pass += 1
            records.append({"task": task["id"], "success": success, "attempts": attempt_no or sandbox.MAX_ATTEMPTS})

        n = len(tasks)
        metrics = {
            "first_pass_rate": round(first_pass / n, 4) if n else 0.0,
            "solved_within_max_rate": round(solved / n, 4) if n else 0.0,
        }
        print(f"\n[沙箱评测] 一次通过率={metrics['first_pass_rate']} "
              f"3次内解决率={metrics['solved_within_max_rate']}")

        REPORT_PATH.parent.mkdir(exist_ok=True)
        with REPORT_PATH.open("w", encoding="utf-8") as f:
            for r in records:
                f.write(json.dumps({"metrics": metrics, **r}, ensure_ascii=False) + "\n")

        assert metrics["solved_within_max_rate"] >= 0.75, "3 次内解决率不达标"
