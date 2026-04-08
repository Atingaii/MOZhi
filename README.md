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

### 本地 Docker 一键启动

如果你希望在宿主机不额外安装 Java / Node 的情况下直接跑起项目，可使用本地 Docker 开发版。

首次启动或修改了 `Dockerfile` 后执行：

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-local.yml up --build
```

日常开发只需：

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-local.yml up
```

启动后可访问：

- Frontend: `http://127.0.0.1:5173/`
- Backend API: `http://127.0.0.1:8090/api/health`
- Swagger UI: `http://127.0.0.1:8090/swagger-ui/index.html`

本地中间件现在默认使用 Docker named volumes 持久化：

- MySQL 用户和业务数据会保留
- Redis AOF 数据会保留
- Kafka 本地 topic / segment / offsets 会保留
- MinIO bucket 与对象资源会保留

源文件修改后：

- 前端会通过 Vite 直接热更新
- 后端会在容器内自动重新打包并重启应用进程

只有在修改基础镜像、`Dockerfile` 或需要强制重装容器环境时，才需要重新带上 `--build`。

停止命令：

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-local.yml down
```

如果你要连本地数据卷一起删除并回到全新状态，使用：

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-local.yml down -v
```

`down` 只停容器，`down -v` 才会清掉本地持久化数据。

本地 Docker 版刻意不引入 Nginx，目的是保持最快启动路径和最低排障成本；如果后续需要单入口、HTTPS 和静态资源反向代理，再补独立的生产版部署配置。

### 生产版 Docker 入口

如果你希望用更接近真实部署的方式运行项目，可使用 `Nginx + 前端静态资源 + 后端容器` 组合：

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-app.yml up --build -d
```

默认入口：

- 站点入口：`http://127.0.0.1:8080/`
- API 健康检查：`http://127.0.0.1:8080/api/health`
- Swagger：`http://127.0.0.1:8080/swagger-ui/index.html`

这个版本会通过 Nginx 统一入口代理前端静态资源和后端 `/api`、`/swagger-ui` 请求，更接近预发 / 生产部署形态。

## 认证会话模型

- `accessToken` 只保存在前端内存，不写入 `localStorage`
- `refreshToken` 通过 `mozhi_refresh_token` `HttpOnly` Cookie 下发
- 前端刷新页面时会自动走 `/api/auth/refresh` 恢复会话
- 生产默认 `MOZHI_AUTH_COOKIE_SECURE=true`，本地开发通过 `application-dev.yml` 显式覆盖为 `false`

## 认证安全开关

- `MOZHI_AUTH_COOKIE_SECURE`
  控制 refresh cookie 是否带 `Secure`
- `MOZHI_AUTH_CHALLENGE_PROVIDER`
  默认 `turnstile`；测试环境通过 `test` provider 提供确定性 challenge 校验
- `MOZHI_AUTH_TURNSTILE_SECRET_KEY`
  后端调用 Turnstile `siteverify` 的服务端密钥，只能放在环境变量里
- `MOZHI_AUTH_TURNSTILE_ALLOWED_HOSTNAMES`
  Turnstile 校验成功后允许通过的主机名列表，例如 `localhost,127.0.0.1`
- `VITE_TURNSTILE_SITE_KEY`
  前端渲染 challenge widget 使用的站点密钥

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
