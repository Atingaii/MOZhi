# MOZhi

MOZhi 是一个面向内容、知识、社区与商业化场景的全栈平台仓库。
项目采用单仓库组织方式，后端以 DDD 六模块架构为核心，前端以 React + TypeScript 为核心，
目标是沉淀一套可持续演进、可本地快速启动、可逐阶段扩展的工程基座。

## 项目定位

- 面向长期演进的内容平台工程，而不是一次性 Demo
- 后端坚持清晰分层与领域边界，避免业务逻辑散落
- 前端坚持类型化、集中式 API 层和页面壳分离
- 本地环境统一通过 Docker Compose 管理，降低接手成本

## 核心架构

### 后端

后端固定为六模块 Maven 工程：

- `mozhi-types`：共享枚举、异常、事件、常量
- `mozhi-api`：外部契约与 DTO
- `mozhi-domain`：领域模型、聚合、领域服务、仓储接口
- `mozhi-infrastructure`：DAO、仓储实现、缓存、网关、事件集成
- `mozhi-trigger`：HTTP / Listener / Job / RPC 等入口适配
- `mozhi-app`：Spring Boot 启动、配置与运行时装配

当前根包为 `cn.zy.mozhi`。

### 前端

前端位于 `mozhi-web/`，使用 React 18 + TypeScript + Vite，强调：

- 页面与布局分离
- API 调用集中管理
- 本地状态与跨页状态边界清晰
- 以可扩展骨架先行，再逐步接入业务能力

## 仓库结构

```text
MOZhi/
├─ mozhi-backend/         # 后端六模块工程
├─ mozhi-web/             # 前端工程
├─ docs/                  # 架构、DevOps、设计与规划文档
├─ PROJECT_GUIDE.md       # 详细运行与开发指南
├─ plan.md                # 项目阶段计划与执行路线
└─ pom.xml                # 仓库根 Maven 聚合入口
```

## 快速开始

### 环境要求

- JDK 21
- Docker Desktop
- Node.js 与 npm

### 启动后端

```powershell
.\docs\dev-ops\app\start.ps1
```

启动后可访问：

- API Health: `http://127.0.0.1:8090/api/health`
- Swagger UI: `http://127.0.0.1:8090/swagger-ui/index.html`

如需验证真实 MinIO 直传头像链路，可先设置：

```powershell
$env:MOZHI_STORAGE_MINIO_ENABLED = "true"
.\docs\dev-ops\app\start.ps1
```

启动脚本会自动初始化 `mozhi-assets` bucket，并配置公开读权限。

停止命令：

```powershell
.\docs\dev-ops\app\stop.ps1
```

### 启动前端

```powershell
cd .\mozhi-web
npm install
npm run dev
```

默认访问地址：

- Frontend: `http://127.0.0.1:5173/`

## 认证会话模型

- `accessToken` 只保存在前端内存，不写入 `localStorage`
- `refreshToken` 通过 `mozhi_refresh_token` `HttpOnly` Cookie 下发
- 前端刷新页面时会自动走 `/api/auth/refresh` 恢复会话
- 本地开发默认 `MOZHI_AUTH_COOKIE_SECURE=false`，部署到 HTTPS 环境时应改为 `true`

## 认证安全开关

- `MOZHI_AUTH_COOKIE_SECURE`
  控制 refresh cookie 是否带 `Secure`
- `MOZHI_AUTH_CHALLENGE_PROVIDER`
  当前默认 `noop`，用于本地联调 challenge 升级链路
- `MOZHI_AUTH_CHALLENGE_NOOP_PASS_TOKEN`
  `noop` provider 的通过口令，默认 `dev-pass`

## 文档导航

- [PROJECT_GUIDE.md](./PROJECT_GUIDE.md)
  详细运行、验证与开发说明
- [docs/architecture/bootstrap-structure.md](./docs/architecture/bootstrap-structure.md)
  当前后端六模块与前端骨架说明
- [docs/dev-ops/README.md](./docs/dev-ops/README.md)
  本地环境与启动脚本说明
- [plan.md](./plan.md)
  项目阶段计划与路线图

## 开发原则

- 后端不新增平行层，新增业务优先落到既有六模块与对应子域
- 前端不绕过集中式 API 层直接请求后端
- 中间件按阶段启用，优先保证当前阶段闭环可验证
- 文档分层维护：根 README 保持稳定，细节进入 `PROJECT_GUIDE.md` 与 `docs/`

## 当前说明

本仓库处于持续建设中，路线图和阶段性能力以 [plan.md](./plan.md) 为准。
根 README 只保留稳定信息，不承载频繁变化的实现细节。

## License

当前仓库尚未声明正式开源许可证。
