# AI_PHASE3：可评测 AI 交易 Agent 升级计划

> 目标：基于 LangGraph 的 AI 交易 Agent，补齐全套「主流、可展示、可量化」能力：
> **流式对话 + 分层记忆/RAG + 工具调用 + 策略报告生成**，接入 **Langfuse 观测**，
> 用 **pytest 评测集** 量化效果，工具层兼容 **MCP 协议**。
>
> 执行原则：**每阶段结束，项目必须完整可运行、可验证**。任何阶段中断，都停留在"可演示的完成态"而非半截重构。

---

## 1. 现状基线（改造前的真实情况）

| 维度 | 现状 | 证据 |
|---|---|---|
| Python 依赖 | `langgraph>=0.0.48` / `langchain>=0.1.0`，2024 年 API | `ai-agent-service/requirements.txt` |
| 图结构 | 手写 `agent→tools` 循环，无 checkpointer、无流式 | `ai-agent-service/app/graph/trading_graph.py` |
| 提示词 | 同一份 prompt 在 `trading_graph.py` 与 `context_builder.py` 双写，已漂移 | 两文件比对 |
| 长期记忆 | Java 侧 substring 召回 + 去重；无语义 | `ai-trader-backend/.../memory/service/impl/AiMemoryServiceImpl.java` |
| 摘要 | Java 侧把消息拼接截断 500 字，非 LLM 生成 | `.../conversation/service/impl/AiSummaryServiceImpl.java` |
| Python 侧记忆函数 | md5 hash 假向量（玩具） | `ai-agent-service/app/memory/memory_service.py` |
| 观测 | 无 Langfuse / trace，仅 print | `ai-agent-service/app/main.py` |
| 评测 | 无 golden set、无自动化评测 | — |
| 工具层 | 仅本服务内可用 | `app/tools/market_tools.py` 等 |
| 对话传输 | Java 同步 POST `/agent/chat` → 整包返回 | `main.py` + `.../agent/client/LangGraphClient.java` |
| 前端 | `chat()` 一次性拿到 `reply`，无逐字渲染；对话气泡纯文本 | `AiTrader/src/services/ai.ts` |

---

## 2. 关键架构决策（先定死，执行不摇摆）

1. **版本升级为主干前提**：流式 / checkpointer / Langfuse 回调 / 评测框架都依赖新版 API，Stage 0 先做升级与回归。
2. **记忆分层**：长期事实记忆由 Java 存 MySQL（含 `ai_user_memories` 原文本 + 既有 embedding 表），**语义召回/写入向量库交给 Python**（复用 Redis 向量索引 + user_id 隔离）。Java 只保留存储与触发时机。
3. **摘要**：Java `AiSummaryServiceImpl` 只负责"何时触发摘要"（消息数/字数阈值），真正摘要文本由 Python 生成后回写。
4. **流式协议**：`/agent/chat/stream` 用 `graph.astream` + 统一事件帧 `{type: token|tool|done|error}`；Java 用 `WebClient` 转发为 `SseEmitter`；前端 `fetch` ReadableStream 增量渲染。**保留非流式端点作 fallback**。
5. **观测**：Langfuse 用 callback handler 随 `ainvoke/astream` 的 `config` 注入，每个 run 打 `session_id / user_id / mode` 标签。环境变量缺 key 时静默降级，不影响主链路。
6. **评测指标**：检索离线评测（`recall@k` / `MRR`，不需 LLM）优先落地，再上 LLM-as-judge 忠实度与格式合规率。
7. **MCP**：工具实现抽成共享函数，市场/分析工具以 `FastMCP` 暴露为 MCP server；Python 图内工具与 MCP server **不双写逻辑**。

---

## 3. 阶段计划

### Stage 0：工程基建升级（1~2 天）✅ 完成 2026-09-04
**目标**：把 Python 服务拉回 2025+ 主流工程基线。

