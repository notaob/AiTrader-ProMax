"""Stage2 端到端联调：Java 后端 + Python ai-agent + MySQL（本地无 RediSearch → 向量内存降级）。

流程：造测试用户 → 登录拿 JWT → 建会话 → 20 轮 chat（埋偏好/目标/风控事实）
→ 核对 MySQL 落库与滚动摘要。本地运行：python evals/stage2_e2e_20turns.py
（依赖：后端 :8080、ai-agent :8000、campusmall 库与测试账号权限已就绪）
"""
import hashlib
import time

import httpx
import pymysql

EMAIL = "e2e.stage2@ai.local"
PASSWORD = "E2ePass#2026"
BASE = "http://127.0.0.1:8080"

TURNS = [
    "你好，今天想跟你聊聊我自己的交易安排",
    "我最看好的币种是比特币BTC，我主要做现货买币放着，不碰合约和杠杆",
    "什么是定投DCA？它适合什么样的投资者？",
    "我的投资计划里也包括分批定投以太坊ETH，把它作为长线配置的一部分",
    "现货交易和合约交易的核心区别是什么？",
    "风控上我给自己定了规矩：单笔交易的亏损绝不能超过总资金的2%",
    "你能解释一下什么是止盈纪律吗？",
    "左侧交易和右侧交易分别是什么意思？",
    "什么是稳定币？为什么有人把它当资金中转站？",
    "冷钱包和热钱包有什么区别？",
    "仓位管理里的凯利公式大概是什么思路？",
    "为什么普通投资者不建议频繁交易？",
    "什么是板块轮动？加密市场有类似现象吗？",
    "如何区分投资和投机？",
    "什么是回撤？为什么控制回撤很重要？",
    "周定投和月定投哪种更适合长线？",
    "复利效应为什么对长线持有很重要？",
    "长期持有需要定期做再平衡吗？",
    "你对我这种偏稳健长线的风格有什么简单建议？",
    "你还记得我前面聊过的长期投资里，我计划持有的主流加密资产叫什么吗？"
    "还有我买它的方式是先直接买入放着不动，还是得开带杠杆的账户？",
]


def md5(s: str) -> str:
    return hashlib.md5(s.encode("utf-8")).hexdigest()


def log(msg: str):
    print(msg, flush=True)


def db_conn():
    return pymysql.connect(host="127.0.0.1", port=3306, user="root", password="",
                           database="campusmall", charset="utf8mb4", autocommit=True,
                           connect_timeout=3)


def setup_user():
    conn = db_conn()
    cur = conn.cursor()
    cur.execute("select id from tb_user where email=%s", (EMAIL,))
    row = cur.fetchone()
    if row:
        uid = row[0]
        for tbl in ("ai_user_memories",):
            cur.execute(f"delete from {tbl} where user_id=%s", (uid,))
        cur.execute("delete from ai_messages where conversation_id in "
                    "(select id from ai_conversations where user_id=%s)", (uid,))
        cur.execute("delete from ai_conversation_summaries where conversation_id in "
                    "(select id from ai_conversations where user_id=%s)", (uid,))
        cur.execute("delete from ai_conversations where user_id=%s", (uid,))
        cur.execute("delete from tb_user where id=%s", (uid,))
    now = time.strftime("%Y-%m-%d %H:%M:%S")
    cur.execute("insert into tb_user(email,password,nick_name,create_time,update_time) "
                "values(%s,%s,%s,%s,%s)", (EMAIL, md5(PASSWORD), "Stage2 E2E", now, now))
    conn.commit()
    new_id = cur.lastrowid
    conn.close()
    return new_id


def api(client, method, url, token=None, json_body=None, timeout=220.0):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = token
    r = client.request(method, BASE + url, headers=headers, json=json_body, timeout=timeout)
    try:
        payload = r.json()
    except Exception:
        payload = {"raw": r.text[:300]}
    return payload


def main() -> int:
    log("=== Stage2 20 轮端到端评测 ===")
    user_id = setup_user()
    with httpx.Client() as client:
        lg = api(client, "POST", "/user/login/password",
                 json_body={"email": EMAIL, "password": PASSWORD}, timeout=30)
        if lg.get("code") != 1:
            log(f"登录失败: {lg}")
            return 2
        token = lg["data"]["token"]
        cc = api(client, "POST", "/ai/conversations", token=token,
                 json_body={"title": "Stage2 E2E", "sceneType": "chat"}, timeout=30)
        conv_id = cc["data"]["id"]
        log(f"会话 conversationId={conv_id}")

        t0 = time.time()
        for i, msg in enumerate(TURNS, 1):
            ts = time.time()
            ch = api(client, "POST", f"/ai/conversations/{conv_id}/chat", token=token,
                     json_body={"message": msg, "mode": "chat"})
            reply = (ch.get("data") or {}).get("reply") or ch.get("msg") or str(ch)
            log(f"[TURN {i:02d}] {time.time()-ts:.0f}s :: {reply[:80]}")
        log(f"总耗时 {time.time()-t0:.0f}s")

        conn = db_conn()
        cur = conn.cursor()
        cur.execute("select memory_type,is_active,content from ai_user_memories "
                    "where user_id=%s order by id", (user_id,))
        mem = cur.fetchall()
        cur.execute("select count(*) from ai_conversation_summaries s "
                    "join ai_conversations c on s.conversation_id=c.id where c.user_id=%s",
                    (user_id,))
        summaries = cur.fetchone()[0]
        conn.close()
        for t, act, c in mem:
            log(f"[DB] <{t}> active={act} :: {c}")
        log(f"[DB] summaries={summaries}")
        log("E2E_20TURNS_DONE")
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
