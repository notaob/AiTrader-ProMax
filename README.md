# AiTrader — AI 智能交易辅助平台

面向投资场景的 AI 交易辅助平台：**实时行情 + AI 对话 + 语义记忆 + 知识检索 + 策略报告**。
三端架构（React Web / Spring Boot / LangGraph Agent），并对外提供 **MCP 工具服务**，
任何支持 MCP 的客户端（CodeBuddy、Claude Desktop、Cursor…）都可以直接调用本项目的行情与分析工具。

> 当前仓库为完整 Phase 3 成果：流式对话、语义分层记忆、RAG、Langfuse 可观测、
> pytest 评测体系（golden 78 条）与 MCP 工具层均已落地并量化验证。

---

## 目录

- [架构总览](#架构总览)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [仓库结构](#仓库结构)
- [快速开始](#快速开始)
- [MCP 工具服务](#mcp-工具服务)
- [评测体系](#评测体系)
- [Docker 部署](#docker-部署)
- [配置与安全](#配置与安全)
- [里程碑与文档](#里程碑与文档)

---

## 架构总览

```
┌──────────────────┐    ┌────────────────────┐    ┌───────────────────────────┐
│  React Web (Vite)│───▶│  Spring Boot 后端    │───▶│  FastAPI Agent (LangGraph)│
│  TS + TanStack   │    │  (业务/会话/记忆存储) │    │  ReAct + 工具 + 评测       │
└──────────────────┘    └────────────────────┘    └───────────────────────────┘
         ▲                        │  ▲                       │            │
         │                        │  │                       │            │
         ▼                        ▼  │                       ▼            ▼
   /api 代理 (nginx)         ┌──────┴─────┐            ┌──────────┐  ┌───────────┐
                             │ MySQL      │            │ DashScope│  │ Binance   │
                             │ Redis      │            │ (LLM)    │  │ (行情)     │
                             │ RabbitMQ   │            └──────────┘  └───────────┘
                             └────────────┘                 │
                                                        ┌───┴───┐
                                                        │Langfuse│ (trace)
                                                        └───────┘
        ┌────────────────────────────────────────────────────────────────────┐
        │ ai-trader-mcp（stdio MCP Server）→ CodeBuddy / Cursor / Claude…    │
        │ 6 工具：行情 / 市场状态 / 技术分析 / 交易建议 / 知识库检索 / 知识写入  │
        └────────────────────────────────────────────────────────────────────┘
```

数据流要点：

- 前端经 `/api` 代理调用后端 REST / SSE；AI 对话支持 **流式**（token 逐字 + 工具调用帧）；
- Java 负责 MySQL 落库、会话/摘要/记忆的触发时机；Python 负责 LLM、语义检索与向量写入；
- 图内工具与 MCP server **共享同一份 `app/tools/core` 实现**，逻辑不双写；
- 配置一律走环境变量 + `.env`（全部入 `.gitignore`），见 [配置与安全](#配置与安全)。

## 功能特性

### AI 对话（跨三端流式）
- FastAPI `POST /agent/chat/stream`（SSE 事件帧 `token / tool / done / error`）→ Java `SseEmitter` → 前端逐字渲染；
- 工具调用过程实时展示（"正在调用 XX 工具…"）；断线三级降级（整包 / error 帧 / 前端自动转同步）。

### 语义分层记忆
- 长期记忆：Java 存 MySQL 原文，**语义召回与向量写入由 Python 单入口**完成（user_id 隔离）；
- LLM 摘要：超过阈值自动压缩历史，替代早期"截断 500 字"；
- 向量索引自动探测 Redis Stack（真 HNSW），不可用时内存降级，语义检索仍真实。

### RAG 知识检索
- 文档上传 → 分片 → DashScope `text-embedding-v2` → 向量库 → 命中注入 Prompt；
- 同一向量库服务会话记忆与知识库检索。

### 实时行情与分析
- Binance 真实行情：现价 / 24h 市场状态 / MA7·MA30·RSI(14) / 支撑阻力 / 交易建议（金叉死叉、超买超卖）；
- symbol 全方法透传与归一化（`BTC` / `BTCUSDT` / `eth-usdt` 均可）。

### 策略报告生成
- 四章结构（风险 / 策略 / 仓位 / 纪律）输出纪律化，pytest 格式合规门限守护。

### 可观测与评测
- Langfuse 全链路 trace（session / user / mode 标签，缺 key 静默降级）；
- pytest 评测体系三层量化（离线检索 recall@k / LLM-as-judge / format 合规），见[评测体系](#评测体系)。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | React 18, TypeScript, Vite, TanStack Query, react-markdown |
| 后端 | Spring Boot 3, MyBatis Plus, MySQL, Redis, RabbitMQ, SSE |
| AI Agent | Python 3.12, FastAPI, LangGraph 1.x, LangChain, DashScope, pydantic v2 |
| 工具与协议 | MCP (mcp 2.x, stdio), uv 工程, Redis 向量 (auto HNSW) |
| 观测 / 质量 | Langfuse, pytest (+markers), ruff, pre-commit, Docker |

## 仓库结构

```
.
├── AiTrader/                 # 前端 (React + Vite + TS)
│   └── src/{pages,components,services}
├── ai-trader-backend/        # 后端 (Spring Boot, Java 17, Maven)
│   └── src/main/{java,resources}   # mapper / schema.sql / application-example.yaml
├── ai-agent-service/         # AI Agent (FastAPI + LangGraph) + MCP server + 评测
│   ├── app/
│   │   ├── tools/core/       #   共享工具实现（图内壳与 MCP server 唯一来源）
│   │   ├── mcp_tool_server.py #  ai-trader-mcp（stdio, 6 工具）
│   │   ├── graph/            #   LangGraph 图 + checkpointer
│   │   ├── memory/  rag/     #   语义记忆 / 知识检索
│   │   └── observability/    #   Langfuse handler
│   ├── tests/                # 常规单测 56 例 + MCP 集成（mark `mcp`）
│   ├── evals/                # golden 数据 + 评测 harness（mark `evals`）
│   └── Dockerfile / Dockerfile.mcp
├── AI_PHASE3_LANGGRAPH_AGENT_EVAL.md   # Phase 3 完整计划与阶段留档
└── README.md
```

## 快速开始

### 前置

| 依赖 | 说明 |
|---|---|
| Node.js ≥ 18 + pnpm/npm | 前端 |
| JDK 17 + Maven | 后端 |
| Python 3.12 + [uv](https://docs.astral.sh/uv/) | Agent / MCP |
| MySQL / Redis | 后端与 Agent 共享（127.0.0.1:6379） |
| DashScope API Key | LLM 与 embedding 必填 |

### 1) AI Agent（先起，后端依赖它）

```bash
cd ai-agent-service

# 1. 环境变量：参考 .env.example 创建 .env（或直接导出下列变量）
#    DASHSCOPE_API_KEY / DASHSCOPE_BASE_URL / DASHSCOPE_MODEL
#    REDIS_HOST / REDIS_PORT / REDIS_PASSWORD / REDIS_DB

uv sync                  # 安装锁定依赖（uv.lock）
uv run python -m app.main   # http://localhost:8000
```

> 必填项缺失会启动即报错（pydantic-settings 校验）。Windows 下若访问 Binance 受限，在 `.env` 打开 `PROXY_ENABLE=true`。

### 2) Spring Boot 后端

```bash
cd ai-trader-backend

# 配置：复制模板（模板无任何真实密钥，已入库）并按需改
cp src/main/resources/application-example.yaml src/main/resources/application.yaml
# 或直接注入环境变量（模板中的 ${VAR:default} 均支持覆盖）

mvn spring-boot:run        # http://localhost:8080
```

> 需要本机 MySQL（库名 `campusmall`，可执行 `src/main/resources/schema.sql` / `schema-phase2.sql`）与 Redis。

### 3) 前端

```bash
cd AiTrader
npm install
npm run dev                # Vite dev server，/api 代理到 localhost:8080
```

### 4) 自检

```bash
cd ai-agent-service
uv run pytest -q          # 常规单测 56 例（评测集默认排除）
uv run pytest -m mcp      # MCP stdio 集成：真实 BTC/ETH 行情调用
```

浏览器打开前端 → 登录 → AI 对话：输入"分析一下 BTC 现在适不适合买入"，应看到逐字流式输出与工具调用帧。

## MCP 工具服务

`ai-agent-service/app/mcp_tool_server.py`（`ai-trader-mcp`，stdio）暴露与 LangGraph 图内
**同一批** `app/tools/core` 工具，返回结构化 JSON：

| 工具 | 说明 |
|---|---|
| `get_current_price(symbol)` | 实时价格（如 `BTC` / `BTCUSDT`） |
| `get_market_state(symbol)` | 24h 涨跌幅 / 高低价 / 成交量 |
| `get_technical_analysis(symbol)` | MA7 / MA30 / RSI(14) / 趋势 / 支撑 / 阻力 |
| `get_trading_suggestion(symbol)` | 金叉死叉、RSI、买卖信号列表 |
| `search_knowledge(query, user_id, top_k)` | 语义检索用户知识库 |
| `add_to_knowledge_base(text, source)` | 文本切分写入知识库 |

### CodeBuddy / 任意 MCP 客户端接入

在客户端 MCP 配置中新增（`command` 换成你机器的 `.venv` 绝对路径）：

```json
{
  "mcpServers": {
    "ai-trader-mcp": {
      "type": "stdio",
      "command": "D:/AiTrader/AiTrader/ai-agent-service/.venv/Scripts/python.exe",
      "args": ["-X", "utf8", "-m", "app.mcp_tool_server"],
      "env": {
        "PYTHONPATH": "D:/AiTrader/AiTrader/ai-agent-service",
        "PYTHONUTF8": "1"
      },
      "description": "AiTrader 行情/技术分析/交易建议/知识库检索（6 工具）"
    }
  }
}
```

接入后即可让 AI 直接："查一下 BTC 现价"、"ETH 技术面如何"、"我知识库里止损规则有哪些"。

### 命令行自测 / Docker 启动

```bash
# stdio 自测（spawn 客户端按协议对话；pytest 封装了完整链路）
uv run pytest -m mcp -q          # 3 passed：list_tools 6 工具 + BTC 实时价 + ETH 结构化指标

# 独立镜像（stdio 会话，供支持容器 MCP 的客户端 spawn）
docker build -f ai-agent-service/Dockerfile.mcp -t ai-trader-mcp ai-agent-service
docker run -i --rm ai-trader-mcp
```

返回示例：

```json
{"ok": true, "requested": "BTC", "symbol": "BTCUSDT", "price": 79111.11}
```

## 评测体系

golden 共 **78 条**（kb 检索 60 + judge 端到端 10 + format 策略报告 8），常规 pytest 默认排除评测集。

| 评测层 | 指标（2026-09-04） | 触发 |
|---|---|---|
| 离线检索 recall@k / MRR | recall@1=0.800 / @3=0.958 / @5=0.983 / MRR@10=0.890 | `uv run pytest -m offline` |
| LLM-as-judge 忠实度（10 题） | pass_rate=1.00；c/f/r/s = 4.70/4.40/4.80/5.00 | `uv run pytest -m judge` |
| 策略报告 format 合规（8 条） | format_pass_rate=0.88 / chapter_complete=1.00 | `uv run pytest -m format` |

评测曾真实抓出并驱动修复行情工具忽略 `symbol` 参数（问 ETH 报 BTC）的缺陷。明细见
`ai-agent-service/README.md`「评测指标（Stage 4）」与 `AI_PHASE3_LANGGRAPH_AGENT_EVAL.md`。

## Docker 部署

三个服务各自独立镜像，配置全部经环境变量注入（见各 `Dockerfile` 与 `application-example.yaml`）：

```bash
# 后端（构建期包含编译；运行期注入 MYSQL_*/REDIS_*/AI_API_KEY 等）
docker build -t aitrade-backend ai-trader-backend

# Agent（运行期注入 DASHSCOPE_API_KEY / REDIS_* 等）
docker build -t aitrade-agent ai-agent-service

# 前端（nginx 静态 + /api 反代）
docker build -t aitrade-web AiTrader

# MCP 工具 server（stdio）
docker build -f ai-agent-service/Dockerfile.mcp -t ai-trader-mcp ai-agent-service
```

## 配置与安全

所有密钥/本机配置均**不入库**，策略如下：

| 类别 | 处理 |
|---|---|
| Python 密钥 | `ai-agent-service/.env*` 全忽略；参考本地 `.env.example` 变量清单创建 |
| Java 配置 | `application*.yaml/.properties` 忽略；**模板** `application-example.yaml`（占位符）入库 |
| 前端 | `.env` / `.env.production` 等忽略 |
| 向量/评测产物 | `ai-agent-service/evals/reports/` 忽略（golden 数据 `evals/data/` 保留） |
| 部署残留 | `deploy*.tar.gz` / `*_log.txt` / 含服务器信息的临时脚本忽略 |
| 本机/IDE | `.codebuddy/` / `local.properties` / `.idea/` / `.vscode/` 忽略 |

> 部署服务器或第三方 API 密钥请使用 CI/CD Secrets 或环境变量注入，不要写入任何会被提交的文件。

## 里程碑与文档

Phase 0–5 全部完成（2026-09-04），详见各文档：

| 文档 | 内容 |
|---|---|
| `AI_PHASE3_LANGGRAPH_AGENT_EVAL.md` | Phase 3 完整计划：Stage 0 基建 → 5 MCP，含指标、风险与逐阶段留档 |
| `ai-agent-service/README.md` | Agent API、评测三层明细、MCP 章节、本地/容器启动 |
| `ai-trader-backend` / `AiTrader` | Spring Boot / 前端工程（各含 Dockerfile） |

里程碑速览：

- **Stage 0** 工程基建：uv 锁定依赖、langgraph/pydantic v2 升级、ruff+pre-commit、pytest 骨架
- **Stage 1** 图重构：提示词单一来源、checkpointer、Langfuse trace
- **Stage 2** 语义记忆：Python 单入口向量召回/写入、LLM 摘要替代截断（20 轮端到端通过）
- **Stage 3** 跨端流式：SSE 事件帧 → Java SseEmitter → 前端逐字 + 三级降级
- **Stage 4** 评测体系：78 golden + 离线/judge/format 三层指标（见上表）
- **Stage 5** MCP：共享 core 层 + `MCPServer` 6 工具 + stdio 客户端集成验证

---

> 个人独立开发项目。行情数据仅供研究参考，不构成投资建议。
