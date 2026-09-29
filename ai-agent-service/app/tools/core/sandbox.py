"""沙箱执行共享核心：LLM 生成的策略代码在隔离 Docker 容器内运行。

威胁模型：LLM 生成的内容是**不可信输入**——可能死循环、吃光资源、被 prompt 注入
引导去读环境变量或外传数据。因此隔离边界是容器（网络/内存/CPU/文件系统/用户
五重限制），而不是对代码做字符串黑名单校验。

数据边界：沙箱无网络，行情数据由调用方在边界外获取，本层序列化后注入代码的
DATA 变量——**数据在边界外获取，代码在边界内计算**。

约定：沿 tools/core 惯例只做 IO+计算、返回结构化 dict、永不抛异常（错误进 error 字段）。
"""

from __future__ import annotations

import base64
import json
import subprocess
import time

SANDBOX_IMAGE = "aitrader-sandbox:latest"
TIMEOUT_SECONDS = 15              # 代码执行超时（防死循环/长时间阻塞）
DOCKER_OVERHEAD_SECONDS = 10      # docker run 整体超时 = 执行超时 + 容器启动开销
MAX_CODE_BYTES = 8 * 1024
MAX_OUTPUT_BYTES = 4 * 1024       # stdout/stderr 截断（防输出撑爆 LLM 上下文）
MAX_ATTEMPTS = 3                  # 自纠错循环上限（防改不对就一直烧 token）
MEMORY_LIMIT = "256m"
CPU_LIMIT = "0.5"
SANDBOX_USER = "65534:65534"      # nobody：容器内非 root 运行


def _tail(s: str, limit: int) -> str:
    """保留末尾：traceback 和最终结果通常在输出末尾。"""
    if len(s) <= limit:
        return s
    return "...(前面输出已截断)...\n" + s[-limit:]


def build_payload(code: str, data: dict) -> str:
    """拼接送入沙箱的实际代码：DATA 注入头 + LLM 代码。

    DATA 用 base64 中转，规避 JSON 字符串里的引号/反斜杠与 Python 原始字符串的边界问题。

    注入的 dict 包装为属性/下标双访问（_AttrDict）——不同模型对注入变量的访问习惯不同
    （实测 deepseek-v4 习惯 DATA.candles、qwen 习惯 DATA["candles"]），在注入层兼容，
    而不是改提示词碰运气。
    """
    blob = base64.b64encode(json.dumps(data, ensure_ascii=True).encode("utf-8")).decode("ascii")
    header = (
        "import base64, json\n"
        "def _wrap(v):\n"
        "    if isinstance(v, dict):\n"
        "        return _AttrDict({k: _wrap(x) for k, x in v.items()})\n"
        "    if isinstance(v, list):\n"
        "        return [_wrap(x) for x in v]\n"
        "    return v\n"
        "class _AttrDict(dict):\n"
        "    def __getattr__(self, k):\n"
        "        try:\n"
        "            return _wrap(self[k])\n"
        "        except KeyError as e:\n"
        "            raise AttributeError(k) from e\n"
        f"DATA = _wrap(json.loads(base64.b64decode('{blob}').decode('utf-8')))\n"
    )
    return header + "\n" + code


def docker_cmd() -> list[str]:
    """构造沙箱 docker 命令（五重限制集中在此，便于测试断言）。"""
    return [
        "docker", "run", "--rm",
        "--network", "none",                      # 防外泄：LLM 代码无任何网络出口
        "--memory", MEMORY_LIMIT,
        "--memory-swap", MEMORY_LIMIT,            # 内存与 swap 同限，防 swap 绕过
        "--cpus", CPU_LIMIT,
        "--read-only",                            # 防篡改容器文件系统
        "--tmpfs", "/tmp:size=16m",               # 唯一可写点，限量
        "--user", SANDBOX_USER,                   # 非 root 运行
        "-i",
        SANDBOX_IMAGE, "python", "-I", "-",       # 从 stdin 读代码，隔离模式
    ]


def run_backtest_code(code: str, data: dict, attempts_used: int = 0) -> dict:
    """在沙箱容器内执行 LLM 生成的代码。永不抛异常。

    Args:
        code:        LLM 生成的 Python 代码（应读取内置变量 DATA，把结果 print 到 stdout）
        data:        注入 DATA 的数据（如行情 K 线），由调用方在边界外获取
        attempts_used: 本轮对话中沙箱已执行次数（自纠错循环上限保护）

    Returns:
        {"ok", "stdout", "stderr", "exit_code", "elapsed_ms", "error"}
        error: None | max_attempts_reached | empty_code | code_too_large
             | timeout | docker_unavailable | docker_error
    """
    result = {
        "ok": False, "stdout": "", "stderr": "",
        "exit_code": -1, "elapsed_ms": 0, "error": None,
    }

    # 自纠错循环上限：防止"改不对就一直烧 token"
    if attempts_used >= MAX_ATTEMPTS:
        result["error"] = "max_attempts_reached"
        return result

    if not (code or "").strip():
        result["error"] = "empty_code"
        return result
    if len(code.encode("utf-8")) > MAX_CODE_BYTES:
        result["error"] = "code_too_large"
        return result

    payload = build_payload(code, data)
    t0 = time.monotonic()
    try:
        proc = subprocess.run(
            docker_cmd(),
            input=payload.encode("utf-8"),
            capture_output=True,
            timeout=TIMEOUT_SECONDS + DOCKER_OVERHEAD_SECONDS,
        )
    except FileNotFoundError:
        result["error"] = "docker_unavailable"
        return result
    except subprocess.TimeoutExpired as exc:
        result["elapsed_ms"] = int((time.monotonic() - t0) * 1000)
        result["error"] = "timeout"
        # 超时也回读已产生的部分输出，帮助 LLM 定位卡在哪
        result["stdout"] = _tail((exc.stdout or b"").decode("utf-8", errors="replace"), MAX_OUTPUT_BYTES)
        return result
    except OSError as exc:
        result["error"] = "docker_error"
        result["stderr"] = str(exc)[:MAX_OUTPUT_BYTES]
        return result

    result["elapsed_ms"] = int((time.monotonic() - t0) * 1000)
    result["exit_code"] = proc.returncode
    result["stdout"] = _tail(proc.stdout.decode("utf-8", errors="replace"), MAX_OUTPUT_BYTES)
    result["stderr"] = _tail(proc.stderr.decode("utf-8", errors="replace"), MAX_OUTPUT_BYTES)
    result["ok"] = proc.returncode == 0
    if not result["ok"]:
        result["error"] = "exit_nonzero"
    return result
