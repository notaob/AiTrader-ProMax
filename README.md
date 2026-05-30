# AiTrader

AiTrader 是一个 AI 智能交易辅助平台，面向投资场景提供实时行情、策略分析、AI 对话、知识检索等能力。

## 项目架构

```
┌─────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  React Web  │────▶│  Spring Boot    │────▶│  FastAPI Agent  │
│  (Vite)     │     │  (业务后端)      │     │  (LangGraph)    │
└─────────────┘     └─────────────────┘     └─────────────────┘
                            │                        │
                            ▼                        ▼
                      ┌─────────────┐         ┌─────────────┐
                      │  MySQL      │         │  DashScope  │
                      │  Redis      │         │  (LLM API)  │
                      │  RabbitMQ   │         └─────────────┘
                      └─────────────┘
```

## 核心能力

### AI 对话与上下文管理
- 多轮会话管理，支持会话状态、历史摘要、长期记忆
- Context Builder 统一组装 Prompt 上下文
- ReAct Agent 工作流，支持工具调用与策略报告生成

### RAG 知识检索
- 知识文档上传与分片管理
- DashScope Embedding + 向量检索
- 知识片段注入模型上下文

### 实时行情
- WebSocket 实时价格推送
- K 线图表与技术指标分析
- AI 策略建议生成

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | React, TypeScript, Vite, TanStack Query |
| 后端 | Spring Boot, MyBatis Plus, MySQL, Redis, RabbitMQ |
| AI | FastAPI, LangGraph, DashScope |

## 快速启动

```bash
# 前端
cd AiTrader && npm install && npm run dev

# 后端
cd ai-trader-backend && mvn spring-boot:run

# AI Agent
cd ai-agent-service && pip install -r requirements.txt && python -m app.main
```

## 项目结构

```
AiTrader/
├── AiTrader/              # 前端 (React)
├── ai-trader-backend/     # 后端 (Spring Boot)
└── ai-agent-service/      # AI Agent (FastAPI + LangGraph)
```

## 说明

个人独立开发项目，涵盖前端、后端、AI Agent 全链路。
