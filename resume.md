# 全栈工程师简历

## 个人信息

- **姓名**: 马凯鸣
- **电话**: 13711312104
- **邮箱**: 2543316230@qq.com
- **期望职位**: 全栈开发工程师 / 前端开发工程师 / AI 应用开发工程师 / 后端开发工程师
- **工作地点**: 不限

---

## 专业技能

### 前端技术栈
- **核心能力**: React、TypeScript、JavaScript (ES6+)、Vite、组件化开发、SPA 架构设计
- **状态与路由**: React Context、TanStack Query、React Router 7、Vue Router 4、Pinia
- **UI 与交互**: CSS Modules、Element Plus、表单交互、弹窗联动、路由保护
- **可视化**: Recharts、ECharts、实时行情图表、K 线图、复杂数据展示


### 后端技术栈
- **核心能力**: Spring Boot、Java、Maven、RESTful API 设计、分层架构开发
- **数据与持久层**: MyBatis Plus、MySQL、数据建模、CRUD 封装、事务控制
- **中间件**: Redis、RabbitMQ、缓存设计、异步消息处理
- **安全与通信**: JWT、拦截器鉴权、WebSocket、登录态管理、Token 黑名单

### AI 应用与大模型工程
- **LLM 集成**: DashScope、OpenAI 兼容接口、Prompt 编排、结构化输出
- **Agent 架构**: FastAPI、LangGraph、ReAct 模式、工具调用、多轮推理、策略报告生成
- **上下文管理**: 会话消息、Session State、历史摘要、长期记忆、Context Builder、Prompt 组装
- **RAG 能力**: 文档切片、Embedding、语义检索、知识库管理、检索增强生成

### AI 工具应用
- **工具使用**: Trae、Cursor、GitHub Copilot
- **应用场景**: 辅助完成架构设计、组件开发、API 对接、复杂问题排查、部署配置
- **协作方式**: 结合 AI 进行方案设计、代码实现、文档整理与问题定位

### DevOps & 工具
- **工程工具**: Git、Docker、Nginx、VS Code、IntelliJ IDEA、Postman
- **工程实践**: Dockerfile 编写、反向代理配置、代码规范治理、TypeScript 严格模式、ESLint

---

## 项目经验