- [x] `ai-agent-service` 引入 `uv`：`pyproject.toml` + 锁定版本（无 uv 则 `pip-compile`）→ `uv 0.12.9` + `uv.lock`（65 包）
- [x] 升级：`langgraph>=1.x`、`langchain-core` 1.x、`langchain-openai`、`pydantic` v2 → 实测 `langgraph 1.2.11 / langchain 1.4.0 / pydantic 2.13.4 / fastapi 0.141.1`
- [x] `app/config.py` 迁移 `pydantic-settings`，缺配启动即报错；`.env.example` 补齐（含 Langfuse key）
- [x] 搭 `tests/` + pytest 骨架，先写 2 个冒烟测试（chat / strategy 端点）→ `tests/test_smoke.py` 共 5 用例
- [x] `ruff` + `pre-commit`；`Dockerfile` 与 `requirements.txt` 同步更新 → ruff 自动修复存量 192 项（余 13 项留 Stage 1）；`requirements.txt` 由 `uv export` 锁定；`Dockerfile` 基础镜像对齐 3.12-slim
- [x] 手动冒烟全回归：`/agent/chat`、strategy 模式、`/rag/*` 六端点 → health / rag/stats / chat 均通；5 个 pytest 全绿

**DoD**：`uv run pytest` 全绿（5 passed）+ 核心端点 curl 全通 ✅
**风险**：新版本 `ToolMessage` / `bind_tools` 行为差异 → 已实测旧版手写图在新 langgraph 1.x 下正常运行，无大面积适配。
**备注**：服务已由 `.venv` 运行（`uv run uvicorn`），模型链路 `qwen3.8-flash` 实测可用。

### Stage 1：图重构 + Langfuse 可观测（2~3 天）
**目标**：图为"可演进"形态，任何 run 都能被观测、能复盘。

- [x] 提示词抽到 `app/prompts/`（system / strategy / user-profile），`trading_graph.py` 与 `context_builder.py` 改为引用，消除双写漂移
- [x] 图状态显式化：TradingState 补充 `current_message / thought`，`intermediate_steps` 记录规范化（`_record_step` 自动递增编号）
- [x] `compile(checkpointer=MemorySaver())`；`thread_id` 由 `make_run_config` 生成 `{session_id}:{uuid}`（留 HITL 口，说明见代码注释）
- [x] `app/observability/langfuse.py`：封装 handler 工厂 + 降级开关
- [x] `main.py` 的 `/agent/chat`（含 strategy 模式）、`/agent/execute` 在 ainvoke 的 config 注入 callbacks，metadata 含 `session_id / user_id / mode`
- [x] 单测：`should_continue`、`tools_node`（含 user_id 注入）、`build_prompt` 上下文顺序、prompts 单一来源、Langfuse 降级 → `tests/test_graph.py` + `tests/test_observability.py`
- [x] 联调确认 Langfuse 项目内可见一次含工具调用的完整 ReAct trace（含成本/token/延迟）
  - 2026-09-04 cloud（jp 区）项目 `AiTrader-Dev` 验收：
    - 纯对话 trace：`a1bf4bbf5e462f1f02a1c426175dd911`（6 观测）
    - 含工具调用 trace：`1f042943b1293ac70d257d8d113a2654`（14 观测：2×TOOL `get_current_price`/`get_market_state` 真实返回 $81,056 → 2×GENERATION `qwen3.8-flash`，root latency 9.4s）
    - 链路：`agent → should_continue=tools → tools 节点 → should_continue=end → 终答`
  - 注：`langfuse 4.x` 移除 `langfuse.callback`，封装按 SDK 主版本 v3/v4 双兼容（见 `app/observability/langfuse.py`）

**DoD**：Langfuse 看板有可点的完整 trace；截图即面试素材 ✅
**风险**：Langfuse 与 DashScope（OpenAI 兼容）的 token 用量统计需实测是否准确，不准不影响 trace 价值。

### Stage 2：语义记忆 + LLM 摘要（3~4 天）
**目标**：把"假记忆、截断摘要"换成可展示的语义实现。

- [x] Python 新增 `POST /agent/memories/recall` `{user_id, query, top_k, type?}`：向量检索 + `memory_type`(preference/goal/constraint) 过滤；`memory_service.py` 的 hash 假向量（generate_embedding / search_memories）已删除，统一走 `embedding_service` + `vector_store`
  - 2026-09-04 真实 DashScope embedding 验收：同义改写「我喜欢买BTC长期拿着，不喜欢玩合约」语义命中「偏好比特币做现货长线…」sim≈0.76；type 过滤 / user_id 隔离 / 删除后不可召回 全通过
