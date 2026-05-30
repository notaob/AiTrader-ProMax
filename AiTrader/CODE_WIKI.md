# AiTrader 项目文档

## 1. 项目概述

**AiTrader** 是一个基于 React + TypeScript + Vite 构建的加密货币交易助手 Web 应用，支持跨平台部署（通过 Capacitor）。

### 核心功能

| 功能模块 | 描述 |
|---------|------|
| **用户认证** | 手机号验证码登录、密码登录、注册、密码重置 |
| **实时行情** | 比特币实时价格（WebSocket）、K线图表展示 |
| **AI交易助手** | 智能交易策略分析、自然语言交互 |
| **特惠活动** | 新手礼包领取、积分兑换AI咨询机会 |
| **动态社交** | 发布市场分析、点赞互动、分享 |
| **身份认证** | KYC实名认证流程 |
| **安全中心** | 修改密码等安全设置 |

---

## 2. 技术架构

### 2.1 技术栈

| 分类 | 技术 | 版本 |
|-----|------|-----|
| 框架 | React | ^19.2.0 |
| 语言 | TypeScript | ~5.9.3 |
| 构建工具 | Vite | 7.2.5 |
| 路由 | React Router DOM | ^7.12.0 |
| 状态管理 | TanStack React Query | ^5.90.19 |
| 图标 | Lucide React | ^0.562.0 |
| 图表 | Recharts | ^3.6.0 |
| 跨平台 | Capacitor | ^8.0.0 |

### 2.2 项目结构

```
src/
├── assets/           # 静态资源（样式文件）
├── components/       # 可复用组件
├── context/          # React Context（全局状态）
├── hooks/            # 自定义 Hooks
├── pages/            # 页面组件
├── services/         # API 服务层
├── types/            # TypeScript 类型定义
├── App.tsx           # 根组件
└── main.tsx          # 应用入口
```

### 2.3 模块职责说明

| 模块 | 职责 | 关键文件 |
|-----|------|---------|
| **components** | UI组件复用 | `TradingChart.tsx`, `AIChat.tsx`, `BottomNav.tsx` |
| **pages** | 页面级组件 | `Home.tsx`, `Me.tsx`, `Moments.tsx` |
| **services** | 数据获取与API调用 | `auth.ts`, `crypto.ts`, `ai.ts`, `request.ts` |
| **context** | 全局状态管理 | `AuthContext.tsx` |
| **hooks** | 自定义逻辑封装 | `useInterval.ts` |
| **types** | 类型定义 | `index.ts` |

---

## 3. 核心模块详解

### 3.1 服务层 (services/)

#### 3.1.1 request.ts - 基础请求封装

**职责**：统一处理 HTTP 请求，自动添加 Token、处理响应格式。

**核心函数**：

```typescript
export async function request<T>(url: string, options: RequestOptions = {}): Promise<T>
```

**特性**：
- 自动拼接基础 URL (`/api`)
- 处理查询参数
- 自动添加 Authorization Token（从 localStorage）
- 统一响应格式处理（`code === 1` 表示成功）

