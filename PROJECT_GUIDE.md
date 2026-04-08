# MOZhi Project Guide

本文件是项目的详细运行与开发指南。
根 [README.md](./README.md) 只保留稳定入口信息；启动细节、阶段性状态和更具体的说明统一放在这里。

墨知平台（`MOZhi`）是一个正在建设中的内容社区与知识平台项目，目标是以 DDD 六边形架构为后端基础，以 React + TypeScript 为前端基础，逐步实现认证、内容发布、社交互动、搜索、AI 能力和商业化能力。

当前仓库处于 `Phase 0 / Bootstrap` 阶段：工程骨架、运行环境和本地启动链路已经打通，但业务能力仍以结构预留和占位为主。

## 当前状态

| 维度 | 状态 |
| --- | --- |
| 后端工程 | 已完成六模块 Maven 骨架，支持本地启动 |
| 前端工程 | 已完成 React + Vite + TypeScript 骨架，支持本地开发服务器启动 |
| 本地中间件 | 已提供 Docker Compose 环境，包含 MySQL / Redis / Kafka / MinIO |
| 业务能力 | 暂未实现真实业务域逻辑，仍在基座阶段 |
| 文档 | 已有架构、计划、DevOps 基础文档，README 作为根入口文档 |

## 项目目标

- 构建一个可持续演进的全栈内容平台工程基座
- 以 DDD 六模块后端约束业务边界和依赖方向
- 以统一 API 层、前后端联调能力和容器化本地环境降低开发门槛
- 通过阶段化路线推进认证、内容、社交、搜索、AI 和商业化能力

## 技术栈

### 后端

- Java 21
- Spring Boot 3.3.4
- Maven Wrapper
- MyBatis
- Spring Security
- Redis
- Kafka
- MySQL

### 前端

- React 18
- TypeScript
- Vite 5
- React Router
- Zustand
- TanStack Query
- Axios
- Tailwind CSS

### 本地环境

- Docker Compose
- PowerShell 启动脚本

## 架构总览

### 后端六模块

后端固定为单仓库 Maven 多模块工程，位于 [mozhi-backend](./mozhi-backend)：

| 模块 | 职责 |
| --- | --- |
| `mozhi-types` | 共享枚举、异常、事件、常量 |
| `mozhi-api` | 外部契约、DTO、API 输入输出定义 |
| `mozhi-domain` | 领域模型、聚合、领域服务、仓储接口 |
| `mozhi-infrastructure` | 仓储实现、DAO、网关、缓存、事件集成 |
| `mozhi-trigger` | HTTP / Job / Listener / RPC 等入口适配层 |
| `mozhi-app` | Spring Boot 启动、配置、资源、运行时装配 |

当前根包为 `cn.zy.mozhi`。

### 预留领域切片

后端已经为以下 bounded context 预留目录结构：

- `auth`
- `user`
- `content`
- `social`
- `commerce`
- `storage`
- `ai`
- `search`
- `feed`
- `counter`

这些切片目前是结构性占位，还没有进入真实业务实现阶段。

### 前端结构

前端位于 [mozhi-web](./mozhi-web)，当前是一个 typed shell：

- `src/router`：路由注册
- `src/layouts`：页面壳和布局组合
- `src/pages`：页面级占位
- `src/api`：统一 API 入口
- `src/stores`：状态管理入口
- `src/components`：共享组件与功能域占位组件

## 仓库结构

```text
MOZhi/
├─ docs/                  # 架构、计划、DevOps 文档
├─ mozhi-backend/         # 后端六模块 Maven 工程
├─ mozhi-web/             # 前端 React + Vite 工程
├─ logs/                  # 本地启动日志输出目录
├─ plan.md                # 当前阶段开发计划
└─ pom.xml                # 仓库根 Maven 聚合入口（供 IDEA / Maven 导入）
```

## 快速开始

### 前置要求

- JDK 21
- Docker Desktop
- Node.js 与 npm
- IntelliJ IDEA 或其他支持 Maven 多模块的 Java IDE

### 1. 克隆并打开项目

使用仓库根目录作为工程根打开项目：

```powershell
cd F:\new_opint\VibeCoding\MOZhi
```

如果你使用 IntelliJ IDEA：

1. 打开仓库根目录 `MOZhi`
2. 确认项目 JDK 为 Java 21
3. 对 Maven 执行 `Reload All Maven Projects`
4. 确认 IDEA 导入的是仓库根 [pom.xml](./pom.xml) 与后端聚合 [mozhi-backend/pom.xml](./mozhi-backend/pom.xml)

### 2. 启动后端与本地中间件

推荐直接使用 PowerShell 脚本：

```powershell
.\docs\dev-ops\app\start.ps1
```

这个脚本会做三件事：

- 启动 MySQL / Redis / Kafka / MinIO
- 打包 `mozhi-backend/mozhi-app`
- 以 `dev` profile 启动后端应用

停止命令：

```powershell
.\docs\dev-ops\app\stop.ps1
```

启动成功后，可验证：

- 后端健康检查：`http://127.0.0.1:8090/actuator/health`
- 日志目录：[logs](./logs)

如果要验证真实 MinIO 头像直传链路，而不是本地 mock fallback，可在启动前设置：

```powershell
$env:MOZHI_STORAGE_MINIO_ENABLED = "true"
.\docs\dev-ops\app\start.ps1
```

在这个模式下，启动脚本会自动初始化 `mozhi-assets` bucket，并开启公开读权限。

### 3. 启动前端

进入前端目录：