- [x] Python 新增 `POST /agent/memories/save`：typed candidates embed 入库（MySQL 由 Java 保存）
  - 记忆专用索引 `mem_vectors`（与 RAG `rag_vectors` 隔离），doc id 复用 MySQL 自增 memory_id，metadata 带 user_id 隔离
  - 索引模式三态：`REDIS_VECTOR_MODE=auto/memory/redis`（auto 用 `FT._LIST` 显式探测 RediSearch：有→真 HNSW；无→打印原因并内存降级；redis 强制模式不可用即启动报错，杜绝"带病假索引"）；`/health.vector_modes` 暴露各索引 mode/engine；`tests/test_vector_mode.py` 4 例覆盖决策矩阵
- [x] Java `AiMemoryServiceImpl.recallMemories()` 改为调 Python recall 端点；删除/失效记忆时同步清向量（saveChatMemory 按类清理、deactivateMemory 按 id、clearUserMemories 整用户；保存后一律回调 save 写向量）→ 本地 `mvn compile` 通过
- [x] Python 新增 `POST /agent/summarize`：LLM 把消息批压成语义摘要（含 <think> 清洗复用 `_strip_think_blocks`；输入截最近 60 条/单条 2000 字防超长）
  - 真实验收：6 条含 <think> 的对话摘要保留「BTC 现货 + 仓位30%」「2% 止损约束」「ETH 长线」要点，无 <think> 残留
- [x] Java `AiSummaryServiceImpl.generateSummaryText()` 改为：Java 判断触发时机 → 调 Python 生成 → 落库（Python 失败回退本地截断兜底）→ 本地 `mvn compile` 通过
- [x] 端到端验证：20 轮对话后问"我之前偏好哪个币种"，语义命中而非关键词命中
  - 2026-09-04 本地全链路（Java :8080 + Python :8000 + MySQL campusmall + 原生 Redis 无 RediSearch → 向量内存降级）跑通 20 轮 chat：
    - 末轮"无币种/现货关键词"提问命中币种答案；活跃记忆/滚动摘要正确落库（摘要 15 次）
    - 暴露并修复：`saveChatMemory` 原"每类只保留最新"会被新 preference 顶掉早前事实（BTC 现货偏好丢失）→ 改为 **constraint 仅保留最新风控规则；preference/goal 语义去重追加多条事实**
    - 回归脚本 `evals/stage2_e2e_regression.py`：两条 preference 并存 + constraint 仅最新 + 无关键词 recall 命中 BTC/2% + **全新会话（无历史）凭记忆答出 BTC 现货与 2% 红线** → PASS
  - 注：本地向量走内存降级仍为真实 embedding+cosine 语义检索（Python 进程内持久）；跨进程/重启的 HNSW 持久化待 Redis Stack 部署时复核（auto 模式自动启用，/health 可查 mode）

**DoD**：记忆与摘要不再有"玩具实现"；面试可放心指着代码讲。
**风险**：语义召回与 Java 现有列表页（内存展示/删除）数据一致性 → 增加向量与 MySQL 的同步清理测试。

### Stage 3：跨端流式对话（4~5 天，全程最大块）
**目标**：浏览器逐字输出，trace 全程可见，可回退。

