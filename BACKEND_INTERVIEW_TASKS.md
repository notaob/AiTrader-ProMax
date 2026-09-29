# AiTrader 后端竞争力提升 · 任务清单

> **目标**：应聘后端开发岗。把项目从"AI 项目里带了点后端"改造成"后端疑难问题的战场"。
> **判断标准**：面试官追问细节时答得上来 —— 所以每个任务都要求**自己动手修**，而不是罗列技术栈。

## 进度

| 任务 | 状态 |
|---|---|
| T1 消除 120 秒长事务 | ✅ 已完成 · ✅ **实测通过** |
| T2 修复 ThreadLocal 越权隐患 | ✅ 已完成 · ✅ **实测通过**（单元测试 3 例 + 端到端 100 次 / 0 泄漏） |
| T3 配额扣减原子化 | ✅ 已完成 · ✅ **实测通过** |
| T4 向量同步 MQ 化 + 死信 | ✅ 已完成 · ✅ **实测通过**（11 例） |
| T5 摘要与记忆异步化 | ✅ 已完成 · ✅ **实测通过** |
| T6 SSE 并发上限与超时治理 | ✅ 已完成 · ✅ **实测通过** |
| T7 清理课设残留 | ✅ 已完成（死常量 + 分页已修；模块经决策**保留**，见 T7 节说明） |
| T9 traceId 全链路 | ✅ 已完成 · ✅ **实测通过**（5 例，含接线验证） |
| T9 业务缓存 | ✅ 已完成 · ✅ **实测通过**（6 例） |
| T9 业务指标 | ✅ 已完成 · ✅ **实测通过**（4 例：限流/闸门/死信/积压深度） |
| T8 README 结构 | ✅ 已完成（问题驱动叙事章节 + 实测数字 + 指标端点说明） |

## 验证产物与实测结果

| 任务 | 产物 | 运行方式 | 判据 | 实测 |
|---|---|---|---|---|
| T3 | `src/test/java/com/mp/aitrader/AiChanceConcurrencyTest.java` | `.\mvnw.cmd test -Dtest=AiChanceConcurrencyTest` | 余额=1 时 20 并发扣减**恰好成功 1 次** | ✅ 通过 |
| T1 | `src/test/java/com/mp/aitrader/TransactionBoundaryTest.java` | `.\mvnw.cmd test -Dtest=TransactionBoundaryTest` | LLM 期间事务激活数=0、活跃连接=0、总耗时 < 1.5×LLM 延迟 | ✅ 通过 |
| T2 | `src/test/java/com/mp/aitrader/ThreadLocalCleanupTest.java` | `.\mvnw.cmd test -Dtest=ThreadLocalCleanupTest` | SSE 返回后 `getCurrentId()==null`；匿名放行分支同样清空 | ✅ 通过（3 例） |
| T2 端到端（可选） | `scripts/verify-t2-threadlocal.ps1` | `.\scripts\verify-t2-threadlocal.ps1 -Email <账号> -Password <密码>` | 匿名 100 次，`isLiked=true` 出现 **0** 次 | ✅ 通过（实测 100 次 / 0 次泄漏） |
| T4 投递器 | `src/test/java/com/mp/aitrader/AiTaskPublisherTest.java` | `.\mvnw.cmd test -Dtest=AiTaskPublisherTest` | 事务内投递**必须推迟到 afterCommit**；投递失败降级本地执行 | ✅ 通过（4 例） |
| T4 消费者 | `src/test/java/com/mp/aitrader/AiTaskConsumersTest.java` | `.\mvnw.cmd test -Dtest=AiTaskConsumersTest` | 业务成功才 ack；失败 `basicNack(requeue=false)` 进死信 | ✅ 通过（7 例） |
| T5 | `src/test/java/com/mp/aitrader/AsyncPersistTest.java` | `.\mvnw.cmd test -Dtest=AsyncPersistTest` | 摘要 sleep 2s 时 `chat()` 仍 < 1s 返回；constraint 同步、preference 异步 | ✅ 通过（2 例） |
| T9 | `src/test/java/com/mp/aitrader/TraceIdTest.java` | `.\mvnw.cmd test -Dtest=TraceIdTest` | 异步线程能读到同一 traceId；请求结束清理 MDC；**真实线程池下 SSE worker 继承 traceId** | ✅ 通过（5 例） |
| T9 缓存 | `src/test/java/com/mp/aitrader/AiConversationCacheTest.java` | `.\mvnw.cmd test -Dtest=AiConversationCacheTest` | 空列表也缓存（防穿透）；TTL 落在 [60,90)（防雪崩）；失效时删除 key | ✅ 通过（6 例） |
| T9 指标 | `src/test/java/com/mp/aitrader/AiMetricsTest.java` | `.\mvnw.cmd test -Dtest=AiMetricsTest` | 闸门拒绝/LLM 限流/死信计数正确累加；死信按 kind 分标签；队列深度 Gauge 实时反映积压 | ✅ 通过（4 例） |
| T4 对账 | `src/test/java/com/mp/aitrader/AiVectorReconcileTest.java` | `.\mvnw.cmd test -Dtest=AiVectorReconcileTest` | Redis 无 `mem:doc:{id}` → 重新投递向量写入；键存在 → 不投；Redis 不可用 → 中止而非误判全量；补投受上限保护 | ✅ 通过（4 例） |
| T4 DLQ 重放 | `scripts/replay_dlq.py` | `python replay_dlq.py <dlq> <routing_key>` | 死信从 DLQ 取出、带原 `__TypeId__` 头重发原交换机 → 消费成功 → DLQ 清零 | ✅ 运行时实测（10/10） |
| T6 | `src/test/java/com/mp/aitrader/SseConcurrencyGateTest.java` | `.\mvnw.cmd test -Dtest=SseConcurrencyGateTest` | 超限时被拒请求**不进入业务执行**；worker 结束后许可归还 | ✅ 通过（2 例） |

