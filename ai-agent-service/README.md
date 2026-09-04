# AI Agent Service (LangGraph)

基于 LangGraph 的 AI Agent 服务，复用 Java 后端能力。

## 架构

```
前端 (React) → Java 后端 (campusMall) → Python Agent (LangGraph)
                    ↓                           ↓
                 MySQL/Redis ←────────────── 复用 Redis
```

## 复用的配置

- **LLM**: 阿里云 DashScope (deepseek-v4-pro)
- **Redis**: 127.0.0.1:6379 (与 Java 项目共享)
- **Java 后端**: http://localhost:8080

## 启动

### 1. 安装依赖

```bash
cd ai-agent-service
pip install -r requirements.txt
```

### 2. 配置环境变量

编辑 `.env` 文件（已配置好，复用 Java 项目配置）：

```env
DASHSCOPE_API_KEY=your_dashscope_api_key_here
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=123456
JAVA_BACKEND_URL=http://localhost:8080
```

### 3. 启动服务

```bash
python -m app.main
```

或

```bash
uvicorn app.main:app --reload --port 8000
```

## API 接口

### ReAct 对话

```bash
POST /agent/chat
{
  "message": "BTC现在价格多少？",
  "user_id": "123",
  "session_id": "session_001",
  "history": []
}
```

### RAG 查询

```bash
POST /agent/rag
{
  "question": "什么是比特币？",
  "user_id": "123",
  "top_k": 5
}
```

### 健康检查

```bash
GET /health
```

## Docker 部署

```bash
docker build -t ai-agent-service .
docker run -p 8000:8000 --env-file .env ai-agent-service
```

MCP 工具服务镜像（见下节）：

```bash
docker build -f Dockerfile.mcp -t ai-trader-mcp .
docker run -i --rm ai-trader-mcp   # stdio 会话，供 MCP 客户端 spawn
```

## MCP 工具服务（Stage 5）

`app/mcp_tool_server.py` 以 MCP（stdio transport）暴露与 LangGraph 图内
**同一批** `app/tools/core` 工具——逻辑唯一实现，不双写；LangChain 壳输出
文本、MCP 端返回结构化 JSON（价格/指标/命中直接机器可读）。

暴露 6 个工具（参数 snake_case、描述注入 schema）：

| 工具 | 说明 |
|---|---|
| `get_current_price` | 当前实时价格（symbol：BTC/ETH/DOGE 或 BTCUSDT，USDT 计价） |
| `get_market_state` | 24h 市场状态：现价/涨跌幅/高低价/成交量 |
| `get_technical_analysis` | MA7/MA30/RSI(14)/趋势/支撑位/阻力位 |
| `get_trading_suggestion` | 技术面交易建议（金叉/死叉、RSI、买卖信号列表） |
| `search_knowledge` | 语义检索用户知识库（交易策略/概念/笔记） |
| `add_to_knowledge_base` | 文本切分写入知识库 |

### 本地启动

```bash
cd ai-agent-service
uv sync
uv run python -m app.mcp_tool_server   # stdio：等待 MCP 客户端连接
```

> Windows 注意：MCP stdio 协议要求 UTF-8。客户端 spawn 时请带 `-X utf8`
> （或环境变量 `PYTHONUTF8=1`）；server 内部也已强制 stdout/stdin 为 UTF-8。

### MCP 客户端配置示例

```json
{
  "mcpServers": {
    "ai-trader-mcp": {
      "command": "D:/AiTrader/AiTrader/ai-agent-service/.venv/Scripts/python.exe",
      "args": ["-X", "utf8", "-m", "app.mcp_tool_server"],
      "cwd": "D:/AiTrader/AiTrader/ai-agent-service"
    }
  }
}
```

### 一次成功调用（真实 BTC 行情）

```bash
cd ai-agent-service
uv run pytest -m mcp -q        # 3 passed：list_tools 6 工具 + BTC 实时价 + ETH 结构化技术指标
```

`get_current_price` 经 MCP 返回（示例形态）：

```json
{"ok": true, "requested": "BTC", "symbol": "BTCUSDT", "price": 68123.45}
```

工具核心抽共享层、MCP server、客户端验证的完整说明见 `AI_PHASE3_LANGGRAPH_AGENT_EVAL.md` Stage 5。

## LangGraph 工作流

```
用户输入
    ↓
Agent 节点 (思考)
    ↓
是否需要工具？
    ├─ 是 → 工具节点 (执行) → 返回 Agent 节点
    └─ 否 → 结束
    ↓
返回最终答案
```

## 工具列表

- `get_current_price` - 获取当前价格
- `get_technical_analysis` - 技术分析
- `check_alerts` - 检查预警
- `get_news` - 获取新闻
- `query_knowledge_base` - 查询知识库
- `add_to_knowledge_base` - 添加知识

## 评测指标（Stage 4）

