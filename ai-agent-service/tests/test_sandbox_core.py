"""沙箱执行核心测试：全部 mock subprocess，不依赖 Docker。

覆盖：五重限制参数在 docker 命令中的存在性、DATA 注入、代码/次数上限、
超时 / 非 zero 退出 / docker 缺失 / 输出截断各分支。
"""

import subprocess

from app.tools.core import sandbox


def _result(proc_return):
    """辅助：monkeypatch subprocess.run 的返回值。"""
    def fake_run(*args, **kwargs):
        return proc_return
    return fake_run


def test_docker_cmd_contains_five_restrictions():
    cmd = sandbox.docker_cmd()
    joined = " ".join(cmd)
    # 五重限制逐条断言：网络 / 内存 / CPU / 只读 FS / 非 root 用户
    assert "--network none" in joined
    assert "--memory 256m" in joined and "--memory-swap 256m" in joined
    assert "--cpus 0.5" in joined
    assert "--read-only" in joined and "--tmpfs /tmp:size=16m" in joined
    assert "--user 65534:65534" in joined
    assert sandbox.SANDBOX_IMAGE in cmd and "python" in cmd


def test_build_payload_injects_data_via_base64():
    data = {"candles": [[1, "open'quote", 3]], "note": "含特殊字符\\和'''引号"}
    payload = sandbox.build_payload("print(len(DATA['candles']))", data)
    # DATA 头必须能真实解析回原数据（base64 规避引号/反斜杠边界问题）
    ns = {}
    header = payload.split("\n\n")[0]
    exec(header, ns)
    assert ns["DATA"] == data
    # 用户代码在 DATA 之后
    assert payload.endswith("print(len(DATA['candles']))")


def test_success_path():
    proc = subprocess.CompletedProcess(args=[], returncode=0,
                                       stdout=b"result: 42", stderr=b"")
    monkey_run = _result(proc)
    original = sandbox.subprocess.run
    sandbox.subprocess.run = monkey_run
    try:
        r = sandbox.run_backtest_code("print('result:', 42)", {"a": 1})
    finally:
        sandbox.subprocess.run = original
    assert r["ok"] is True and r["error"] is None
    assert r["exit_code"] == 0
    assert "42" in r["stdout"]


def test_nonzero_exit_returns_stderr_tail():
    tb = "x" * 100 + "\nTraceback ... ZeroDivisionError"
    proc = subprocess.CompletedProcess(args=[], returncode=1,
                                       stdout=b"", stderr=tb.encode())
    original = sandbox.subprocess.run
    sandbox.subprocess.run = _result(proc)
    try:
        r = sandbox.run_backtest_code("1/0", {})
    finally:
        sandbox.subprocess.run = original
    assert r["ok"] is False
    assert r["error"] == "exit_nonzero"
    assert "ZeroDivisionError" in r["stderr"]


def test_timeout_maps_to_timeout_error():
    def fake_run(*args, **kwargs):
        raise subprocess.TimeoutExpired(cmd=["docker"], timeout=sandbox.TIMEOUT_SECONDS,
                                        output=b"partial output before hang")
    original = sandbox.subprocess.run
    sandbox.subprocess.run = fake_run
    try:
        r = sandbox.run_backtest_code("while True: pass", {})
    finally:
        sandbox.subprocess.run = original
    assert r["error"] == "timeout" and r["ok"] is False
    assert "partial output" in r["stdout"]


def test_docker_missing_maps_to_unavailable():
    def fake_run(*args, **kwargs):
        raise FileNotFoundError("docker not found")
    original = sandbox.subprocess.run
    sandbox.subprocess.run = fake_run
    try:
        r = sandbox.run_backtest_code("print(1)", {})
    finally:
        sandbox.subprocess.run = original
    assert r["error"] == "docker_unavailable"


def test_output_truncated_to_limit():
    big = "y" * (sandbox.MAX_OUTPUT_BYTES * 3)
    proc = subprocess.CompletedProcess(args=[], returncode=0, stdout=big.encode(), stderr=b"")
    original = sandbox.subprocess.run
    sandbox.subprocess.run = _result(proc)
    try:
        r = sandbox.run_backtest_code("print('y' * 10000)", {})
    finally:
        sandbox.subprocess.run = original
    assert len(r["stdout"]) <= sandbox.MAX_OUTPUT_BYTES + len("...(前面输出已截断)...\n")
    assert r["stdout"].endswith("y" * 10)  # 保留的是末尾


def test_code_too_large_rejected_without_execution(capsys):
    called = {"n": 0}
    def fake_run(*args, **kwargs):
        called["n"] += 1
        raise AssertionError("不应执行 subprocess")
    original = sandbox.subprocess.run
    sandbox.subprocess.run = fake_run
    try:
        r = sandbox.run_backtest_code("x = '" + "a" * (sandbox.MAX_CODE_BYTES) + "'", {})
    finally:
        sandbox.subprocess.run = original
    assert r["error"] == "code_too_large" and called["n"] == 0


def test_empty_code_rejected():
    r = sandbox.run_backtest_code("   ", {})
    assert r["error"] == "empty_code"


def test_max_attempts_reached():
    r = sandbox.run_backtest_code("print(1)", {}, attempts_used=sandbox.MAX_ATTEMPTS)
    assert r["error"] == "max_attempts_reached"


def test_max_attempts_boundary_still_executes():
    proc = subprocess.CompletedProcess(args=[], returncode=0, stdout=b"ok", stderr=b"")
    original = sandbox.subprocess.run
    sandbox.subprocess.run = _result(proc)
    try:
        # MAX_ATTEMPTS - 1 次已用：还允许最后一次执行
        r = sandbox.run_backtest_code("print('ok')", {}, attempts_used=sandbox.MAX_ATTEMPTS - 1)
    finally:
        sandbox.subprocess.run = original
    assert r["ok"] is True