### T1 实测输出（可直接写进简历 / README）

```
[T1] 并发=20, LLM延迟=3000ms, 连接池=5 → 成功=20, LLM期间活跃连接=0,
     LLM期间事务激活数=0, 总耗时=3174ms（阈值4500ms）
```

**对照：把 `@Transactional` 加回 `chat()` 后同一测试立即失败**（成功数 20 → 5，总耗时 3174ms → 12171ms ≈ 4 × LLM 延迟，正好是 20 个请求被 5 个连接串行成 4 批）。已确认该测试能捕获回归。

### 运行前提与踩坑记录

- 需要本机 **MySQL**（Redis 非必需 —— 这组测试都不触碰 Redis）。
- `ThreadLocalCleanupTest` 连 MySQL 都不需要，是纯 Mockito 单元测试，任何环境都能跑。
- 端到端脚本需后端 + Redis 启动。已在本机实测通过（匿名 100 次，0 次泄漏）。
- **坑**：`.ps1` 若以无 BOM 的 UTF-8 保存，PowerShell 5.1 会按 GBK 解析，中文变成乱码并破坏语法。
  脚本必须带 BOM（或纯 ASCII）。
- 本机 MySQL root 口令为空时用 `-Dspring.datasource.password=` 覆盖配置。
- **坑**：`Copy-Item` 还原源码会保留原文件时间戳，导致 Maven 判定"无需重编译"而继续使用旧 class。
  验证前若改动过源码，请用 `mvnw clean test` 强制重建。

---

## 一、总览

| ID | 任务 | 优先级 | 面试可讲时长 | 覆盖考点 |
|---|---|---|---|---|
| T1 | 消除 120 秒长事务 | P0 | 20 min | 事务边界、连接池、雪崩 |
| T2 | 修复异步请求下 ThreadLocal 残留（越权隐患） | P0 | 20 min | Servlet 异步模型、线程池、内存模型 |
| T3 | 配额扣减改原子操作 | P0 | 10 min | CAS、乐观锁、幂等 |
| T4 | 向量同步 MQ 化 + 死信 + 对账 | P1 | 10 min | 最终一致、可靠投递、幂等 |
| T5 | 摘要与记忆落库异步化 | P1 | 8 min | 主从链路拆分、关键路径 |
| T6 | SSE 并发上限与超时治理 | P1 | 8 min | 资源上限、快速失败 |
| T7 | 清理无关模块与课设残留 | P2 | — | 项目聚焦度 |
| T8 | README 重写（问题驱动叙事） | P2 | — | 第一印象 |
| T9 | 业务缓存 + traceId 全链路 | P2 | 5 min | 缓存一致性、可观测 |

