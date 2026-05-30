# CampusMall AI-Trader 后端项目文档

## 1. 项目概述

CampusMall 是一个基于 Spring Boot 的加密货币交易助手后端服务，提供用户管理、AI交易分析、市场活动和社交动态等核心功能。

### 1.1 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.5.7 | 应用框架 |
| MyBatis Plus | 3.5.7 | ORM框架 |
| JWT | 0.11.5 | 身份认证 |
| Redis | - | 缓存/会话管理 |
| Hutool | 5.8.22 | 工具库 |
| Java-WebSocket | 1.5.3 | WebSocket客户端 |
| MySQL | - | 数据库 |

### 1.2 主要功能模块

| 模块 | 功能描述 |
|------|----------|
| **User Module** | 用户注册、登录、信息管理 |
| **AI Module** | AI交易分析对话 |
| **Market Module** | 促销活动、积分兑换、礼包领取 |
| **Moment Module** | 社交动态发布、点赞 |
| **Crypto Market** | 加密货币行情数据获取与指标计算 |

---

## 2. 项目架构

### 2.1 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Controller 层                          │
│   UserController | AiController | MarketController         │
│   MomentController                                          │
├─────────────────────────────────────────────────────────────┤
│                      Service 层                             │
│   TbUserService | TbAiService | TbMarketService            │
│   TbMomentService | CryptoMarketService                    │
├─────────────────────────────────────────────────────────────┤
│                      Mapper 层                              │
│   TbUserMapper | TbMomentMapper | TbPromotionMapper        │
│   TbMomentLikeMapper | TbUserGiftClaimMapper               │
├─────────────────────────────────────────────────────────────┤
│                      Domain 层                              │
│   TbUser | TbMoment | TbMomentLike | TbPromotion           │
│   TbUserGiftClaim                                          │
├─────────────────────────────────────────────────────────────┤
│                      基础设施                                │
│   Redis | MySQL | Binance API                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 目录结构

```
src/main/java/com/mp/campusmall/
├── Constant/           # 常量定义
├── DTO/                # 数据传输对象（请求）
├── VO/                 # 视图对象（响应）
├── Utils/              # 工具类
├── config/             # 配置类
├── context/            # 上下文（ThreadLocal）
├── controller/         # REST API 控制层
├── domain/             # 数据库实体
├── interceptor/        # 拦截器
├── mapper/             # MyBatis Mapper
├── properties/         # 配置属性类
├── service/            # 业务服务接口
│   └── impl/           # 服务实现类
├── websocket/          # WebSocket客户端
└── CampusMallApplication.java
```

---

## 3. 数据库设计

### 3.1 数据库表一览

| 表名 | 说明 | 核心字段 |
|------|------|----------|
| `tb_user` | 用户表 | id, phone, password, nick_name, balance, ai_chance, point |
| `tb_moment` | 动态表 | id, user_id, content, likes, comments |
| `tb_moment_like` | 动态点赞表 | id, moment_id, user_id |
| `tb_promotion` | 促销活动表 | id, title, description, type, required_points |
| `tb_user_gift_claim` | 用户礼包领取记录 | id, user_id, gift_type, claim_time |

### 3.2 表结构详解

#### 3.2.1 tb_user（用户表）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 用户ID |
| phone | VARCHAR(20) | NOT NULL, UNIQUE | 手机号码 |
| password | VARCHAR(255) | NULL | 密码（MD5加密） |
| nick_name | VARCHAR(50) | NULL | 昵称 |
| icon | VARCHAR(255) | NULL | 头像URL |
| vip_level | INT | DEFAULT 0 | VIP等级 |
| balance | DECIMAL(20,8) | DEFAULT 0 | 余额（USDT） |
| btc_amount | DECIMAL(20,8) | DEFAULT 0 | BTC持仓 |
| ai_chance | INT | DEFAULT 0 | AI交易机会次数 |
| point | INT | DEFAULT 0 | 积分 |
| create_time | DATETIME | NULL | 创建时间 |
| update_time | DATETIME | NULL | 更新时间 |

#### 3.2.2 tb_moment（动态表）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 动态ID |
| user_id | BIGINT | NOT NULL | 发布用户ID |
| content | TEXT | NOT NULL | 动态内容 |
| likes | INT | DEFAULT 0 | 点赞数 |
| comments | INT | DEFAULT 0 | 评论数 |
| create_time | DATETIME | NULL | 创建时间 |
| update_time | DATETIME | NULL | 更新时间 |