```powershell
cd .\mozhi-web
```

安装依赖：

```powershell
npm install
```

启动开发服务器：

```powershell
npx vite --host 127.0.0.1 --port 5173
```

访问地址：

- 前端本地地址：`http://127.0.0.1:5173/`

## 认证会话模型

当前认证链路已经切到 cookie session 模型：

- `accessToken` 只保存在前端内存态，用于当前页面请求附带 `Authorization: Bearer ...`
- `refreshToken` 不再写入前端持久化存储，而是由后端通过 `mozhi_refresh_token` `HttpOnly` Cookie 下发
- 页面刷新后，前端会先触发 `/api/auth/refresh` 做 silent bootstrap，再决定是否进入受保护路由
- 登出会清理内存中的 `accessToken`，并让后端清空 refresh cookie

## 认证安全开关

后端运行时与认证安全相关的主要环境变量如下：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `MOZHI_AUTH_COOKIE_SECURE` | refresh cookie 是否带 `Secure` | `true` |
| `MOZHI_AUTH_CHALLENGE_PROVIDER` | challenge provider，默认 `turnstile` | `turnstile` |
| `MOZHI_AUTH_TURNSTILE_SECRET_KEY` | Turnstile secret key，仅后端使用 | - |
| `MOZHI_AUTH_TURNSTILE_ALLOWED_HOSTNAMES` | Turnstile 允许的 hostname 列表 | 空 |
| `VITE_TURNSTILE_SITE_KEY` | Turnstile site key，前端公开变量 | - |

本地开发通常保持：

```powershell
$env:MOZHI_AUTH_COOKIE_SECURE = "false"
$env:MOZHI_AUTH_TURNSTILE_ALLOWED_HOSTNAMES = "localhost,127.0.0.1"
```

本地开发可使用 Cloudflare Turnstile 测试 key，或者将真实 key 绑定到 `localhost/127.0.0.1` 后再运行。后端始终做 server-side 校验，并按 allow-list 校验返回的 `hostname`。

## 本地服务与端口

| 服务 | 地址 / 端口 |
| --- | --- |
| Frontend | `127.0.0.1:5173` |
| Backend | `127.0.0.1:8090` |
| Actuator Health | `127.0.0.1:8090/actuator/health` |
| MySQL | `127.0.0.1:13306` |
| Redis | `127.0.0.1:16379` |
| Kafka | `127.0.0.1:19092` |
| MinIO API | `127.0.0.1:19000` |
| MinIO Console | `127.0.0.1:19001` |

## 开发命令

### 后端

运行测试：

```powershell
cd .\mozhi-backend
.\mvnw.cmd -q -pl mozhi-app -am test
```

打包应用：

```powershell
cd .\mozhi-backend
.\mvnw.cmd -q -pl mozhi-app -am package -DskipTests
```

### 前端

代码检查：

```powershell
cd .\mozhi-web
npm run lint
```

生产构建：

```powershell
cd .\mozhi-web
npm run build
```

运行测试：

```powershell
cd .\mozhi-web
npm run test -- --run
```

## 文档索引

- 架构骨架说明：[docs/architecture/bootstrap-structure.md](./docs/architecture/bootstrap-structure.md)
- 平台白板草图：[docs/architecture/mozhi-platform.canvas](./docs/architecture/mozhi-platform.canvas)
- DevOps 说明：[docs/dev-ops/README.md](./docs/dev-ops/README.md)
- 开发计划：[plan.md](./plan.md)

## 当前已完成内容

- 后端六模块工程骨架
- `cn.zy` 统一 Maven 坐标与根包路径
- 本地 Docker 中间件环境
- 后端启动脚本与健康检查链路
- 前端 React + Vite 骨架与页面占位布局
- 仓库根 Maven 聚合入口，便于 IDEA 正确导入

## 当前未完成内容

- 统一响应体与全局异常处理尚在 `Step 0.2`
- `/api/health` 业务健康接口尚未落地
- Swagger UI 尚未接入
- Flyway 迁移体系尚未正式接入
- 认证、用户、内容、社交等业务域尚未开始实现

## 路线图

当前阶段按 [plan.md](./plan.md) 推进：

- `Phase 0`：基座搭建
- `Phase 1`：认证域 + 用户域
- `Phase 2`：内容域 + 存储域
- 后续继续推进社交、搜索、AI、商业化等能力

## 开发约束

本仓库有明确的工程约束，请在开始开发前阅读：

- 后端遵循固定六模块 DDD 分层，不新增平行层
- 领域逻辑只放在 `domain`
- 入口适配只放在 `trigger`
- 数据访问、外部网关、缓存只放在 `infrastructure`
- 前端必须保持 TypeScript、集中 API 层、函数组件和现有目录边界

具体规则见仓库内 `AGENTS.md` 约束。

## 贡献说明

当前仓库仍处于基础建设阶段，建议贡献方式如下：

1. 从仓库根目录导入工程并确认本地可运行
2. 优先遵循 [plan.md](./plan.md) 的阶段顺序推进
3. 代码提交前至少完成对应模块的测试与构建验证
4. 避免在业务实现前破坏既定六模块边界

## 已知限制

- 当前后端启动后会使用 Spring Security 默认开发密码，认证域尚未替换为正式方案
- 当前前端仍以骨架与占位为主，未与真实业务接口深度联动
- 当前 README 以仓库真实状态为准，不提前承诺尚未实现的能力

## License

仓库当前尚未声明开源许可证。

在许可证文件正式添加之前，不应默认将本项目视为可自由分发或商用的软件。