---

## 二、P0 · 必修（这三件是"加分故事"，不是作业）

### T1 · 消除 120 秒长事务

**问题**：`@Transactional` 方法里包住了最长 120 秒的外部 HTTP 调用，事务期间一直占用数据库连接。

**证据**
- `ai-trader-backend/src/main/java/com/mp/aitrader/service/impl/TbAiServiceImpl.java:32-33`（`@Transactional`）→ `:49` `callAgent(user)`
- `.../conversation/service/impl/AiConversationServiceImpl.java:99-100`（`@Transactional`）→ `:291` `langGraphClient.chatWithContext(...)`
- HTTP 超时：`agent/client/LangGraphClient.java:134`、`:186` 均为 `timeout(120000)`
- `src/main/resources/application-example.yaml` **未配置 HikariCP** → 默认连接池 = **10**

**后果**：10 个并发策略请求即耗尽连接池 → 全应用所有接口阻塞。这是能直接搞挂生产的 P0。

**改造方案**
1. 事务边界收窄：事务内只做「配额扣减 + 落占位记录」，事务外执行 LLM 调用与结果落库。
2. 拆分为三段：
   - `Tx1`（事务）：扣 `aiChance`、插入 user 消息、写 `status=RUNNING`
   - `LLM`（无事务）：调用 Python Agent
   - `Tx2`（事务）：写入 assistant 回复、更新状态
3. 代码示意：

```java
// ① 事务内：只覆盖数据库写
@Transactional
public Long prepareTurn(Long conversationId, Long userId, String msg) {
    if (userMapper.deductAiChance(userId) == 0) throw new BizException("AI交易机会不足");
    messageMapper.insert(userMsg(conversationId, msg));
    return userMsg.getId();
}

// ② 无事务：外部 IO
LangGraphChatResult r = langGraphClient.chatWithContext(...);

// ③ 事务内：结果落库
@Transactional
public void finishTurn(Long conversationId, Long userId, String reply) { ... }
```

> **状态：代码已改造完成（编译通过），待运行时压测验证。**
> 实现要点：移除两个入口的 `@Transactional`；改用 `TransactionTemplate` 把事务边界精确收在"纯数据库写"上；
> `persistAiReply` 中摘要与记忆保存移到事务外；`buildChatContext`（只读）与 LLM 调用全程无事务。

**验收**
- [x] `chat()` / `TbAiServiceImpl.chat()` 上不再有 `@Transactional` 包裹远程调用
- [ ] 本地起 20 并发策略请求，观察 HikariCP 活跃连接数峰值 **≤ 3**
- [ ] 记录改造前后连接池占用对比数字（README 要用）

---

### T2 · 修复异步请求下 ThreadLocal 残留（偶发越权）

**问题**：用户身份存于 `ThreadLocal`，在拦截器 `afterCompletion` 清理；但 SSE 让请求进入 Servlet 异步模型，**清理不保证发生在设置值的那个线程上**，线程带着上一个用户的 id 回到 Tomcat 线程池。

**证据链**
- `context/BaseContext.java:4` — `ThreadLocal<Long>`
- `interceptor/JwtInterceptor.java:65` 设置；`:74-77` 在 `afterCompletion` 清理
- `interceptor/JwtInterceptor.java:41-44` — `/moments/list` 放行匿名，**不设置也不清理**
- `service/impl/TbMomentServiceImpl.java:50` — `getMomentList` 读取 `BaseContext.getCurrentId()`
- `conversation/controller/AiConversationController.java:38` — `new SseEmitter(300_000L)` 触发异步

**后果**：匿名请求复用到残留线程 → 拿到他人 `userId` → `isLiked` 按他人计算，存在数据泄露/越权隐患。特征是**偶发、难复现**。

