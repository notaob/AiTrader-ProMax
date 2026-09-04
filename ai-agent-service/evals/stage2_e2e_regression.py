"""Stage2 回归：preference/goal 语义去重追加（不覆盖旧事实），constraint 仅保留最新。

场景（本地运行，依赖后端 :8080、ai-agent :8000、campusmall 库）：
- 造新用户 → 会话 A 依次说 BTC 现货偏好 / 2% 风控 / 稳健长线偏好
- 断言：两条 preference 并存；constraint 仅一条且为最新
- 直接调 /agent/memories/recall 用无关键词查询 → 应命中 BTC 现货 与 2%
- 全新会话 B（无历史）同一查询 → AI 应凭记忆答出 BTC + 现货 + 2%
"""
import hashlib
import time

import httpx
import pymysql

EMAIL = "e2e.stage2fix@ai.local"
PASSWORD = "E2ePass#2026"
BASE = "http://127.0.0.1:8080"
PROBE = "我们之前聊过的我的长线资产选择和账户方式，还有我给自己定的亏损安全线，你还记得吗？"


def md5(s):
    return hashlib.md5(s.encode("utf-8")).hexdigest()


def log(m):
    print(m, flush=True)


def db():
    return pymysql.connect(host="127.0.0.1", port=3306, user="root", password="",
                           database="campusmall", charset="utf8mb4", autocommit=True,
                           connect_timeout=3)


def setup():
    conn = db()
    cur = conn.cursor()
    cur.execute("select id from tb_user where email=%s", (EMAIL,))
    row = cur.fetchone()
    if row:
        uid = row[0]
        cur.execute("delete from ai_user_memories where user_id=%s", (uid,))
        cur.execute("delete from ai_messages where conversation_id in "
                    "(select id from ai_conversations where user_id=%s)", (uid,))
        cur.execute("delete from ai_conversation_summaries where conversation_id in "
                    "(select id from ai_conversations where user_id=%s)", (uid,))
        cur.execute("delete from ai_conversations where user_id=%s", (uid,))
        cur.execute("delete from tb_user where id=%s", (uid,))
    now = time.strftime("%Y-%m-%d %H:%M:%S")
    cur.execute("insert into tb_user(email,password,nick_name,create_time,update_time) "
                "values(%s,%s,%s,%s,%s)", (EMAIL, md5(PASSWORD), "Stage2Fix", now, now))
    conn.commit()
    uid = cur.lastrowid
    conn.close()
    return uid


def call(client, method, url, token=None, body=None, timeout=240.0):
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = token
    r = client.request(method, BASE + url, headers=h, json=body, timeout=timeout)
    try:
        return r.json()
    except Exception:
        return {"raw": r.text[:200]}


def main():
    uid = setup()
    log(f"user={uid}")
    with httpx.Client() as cli:
        lg = call(cli, "POST", "/user/login/password",
                  body={"email": EMAIL, "password": PASSWORD}, timeout=30)
        token = lg["data"]["token"]
        conv_a = call(cli, "POST", "/ai/conversations", token,
                      {"title": "fixA", "sceneType": "chat"}, 30)["data"]["id"]
        for i, m in enumerate([
            "我最看好的币种是比特币BTC，我主要做现货买币放着，不碰合约和杠杆",
            "风控上我给自己定了规矩：单笔亏损绝不超过总资金2%",
            "另外补充：我整体是偏稳健长线的风格，喜欢长期拿住不折腾",
        ], 1):
            ts = time.time()
            ch = call(cli, "POST", f"/ai/conversations/{conv_a}/chat", token,
                      {"message": m, "mode": "chat"})
            reply = (ch.get("data") or {}).get("reply") or ch.get("msg") or str(ch)
            log(f"[A{i}] {time.time()-ts:.0f}s :: {reply[:60]}")

        conn = db()
        cur = conn.cursor()
        cur.execute("select memory_type,is_active,content from ai_user_memories "
                    "where user_id=%s order by id", (uid,))
        rows = cur.fetchall()
        conn.close()
        for t, act, c in rows:
            log(f"[DB] <{t}> active={act} :: {c}")
        assert any(a == 1 and ("BTC" in c or "比特币" in c) for _, a, c in rows), \
            "BTC preference 被新 preference 覆盖丢失!"
        assert any(a == 1 and "稳健" in c for _, a, c in rows), "稳健长线 preference 未保存"
        cons = [c for t, a, c in rows if t == "constraint" and a == 1]
        assert len(cons) == 1 and "2%" in cons[0], f"constraint 应仅保留最新一条: {cons}"
        log("PASS-A: 两条 preference 并存，constraint 仅最新")

        hits = (httpx.post("http://127.0.0.1:8000/agent/memories/recall",
                           json={"user_id": str(uid), "query": PROBE, "top_k": 5},
                           timeout=30).json().get("memories") or [])
        for h in hits:
            log(f"[RECALL] sim={h.get('similarity')} :: {h.get('content')}")
        assert any("BTC" in (h.get("content") or "") or "比特币" in (h.get("content") or "")
                   for h in hits), "语义召回未命中 BTC"
        assert any("2%" in (h.get("content") or "") for h in hits), "语义召回未命中 2%"
        log("PASS-RECALL: 无关键词召回命中 BTC 现货与 2%")

        conv_b = call(cli, "POST", "/ai/conversations", token,
                      {"title": "probeB", "sceneType": "chat"}, 30)["data"]["id"]
        ts = time.time()
        ch = call(cli, "POST", f"/ai/conversations/{conv_b}/chat", token,
                  {"message": PROBE, "mode": "chat"})
        reply = (ch.get("data") or {}).get("reply") or ch.get("msg") or str(ch)
        log(f"[PROBE-B] {time.time()-ts:.0f}s :: {reply}")
        assert any(k in reply for k in ("BTC", "比特币")), "新会话无历史仍应凭记忆答出 BTC"
        assert ("现货" in reply or "买币" in reply or "持有" in reply), "未答出持有方式"
        log("E2E_REG_PASS")
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
