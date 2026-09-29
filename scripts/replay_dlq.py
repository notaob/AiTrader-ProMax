# -*- coding: utf-8 -*-
"""T4 DLQ 重放：从 *.dlq 取出死信，重新发布回原交换机/路由键（人工重放的标准操作）。

用 RabbitMQ Management HTTP API 完成：
  1. POST /api/queues/{vhost}/{dlq}/get  —— 取出并确认（消息离开 DLQ）
  2. POST /api/exchanges/{vhost}/{ex}/publish —— 带原 __TypeId__ 头重发

运行：python replay_dlq.py <dlq名称> <目标路由键>
"""
import json
import sys

import httpx

BASE = "http://127.0.0.1:15672"
AUTH = ("guest", "guest")
VHOST = "%2F"          # 默认 vhost "/" 的 URL 编码
EXCHANGE = "ai.topic"

dlq = sys.argv[1]
rk = sys.argv[2]

with httpx.Client(auth=AUTH, timeout=30) as c:
    # 1) 取出死信（ack 模式：消息从 DLQ 移除，避免重放后残留双份）
    r = c.post(f"{BASE}/api/queues/{VHOST}/{dlq}/get", json={
        "count": 100, "ackmode": "ack_requeue_false",
        "encoding": "auto", "truncate": 1000000,
    })
    r.raise_for_status()
    msgs = r.json()
    print(f"从 {dlq} 取出 {len(msgs)} 条死信")

    # 2) 逐条重放到原交换机
    ok = 0
    for m in msgs:
        headers = (m.get("properties") or {}).get("headers") or {}
        # 保留 __TypeId__（Jackson 反序列化目标类型），剔除 x-death（由 broker 重新生成）
        type_id = headers.get("__TypeId__")
        props = {"content_type": "application/json", "delivery_mode": 2}
        if type_id:
            props["headers"] = {"__TypeId__": type_id}
        pub = c.post(f"{BASE}/api/exchanges/{VHOST}/{EXCHANGE}/publish", json={
            "properties": props,
            "routing_key": rk,
            "payload": m["payload"],
            "payload_encoding": "string",
        })
        pub.raise_for_status()
        if pub.json().get("published"):
            ok += 1
    print(f"重放完成: {ok}/{len(msgs)} 条已发布到 {EXCHANGE} rk={rk}")