**改造方案**（推荐方案一）
1. **身份不跨异步边界**：Controller 里读取后**立即显式传参**，SSE 分支 `try/finally` 显式 `removeCurrentId()`。
2. 备选：换 `TransmittableThreadLocal` + `TtlExecutors.getTtlExecutorService()` 装饰 `streamExecutor`。
3. 兜底：`JwtInterceptor` 匿名放行分支里补一句 `BaseContext.removeCurrentId()`。

```java
@PostMapping("/{id}/chat/stream")
public SseEmitter chatStream(...) {
    Long userId = BaseContext.getCurrentId();
    try {
        SseEmitter emitter = new SseEmitter(120_000L);
        streamExecutor.execute(() -> conversationService.runChatStream(conversationId, userId, request, emitter));
        return emitter;
    } finally {
        BaseContext.removeCurrentId();   // 异步请求下必须显式清理
    }
}
```

> **状态：代码已改造完成（编译通过），待运行时压测验证。**
> 实现要点：`chatStream` 取值后立即 `try/finally` 显式 `removeCurrentId()`；
> `JwtInterceptor` 匿名放行分支补 `removeCurrentId()` 兜底；userId 显式传参，不再跨异步边界。

**验收**
- [x] SSE 接口返回后，`BaseContext.getCurrentId()` 在原线程上为 `null`
- [ ] 匿名调用 `GET /moments/list`，日志中 `currentUserId` 恒为 `null` 且连续 100 次调用无串号
- [ ] 写一段复现用例（并发：一个已登录 SSE 请求 + 若干匿名 list 请求）

---

### T3 · 配额扣减改为原子操作

**问题**：读—校验—写三步非原子，并发下两个请求都读到 `1`、都通过校验、都写 `0`，用户白用一次。且该配额可用积分兑换（1000 积分 = 1 次），涉及资金属性。

**证据**
- `conversation/service/impl/AiConversationServiceImpl.java:125-134`
- `service/impl/TbAiServiceImpl.java:43-55`

**改造方案**：一条原子 SQL，靠影响行数判断，不加锁。

```java
// TbUserMapper
@Update("UPDATE tb_user SET ai_chance = ai_chance - 1, update_time = NOW() " +
        "WHERE id = #{userId} AND ai_chance > 0")
int deductAiChance(@Param("userId") Long userId);
```

```java
if (userMapper.deductAiChance(userId) == 0) {
    return ChatResponse.builder().reply("AI交易机会不足，请先获取机会").build();
}
```

> **状态：代码已改造完成（编译通过），待并发压测验证。**
> 实现要点：新增 `TbUserMapper.deductAiChance`（`UPDATE ... WHERE ai_chance > 0`，以影响行数判定）；
> `AiConversationServiceImpl` 与 `TbAiServiceImpl` 两处「读-改-写」均已替换。

**验收**
- [x] 两处扣减逻辑统一走 `deductAiChance`
- [ ] 并发压测：初始 `aiChance=1`，20 并发请求 → **仅 1 个成功**，其余返回"机会不足"
- [ ] 能口述：为什么不用悲观锁、乐观锁 version 与本方案的区别

---

## 三、P1 · 可靠性与性能

### T4 · 向量同步 MQ 化 + 死信 + 对账

**问题**：MySQL 落库成功后同步 HTTP 调 Python 写向量，失败仅 `log.warn` → MySQL 有数据、Redis 无向量，**永久召回不到且无人知晓**。

**证据**
- `memory/service/impl/AiMemoryServiceImpl.java:276-279`（`syncVectorSave`）
- `knowledge/service/impl/AiKnowledgeServiceImpl.java:34-35`（`@Transactional`）+ `:66-70`（事务内发 HTTP）

**改造方案**
1. 声明 `ai.topic`（topic exchange）+ 4 个队列 + 死信交换机 `ai.dlx`：

| routing key | 队列 | 内容 |
|---|---|---|
| `vector.memory.save` | `ai.vector.memory.save` | 按 `memory_id` 写向量 |
| `vector.memory.delete` | `ai.vector.memory.delete` | 删向量 |
| `vector.rag.sync` | `ai.vector.rag.sync` | 知识库分片向量化 |

