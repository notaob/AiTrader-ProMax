"""Stage 4 · 策略报告格式合规评测（marker: evals+format，默认 nightly）。

被评测对象：生产链路 `POST /agent/chat` 的 strategy 模式（系统提示词 STRATEGY_PROMPT
约定：必须含 ## 1~4 四章、不用表格（|）、不用 emoji）。
校验全部为确定性文本规则（章节标题按去空白匹配，容忍标题措辞微调），无需 LLM judge。

用法：
    uv run python -m evals.format_compliance                # 全量（约 1-3 分钟）
    uv run python -m evals.format_compliance --limit 2      # 冒烟
    uv run pytest -m format -q                              # pytest 入口
"""
from __future__ import annotations

import argparse
import json
import re
import time
from pathlib import Path

import httpx

BASE_URL = "http://127.0.0.1:8000"
DATA_DIR = Path(__file__).resolve().parent / "data"
REPORT_DIR = Path(__file__).resolve().parent / "reports"

# 与 STRATEGY_PROMPT 的四章标题保持一致
REQUIRED_CHAPTERS = [
    "## 1. 市场趋势分析",
    "## 2. 关键价位",
    "## 3. 交易建议",
    "## 4. 风险提示",
]

# 常用 emoji 区间（含 📊⚠️✅🎯💡 等），够覆盖策略报告常见违规即可
_EMOJI_RE = re.compile(
    "[\U0001F300-\U0001F5FF\U0001F600-\U0001F64F\U0001F680-\U0001F6FF"
    "\U0001F900-\U0001F9FF\U0001FA70-\U0001FAFF"
    "\u2600-\u27BF\uFE0F\u2B50\u2764\U0001F000-\U0001F0FF]"
)


def _norm(text: str) -> str:
    """去掉全部空白，用于章节标题容错匹配（容忍 '##1.市场趋势分析' 之类变体）。"""
    return re.sub(r"\s+", "", text)


def validate(answer: str) -> dict:
    """纯规则校验：四章完整性、无表格、无 emoji。"""
    norm_answer = _norm(answer)
    missing = [c for c in REQUIRED_CHAPTERS if _norm(c) not in norm_answer]
    has_table = "|" in answer
    has_emoji = bool(_EMOJI_RE.search(answer))
    return {
        "missing_chapters": missing,
        "has_table": has_table,
        "has_emoji": has_emoji,
        "ok": not missing and not has_table and not has_emoji,
    }


def _load() -> list[dict]:
    rows = []
    for line in (DATA_DIR / "strategy_cases.jsonl").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            rows.append(json.loads(line))
    return rows


def ask_strategy(item: dict, timeout: float = 240.0) -> str:
    """以 strategy 模式调用 /agent/chat，返回报告文本。"""
    payload = {
        "message": item["question"],
        "user_id": f"eval-format-{item['id']}",
        "session_id": f"eval-{item['id']}",
        "mode": "strategy",  # 与 judge 模块的 chat 模式唯一差异
        "history": [],
    }
    with httpx.Client(timeout=timeout) as cli:
        r = cli.post(f"{BASE_URL}/agent/chat", json=payload)
        r.raise_for_status()
        data = r.json()
        if not data.get("success"):
            raise RuntimeError(f"agent success=false: {str(data)[:300]}")
        answer = (data.get("answer") or "").strip()
        if not answer:
            raise RuntimeError("agent answer 为空")
        return answer


def evaluate(limit: int | None = None, sleep_s: float = 1.0) -> tuple[dict, list[dict]]:
    items = _load()
    if limit:
        items = items[:limit]
    details: list[dict] = []
    for idx, item in enumerate(items, 1):
        t0 = time.time()
        entry = {"id": item["id"], "question": item["question"]}
        try:
            answer = ask_strategy(item)
            entry["answer"] = answer
            entry.update(validate(answer))
            entry["answered"] = True
        except Exception as e:
            entry["answered"] = False
            entry["error"] = f"{type(e).__name__}: {str(e)[:200]}"
            entry.update({"missing_chapters": [], "has_table": False,
                          "has_emoji": False, "ok": False})
        details.append(entry)
        print(f"[{idx}/{len(items)}] {item['id']} ok={entry.get('ok')} "
              f"missing={entry.get('missing_chapters') or []} "
              f"table={entry.get('has_table')} emoji={entry.get('has_emoji')} "
              f"({time.time()-t0:.0f}s)", flush=True)
        time.sleep(sleep_s)

    answered = [d for d in details if d.get("answered")]
    n = len(answered)
    metrics = {
        "items": len(items),
        "answered": n,
        "format_pass_rate": (sum(d["ok"] for d in answered) / n) if n else 0.0,
        "chapter_complete": (sum(not d["missing_chapters"] for d in answered) / n) if n else 0.0,
        "table_violations": sum(d["has_table"] for d in answered),
        "emoji_violations": sum(d["has_emoji"] for d in answered),
    }
    return metrics, details


def format_summary(metrics: dict) -> str:
    return (
        "Stage 4 策略报告格式合规评测\n"
        f"  items={metrics['items']} answered={metrics['answered']} "
        f"format_pass_rate={metrics['format_pass_rate']:.2f}\n"
        f"  chapter_complete={metrics['chapter_complete']:.2f} "
        f"table_violations={metrics['table_violations']} "
        f"emoji_violations={metrics['emoji_violations']}"
    )


def run(limit: int | None = None, verbose: bool = True,
        report_path: str | None = None) -> dict:
    metrics, details = evaluate(limit)
    if report_path is None:
        report_path = REPORT_DIR / "format_latest.jsonl"
    else:
        report_path = Path(report_path)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    with report_path.open("w", encoding="utf-8") as f:
        for d in details:
            f.write(json.dumps(d, ensure_ascii=False) + "\n")
    if verbose:
        print(format_summary(metrics), flush=True)
    return metrics


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Strategy report format compliance")
    parser.add_argument("--limit", type=int, default=None, help="只跑前 N 条（冒烟）")
    args = parser.parse_args()
    run(limit=args.limit)