三层评测：**离线检索**（embedding 召回质量，无需 LLM）→ **LLM-as-judge**（端到端
忠实度，成本高）→ **format 合规**（纯规则，零成本）。golden 共 78 条用例
（kb 检索 60 + judge 端到端 10 + format 策略报告 8）。

| 评测层 | 数据集/门限 | 指标（2026-09-04） |
|---|---|---|
| 离线检索 recall@k / MRR | kb 22 语料 / 60 查询 | recall@1=0.800 / recall@3=0.958 / recall@5=0.983 / MRR@10=0.890（门限 recall@5≥0.90 / mrr≥0.80 ✅） |
| judge 端到端（10 题 = 8 知识 + 2 行情） | pass_rate / 四维 5 分制 | 首跑 0.80 → 修复 symbol 缺陷+输出纪律后 **pass_rate=1.00**；c/f/r/s = 4.70/4.40/4.80/5.00（门限 pass≥0.70 / c≥3.5 / f≥3.3 ✅） |
| format 合规（8 条策略报告） | 四章完整 + 无表格 + 无 emoji | format_pass_rate=0.88（7/8）；chapter_complete=1.00（门限 0.75 ✅） |

明细见下方三节；逐条 JSONL 落 `evals/reports/`。

### 离线检索评测

固定知识库语料（`evals/data/kb_corpus.jsonl`）对标注查询集
（`evals/data/kb_queries.jsonl`）的语义召回质量，无需 LLM，可作每次 prompt /
图改动后的回归基线。

| 指标 | 值（2026-09-04，扩集 60 查询） |
|---|---|
| recall@1 | 0.800 |
| recall@3 | 0.958 |
| recall@5 | 0.983 |
| MRR@10 | 0.890 |

- 语料 22 条 / 查询 60 条（含 4 条多命中用例）；embedding = DashScope
  `text-embedding-v2`（1536 维）。
- 已知边界：`kbq-021`（涨 30% 是否卖出 → 止盈纪律）未进 top5，属中文短句带数值
  场景下该 embedding 的区分度局限（25 条 → 60 条规模下仍为唯一 miss），留作换
  embedding 时对比点。
- 运行：

```bash
# 命令行出指标表（含未命中明细）
uv run python -m evals.offline_retrieval

# pytest 入口
uv run pytest -m offline
```

### LLM-as-judge 端到端忠实度（nightly，需 :8000 在线）

走生产 `POST /agent/chat`，由 qwen3.8-flash（temperature=0）按
correctness / faithfulness / relevance / safety 四维 + issues 判分。

| 指标 | 值（2026-09-04，10 题 = 8 知识 + 2 行情；首跑 → 修复后） |
|---|---|
| pass_rate | 0.80 → **1.00** |
| correctness / faithfulness / relevance / safety | 4.10/4.00/4.70/4.10 → **4.70/4.40/4.80/5.00** |

- 数据集：`evals/data/e2e_qa.jsonl`；逐条明细落 `evals/reports/judge_e2e_latest.jsonl`。
- 评测驱动的一次真实修复闭环：首跑红项定位到 ①行情工具/`BinanceClient` **忽略 symbol 参数、
  一律取默认 BTC**（问 ETH 报 BTC 价，`eqa-010` 根因）；②行情回答缺时点与免责。修复：
  client 全方法支持 `symbol` + 归一化、`market_tools`/`analysis_tools` 透传（新增
  `tests/test_market_symbol.py` 19 例回归）、`app/prompts/system.py` 增补【输出纪律】。
  回归后 10/10 全绿、ETH 返回真实价格（$2,454）、行情答均带免责声明。
- 运行：

```bash
uv run python -m evals.judge_e2e_faithfulness            # 全量（约 6 分钟）
uv run python -m evals.judge_e2e_faithfulness --limit 2  # 冒烟
uv run pytest -m judge                                   # pytest 入口
```

### 策略报告 format 合规（nightly，需 :8000 在线）

strategy 模式产物做纯规则校验（章节完整性 + 无表格 `|` + 无 emoji），不花 LLM judge。

| 指标 | 值（2026-09-04，8 条策略报告） |
|---|---|
| format_pass_rate | 0.88（7/8） |
| chapter_complete | 1.00 |
| table_violations / emoji_violations | 1 / 0 |

- 数据集：`evals/data/strategy_cases.jsonl`；逐条明细落 `evals/reports/format_latest.jsonl`。
- 已知边界：约束密集场景（如 fmt-006 同时携带止损/止盈纪律）模型偶发用 `|` 表格
  呈现价位，属格式偏好的模型随机性，非缺陷回归（pytest 门限 0.75 容 2/8 抖动）。
- 运行：

```bash
uv run python -m evals.format_compliance            # 全量
uv run pytest -m format                             # pytest 入口
```

说明：评测默认被排除在常规测试外（`pytest` 仅跑单元/冒烟；`-m evals` 全量，`-m offline`/`-m judge`/`-m format` 分项触发）。完整计划与阶段留档见 `AI_PHASE3_LANGGRAPH_AGENT_EVAL.md`。