#### 3.2.3 tb_promotion（促销活动表）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 活动ID |
| title | VARCHAR(255) | NOT NULL | 活动标题 |
| description | TEXT | NULL | 活动描述 |
| action_text | VARCHAR(50) | NULL | 按钮文字 |
| action_color | VARCHAR(50) | NULL | 按钮颜色 |
| type | VARCHAR(50) | NOT NULL | 类型：gift/exchange/recharge |
| required_points | INT | DEFAULT 0 | 兑换所需积分 |

---

## 4. 核心模块详解

### 4.1 用户模块

#### 4.1.1 Controller 接口

| API路径 | HTTP方法 | 功能描述 | 是否需要登录 |
|---------|----------|----------|--------------|
| `/user/code` | POST | 发送短信验证码 | 否 |
| `/user/login/password` | POST | 密码登录 | 否 |
| `/user/login/sms` | POST | 短信验证码登录 | 否 |
| `/user/register` | POST | 用户注册 | 否 |
| `/user/resetPassword` | POST | 重置密码 | 否 |
| `/user/update` | POST | 更新用户信息 | 是 |
| `/user/upload` | POST | 上传头像 | 是 |
| `/user/me` | GET | 获取当前用户信息 | 是 |
| `/user/logout` | POST | 登出 | 是 |
| `/user/password/change` | POST | 修改密码 | 是 |

#### 4.1.2 Service 核心方法