2. **幂等天然成立**：向量 doc id 即 MySQL 主键，重复消费为覆盖写。
3. **事务安全**（必踩的坑）：`uploadDocument` 是 `@Transactional`，不能在事务内直接投递，否则消费者读到未提交数据。统一用 `afterCommit`：

```java
if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { rabbitTemplate.convertAndSend(EXCHANGE, rk, payload); }
    });
} else {
    rabbitTemplate.convertAndSend(EXCHANGE, rk, payload);
}
```

4. 消费者手动 ack，`requeue=false` 走 DLQ：

```java
@RabbitListener(queues = "ai.vector.memory.save")
public void onVectorSave(VectorSyncTask t, Channel ch, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
    if (langGraphClient.syncMemorySave(t.getUserId(), List.of(t.toItem()))) ch.basicAck(tag, false);
    else ch.basicNack(tag, false, false);
}
```

5. **对账补偿**：定时任务扫「MySQL 有记忆 / Redis 无向量」，重新投递。

**验收**
- [x] `pom.xml` 已有 `spring-boot-starter-amqp`（`:35`），补充 `application.yaml` 连接配置
- [x] 手动 ack + `default-requeue-rejected: false`
- [x] 杀掉 Python 服务投递消息 → 消息进入 DLQ 而非丢失；重启后重放成功
      （2026-09-29 运行时实测：10 条全部进死信、`scripts/replay_dlq.py` 重放 10/10 成功）
- [x] 提供一次对账任务的手动触发入口：`POST /ai/reconcile/vector`（`AiReconcileController`）
      —— 三层兜底闭环：MQ 重试（投了但消费失败）→ DLQ 死信重放（反复失败）→
      对账扫描（消息根本没投出去：降级本地执行时进程重启、兜底执行也失败）。
      原理：向量 doc id = MySQL 主键 → Java 侧 `EXISTS mem:doc:{id}` 即可判定缺失，
      缺失则重投向量写入任务（幂等覆盖写）。补投计数进指标 `ai.vector.reconcile.resubmitted`。

---

### T5 · 摘要与记忆落库异步化

**问题**：`persistAiReply` 在 `sendDoneFrame` **之前**执行，内部串行发起 1 次 LLM 摘要（60s 超时）+ 2~3 次 embedding 调用 → 用户多等数秒才收到结束信号。

**证据**
- `AiConversationServiceImpl.java:448-449`（先落库）→ `:452-462`（后发 done 帧）
- `:623-625` 触发摘要；`:628-639` 触发记忆保存
- `AiSummaryServiceImpl.java:46` → `:88` `summarizeMessages`（`LangGraphClient.java:399` 超时 60s）

**改造方案**
1. 摘要拆两段：同步侧只做「判定 + 取 messageId 快照」，LLM 与落库交给队列 `ai.conversation.summary`。
   - **必须传 id 快照，不能只传 `conversationId`**：延迟消费时重新查询会因新消息导致摘要范围漂移。
2. 记忆保存进队列 `ai.memory.persist`（去重、停用、落库），向量化另发 `vector.memory.save`。
3. **`constraint` 类型保持同步**：风控/止损规则延迟生效有业务风险。
4. 顺序保证：单队列 + 单消费者 + `prefetch=1`，同用户天然串行。
5. 幂等兜底：`ai_conversation_summaries` 加 `uk(conversation_id, end_message_index)`；`ai_user_memories` 加 `content_hash` 唯一索引。

**验收**
- [ ] `persistAiReply` 后 `sendDoneFrame` 提前；埋点记录耗时 P95 从「秒级」降到 **< 50ms**
- [ ] `constraint` 类型仍为同步路径（代码注释标明原因）
- [ ] 跑 `ai-agent-service/evals/` 的 20 轮记忆评测，**召回质量不退化**
- [ ] 加开关 `ai.task.mq.enabled`，可一键回退同步路径

---

### T6 · SSE 并发上限与超时治理

