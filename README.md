# BFSM 美容美发预约管理系统

BFSM 是一个基于 Spring Boot 3 和 Vue 3 的美容美发门店预约管理系统。系统围绕门店线上化经营场景，覆盖用户预约、技师排班、服务履约、商品商城、支付结算、AI 咨询、即时聊天、系统通知和运营统计等流程。

## 功能概览

- 用户端：注册登录、服务浏览、预约下单、钱包支付、商城购物、收藏评论、AI 发型/护理咨询、消息通知、在线聊天。
- 技师端：工作台、预约处理、服务状态流转、请假申请、考勤打卡、个人统计。
- 管理端：用户管理、服务和技师管理、排班管理、预约审核、商品管理、订单发货、请假审批、评论管理、轮播公告、销售统计。
- 支付能力：站内余额、手动收银演示模式、支付宝沙箱配置、微信支付网关抽象。
- AI 能力：兼容 OpenAI Chat Completions 的视觉/文本咨询接口，支持服务和商品推荐映射，并提供本地降级策略。
- 实时能力：WebSocket 聊天、系统通知、订单/预约/请假等关键业务提醒。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.4.4
- Spring MVC
- Spring Security + JWT
- MyBatis-Plus
- MySQL 8
- Redis
- WebSocket
- Knife4j / OpenAPI
- Maven

### 前端

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Axios
- Element Plus
- ECharts
- QRCode

## 项目结构

```text
bfsm/
├─ backend/                  # Spring Boot 后端
│  ├─ src/main/java/com/bfsm # 业务代码
│  ├─ src/main/resources     # 配置、SQL 初始化脚本
│  └─ pom.xml
├─ frontend/                 # Vue 3 前端
│  ├─ src
│  ├─ public
│  └─ package.json
├─ docs/                     # 设计文档、ER 图、测试说明
├─ tools/                    # 文档和图表生成脚本
└─ README.md
```

## 环境要求

- JDK 17
- Maven 3.8+
- Node.js 18+
- MySQL 8+
- Redis 6+

## 数据库初始化

1. 创建数据库：

```sql
CREATE DATABASE bfsm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 导入初始化脚本：

```bash
mysql -u root -p bfsm < backend/src/main/resources/schema.sql
```

3. 根据本机环境修改 `backend/src/main/resources/application.yml` 中的 MySQL 和 Redis 配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/bfsm?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: root
    password: 123456
  data:
    redis:
      host: localhost
      port: 6379
```

## 后端启动

```bash
cd backend
mvn spring-boot:run
```

启动成功后，默认后端地址为：

```text
http://localhost:8080
```

接口文档入口：

```text
http://localhost:8080/doc.html
```

## 前端启动

```bash
cd frontend
npm install
npm run dev
```

启动成功后，默认前端地址为：

```text
http://localhost:5173
```

前端开发环境会通过 Vite 代理访问后端接口，代理配置位于 `frontend/vite.config.ts`。

## 常用配置

后端支持通过环境变量覆盖部分配置：

| 变量名 | 说明 | 默认值 |
| --- | --- | --- |
| `BFSM_JWT_SECRET` | JWT 签名密钥 | `bfsm-local-dev-secret-change-me-2026` |
| `BFSM_PAYMENT_MODE` | 支付模式 | `MANUAL_CASHIER` |
| `BFSM_PAYMENT_MANUAL_COMPLETION_ENABLED` | 是否允许手动完成支付 | `true` |
| `BFSM_AI_BASE_URL` | AI 接口基础地址 | `https://open.bigmodel.cn/api/paas/v4` |
| `BFSM_AI_API_KEY` | AI 接口密钥 | 空 |
| `BFSM_AI_MODEL` | AI 模型名称 | `glm-4v-flash` |
| `BFSM_AI_FALLBACK_TO_LOCAL` | AI 失败时是否本地降级 | `false` |

生产或公开部署前，请务必修改数据库密码、JWT 密钥、支付密钥和 AI API Key，不要把真实密钥提交到仓库。

## 演示数据

`schema.sql` 内置了角色、服务、商品、技师、排班、公告等演示数据。常见演示账号包括：

| 账号 | 角色 | 说明 |
| --- | --- | --- |
| `admin` | 管理员 | 管理后台入口 |
| `tech_amy` | 技师 | 技师工作台 |
| `tech_bob` | 技师 | 技师工作台 |

如果需要确认或重置演示密码，请查看 `backend/src/main/resources/schema.sql` 中的 `sys_user` 初始化数据，或通过注册/管理端重新创建账号。

## 测试与构建

后端测试：

```bash
cd backend
mvn test
```

后端打包：

```bash
cd backend
mvn -DskipTests package
```

前端构建：

```bash
cd frontend
npm run build
```

## 核心业务流程

### 预约流程

用户选择服务和预约时间后，系统会根据技师服务能力、排班、请假、已有预约和服务时长判断可预约性。创建预约时通过 Redis 锁降低并发抢占风险，支付成功后进入待确认和服务履约流程。

预约状态包含：

```text
CREATED -> PENDING -> APPROVED -> WAIT_SERVICE -> IN_SERVICE -> COMPLETED
```

异常流程包含取消和拒绝：

```text
CANCELLED / REJECTED
```

### 商城订单流程

用户可以浏览商品、加入购物车、选择收货地址并提交订单。后端在下单时校验商品状态和库存，并通过 Redis 锁处理并发扣减。订单支持支付、发货、确认收货、售后和退款流程。

订单状态包含：

```text
CREATED -> PAID -> SHIPPED -> COMPLETED
```

异常流程包含取消和退款：

```text
CANCELLED / REFUNDED
```

### 支付流程

系统使用统一的 `PaymentService` 管理支付单，支持预约支付、商品订单支付和钱包充值。支付网关通过接口抽象，当前包含手动收银、支付宝页面支付和微信 Native 支付实现。

### AI 咨询流程

用户上传图片或输入诉求后，后端会组合用户需求、图片信息、服务目录和商品目录，调用兼容 OpenAI Chat Completions 的 AI 接口生成建议，并将建议映射到可预约服务和可购买商品。

## GitHub 上传建议

上传仓库前建议只提交源码、配置模板、数据库脚本和文档。以下内容应由 `.gitignore` 排除：

- `frontend/node_modules/`
- `frontend/dist/`
- `backend/target/`
- 本地日志文件
- IDE 配置目录
- 用户上传图片
- 临时预览图和构建缓存

## 许可证

本项目用于毕业设计和学习演示。如需公开发布或商用，请根据实际需求补充许可证和第三方资源授权说明。
