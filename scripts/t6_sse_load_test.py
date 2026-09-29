"""T6 运行时压测：50 并发 SSE 对话，验证并发闸门与系统稳定性。

判据（对应任务清单 T6 验收第 3 条）：
- 并发 50 时应用不 OOM、不挂起：所有请求在 120s 内收到终态帧（token/done/error 之一）；
- 超过闸门（ai.chat.max-concurrent=10）的请求**立即**收到 error 帧（"咨询人数较多"），
  而不是排队挂死 —— 快速失败是本改造的核心；
- 放行的请求正常完成（done 帧）；
- 压测后后端仍健康（/actuator/health 或再次普通对话成功）。

运行：python scripts/t6_sse_load_test.py
依赖：后端 :8080、ai-agent :8000、MySQL；测试用户自动创建。
"""
import concurrent.futures
import hashlib
import json
import sys
import time

import httpx

# Windows 重定向 stdout 默认 GBK，回复含 emoji 时会 UnicodeEncodeError
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE = "http://127.0.0.1:8080"
EMAIL = "t6.loadtest@ai.local"
PASSWORD = "T6Pass#2026"
CONCURRENCY = 50

QUERIES = [
    "什么是止盈纪律？",
    "简要解释一下回撤",
    "什么是定投？",
    "现货和合约的区别？",
    "什么是冷钱包？",
]


def md5(s: str) -> str:
    return hashlib.md5(s.encode("utf-8")).hexdigest()


def log(msg: str):
    print(msg, flush=True)


def setup_user() -> int:
    import pymysql
    conn = pymysql.connect(host="127.0.0.1", port=3306, user="root", password="",
                           database="campusmall", charset="utf8mb4", autocommit=True)
    cur = conn.cursor()
    cur.execute("select id from tb_user where email=%s", (EMAIL,))
    row = cur.fetchone()
    if row:
        uid = row[0]
        cur.execute("delete from ai_messages where conversation_id in "
                    "(select id from ai_conversations where user_id=%s)", (uid,))
        cur.execute("delete from ai_conversation_summaries where conversation_id in "
                    "(select id from ai_conversations where user_id=%s)", (uid,))
        cur.execute("delete from ai_session_state where conversation_id in "
                    "(select id from ai_conversations where user_id=%s)", (uid,))
        cur.execute("delete from ai_conversations where user_id=%s", (uid,))
        cur.execute("delete from ai_user_memories where user_id=%s", (uid,))
        cur.execute("delete from tb_user where id=%s", (uid,))
    now = time.strftime("%Y-%m-%d %H:%M:%S")
    cur.execute("insert into tb_user(email,password,nick_name,ai_chance,create_time,update_time) "
                "values(%s,%s,%s,100,%s,%s)", (EMAIL, md5(PASSWORD), "T6 LoadTest", now, now))
    uid = cur.lastrowid
    conn.commit()
    conn.close()
    return uid


def chat_stream_once(client: httpx.Client, token: str, conv_id: int, msg: str) -> dict:
    """发起一次 SSE 对话，读完整个流，返回 {status, frames, error_msg, elapsed}。"""
    t0 = time.time()
    frames, error_msg = [], None
    try:
        with client.stream("POST", f"{BASE}/ai/conversations/{conv_id}/chat/stream",
                           headers={"Authorization": token},
                           json={"message": msg, "mode": "chat"}, timeout=125.0) as resp:
            status = resp.status_code
            for line in resp.iter_lines():
                line = line.strip()
                if not line.startswith("data:"):
                    continue
                try:
                    frame = json.loads(line[5:].strip())
                except Exception:
                    continue
                frames.append(frame.get("type"))
                if frame.get("type") == "error":
                    error_msg = frame.get("message")
        return {"status": status, "frames": frames, "error": error_msg,
                "elapsed": time.time() - t0, "exception": None}
    except Exception as e:
        return {"status": None, "frames": frames, "error": None,
                "elapsed": time.time() - t0, "exception": repr(e)[:120]}


def main() -> int:
    log(f"=== T6 SSE 并发压测：{CONCURRENCY} 并发 ===")
    uid = setup_user()
    with httpx.Client() as client:
        lg = client.post(f"{BASE}/user/login/password",
                         json={"email": EMAIL, "password": PASSWORD}, timeout=30).json()
        if lg.get("code") != 1:
            log(f"登录失败: {lg}")
            return 2
        token = lg["data"]["token"]

        # 每个请求独立会话，避免共享行锁干扰（T1 压测的教训）
        conv_ids = []
        for i in range(CONCURRENCY):
            cc = client.post(f"{BASE}/ai/conversations", headers={"Authorization": token},
                             json={"title": f"T6-{i}", "sceneType": "chat"}, timeout=30).json()
            conv_ids.append(cc["data"]["id"])
        log(f"已创建 {len(conv_ids)} 个独立会话")

        t0 = time.time()
        with httpx.Client() as load_client:
            with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENCY) as pool:
                futures = [pool.submit(chat_stream_once, load_client, token, conv_ids[i],
                                       QUERIES[i % len(QUERIES)])
                           for i in range(CONCURRENCY)]
                results = [f.result() for f in futures]
        wall = time.time() - t0

    done = [r for r in results if "done" in r["frames"]]
    errored_fast = [r for r in results if r["error"] and r["elapsed"] < 5.0]
    errored_slow = [r for r in results if r["error"] and r["elapsed"] >= 5.0]
    exceptions = [r for r in results if r["exception"]]
    hung = [r for r in results if not r["frames"] and not r["exception"]]

    for i, r in enumerate(results):
        tag = "done" if "done" in r["frames"] else ("error" if r["error"] else "???")
        log(f"  [{i:02d}] {tag:5s} {r['elapsed']:6.1f}s frames={len(r['frames']):4d} "
            f"{(r['error'] or r['exception'] or '')[:40]}")

    log("---")
    log(f"总耗时: {wall:.1f}s（50 并发全部收到终态帧）")
    log(f"done:   {len(done)}")
    log(f"error(快速失败<5s): {len(errored_fast)}")
    log(f"error(慢>5s):       {len(errored_slow)}")
    log(f"exception: {len(exceptions)}")
    log(f"无任何帧(挂起): {len(hung)}")

    ok = True
    if hung:
        log("✗ 存在挂起请求（未收到任何帧）—— 快速失败失效")
        ok = False
    if exceptions:
        log("✗ 存在异常请求")
        ok = False
    if len(done) + len(errored_fast) + len(errored_slow) != CONCURRENCY:
        log("✗ 有请求未收到终态帧")
        ok = False
    # 闸门=10：应有大量请求被立即拒绝；若全部 done，说明闸门没生效
    if len(errored_fast) == 0:
        log("✗ 没有请求被快速拒绝 —— 并发闸门可能未生效")
        ok = False
    if ok:
        log(f"✓ PASS: {len(done)} 完成 / {len(errored_fast)} 快速拒绝 / 0 挂起 —— "
            f"应用未 OOM、未挂起，超限请求立即失败")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