> **状态：已完成 · 实测通过（2 例），已验证能捕获回归**（禁用闸门后 `Wanted 1 time: But was 2 times`）。
> 落地要点：有界线程池（4~16 / 队列 100）替换 `newCachedThreadPool`；
> `Semaphore` 闸门 `ai.chat.max-concurrent`（默认 10），超限返回 error 帧；
> 三处超时统一为 120s（SseEmitter / Python SSE 读超时 / `spring.mvc.async.request-timeout`）；
> Python 侧 `asyncio.Semaphore`（`LLM_MAX_CONCURRENCY` 默认 4，等待 5s 超时 → 429 / error 帧）守住真正的 LLM 出口。

**问题**：`Executors.newCachedThreadPool()` 无上限；单请求 `SseEmitter(300_000L)` 最长占线程 5 分钟；且 `spring.mvc.async.request-timeout: 120000` 与 300s 配置打架。

**证据**
- `AiConversationController.java:26-30`、`:38`
- `application-example.yaml:13-14`

**改造方案**
1. 有界线程池 + `Semaphore` 快速失败：

```java
private static final Semaphore AI_GATE = new Semaphore(10);

if (!AI_GATE.tryAcquire()) throw new BizException("当前 AI 请求较多，请稍后重试");
streamExecutor.execute(() -> {
    try { conversationService.runChatStream(...); }
    finally { AI_GATE.release(); }        // ★ 必须在 worker 的 finally 里，不能放 controller 末尾
});
```

2. 超时统一：`SseEmitter` 300s → **90s**，与 yaml 保持一致。
3. SSE 心跳：帧间隔上限 30s，无输出即判死并主动 `completeWithError`。
4. Python 侧加 `asyncio.Semaphore` 兜住真正的 LLM 出口（`/agent/chat`、`/agent/chat/stream`）。

**验收**
- [ ] 线程池有界，`AI_GATE` 满时快速失败且返回友好文案
- [ ] 三处超时值（SseEmitter / yaml / HTTP timeout）一致
- [ ] 并发 50 请求时应用不 OOM、不挂起，多余请求立即收到"繁忙"

---

## 四、P2 · 包装与补齐

### T7 · 清理无关模块与课设残留

**已完成**
- [x] 清理 `RedisConstants` 死常量：删除 `CACHE_SHOP_KEY` / `CACHE_SHOPTYPE_KEY` / `LOCK_SHOP_KEY` /
      `SECKILL_STOCK_KEY` / `BLOG_LIKED_KEY` / `FEED_KEY` / `SHOP_GEO_KEY` / `USER_SIGN_KEY`
      及其配套 TTL（共 11 项，全局搜索确认无任何引用）
- [x] 修正常量误用：`TbUserServiceImpl` 用 `CACHE_NULL_TTL` 当验证码 TTL → 改用语义正确的 `LOGIN_CODE_TTL`（两者同为 2L，行为不变）
- [x] 新增 `config/MybatisPlusConfig` 注册分页插件（此前**未注册**，这也是原来只能手工拼分页 SQL 的原因）
- [x] `getMomentList` 改用 `Page` 分页 + 入参保底（`page>=1`、`size<=50`），去掉字符串拼接的 `.last("LIMIT ...")`

**未做（已决策：保留）**
- [x] ~~删除朋友圈（`TbMoment*` + like/comment）、优惠券（`TbPromotion`）、新手礼包模块~~
      **决策：保留**。理由：① 它们是后端基本功的展示面（分页、点赞计数、事务），T3 的原子扣减
      就在朋友圈模块的登录链路旁；② 面试官问起时**主动说明**即可："这是早期练手模块，
      我保留了 CRUD/分页/缓存这些基本功展示，项目重心在 AI 链路的稳定性治理" ——
      主动交代比删代码更能体现工程判断；③ 删除需同步改前端 5 个文件与路由，且
      `JwtInterceptor` 匿名放行分支与 `ThreadLocalCleanupTest` 依赖该路径，收益低、风险高。
- [ ] 数据库名 `campusmall` → `aitrader`：会让本机现有库直接连不上，属于**部署迁移项**，建议上线前用迁移脚本处理

### T8 · README 重写（问题驱动叙事）
- [ ] 移除纯架构图 + 技术栈清单的写法
- [ ] 改为「问题 → 根因 → 方案 → 结果」四段式，每条带数字
- [ ] 示例：

