# AiTrader

AiTrader 是一个面向加密货币投资场景的 AI 智能交易辅助平台，围绕实时行情、策略分析、AI 对话、上下文管理、知识检索、社交互动和用户权益体系构建完整产品闭环，当前主要提供 Web 端能力。

项目采用三服务拆分架构：

- 前端应用：`React + TypeScript + Vite`
- 业务后端：`Spring Boot + MyBatis + MySQL + Redis + RabbitMQ`
- AI Agent：`FastAPI + LangGraph + LangChain + DashScope`

## 项目亮点

- 支持普通问答和策略报告双模式 AI 交互
- 实现 `会话 + 消息 + Session State + 摘要 + 长期记忆 + 知识库` 的上下文管理链路
- 基于 LangGraph 搭建 ReAct Agent，支持工具调用与多轮推理
- 集成知识文档上传、文档切片、语义检索和上下文增强生成
- 提供实时行情、K 线图表、动态社交、积分兑换 AI 次数、KYC 和安全中心等业务能力
- 当前主要支持 Web 端访问

## 系统架构

```text
React Web
        |
        v
Spring Boot API Gateway
        |
        +--> MySQL / Redis / RabbitMQ
        |
        v
FastAPI + LangGraph Agent
        |
        +--> DashScope LLM / Embedding
        +--> RAG / Context Builder / Memory Extraction
```

## 目录结构

```text
AiTrader/
├── AiTrader/            # 前端应用（React + TypeScript + Vite）
├── ai-trader-backend/   # 业务后端（Spring Boot）
├── ai-agent-service/    # AI Agent 服务（FastAPI + LangGraph）
├── resume.md            # Markdown 简历
├── resume.html          # HTML 简历
└── README.md            # 项目说明
```

## 核心能力

### 1. AI 对话与上下文管理

- 通过 `conversationId` 管理用户会话
- 保存原始消息、会话状态与历史摘要
- 支持长期记忆和知识片段接入模型上下文
- 提供上下文日志，便于排查回答质量

上下文组装顺序如下：

1. `session_state`
2. 最近消息
3. 历史摘要
4. 长期记忆
5. 知识片段
6. 当前用户输入

### 2. ReAct Agent 工作流

AI Agent 基于 LangGraph 构建，采用“思考 -> 行动 -> 观察”循环。

支持能力包括：

- 获取当前价格
- 查询市场状态
- 技术分析
- 生成交易建议
- 查询知识库
- 添加知识到知识库

### 3. RAG 与知识管理

- 支持知识文档上传与管理
- 支持文档切片、Embedding 与语义召回
- 可将知识片段作为上下文注入到 Agent
- 前端支持查看知识文档和切片内容

### 4. 用户系统与业务闭环

- 登录注册、密码重置、JWT 鉴权
- Token 黑名单、KYC、安全中心
- 动态发布、点赞互动
- 新手礼包、积分兑换 AI 次数
- 用户中心、头像上传与资料维护

### 5. 实时行情与图表

- WebSocket 实时价格推送
- REST 历史数据兜底
- Recharts 展示行情走势与图表
- 结合技术指标与 AI 策略分析输出交易建议

## 技术栈

### 前端

- React 19
- TypeScript 5
- Vite
- TanStack Query
- React Router 7
- Recharts
- Capacitor

### 后端

- Spring Boot 3.5
- MyBatis / MyBatis Plus
- MySQL
- Redis
- RabbitMQ
- JWT

### AI 与数据

- FastAPI
- LangGraph
- LangChain
- DashScope
- RAG
- Context Builder
- Memory Extraction

## 已落地的数据模型

当前项目围绕 AI 上下文管理已设计并实现以下核心实体：

- `AiConversation`
- `AiMessage`
- `AiSessionState`
- `AiConversationSummary`
- `AiUserMemory`
- `AiKnowledgeDoc`
- `AiKnowledgeChunk`
- `AiContextLog`

这套模型支撑了从单轮问答到多轮上下文管理、长期记忆与统一知识检索的完整链路。

## 前端亮点

- `AIChat` 支持会话恢复、打字机输出、策略模式切换
- 支持策略报告卡片化跳转展示
- 支持记忆与知识侧边栏查看
- 使用 TypeScript 严格模式和模块化服务层组织业务逻辑

## 后端亮点

- Spring Boot 提供统一会话与业务 API
- 通过 `AiConversationController`、`AiMemoryController`、`AiKnowledgeController` 暴露 AI 相关接口
- 使用 `AiContextBuilderService` 统一构建上下文
- 支持 Redis 缓存、JWT 鉴权和消息异步处理

## AI Agent 亮点

- 基于 LangGraph 管理 Agent 节点和工具节点
- 支持普通问答与策略报告双模式
- 支持 state、summaries、memories、knowledge_chunks 的统一上下文输入
- 支持从对话中提取长期记忆候选

## 本地启动

### 1. 启动前端

```bash
cd AiTrader
npm install
npm run dev
```

### 2. 启动业务后端

```bash
cd ai-trader-backend
mvn spring-boot:run
```

### 3. 启动 AI Agent 服务

```bash
cd ai-agent-service
pip install -r requirements.txt
python -m app.main
```

## 后续演进方向

- 完善上下文质量校验与自动纠错
- 增加更稳定的向量检索与重排序
- 扩展更多行情、新闻与策略分析工具
- 增加策略回测和资产管理能力
- 增强移动端体验与跨端统一会话能力

## 项目说明

该项目由个人独立完成主要产品设计、前端开发、后端开发、AI Agent 架构设计和部署联调，适合作为全栈开发、AI 应用开发、Agent/RAG 工程化方向的 Web 端作品展示。
