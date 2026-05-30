# AI Agent Service (LangGraph)

基于 LangGraph 的 AI Agent 服务，复用 Java 后端能力。

## 架构

```
前端 (React) → Java 后端 (campusMall) → Python Agent (LangGraph)
                    ↓                           ↓
                 MySQL/Redis ←────────────── 复用 Redis
```

## 复用的配置

- **LLM**: 阿里云 DashScope (qwen3-max)
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
DASHSCOPE_API_KEY=sk-c610024b260b4b8ea52703af5c47d88d
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