```markdown
| 维度 | 内容 |
|---|---|
| 稳定性 | 定位并修复 120s 长事务，数据库连接占用从「10 并发即耗尽」降至峰值 ≤3 |
| 安全   | 修复 Servlet 异步模型下 ThreadLocal 残留导致的偶发越权 |
| 一致性 | 修复并发配额超扣；引入 MQ + 死信 + 对账，向量同步失败率归零 |
| 可靠性 | SSE 链路增加并发上限与快速失败，消除雪崩风险 |
```

### T9 · 业务缓存 + traceId + 指标
- [x] 给会话列表 / 行情数据加 Redis 缓存，能讲清穿透、击穿、雪崩与双写一致性
- [x] 全链路 `traceId`（含异步线程 MDC 传递，与 T2 同知识点）
- [x] 后端侧指标（Micrometer，`metric/AiMetrics`）：
  - `ai.llm.throttled` — 收到 Python 侧限流 error 帧次数
  - `ai.chat.gate.rejected` — SSE 并发闸门拒绝次数（两条拒绝路径都埋点）
  - `ai.task.dlq{kind}` — 消息进死信计数（消费者 nack 时累加）
  - `ai.task.queue.depth{kind}` — 本地异步队列积压深度（Gauge）
  - 暴露：`/actuator/metrics/*` 与 `/actuator/prometheus`（management 已在 yaml 配置）

---

## 五、排期建议

| 周次 | 任务 | 产出 |
|---|---|---|
| 第 1 周 | T1 + T2 + T3 | 三个"能讲 20 分钟"的故事 |
| 第 2 周 | T4 + T5 + T6 | 可靠性与性能闭环 |
| 第 3 周 | T7 + T8 + T9 | 聚焦、包装、补齐 |
| 第 4 周 | 复盘整理 | 每个任务写成 STAR 话术，准备追问 |

**追问准备清单**（每个任务都要能答）
- 为什么会发生？根因是什么？
- 还有别的修法吗？为什么选这个？
- 修完怎么验证？怎么观测？
- 如果再出问题，你怎么发现？

---

## 五点五、运行时验收进度（2026-09-28 实测）

> 首轮因 DashScope 账号欠费（`code: 'Arrearage'`）中断 → chat 供应商切换至火山方舟 GLM 后重跑完成。

| 验收项 | 状态 | 说明 |
|---|---|---|
| T5 evals 20 轮记忆评测 | ✅ **20/20 通过** | chat 供应商切至火山方舟 GLM-5.3 flash（DashScope 欠费，`ARK_*` 一键切换）后重跑：总耗时 1169s，**18/20 完整回复**（2 轮 reply 为空，GLM 思考 token 兼容性问题已修）；**第 20 轮模型准确回忆第 2 轮埋入的事实**（"你计划长期持有的主流加密资产：比特币 BTC——这是你的主仓位"）；DB 核对：4 条记忆分类全对（preference×2/goal/constraint）无重复、滚动摘要 14 条、异步落库正常。注：embedding 仍走欠费的 DashScope，语义召回按设计降级为关键词匹配，不影响记忆落库/摘要/召回兜底链路的验证 |
| **附带修复真实缺陷** | ✅ ×3 | ① `classify`/`summarize` 同步 LLM 调用跑在 `async def` 里**阻塞整个事件循环** + 摘要绕过 `_LLM_GATE` → 已改 `asyncio.to_thread` + 纳入闸门；② **思考型模型兼容**：思考 token 计入 `max_tokens`，原 256/1200/8000 被思考耗尽导致 JSON 截断与空回复 → 提升 + `extract_llm_text` 回退 `reasoning_content` + 截断 JSON 修复；③ Python 56 例测试全程绿 |
| T6 50 并发 SSE 压测 | ✅ **通过** | `scripts/t6_sse_load_test.py`：50 并发 → **10 完成（闸门容量）+ 40 个 0.0s 快速拒绝（友好 error 帧）+ 0 挂起 + 0 异常**，总耗时 55s；压测后服务健康。放行的 10 个请求全部走完整 LLM 链路成功 |
| T4 DLQ 实测 | ✅ **全链路通过** | 便携版 RabbitMQ 3.13.7 + Erlang 26.2.5（免安装，`D:\AiTrader\tools`）。四步实测：① 正常路径——Agent 在线，对账补投 10 条全部消费成功，DLQ=0；② **杀掉 Python** —— 消费失败 `basicNack` → **10 条全部转入 `ai.vector.memory.save.dlq`，零丢失**（payload/headers 原封未动）；③ 重启 Python，用 `scripts/replay_dlq.py`（Management API 取出死信、带原 `__TypeId__` 头重发原交换机）重放 → **10 条全部成功消费**，DLQ 清零；④ Java 41 例回归通过 |
| **DLQ 实测抓到真 bug** | ✅ 已修 | `SimpleMessageConverter` 只支持 String/byte[]/Serializable，任务 payload 不是 Serializable → **MQ 模式下每次 publish 都抛异常、被降级逻辑吞成本地执行**——表面一切正常，MQ 实际从未承载过一条消息。单测 mock 了 RabbitTemplate 测不出装配缺陷，真 broker 才暴露。修复：`AiTaskRabbitConfig` 注册 `Jackson2JsonMessageConverter`（发布/消费共用，Boot 自动装配） |