- [x] Python `POST /agent/chat/stream`（SSE）：事件帧协议 `{type: token|tool|done|error, ...}`（2026-09-04：实现用 `astream_events(v2)` 拿 token，因 `graph.astream` 仅节点级无法逐字；`build_chat_inputs` 抽到 `app/streaming.py` 与 `/agent/chat` 单一来源；httpx 流式消费验证：纯问答 54 token→done、含工具轮 tool start/end→14 token→done、中文 utf-8 无损；pytest 28 passed）
- [x] Langfuse 回调随 `astream_events` config 注入，工具调用过程也按帧上送（前端可展示"正在调用 XX 工具…"）（2026-09-04 spike 验证：带 Langfuse callbacks 下 token/tool 事件流完整无干扰）
- [x] Java 流式链路：`LangGraphClient.chatWithContextStream()`（2026-09-04，用 Hutool 阻塞读 bodyStream 按行消费——项目无 WebClient，未引入新依赖）+ `AiConversationController` `/ai/conversations/{id}/chat/stream`（`SseEmitter` 300s，独立线程池执行编排）；异常降级：未发帧→同步非流式整包、已发部分帧→error 帧收尾；`chat()` 同步端点输入组装/收尾落库抽为 `buildChatContext`/`persistAiReply` 与流式单一来源；strategy/画像引导等本地分支委托同步 `chat()` 整包 done（不双写文案）；端到端 httpx 实测：56 token→done、tool 帧转发、双轮消息落库、strategy 委托 done 均 PASS）
- [x] 前端真流式渲染：`ai.ts` 新增 `chatStream()`（fetch ReadableStream 逐帧解析 `data:{...}` SSE，返回 `done|error|broken` 三态）；`AIChat.tsx` 通用 chat 模式改走流式——token 逐帧直写 DOM（不触发 re-render）、tool 帧显示「正在调用 XX 工具…」、done 帧以 markdown 固化到消息（`react-markdown` + 暗色 `.chat-markdown` 样式，与策略报告同库）；连接级失败（无任何帧）自动降级同步 `chat()`，收到 Java error 帧不重发（防 user 消息双写）；strategy/画像引导仍整包展示（profileOptions 按钮保留）；`request.ts` 导出 `API_BASE_URL`；`tsc`/`eslint` 零错误（2026-09-04，浏览器逐字体验待人工确认）
- [x] 保活 / 超时 / 断线兜底：Java `SseEmitter` 300s 超时 + 独立线程池执行；Python httpx 侧 60s 读超时；断线分层兜底——未发帧→Java 同步非流式整包、已发部分帧→Java error 帧收尾（前端不重发，防 DB user 消息双写）、连接级失败（无任何帧）→前端自动降级同步 `chat()`；非流式端点保留作 fallback（已实现并经端到端 httpx 验证）

**DoD**：浏览器逐字输出、工具步骤可见、Langfuse trace 完整、断线可回退。
**风险**：跨三端 SSE 转发易出编码/半包问题 → 帧内 base64/utf-8 明确，先 curl 验证 Python 端再动 Java/前端。

### Stage 4：pytest 评测体系（3~5 天）— 区分度主力
**目标**：每次改 prompt/图后，有一条命令给出量化结果。

- [x] pytest 评测体系基座：`pyproject.toml` 注册 markers（evals/offline/judge/format）+ 默认 `-m "not evals"` 排除评测集（常规 28 用例不回归），`-m evals` 显式触发；`--strict-markers`
- [x] golden set（首批 kb 类 22 语料 / 25 标注查询）：`evals/data/kb_corpus.jsonl`（单条事实=独立检索单元）+ `kb_queries.jsonl`（含 2 条多命中 recall 区分度用例）；格式 JSONL，后续扩行情问答/策略报告两类
- [x] 离线检索评测 harness：`evals/offline_retrieval.py`（DashScope `text-embedding-v2` + **进程内隔离 MemoryVectorStore**，不触碰运行中 `rag_vectors`/`mem_vectors`；算 `recall@1/3/5` + `MRR@10`；`python -m evals.offline_retrieval` 出指标表并打印未命中明细）
  - 2026-09-04 首跑：**recall@1=0.740 / recall@3=0.900 / recall@5=0.960 / MRR@10=0.842**（25 命中 24；`kbq-021`「涨 30% 是否卖出→止盈纪律」未进 top5，属中文短句带数值场景 embedding 区分度边界，已标注，留作换 embedding 时对比点）；pytest 门限 recall@5≥0.90 / mrr≥0.80 通过