**TbUserService 接口** [file:///d:/CampusMall%20-%20mp/campusMall/src/main/java/com/mp/campusmall/service/TbUserService.java]

| 方法名 | 功能说明 | 参数 | 返回值 |
|--------|----------|------|--------|
| `sendCode` | 发送短信验证码 | `phone`: 手机号 | `Result<String>` |
| `smsLogin` | 短信登录 | `LoginDTO` | `Result<LoginVO>` |
| `passwordLogin` | 密码登录 | `LoginDTO` | `Result<LoginVO>` |
| `register` | 用户注册 | `LoginDTO` | `Result<String>` |
| `resetPassword` | 重置密码 | `LoginDTO` | `Result<String>` |
| `updateUserInfo` | 更新用户信息 | `LoginDTO` | `Result<String>` |
| `changePassword` | 修改密码 | `PasswordChangeDTO` | `Result<String>` |
| `uploadAvatar` | 上传头像 | `MultipartFile` | `Result<String>` |
| `getCurrentUser` | 获取当前用户 | 无 | `Result<LoginVO>` |
| `logout` | 登出 | `token`: JWT令牌 | `Result<String>` |

#### 4.1.3 登录流程

```
1. 用户请求登录（短信/密码）
2. 验证手机号/验证码/密码
3. 查询或创建用户
4. 生成JWT令牌
5. 将用户信息存入Redis
6. 返回LoginVO（含token）
```

---

### 4.2 AI模块

#### 4.2.1 Controller 接口

| API路径 | HTTP方法 | 功能描述 | 是否需要登录 |
|---------|----------|----------|--------------|
| `/ai/chat` | POST | AI交易分析对话 | 是 |

#### 4.2.2 Service 核心逻辑

**TbAiServiceImpl** [file:///d:/CampusMall%20-%20mp/campusMall/src/main/java/com/mp/campusmall/service/impl/TbAiServiceImpl.java]

**chat() 方法流程**：

```
1. 获取当前用户ID
2. 查询用户信息，检查AI机会次数
3. 调用CryptoMarketService获取BTC行情数据
4. 构造AI Prompt（包含市场数据和用户信息）
5. 调用外部AI API（OpenAI兼容接口）
6. 解析响应，扣除一次AI机会
7. 返回AI分析结果
```

**AI Prompt 结构**：
```
请作为一名专业的加密货币交易专家，根据以下BTC市场数据，制定详细交易策略：
- 市场趋势分析（多头/空头/震荡）
- 关键支撑位和阻力位
- 具体交易建议（入场点位、止损点位、止盈点位）
```

---

### 4.3 市场模块

#### 4.3.1 Controller 接口

| API路径 | HTTP方法 | 功能描述 | 是否需要登录 |
|---------|----------|----------|--------------|
| `/market/promotions` | GET | 获取活动列表 | 否 |
| `/market/gift/claim` | POST | 领取新手礼包 | 是 |
| `/market/exchange/ai` | POST | 积分兑换AI次数 | 是 |

#### 4.3.2 Service 核心方法

**TbMarketServiceImpl** [file:///d:/CampusMall%20-%20mp/campusMall/src/main/java/com/mp/campusmall/service/impl/TbMarketServiceImpl.java]

| 方法名 | 功能说明 | 业务逻辑 |
|--------|----------|----------|
| `getPromotionList` | 获取促销活动列表 | 查询tb_promotion表，转换为VO返回 |
| `claimWelcomeGift` | 领取新手礼包 | 检查是否已领取 → 记录领取 → 发放10次AI机会 |
| `exchangeAiChance` | 积分兑换AI次数 | 检查积分≥1000 → 扣除积分 → 增加1次AI机会 |

---

### 4.4 动态模块

#### 4.4.1 Controller 接口

| API路径 | HTTP方法 | 功能描述 | 是否需要登录 |
|---------|----------|----------|--------------|
| `/moments/list` | GET | 获取动态列表 | 可选 |
| `/moments/create` | POST | 发布动态 | 是 |
| `/moments/like` | POST | 点赞/取消点赞 | 是 |

#### 4.4.2 Service 核心方法

**TbMomentServiceImpl** [file:///d:/CampusMall%20-%20mp/campusMall/src/main/java/com/mp/campusmall/service/impl/TbMomentServiceImpl.java]

| 方法名 | 功能说明 | 业务逻辑 |
|--------|----------|----------|
| `getMomentList` | 获取动态列表 | 查询所有动态，关联用户信息，判断当前用户是否点赞 |
| `createMoment` | 发布动态 | 创建动态记录，初始化点赞数和评论数为0 |
| `likeMoment` | 点赞/取消点赞 | 检查是否已点赞 → 新增/删除点赞记录 → 更新动态点赞数 |

---

### 4.5 加密货币市场服务

**CryptoMarketService** [file:///d:/CampusMall%20-%20mp/campusMall/src/main/java/com/mp/campusmall/service/CryptoMarketService.java]

提供加密货币行情数据获取和技术指标计算功能。

#### 4.5.1 核心方法

| 方法名 | 功能说明 |
|--------|----------|
| `getBtcMarketData()` | 获取BTC市场深度分析数据 |
| `calculateMA()` | 计算简单移动平均线(SMA) |
| `calculateEMA()` | 计算指数移动平均线(EMA) |
| `calculateRSI()` | 计算相对强弱指标(RSI) |
| `calculateStdDev()` | 计算标准差(用于布林带) |

#### 4.5.2 技术指标

| 指标 | 参数 | 说明 |
|------|------|------|
| RSI | 14周期 | 相对强弱指标，>70超买，<30超卖 |
| MA7 | 7日 | 短期移动平均线 |
| MA25 | 25日 | 中期移动平均线 |
| 布林带 | 20日, 2σ | 上轨/中轨/下轨 |
| EMA | 12/26 | 指数移动平均（MACD计算） |

#### 4.5.3 数据源

- **REST API**: Binance `/api/v3/klines` - 获取历史K线数据
- **WebSocket**: Binance WebSocket - 获取实时价格更新

---

## 5. 关键类与工具

### 5.1 JWT工具类

**JwtUtil** [file:///d:/CampusMall%20-%20mp/campusMall/src/main/java/com/mp/campusmall/Utils/JwtUtil.java]

| 方法名 | 功能说明 | 参数 |
|--------|----------|------|
| `createJWT()` | 生成JWT令牌 | `secretKey`: 密钥, `ttlMillis`: 过期时间, `claims`: 自定义载荷 |
| `parseJWT()` | 解析JWT令牌 | `secretKey`: 密钥, `token`: 令牌 |

### 5.2 JWT拦截器

**JwtInterceptor** [file:///d:/CampusMall%20-%20mp/campusMall/src/main/java/com/mp/campusmall/interceptor/JwtInterceptor.java]

- **功能**: 统一验证JWT令牌，将用户ID存入ThreadLocal
- **放行规则**: `/moments/list` 接口允许未登录访问
- **黑名单机制**: 登出后将token加入Redis黑名单

### 5.3 上下文管理

**BaseContext** [file:///d:/CampusMall%20-%20mp/campusMall/src/main/java/com/mp/campusmall/context/BaseContext.java]

使用 `ThreadLocal` 存储当前登录用户ID，在拦截器中设置，在请求结束时清理。

---

## 6. DTO与VO定义

### 6.1 DTO（请求对象）

| DTO类 | 用途 | 字段 |
|-------|------|------|
| **LoginDTO** | 登录/注册请求 | phone, password, code, nickName |
| **PasswordChangeDTO** | 修改密码请求 | oldPassword, newPassword |
| **ExchangeDTO** | 兑换请求 | - |
| **MomentDTO** | 发布动态请求 | content |
| **MomentLikeDTO** | 点赞请求 | id (moment_id) |

### 6.2 VO（响应对象）

| VO类 | 用途 | 字段 |
|------|------|------|
| **LoginVO** | 登录响应 | id, phone, nickName, icon, token, vipLevel, aiChance, point |
| **AIChatVO** | AI对话响应 | reply |
| **PromotionVO** | 促销活动响应 | id, title, description, actionText, actionColor, type, requiredPoints |
| **MomentVO** | 动态响应 | id, userName, userAvatar, time, content, likes, comments, isLiked |
| **MomentLikeVO** | 点赞响应 | isLiked, likes |
| **Result<T>** | 统一响应包装 | code, msg, data |

---

## 7. 配置与运行

### 7.1 配置文件

需要在 `application.yml` 中配置以下内容：

```yaml
# 数据源配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/example_db
    username: admin
    password: password

# Redis配置
  data:
    redis:
      host: localhost
      port: 6379

# JWT配置
jwt:
  user-secret-key: your-256-bit-secret-key-here-min-32-chars
  user-ttl: 7200000  # 2小时
  user-token-name: Authorization

# AI配置
ai:
  api-url: https://api.openai.com/v1/chat/completions
  api-key: your-api-key
  model: gpt-3.5-turbo

# 代理配置（可选）
proxy:
  enable: false
  host: 127.0.0.1
  port: 7890
```

### 7.2 启动方式

**开发态运行**：
```bash
mvn spring-boot:run
```

**打包构建**：
```bash
mvn clean package
```

**运行打包后的Jar**：
```bash
java -jar target/campusMall-0.0.1-SNAPSHOT.jar
```

### 7.3 数据库初始化

项目启动时会自动执行 `schema.sql` 创建表结构。可通过 `data.sql` 初始化测试数据。

---

## 8. 安全机制

### 8.1 JWT认证

- 使用HS256算法签名
- 密钥长度要求至少32字节（256位）
- Token有效期2小时
- 登出时将Token加入黑名单（Redis）

### 8.2 密码安全

- 密码使用MD5加密存储
- 支持短信验证码登录（免密码）

### 8.3 接口保护

- 除登录/注册/验证码接口外，其他接口需要Token
- 动态列表接口支持可选登录（未登录时不显示点赞状态）

---

## 9. 依赖关系图

```
CampusMallApplication
    ├── Controller层
    │   ├── UserController ───────────► TbUserService
    │   ├── AiController ─────────────► TbAiService
    │   ├── MarketController ─────────► TbMarketService
    │   └── MomentController ─────────► TbMomentService
    │
    ├── Service层
    │   ├── TbUserServiceImpl ────────► TbUserMapper
    │   │                            ├── JwtUtil
    │   │                            └── BaseContext
    │   │
    │   ├── TbAiServiceImpl ──────────► TbUserMapper
    │   │                            └── CryptoMarketService
    │   │
    │   ├── TbMarketServiceImpl ──────► TbPromotionMapper
    │   │                            ├── TbUserMapper
    │   │                            └── TbUserGiftClaimMapper
    │   │
    │   ├── TbMomentServiceImpl ──────► TbMomentMapper
    │   │                            ├── TbUserMapper
    │   │                            └── TbMomentLikeMapper
    │   │
    │   └── CryptoMarketService ──────► BinanceWebSocketClient
    │
    └── 拦截器
        └── JwtInterceptor ───────────► BaseContext
                                    └── StringRedisTemplate
```

---

## 10. 核心业务流程图

### 10.1 用户登录流程

```
用户请求 → Controller → Service → Mapper → Database
              ↓              ↓
           验证参数       查询/创建用户
              ↓              ↓
           生成JWT        存入Redis
              ↓
          返回LoginVO
```

### 10.2 AI分析流程

```
用户请求 → AiController → TbAiService → CryptoMarketService
                                            ↓
                                      Binance API/WebSocket
                                            ↓
                                    获取市场数据 + 计算指标
                                            ↓
                                    构造Prompt → 调用AI API
                                            ↓
                                    解析响应 → 扣除AI机会
                                            ↓
                                    返回AIChatVO
```

### 10.3 点赞流程

```
用户请求 → MomentController → TbMomentService
                                    ↓
                            查询是否已点赞
                               ↓     ↓
                          已点赞   未点赞
                            ↓        ↓
                        删除记录   新增记录
                            ↓        ↓
                        点赞数-1   点赞数+1
                            ↓
                        返回MomentLikeVO
```

---

## 11. API 接口汇总

| 模块 | API路径 | HTTP方法 | Controller文件 |
|------|---------|----------|----------------|
| 用户 | `/user/code` | POST | UserController.java |
| 用户 | `/user/login/password` | POST | UserController.java |
| 用户 | `/user/login/sms` | POST | UserController.java |
| 用户 | `/user/register` | POST | UserController.java |
| 用户 | `/user/resetPassword` | POST | UserController.java |
| 用户 | `/user/update` | POST | UserController.java |
| 用户 | `/user/upload` | POST | UserController.java |
| 用户 | `/user/me` | GET | UserController.java |
| 用户 | `/user/logout` | POST | UserController.java |
| 用户 | `/user/password/change` | POST | UserController.java |
| AI | `/ai/chat` | POST | AiController.java |
| 市场 | `/market/promotions` | GET | MarketController.java |
| 市场 | `/market/gift/claim` | POST | MarketController.java |
| 市场 | `/market/exchange/ai` | POST | MarketController.java |
| 动态 | `/moments/list` | GET | MomentController.java |
| 动态 | `/moments/create` | POST | MomentController.java |
| 动态 | `/moments/like` | POST | MomentController.java |

---

## 12. 代码质量与规范

### 12.1 编码规范

- 使用 Lombok 注解（@Slf4j, @Autowired, @Builder）
- 遵循 Spring Boot 命名规范
- 异常处理使用 try-catch 包裹
- 日志使用 SLF4J

### 12.2 事务管理

- 使用 `@Transactional` 注解管理事务
- 关键业务（如积分兑换、点赞）使用事务保证数据一致性

### 12.3 错误处理

- 统一使用 `Result<T>` 包装响应
- 错误码和错误信息通过 `Result.error()` 返回
- 异常日志记录使用 `log.error()`

---

## 13. 扩展建议

### 13.1 待优化项

1. **AI API限流**：添加请求频率限制，防止滥用
2. **图片存储优化**：当前使用本地存储，建议迁移至云存储（如OSS）
3. **WebSocket断线重连**：增强BinanceWebSocketClient的稳定性
4. **单元测试覆盖**：补充各层的单元测试
5. **API文档**：集成Swagger/OpenAPI自动生成文档

### 13.2 功能扩展

1. **交易模拟**：添加模拟交易功能
2. **K线图表**：提供K线数据接口
3. **通知推送**：价格预警、活动通知
4. **社交互动**：评论、转发功能

---

## 附录：常量定义

### JwtClaimsConstant

| 常量 | 值 | 说明 |
|------|-----|------|
| USER_ID | "userId" | JWT载荷中的用户ID键名 |

### RedisConstants

| 常量 | 值 | 说明 |
|------|-----|------|
| LOGIN_CODE_KEY | "login:code:" | 验证码缓存前缀 |
| LOGIN_USER_KEY | "login:token:" | 用户缓存前缀 |
| LOGIN_USER_TTL | 30 | 用户缓存有效期（分钟） |
| CACHE_NULL_TTL | 2 | 验证码有效期（分钟） |

### MessageConstant

| 常量 | 值 | 说明 |
|------|-----|------|
| PASSWORD_ERROR | "密码错误" | 密码错误提示 |
| ACCOUNT_LOCKED | "账号被锁定" | 账号锁定提示 |