### 供应商切换（新增能力）

chat 模型供应商支持一键切换：`.env` 配置 `ARK_API_KEY` / `ARK_BASE_URL` / `ARK_MODEL` 即走火山方舟（OpenAI 兼容），不配则走 DashScope。实现收敛在 `app/llm.py#chat_model_kwargs`（图内 ReAct / 记忆分类 / 摘要三处共用）。embedding 仍走 DashScope，失败自动降级关键词召回。

### 新增踩坑记录

- **DashScope `Arrearage` 是 400 不是 429**：账号欠费时 LLM/embedding 全部返回 400，
  Java 侧表现为所有对话回答"AI 服务暂时繁忙"——先查账号再查代码。
- **Windows 重定向 stdout 默认 GBK**：Python 脚本 print 到重定向文件时遇到 emoji
  直接 `UnicodeEncodeError` 崩溃（与 .ps1 的 BOM 坑同源）。统一在脚本头部
  `sys.stdout.reconfigure(encoding="utf-8", errors="replace")`。
- **同步 LLM 调用 × async 端点 = 事件循环阻塞**：FastAPI 里 `async def` 端点直接调
  LangChain 同步 `invoke` 会卡死整个循环；必须 `asyncio.to_thread` 包装。
- **SimpleMessageConverter 假通路**：MQ 模式下 publish 抛 `IllegalArgumentException` 被
  降级逻辑静默吞掉，系统表现为"一切正常但 MQ 没用过"——降级兜底反而掩盖了配置缺陷，
  运行时验收（真 broker + 看队列深度）是唯一可靠的验证手段。
- **便携 RabbitMQ 起不来报 `nodistribution`**：首次启动时 epmd 未就绪的竞态，重试即可；
  Erlang/RabbitMQ 用 GitHub 便携 zip（免管理员），`ERLANG_HOME` 指向解压根目录。

---

## 六、验收 Checklist（面试前自查）

- [ ] 项目中无 `@Transactional` 包裹远程调用
- [ ] 无 `ThreadLocal` 跨越异步边界
- [ ] 所有"扣减/配额"类操作为原子 SQL
- [ ] 所有跨服务写入有重试 + 死信 + 对账
- [ ] 所有线程池有界，所有长连接超时合理
- [ ] 无 `shop` / `seckill` / `campusmall` 等无关残留
- [ ] README 通篇是"问题与结果"，不是"技术栈清单"
- [ ] 每个任务都能用 STAR 讲 3 分钟，且经得起两轮追问

---

## 七、风险提示（务必处理）

`TbAiServiceImpl.java:66-76` 中策略报告明确输出「入场点位、止损点位、止盈点位」。在国内，**未持牌开展证券投资咨询存在合规风险**。

面试时建议主动说明你的处理方式（例如：定位为交易纪律监督/复盘工具而非投资建议、增加免责声明、去掉具体点位输出）。这既规避风险，也是一个体现产品判断力的加分点。