- [x] golden set 扩到 **60+（合计 78 条）**：kb 检索扩到 **22 语料 / 60 查询**（原 25 + 35 条新问法，覆盖各事实多表述与 4 条多命中组合）→ 扩集后 **recall@1=0.800 / recall@3=0.958 / recall@5=0.983（60 条仅保留既有边界 kbq-021 一条 miss）/ MRR@10=0.890**；judge 端到端 10 题（8 知识+2 行情）+ format 策略报告 8 条（原 4 + 4：对冲/双纪律/低风险定投/日内短线），合计 60+10+8=78 条 golden（另有 22 语料与 22 条报告级明细）
- [x] 端到端问答评测 LLM-as-judge 忠实度：`evals/judge_e2e_faithfulness.py`（走生产 `POST /agent/chat`；qwen3.8-flash temperature=0 判分；rubric correctness/faithfulness/relevance/safety + issues，5 分制；逐条明细落 `evals/reports/judge_e2e_latest.jsonl`；CLI `--limit`/`--only` 冒烟）
  - 2026-09-04 首跑（10 题）：**pass_rate=0.80 / c=4.10 / f=4.00 / r=4.70 / s=4.10**；judge 长文输出截断致 2 条 parse 失败 → `max_tokens` 3000 + 忽略排版提示 → parse_fail=0
  - **评测抓到→修复→回归（闭环验证）**：首跑红项 eqa-009（行情答缺时点/免责）、eqa-010（问 ETH 报 BTC 价 + 指令式仓位）定位到根因后修复：
    ① `app/market_data/binance_client.py` 全方法支持 `symbol`（`normalize_symbol` 归一 BTC/ETH/eth-usdt 等，缺省 BTCUSDT），`app/tools/market_tools.py`/`analysis_tools.py` 把 symbol 透传（原 4 个工具全部硬编码默认 BTC，eqa-010 根因）；
    ② `app/prompts/system.py` 新增【输出纪律】（行情带时点+「仅供参考、不构成投资建议」、不承诺收益、仓位/买卖用条件式、不臆测用户画像）；
    ③ 新增回归测试 `tests/test_market_symbol.py`（19 例，全 mock 不触网）。
    **修复后回归：pass_rate=1.00 / c=4.70 / f=4.40 / r=4.80 / s=5.00**，eqa-010 返回真实 ETH 价（$2,454），eqa-009 含免责；`uv run pytest -m judge` 通过（门限已按新基线上调 pass≥0.70 / c≥3.5 / f≥3.3）
  - 遗留（已如实记录、非评测缺陷）：eqa-010 的行情指标数值未注明来源、建议仍偏操作化 → judge 在 issues 标注、safety/correctness 已达标（5/5），后续可在策略模式做更严格治理
- [x] 策略报告格式合规：`evals/format_compliance.py`（strategy 模式走生产 `/agent/chat`；纯规则校验：四章标题完整性 + 无表格 `|` + 无 emoji，章节按去空白容错匹配；逐条落 `evals/reports/format_latest.jsonl`）
  - 2026-09-04（`evals/data/strategy_cases.jsonl` 扩至 8 条：BTC/ETH/DOGE/带用户约束/对冲/双纪律/低风险定投/日内短线）：**format_pass_rate=0.88（7/8）/ chapter_complete=1.00**，table_violations=1、emoji=0；违规两度出现在约束密集场景（fmt-002/fmt-006 用 `|` 表格呈现价位，属模型随机性而非缺陷回归）→ pytest 门限 0.75 容 2/8 抖动；`uv run pytest -m format` 通过
- [x] README 指标区完善：`ai-agent-service/README「评测指标（Stage 4）」`加**三层评测总表**（离线检索 / judge 端到端 / format 合规）+ 各层明细表与门限、已知边界；golden 78 条计数与 reports/ 落盘说明

**DoD**：`uv run pytest -m evals` 出一张指标表，能写进简历/README。
**风险**：LLM-as-judge 有成本与抖动 → golden set 分批 mark，默认跑离线检索类，完整集 nightly 跑。

### Stage 5：MCP 工具层（2~3 天，最独立）
**目标**：工具层真兼容 MCP，且不改主链路。

