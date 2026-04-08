# 本地 Docker 热更新运行时设计

## 目标

把本地 Docker 运行形态从“每次源码变更都要重建应用镜像”调整为真正的开发态运行时：

- 前端修改代码后直接触发 Vite 热更新
- 后端修改 Java / XML / YML 后自动重启 Spring Boot 进程
- 中间件继续由 `docker-compose-environment.yml` 统一管理
- 开发者在首次拉起后，不需要因为普通源码修改反复执行 `docker compose up --build`

## 现状问题

- `docker-compose-local.yml` 当前仍然把前后端当成“构建后运行”的镜像，源码没有挂载到容器
- 前端容器虽然跑的是 `npm run dev`，但镜像内源码是构建时快照，修改宿主机文件不会进入容器
- 后端容器直接运行打包好的 JAR，任何 Java 代码修改都必须重建镜像

## 方案

### 1. 前端开发容器

前端开发容器改成源码挂载模式：

- 镜像只提供 Node 22 运行时
- `mozhi-web/` 通过 bind mount 挂载到容器工作目录
- `node_modules` 使用命名卷隔离，避免被源码挂载覆盖
- 容器启动时检测依赖目录是否为空；为空则执行 `npm ci`
- 开启 `CHOKIDAR_USEPOLLING=true`，保证 Windows + Docker Desktop 下文件改动能稳定触发 Vite

这样前端源文件修改后可以直接触发浏览器热更新。

### 2. 后端开发容器

后端开发容器改成“源码挂载 + 自动重新打包重启”模式：

- 镜像使用 `maven:3.9.9-eclipse-temurin-21`
- 容器安装 `entr`，用于监控源码文件变化并重新执行 `mvn package + java -jar`
- `mozhi-backend/` 通过 bind mount 挂载到容器工作目录
- Maven 本地仓库使用命名卷缓存，避免每次拉起重新下载依赖
- `entr` 监听 `*.java`、`*.xml`、`*.yml`、`*.yaml`，一旦文件变化就重启 Spring Boot 进程

这样后端虽然不是 JVM 级别热替换，但能够在容器内自动重新打包并重启，不再需要重建镜像。

### 3. 文档和运行约定

README 与 DevOps 文档同步调整：

- 首次启动仍推荐 `docker compose ... up --build`
- 日常开发只需 `docker compose ... up`
- 明确说明：
  - 前端是实时热更新
  - 后端是文件变更后自动重启
  - 修改 Dockerfile 或基础镜像时才需要重新 `--build`

## 风险与边界

- 后端自动重启不是无缝热替换，保存 Java 文件后会经历一次短暂重启窗口
- Windows 文件监听依赖轮询和 bind mount，性能优先级低于“能稳定工作”
- 本次不引入额外反向代理或复杂开发编排，仍维持 `5173 + 8090` 的本地调试模型

## 验证

- 运行契约测试校验 local compose 已使用 bind mount、命名卷与开发命令
- `docker compose ... config` 必须通过
- 前端 `npm run test`、`npm run lint`、`npm run build` 必须继续通过
- 后端 `.\mvnw.cmd -q -pl mozhi-app -am test` 必须继续通过
- 本地 `docker compose ... up -d --build` 后，前端和后端端口可访问