**文件路径**：[src/services/request.ts](file:///d:/AiTrader/AiTrader/src/services/request.ts)

#### 3.1.2 auth.ts - 用户认证服务

**职责**：处理用户登录、注册、信息管理等认证相关操作。

**核心方法**：

| 方法名 | 功能 | 参数 | 返回值 |
|-------|------|------|-------|
| `sendCode` | 发送验证码 | `phone: string` | `Promise<string>` |
| `loginByCode` | 验证码登录 | `phone, code: string` | `Promise<LoginResponse>` |
| `loginByPassword` | 密码登录 | `phone, password: string` | `Promise<LoginResponse>` |
| `register` | 用户注册 | `phone, code, password, nickName: string` | `Promise<LoginResponse>` |
| `resetPassword` | 重置密码 | `phone, code, password: string` | `Promise<void>` |
| `getCurrentUser` | 获取当前用户信息 | 无 | `Promise<User>` |
| `logout` | 退出登录 | 无 | `Promise<string>` |
| `updateUserInfo` | 更新用户信息 | `nickName: string` | `Promise<User>` |
| `uploadAvatar` | 上传头像 | `formData: FormData` | `Promise<string>` |
| `changePassword` | 修改密码 | `oldPassword, newPassword: string` | `Promise<void>` |

**文件路径**：[src/services/auth.ts](file:///d:/AiTrader/AiTrader/src/services/auth.ts)

#### 3.1.3 crypto.ts - 加密货币数据服务

**职责**：从 Binance API 获取实时价格和历史 K 线数据。

**核心方法**：

| 方法名 | 功能 | 参数 | 返回值 |
|-------|------|------|-------|
| `getBtcPrice` | 获取比特币当前价格（REST） | 无 | `Promise<number>` |
| `subscribeToBtcPrice` | WebSocket 订阅实时价格 | `callback: (price: number) => void` | `() => void` (取消订阅) |
| `getBtcHistory` | 获取历史 K 线数据 | 无 | `Promise<ChartData[]>` |

**文件路径**：[src/services/crypto.ts](file:///d:/AiTrader/AiTrader/src/services/crypto.ts)

#### 3.1.4 ai.ts - AI 服务

**职责**：与 AI 交易助手后端交互。

**核心方法**：

| 方法名 | 功能 | 参数 | 返回值 |
|-------|------|------|-------|
| `chat` | 发送消息给 AI | 无 | `Promise<AIChatResponse>` |

**文件路径**：[src/services/ai.ts](file:///d:/AiTrader/AiTrader/src/services/ai.ts)

#### 3.1.5 market.ts - 市场活动服务

**职责**：处理特惠活动、礼包领取、积分兑换等业务。

**核心方法**：

| 方法名 | 功能 | 参数 | 返回值 |
|-------|------|------|-------|
| `getPromotions` | 获取活动列表 | 无 | `Promise<Promotion[]>` |
| `claimWelcomeGift` | 领取新手礼包 | 无 | `Promise<void>` |
| `exchangeAiChance` | 积分兑换 AI 机会 | `points: number` | `Promise<void>` |

**文件路径**：[src/services/market.ts](file:///d:/AiTrader/AiTrader/src/services/market.ts)

#### 3.1.6 moments.ts - 动态社交服务

**职责**：处理动态的发布、点赞、列表获取。

**核心方法**：

| 方法名 | 功能 | 参数 | 返回值 |
|-------|------|------|-------|
| `getList` | 获取动态列表 | 无 | `Promise<Post[]>` |
| `create` | 发布动态 | `content: string` | `Promise<Post>` |
| `toggleLike` | 点赞/取消点赞 | `id: number` | `Promise<{isLiked, likes}>` |

**文件路径**：[src/services/moments.ts](file:///d:/AiTrader/AiTrader/src/services/moments.ts)

---

### 3.2 上下文层 (context/)

#### AuthContext.tsx - 认证上下文

**职责**：管理全局用户认证状态，提供登录、登出、用户信息更新等能力。

**核心 API**：

| 属性/方法 | 类型 | 描述 |
|----------|------|------|
| `user` | `User \| null` | 当前登录用户信息 |
| `authModalView` | `'login' \| 'register' \| 'forgot-password' \| null` | 认证弹窗状态 |
| `openAuthModal` | `(view: AuthView) => void` | 打开认证弹窗 |
| `closeAuthModal` | `() => void` | 关闭认证弹窗 |
| `login` | `(phone, codeOrPassword, method) => Promise<void>` | 登录 |
| `logout` | `() => void` | 登出 |
| `updateUser` | `(updates: Partial<User>) => void` | 更新用户信息 |

**特性**：
- 自动从 localStorage 恢复登录状态
- App 初始化时自动刷新用户信息
- Token 过期自动登出

**文件路径**：[src/context/AuthContext.tsx](file:///d:/AiTrader/AiTrader/src/context/AuthContext.tsx)

---

### 3.3 组件层 (components/)

#### 3.3.1 TradingChart.tsx - K线图表组件

**职责**：展示比特币价格走势图表。

**Props**：

| 属性 | 类型 | 描述 |
|------|------|------|
| `data` | `ChartData[]` | K线数据数组 |

**依赖**：`recharts` 库

**文件路径**：[src/components/TradingChart.tsx](file:///d:/AiTrader/AiTrader/src/components/TradingChart.tsx)

#### 3.3.2 AIChat.tsx - AI 聊天组件

**职责**：提供与 AI 交易助手的交互界面，支持流式输出效果。

**特性**：
- 打字机效果输出
- 自动滚动到底部
- 策略报告卡片点击跳转

**文件路径**：[src/components/AIChat.tsx](file:///d:/AiTrader/AiTrader/src/components/AIChat.tsx)

#### 3.3.3 ProtectedRoute.tsx - 路由保护组件

**职责**：保护需要登录才能访问的页面。

**Props**：

| 属性 | 类型 | 描述 |
|------|------|------|
| `children` | `JSX.Element` | 受保护的子组件 |

**特性**：未登录时自动打开登录弹窗并重定向到首页

**文件路径**：[src/components/ProtectedRoute.tsx](file:///d:/AiTrader/AiTrader/src/components/ProtectedRoute.tsx)

#### 3.3.4 BottomNav.tsx - 底部导航

**职责**：应用底部导航栏，支持四个主要页面切换。

**导航项**：
- 首页 (`/`)
- 特惠 (`/deals`)
- 动态 (`/moments`)
- 我的 (`/me`)

**特性**：在特定页面（如 `/moments/new`、`/report`）自动隐藏

**文件路径**：[src/components/BottomNav.tsx](file:///d:/AiTrader/AiTrader/src/components/BottomNav.tsx)

---

### 3.4 页面层 (pages/)

#### 页面路由表

| 路径 | 页面组件 | 是否需要登录 | 描述 |
|------|---------|-------------|------|
| `/` | `Home.tsx` | 否 | 首页（行情 + AI助手） |
| `/deals` | `Deals.tsx` | 否 | 特惠活动页 |
| `/moments` | `Moments.tsx` | 否 | 动态列表页 |
| `/moments/new` | `NewMoment.tsx` | 是 | 发布动态页 |
| `/me` | `Me.tsx` | 否 | 个人中心 |
| `/kyc` | `Kyc.tsx` | 是 | 身份认证页 |
| `/security` | `Security.tsx` | 是 | 安全中心 |
| `/about` | `About.tsx` | 否 | 关于我们 |
| `/report` | `StrategyReport.tsx` | 否 | 策略报告页 |

**文件路径**：[src/pages/](file:///d:/AiTrader/AiTrader/src/pages/)

---

### 3.5 Hooks 层 (hooks/)

#### useInterval.ts - 安全的定时器 Hook

**职责**：封装 `setInterval`，确保组件卸载时自动清理，支持动态调整延迟。

**参数**：

| 参数 | 类型 | 描述 |
|------|------|------|
| `callback` | `() => void` | 定时执行的回调函数 |
| `delay` | `number \| null` | 延迟时间（毫秒），`null` 时停止 |

**文件路径**：[src/hooks/useInterval.ts](file:///d:/AiTrader/AiTrader/src/hooks/useInterval.ts)

---

## 4. 类型定义 (types/)

### 4.1 核心类型

```typescript
// 图表数据
export interface ChartData {
  time: string;
  price: number;
  timestamp: number;
}

// 用户信息
export interface User {
  id: string;
  nickName: string;
  phone: string;
  icon?: string;
  vipLevel?: number;
  aiChance?: number;
  point?: number;
}

// 动态帖子
export interface Post {
  id: number;
  userName: string;
  userAvatar?: string;
  time: string;
  content: string;
  likes: number;
  comments: number;
  isLiked: boolean;
}

// 导航菜单项
export interface MenuItem {
  icon: React.ElementType;
  label: string;
  path: string;
}
```

**文件路径**：[src/types/index.ts](file:///d:/AiTrader/AiTrader/src/types/index.ts)

---

## 5. 数据流向

### 5.1 认证流程

```
用户输入 → AuthContext.login() → authService.loginByCode/Password() → 
→ request() [添加 Token] → 后端 API → 成功后保存 Token + User 到 localStorage
```

### 5.2 实时行情流程

```
Home.tsx 挂载 → cryptoService.subscribeToBtcPrice() → WebSocket 连接 Binance →
→ 实时推送价格 → setCurrentPrice() → 触发重渲染
```

### 5.3 React Query 数据缓存

项目使用 `@tanstack/react-query` 进行服务端状态管理：

| Query Key | 数据源 | 缓存时间 |
|-----------|--------|---------|
| `['promotions']` | `marketService.getPromotions()` | 5分钟 |
| `['moments']` | `momentsService.getList()` | 1分钟 |

---

## 6. 项目运行

### 6.1 安装依赖

```bash
npm install
```

### 6.2 开发模式

```bash
npm run dev
```

### 6.3 构建生产版本

```bash
npm run build
```

### 6.4 代码检查

```bash
npm run lint
```

### 6.5 预览构建结果

```bash
npm run preview
```

---

## 7. 跨平台部署

项目集成 Capacitor 支持移动端部署：

### 7.1 构建 Android 应用

```bash
# 更新 web 资源
npx cap sync

# 打开 Android Studio
npx cap open android
```

### 7.2 目录结构

```
android/
├── app/                  # Android 应用代码
├── gradle/               # Gradle 配置
├── build.gradle          # 项目构建配置
└── settings.gradle       # 模块配置
```

---

## 8. 配置文件

### 8.1 vite.config.ts

主要配置：
- 代理 `/api` 请求到后端服务
- React 插件配置
- 路径别名（`@/*` → `src/*`）

### 8.2 capacitor.config.ts

Capacitor 配置：
- 应用 ID
- 服务器配置
- 插件配置

---

## 9. 安全注意事项

1. **Token 管理**：使用 `localStorage` 存储 JWT Token，请求时自动添加到 `Authorization` 头
2. **密码安全**：密码字段使用 `type="password"`，避免明文显示
3. **敏感信息保护**：身份证号等敏感信息在展示时进行脱敏处理
4. **输入验证**：前端对手机号、验证码等进行格式验证
5. **XSS 防护**：使用 React 自动转义，避免 XSS 攻击

---

## 10. 扩展建议

### 待开发功能

| 功能 | 优先级 | 描述 |
|------|-------|------|
| 交易功能 | 高 | 实盘/模拟交易 |
| 资产管理 | 高 | 钱包余额、交易记录 |
| 消息推送 | 中 | 价格提醒、活动通知 |
| 多币种支持 | 中 | 支持更多加密货币 |
| 策略回测 | 低 | AI 策略历史回测 |

### 性能优化建议

1. **虚拟滚动**：动态列表使用 `react-window` 优化长列表性能
2. **图片懒加载**：使用 `loading="lazy"` 或 `react-lazyload`
3. **代码分割**：使用 React.lazy 进行组件懒加载
4. **缓存策略**：优化 React Query 的缓存配置