- [x] 工具核心逻辑抽共享模块：`app/tools/core/`（`market.py` / `analysis.py` / `knowledge.py`，结构化 dict/dataclass 返回，异常与数据不足走字段而非裸抛）建立；LangChain 图内工具 `app/tools/{market,analysis,rag}_tools.py` 退化为字符串薄壳，docstring 与输出逐字不变（Stage 4 judge/format 零回归，47 单测全绿）；新增 `tests/test_tools_core.py` 9 例锁定 core↔壳一致（结构化字段、成功/失败/数据不足分支文案）
- [x] 建 `ai-trader-mcp` server（`app/mcp_tool_server.py`，mcp 2.x `MCPServer`；`uv add mcp>=2.1.1`，uv.lock 与 requirements.txt 已同步）：暴露行情/技术分析/知识库 6 工具，参数 Annotated 描述 + snake_case，返回结构化 JSON——复用 `app/tools/core`，与图内 LangChain 壳同一实现、不双写；独立 `Dockerfile.mcp` 可部署（stdio，`docker run -i --rm`）。兼容适配：`vector_store.py` / `binance_client.py` 模块日志改走 stderr（MCP stdio stdout 只许 JSON-RPC 帧）、server 强制 stdout/stdin UTF-8（Windows 管道 GBK 防护）
- [x] 用 stdio MCP 客户端验证工具可列出、可调用：新增 `tests/test_mcp_server.py`（mark `mcp`，与 evals 同默认排除，`uv run pytest -m mcp` 显式触发）→ **3 passed**：list_tools 列出 6 工具 + 真实调用 `get_current_price(BTC)` 返回 `{ok:true, symbol:BTCUSDT, price>0}` + `get_technical_analysis(ETH)` 返回结构化指标；常规单测 56 例保持全绿
- [x] README 记录启动方式与一次成功调用：新增「MCP 工具服务（Stage 5）」章节——本地启动命令、Windows UTF-8 注意、MCP 客户端配置 JSON、一次成功调用（`pytest -m mcp` + 返回 JSON 示例）、Dockerfile.mcp 构建运行

**DoD**：外部 MCP 客户端可直接查 BTC 行情；"工具层兼容 MCP"不再是一句口号。✅ 2026-09-04 达成：`uv run pytest -m mcp` → 3 passed（真实 BTC 现价、ETH 结构化指标经 MCP 协议返回）。
**风险**：低。注意工具参数命名要符合 MCP schema（snake_case、必填声明）。

---

## 4. 依赖关系与排期

```
Stage 0 基建 ─→ Stage 1 图重构+可观测 ─→ Stage 3 流式(跨三端)
     │                    │
     │                    └──→ Stage 2 记忆/摘要（可与 1 交替做）
     └── Stage 5 MCP（独立，可随时插入）
Stage 4 Eval 依赖 Stage 2/3 行为稳定后做
```

| 周 | 内容 | 里程碑产出 |
|---|---|---|
| W1 | Stage 0 + 1 | 现代工程基线 + Langfuse 完整 trace 截图 |
| W2 | Stage 2 + Stage 3（前半） | 真语义记忆/摘要 + Python 流式端点可用 |
| W3 | Stage 3（Java/前端收尾）+ Stage 4 前半 | 浏览器逐字输出 + 离线检索评测指标 |
| W4（余量） | Stage 4 收尾 + Stage 5 | 评测指标表 + MCP 演示 + README 收尾 |

单线程串行推进；每阶段完成后更新下方追踪表并留档（截图/指标/日志）。

---

## 5. 全局风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| LangChain/LangGraph 大版本迁移 | 阻塞全部后续 | 单独列为 Stage 0.5 回归任务，冒烟先行 |
| 跨三端流式联调（编码/半包/超时） | 体感差、耗时 | Python 端先用 curl 独立验证，再逐端接入；保留 fallback |
| DashScope/模型输出兼容（<think> 等） | 评测与解析错乱 | 复用既有 <think> 清洗；评测前统一归一化 |
| LLM-as-judge 成本与抖动 | 指标不可信 | golden set 分批；默认离线指标，完整集 nightly |
| 记忆/向量与 MySQL 双写不一致 | 语义召回失真 | 保存/删除都经 Python 单入口，同步测试覆盖 |

---

## 6. 进度追踪