### AiTrader - AI 智能交易辅助平台
**项目周期**: 独立开发，持续迭代  
**项目规模**: 独立全栈开发  
**项目链接**: [GitHub](https://github.com/notaob/AiTrader-Pro.git)

**项目简介**: 面向投资场景的 AI 智能分析与交易辅助平台，围绕实时行情、策略分析、AI 对话上下文管理、知识检索、社交互动和用户权益体系构建完整产品闭环。项目采用前端、业务后端、AI Agent 三服务拆分架构，当前主要支持 Web 端访问。

**技术架构**:
```
前端: React 19 + TypeScript + Vite + TanStack Query + React Router 7
业务后端: Spring Boot 3.5 + MyBatis/MyBatis Plus + MySQL + Redis + RabbitMQ + JWT
AI 服务: FastAPI + LangGraph + DashScope + Redis
行情与分析: WebSocket / REST + 技术指标计算 + RAG 检索
```

**核心功能与技术实现**:

#### 1. 平台架构与端到端链路
- **三层服务拆分**: 设计 `React 前端 + Spring Boot 业务后端 + FastAPI/LangGraph Agent` 三服务架构，Java 负责鉴权、会话与业务数据，Python 负责 Agent 推理、工具调用与 RAG，降低 AI 能力和业务服务的耦合度
- **统一会话入口**: 实现 `conversationId` 驱动的 AI 会话 API，前端不再手工拼历史，后端统一完成消息保存、状态读取、摘要加载和 Agent 调用
- **链路打通**: 完成前端 AIChat 组件、Java 会话接口、Python Agent 服务之间的联调，形成“用户输入 -> 上下文组装 -> Agent 推理 -> 回复回写”的完整调用闭环

#### 2. AI 对话上下文管理
- **会话持久化**: 设计并落地 `会话 / 消息 / Session State / 摘要` 数据模型，支持多轮对话恢复、状态查询和历史压缩
- **上下文组装**: 在后端实现 `Context Builder`，按 `状态 -> 最近消息 -> 摘要 -> 长期记忆 -> 知识片段` 的顺序组装 Prompt，并记录上下文日志便于排查回答质量
- **状态驱动对话**: 为策略分析等多轮任务设计 `current_mode / current_step / current_intent / state_json` 结构，避免模型仅依赖聊天记录推断当前进度
- **摘要压缩机制**: 在会话过长时生成历史摘要，保留最近消息原文并压缩旧上下文，降低 token 消耗并提升多轮对话稳定性
- **记忆与知识接入**: 完成用户长期记忆、知识文档、知识分片等接口与前端面板联动，支持记忆列表查看、知识文档上传和分片查询

#### 3. Agent 与 RAG 能力
- **ReAct Agent**: 基于 LangGraph 搭建 ReAct 工作流，通过“思考 -> 行动 -> 观察”循环实现多轮推理，支持普通问答和策略报告双模式
- **工具系统**: 封装行情查询、市场状态、技术分析、交易建议、知识检索等工具，支持模型根据问题自动选择工具
- **统一 Prompt 构建**: 在 Python 侧实现 `build_prompt`，将 state、recent_messages、summaries、memories、knowledge_chunks 统一编排为模型可消费的上下文
- **RAG 检索增强**: 实现文档切片、知识入库、语义检索和上下文增强，支持把知识片段与会话状态、历史摘要统一注入模型输入
- **记忆提取链路**: 从用户问题与 AI 回复中提取长期记忆候选，打通“对话 -> 候选记忆 -> 后端存储”的扩展链路，为后续跨会话能力预留基础

#### 4. 前端交互与工程化
- **AI 聊天体验**: 开发 `AIChat` 组件，支持会话恢复、打字机输出、通用模式/策略模式切换、策略报告卡片跳转
- **前端架构设计**: 基于 React 19 + TypeScript + Vite 构建单页应用，使用 React Context 管理认证状态，使用 TanStack Query 管理服务端状态、缓存与数据同步
- **记忆与知识侧边栏**: 设计前端 `MemoryPanel`、`KnowledgePanel`，可查看长期记忆、知识文档及分片内容
- **工程化与类型安全**: 使用 TypeScript 严格模式、React Context、TanStack Query 和模块化服务层组织复杂业务逻辑
- **服务层抽象**: 封装 `ai.ts`、`memory.ts`、`knowledge.ts`、`auth.ts`、`market.ts`、`moments.ts` 等服务模块，统一处理请求、鉴权与数据转换
- **页面与组件拆分**: 将首页、动态、个人中心、策略报告、认证弹窗等业务拆分为页面组件和复用组件，降低复杂交互带来的维护成本
- **交互与体验优化**: 实现 ProtectedRoute 路由保护、登录弹窗联动、策略报告卡片跳转、自动滚动和消息打字机效果，提升复杂业务场景下的交互体验

#### 5. 行情、用户与业务能力
- **实时行情**: 接入 WebSocket + REST 双通道行情数据，结合 K 线图、技术分析和轮询兜底提升稳定性
- **技术指标分析**: 基于市场数据输出 MA、RSI、支撑阻力位等分析信息，并将技术分析结果接入 AI 策略生成链路
- **用户体系**: 完成登录注册、JWT 鉴权、Token 黑名单、头像上传、KYC、安全中心等完整账户链路
- **业务闭环**: 实现动态社交、积分兑换 AI 次数、活动礼包、用户中心等功能，并结合 Redis、RabbitMQ 提升性能与异步处理能力
- **事务与一致性**: 在积分兑换、礼包领取等场景中使用事务控制，保证积分扣减、AI 次数增加等核心业务数据一致性
- **缓存与异步处理**: 使用 Redis 存储验证码、Token 黑名单和热点数据，使用 RabbitMQ 处理欢迎消息、积分通知等异步任务

**项目成果**:
- 独立完成产品设计、前后端开发、Agent 服务搭建与部署联调
- 将 AI 能力从单轮问答升级为具备会话、状态、摘要、记忆和知识接入的上下文系统
- 完成 Web 端从行情展示、AI 对话到用户体系和社交互动的完整闭环，具备持续迭代为投资分析类 AI 产品的基础架构
- 项目覆盖前端交互、后端接口、数据库设计、Agent 工作流、RAG 与工程化部署全链路

---

## 教育背景

**广东海洋大学 - 数据科学与大数据技术**  
本科 | 2023.09 - 2027.06  
**主要课程**: 计算机网络、Python 程序设计、Java 程序设计、大数据架构与技术

---

## 主要荣誉

| 奖项 | 级别 | 时间 |
|------|------|------|
| 第十七届蓝桥杯 Web 应用开发大学组 | 广东省一等奖 | 2026 |
| 第十六届蓝桥杯 Java 软件开发大学 B 组 | 广东省二等奖 | 2025 |
| 校级奖学金 | 校级 | 2025 |
| 英语四级 (CET-4) | 通过 | 2024 |

---