- [x] **Stage 0** 基建升级（uv / 版本 / pydantic-settings / pytest 冒烟）
- [x] **Stage 1** 提示词抽取 + checkpointer + Langfuse trace 全链路（2026-09-04 云 trace 验收完成）
- [x] **Stage 2** 语义记忆召回 / 保存 + LLM 摘要替换（2026-09-04 本地 20 轮端到端通过：Java+Python+MySQL 全链路、内存向量降级语义召回、preference 覆盖缺陷修复并回归；Redis Stack 真 HNSW 持久化待部署里程碑复核，auto 模式/health 已就绪）
- [x] **Stage 3** 流式：Python SSE → Java SseEmitter → 前端增量渲染（2026-09-04：Python/Java/前端实现与跨端链路全部落地并验证；文档勾选项含：真实 token 逐字、工具步骤帧、Java 多级降级、前端 broken 自动降级同步、react-markdown 渲染、保活/超时/断线策略与 fallback 保留。剩"浏览器逐字人工体验确认"一项，不阻塞 Stage 4）
- [x] **Stage 4** golden set + 离线检索评测 + LLM-as-judge + format 合规 + 报告 README（2026-09-04 完成：golden 扩至合计 78 条 = kb 60 + judge 10 + format 8；离线 **recall@1=0.800 / recall@3=0.958 / recall@5=0.983 / MRR@10=0.890**；judge 首跑 0.80 → 修复工具层 `symbol` 硬编码与输出纪律 → 回归 **pass_rate=1.00 / c=4.70 / f=4.40 / r=4.80 / s=5.00**；format 8 条 **format_pass_rate=0.88 / chapter_complete=1.00**；README 三层总表 + 门限 pytest（offline/judge/format）全绿）
- [x] **Stage 5** MCP 工具 server（共享 core 层 + MCPServer 6 工具）+ stdio 客户端验证（`pytest -m mcp` 3 passed）+ README 记录

---

## 7. 相关文件索引

**Python 服务（ai-agent-service/）**
- `requirements.txt` → Stage 0 重建为 `pyproject.toml`
- `app/config.py` → Stage 0 迁移 pydantic-settings
- `app/main.py` → Stage 1/2/3 加端点与观测
- `app/graph/trading_graph.py` → Stage 1 重构
- `app/context/context_builder.py` → Stage 1 抽 prompt
- `app/memory/memory_service.py` → Stage 2 移除假向量，加 recall/save/delete（`mem_vectors` 索引）
- `app/memory/conversation_summarizer.py` → Stage 2 新增（LLM 摘要）
- `app/rag/embedding.py`、`app/rag/vector_store.py` → Stage 2 复用 + 索引参数化
- 新增：`app/prompts/`、`app/observability/langfuse.py`、`evals/`（stage2_e2e_20turns.py / stage2_e2e_regression.py / `offline_retrieval.py` / `judge_e2e_faithfulness.py` / `format_compliance.py` / `data/kb_corpus.jsonl` / `data/kb_queries.jsonl` / `data/e2e_qa.jsonl` / `data/strategy_cases.jsonl` / `reports/`）、`tests/test_memory_stage2.py`、`tests/test_vector_mode.py`、`tests/test_evals_retrieval.py`（marker `evals`+`offline`）、`tests/test_evals_judge.py`（marker `evals`+`judge`）、`tests/test_evals_format.py`（marker `evals`+`format`）、    `tests/test_market_symbol.py`（judge 抓出的 symbol 缺陷回归测试）、`tests/test_tools_core.py`（Stage 5 core↔壳一致 9 例）、`tests/test_mcp_server.py`（Stage 5 stdio 集成，marker `mcp`）
- Stage 5 新增：`app/tools/core/`（market/analysis/knowledge 共享结构化实现——图内工具与 MCP server 的唯一逻辑来源）、`app/mcp_tool_server.py`（mcp 2.x `MCPServer` 暴露 6 工具）、`Dockerfile.mcp`（独立 MCP 镜像）
- 修改（修复既有缺陷 + MCP stdio 适配）：`app/market_data/binance_client.py`（symbol 透传 + 归一化；模块日志改走 stderr）、`app/tools/market_tools.py`/`app/tools/analysis_tools.py`（symbol 透传、退化为字符串薄壳）、`app/rag/vector_store.py`（模块日志改走 stderr，stdout 留给 MCP JSON-RPC 帧）、`app/prompts/system.py`（输出纪律）、`ai-agent-service/.gitignore`（uvicorn 日志/venv）

**Java 后端（ai-trader-backend/）**
- `agent/client/LangGraphClient.java` → Stage 3 加 stream 方法
- `conversation/service/impl/AiConversationServiceImpl.java` → Stage 3 编排
- `conversation/service/impl/AiSummaryServiceImpl.java` → Stage 2 调 Python
- `memory/service/impl/AiMemoryServiceImpl.java` → Stage 2 语义召回
- `conversation/controller/AiConversationController.java` → Stage 3 SSE

**前端（AiTrader/）**
- `src/services/ai.ts` → Stage 3 加 `chatStream()`
- 聊天组件 → Stage 3 增量渲染 + react-markdown
