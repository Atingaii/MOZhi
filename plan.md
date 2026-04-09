

# 墨知平台 — 逐步开发计划（纯规划版）

---

## 总原则

```
每一步都遵循同一个节奏：

  明确目标 → 设计确认 → 编码实现 → 单元测试 → 集成测试 → 联调验收 → 代码审查 → 合入主干

任何一步的产出物，都必须是「可独立运行、可独立验证」的，
不依赖后续模块才能跑起来。
```

## 术语与工程边界

- 后端采用**单仓库 + 六模块 Maven 工程**，固定模块为：`mozhi-types`、`mozhi-api`、`mozhi-domain`、`mozhi-infrastructure`、`mozhi-trigger`、`mozhi-app`。
- 文中提到的 `auth`、`user`、`content`、`social`、`commerce`、`storage`、`ai`、`search`、`feed`、`counter` 均指 **domain slice / bounded context**，不是新的顶层 Maven 模块。
- 中间件统一走 **Docker Compose**，并按阶段启用；未进入对应 Phase 前，不强行拉起无关中间件。

---

## Phase 0 · 基座搭建（第 1～2 周）

### 目标

搭建一个"空壳但能跑"的全栈工程骨架，所有后续模块在这个骨架上即插即用。

### Step 0.1 — 后端 Maven 多模块工程初始化（Day 1）

**做什么**：创建并固化六模块后端工程结构，补齐父 POM 的统一依赖管理、测试插件管理、Maven Wrapper，以及面向骨架的最小自检测试。这个 Step 不新增业务实现，只保证“工程能稳定编译、能稳定跑测试、团队无需依赖本机 Maven 差异”。

**怎么验证**：在 `mozhi-backend/` 目录执行 `mvn clean compile` 通过；在 `mozhi-backend/` 目录使用 `mvnw.cmd -q -pl mozhi-app -am test` 通过，证明 Wrapper 和测试基线可用。

### Step 0.2 — 共享基础能力归位（Day 2～3）

**做什么**：将共享基础能力按现有六模块归位：`mozhi-types` 放枚举、异常、事件、常量；`mozhi-trigger`/`mozhi-app` 放统一响应体、全局异常处理器和 Web 层公共配置；避免再新增一个脱离现有骨架的 `mozhi-common` 模块。

**怎么验证**：为共享类型和公共工具编写单元测试；在 `mozhi-trigger` 与 `mozhi-app` 中引入相关能力后，编译与测试均通过。

### Step 0.3 — Docker Compose 中间件环境（Day 4～5）

**做什么**：维护 `docs/dev-ops/docker-compose-environment.yml`，通过 Docker 提供本地开发所需中间件。Phase 0 先保障 `MySQL + Redis + MinIO + Kafka` 可用；`Elasticsearch`、`Milvus`、`Canal` 在进入搜索、RAG、Outbox 链路 Phase 时再追加到 Compose 文件。编写 MySQL 初始化脚本，自动创建业务库。

**怎么验证**：执行 `docker compose -f docs/dev-ops/docker-compose-environment.yml up -d`，等待容器启动。逐一验证：MySQL 可连接且业务库已存在；Redis `PING` 返回 `PONG`；Kafka 能创建和消费测试 Topic；MinIO 控制台可登录。未进入当前 Phase 的中间件不作为阻塞项。

### Step 0.4 — 数据库版本管理（Day 5）

**做什么**：引入 Flyway，并统一放在 `mozhi-app/src/main/resources/db/migration/` 下按业务库或上下文分目录管理。Phase 0 只放占位迁移文件和目录约定，后续各 Phase 按需追加建表脚本。

**怎么验证**：启动一个最简单的 Spring Boot Application，Flyway 自动执行迁移，`flyway_schema_history` 表中有记录。

### Step 0.5 — 前端项目初始化（Day 6～7）

**做什么**：使用 Vite 创建 React + TypeScript 项目。安装 TailwindCSS + Shadcn/UI + React Router + Zustand + TanStack Query + Axios。配置 `tailwind.config.js` 中的墨知品牌色系。搭建全局布局骨架：顶部导航栏（Logo + 搜索框占位 + 右侧按钮占位）、双列布局容器（主栏 694px + 侧边栏 296px）、移动端底部 Tab 栏。所有内容用静态占位文字和骨架屏组件填充。

**怎么验证**：`npm run dev` 启动后，浏览器可看到知乎风格的空壳布局，响应式断点切换正常（桌面双栏、平板单栏、移动端底部 Tab）。

### Step 0.6 — 前后端联调基础设施（Day 8）

**做什么**：在 `mozhi-app` + `mozhi-trigger` 的现有骨架中配置 CORS。编写一个 `GET /api/health` 健康检查接口，返回统一响应。前端 Axios 实例配置 `baseURL`，调用此接口。引入 SpringDoc OpenAPI，配置 Swagger UI。

**怎么验证**：前端页面加载后成功调用后端健康检查接口并展示返回数据。浏览器访问 `http://localhost:8090/swagger-ui/index.html` 可看到 API 文档界面。

### Step 0.7 — Git 规范与自检（Day 8）

**做什么**：初始化 Git 仓库，配置 `.gitignore`（排除 target/、node_modules/、.idea/、.env 等）。约定分支策略（main + develop + feature/xxx）。约定 commit message 格式（如 `feat(auth): 实现登录接口`）。编写 `README.md` 说明项目启动步骤。

**怎么验证**：新成员按 README 步骤可以从零启动整个开发环境。

### Phase 0 交付验收标准

```
□  mvn clean compile 全部模块通过
□  在 `mozhi-backend/` 目录执行 `mvnw.cmd -q -pl mozhi-app -am test` 通过
□  `docker compose -f docs/dev-ops/docker-compose-environment.yml up -d` 可拉起当前 Phase 所需中间件
□  前端 npm run dev 可看到布局骨架
□  前端成功调用后端 /api/health 接口
□  Swagger UI 可访问
□  Git 仓库初始化完成，README 可指导新人启动
```

---

## Phase 1 · 认证域 + 用户域（第 3～4 周）

### 目标
实现用户注册、登录、JWT 双令牌认证、令牌刷新与撤销、基础用户信息管理。这是所有业务模块的前置依赖——没有认证，后续所有接口都无法鉴权。

### Step 1.1 — 用户域：表结构与基础 CRUD（Day 1～2）

**做什么**：设计 `user` 表（id、username、email、password_hash、nickname、avatar_url、bio、status、created_at、updated_at）。编写 Flyway 迁移脚本建表。实现 User 聚合根、UserRepository 接口与 MyBatis 实现。实现注册接口 `POST /api/user/register`（参数校验 + 密码 BCrypt 加密 + 唯一性检查 + 入库）。实现用户信息查询接口 `GET /api/user/{userId}`。

**怎么测试**：针对 UserRepository 编写集成测试（用 Testcontainers 启动 MySQL 容器，验证 CRUD）。用 Postman/Swagger 手动调用注册接口，验证注册成功、重复注册被拒绝、参数校验生效。

### Step 1.2 — 认证域：JWT 双令牌签发（Day 3～4）

**做什么**：生成 RS256 密钥对（私钥签发、公钥验签）。实现 TokenService（签发 AccessToken 15 分钟 + RefreshToken 7 天）。实现登录接口 `POST /api/auth/login`（验证用户名密码 → 签发双令牌 → RefreshToken 写入 Redis 白名单）。实现刷新接口 `POST /api/auth/refresh`（验证旧 RefreshToken → 白名单校验 → 旧令牌失效 → 签发新双令牌 → 令牌旋转）。

**怎么测试**：为 TokenService 编写单元测试（签发 → 解析 → 验证签名 → 验证 Claims）。集成测试：注册 → 登录拿到双令牌 → 用 AccessToken 调用受保护接口 → 成功。等 15 分钟（或用短有效期测试配置）→ AccessToken 过期 → 返回 401 → 用 RefreshToken 刷新 → 拿到新令牌 → 再次访问成功。

### Step 1.3 — 认证域：Spring Security 过滤链 + 令牌撤销（Day 5～6）

**做什么**：自定义 `JwtAuthenticationFilter`（提取 Bearer Token → RS256 验签 → 检查黑名单 → 注入 SecurityContext）。配置 Spring Security 过滤链（放行注册/登录/刷新接口，其余接口需认证）。实现登出接口 `POST /api/auth/logout`（删除 Redis 白名单 + AccessToken 加入黑名单）。实现「踢出全部设备」接口（清除 `user:session:{uid}` 中所有 jti）。

**怎么测试**：不带 Token 访问受保护接口 → 401。带合法 Token 访问 → 200。登出后用旧 Token 访问 → 401。刷新后用旧 RefreshToken 再刷新 → 被拒绝（旋转生效）。踢出全部设备后，所有旧 Token 失效。

### Step 1.4 — 用户域：个人资料与头像上传（Day 7～8）

**做什么**：实现 `PUT /api/user/profile`（修改昵称、简介）。在现有六模块骨架内落地 `storage` 子域，实现 MinIO 预签名上传服务。实现 `POST /api/user/avatar/presign`（返回预签名 URL）。前端对接头像上传（请求预签名 → 直传 MinIO → 回调确认 → 更新 avatar_url）。

**怎么测试**：调用预签名接口获得 URL → 用 curl 模拟 PUT 上传文件到 MinIO → 在 MinIO 控制台确认文件存在。前端上传头像 → 个人资料页展示新头像。

### Step 1.5 — 前端：认证流程全联调（Day 9～10）

**做什么**：开发登录页和注册页。实现 Zustand AuthStore（存储 token 和用户信息）。实现 Axios 拦截器（401 自动调用刷新接口 → 重试原请求 → 并发刷新时排队等待）。实现路由守卫（未登录跳转登录页）。开发个人资料编辑页。

**怎么测试**：完整用户旅程：打开首页 → 被重定向到登录页 → 注册新账号 → 登录成功 → 跳转首页 → 修改头像和昵称 → 刷新页面 Token 自动恢复 → 登出 → 回到登录页。打开浏览器 DevTools Network 面板，观察 Token 过期后自动刷新的请求流。

### Phase 1 交付验收标准

```
□  注册接口：参数校验、唯一性检查、BCrypt 加密
□  登录接口：返回 AccessToken + RefreshToken
□  受保护接口：无 Token 返回 401，有合法 Token 返回 200
□  刷新接口：旧令牌旋转失效，新令牌可用
□  登出接口：旧令牌立即失效
□  前端完整认证流程可走通
□  头像预签名上传 + MinIO 直传可用
□  单元测试 + 集成测试覆盖核心逻辑
```

---

## Phase 2 · 内容域 + 存储域（第 5～7 周）

### 目标

实现渐进式内容发布（草稿 → 上传媒体 → 发布）、富媒体直传、文章详情、评论功能、AI 摘要生成。

### Step 2.1 — 内容域：表结构与草稿 CRUD（Day 1～2）

**做什么**：设计 `note` 表、`draft` 表、`media_ref` 表。Flyway 建表。实现草稿创建、查询、更新、删除接口。实现渐进式状态枚举 `DRAFT → UPLOADING → PENDING_REVIEW → PUBLISHED → REJECTED → ARCHIVED`。

**怎么测试**：创建草稿 → 查询草稿列表 → 更新草稿内容 → 删除草稿。验证状态流转：只能按合法路径推进，非法状态跳转被拒绝。

### Step 2.2 — 存储域：通用预签名直传与草稿媒体绑定（Day 3～4）

**做什么**：实现通用存储预签名接口 `POST /api/storage/presign`，由后端统一生成 `uploadTicket`、上传 URL、`provider / bucket / objectKey` 等存储定位信息。上传确认不再走独立的存储确认接口，而是由内容域草稿媒体绑定接口 `POST /api/content/drafts/{draftId}/media/confirm` 完成：后端先验票，再对对象存储做元数据探测，校验对象存在、`content-type`、字节数、`etag` 等信息后，将媒体写入 `media_ref`。`media_ref` 需要具备 `storage_provider / bucket_name / object_key / file_name / size_bytes / etag / upload_status / bound_at` 等可扩展字段，为后续接入 OSS / S3 / COS / CDN 保留统一抽象。

**当前这一步覆盖的真实场景**：
- 对象路径由后端统一生成，格式类似 `drafts/{userId}/{draftId}/{yyyyMMdd}/{uuid}.{ext}`，客户端不能自定义存储前缀，避免路径污染和越权写入。
- 预签名票据是“带上下文的上传授权”，会绑定 `userId / draftId / purpose / mediaType / contentType / declaredSizeBytes / provider / bucket / objectKey`，而不是裸 URL。
- 确认阶段不信任客户端“上传成功”的口头声明，而是由后端主动探测对象存储，核对存在性和元数据后再入库。
- 只有当前用户自己的、且仍可写的草稿允许申请上传和确认绑定；冻结态草稿禁止继续绑媒体，保持内容写模型一致性。
- 同一个对象重复确认要幂等；如果对象已经被其他草稿或其他用户绑定，需要拒绝脏绑定。
- 业务表不能只存 `publicUrl`，而是保留 `provider + bucket + objectKey + metadata` 作为稳定事实，方便后续做 CDN 域名切换、回源、去重、审核和发布冻结。
- 当前 provider 以 Local Mock / MinIO 为主，但领域和接口契约已保持通用，后续接阿里云 OSS、腾讯 COS、AWS S3 时只需要补基础设施适配层。

**怎么测试**：创建草稿 → 请求预签名票据 → 用 curl 或前端直传对象 → 调用草稿媒体确认接口 → 查询草稿媒体列表，验证 `upload_status=CONFIRMED`。另外验证：非法 `content-type` 被拒绝；篡改的 `uploadTicket` 被拒绝；非本人草稿返回 `404`；冻结态草稿不能确认；重复确认同一对象保持幂等。

**后续增强（写入计划，当前不阻塞主线）**：
- 未确认对象清理：定时扫描长期停留在 `PRESIGNED` 的对象和记录，自动清理桶内垃圾文件与过期票据。
- 媒体安全处理：接入病毒扫描、内容安全审核、图片/附件风控状态流转。
- 大文件与视频：支持 multipart / 分片上传、断点续传、异步合并与失败重试。
- 媒体处理流水线：图片压缩、缩略图生成、格式转换、视频转码与封面抽帧。
- CDN / OSS 演进：provider 扩展到 OSS / S3 / COS，`publicUrl` 切换 CDN 域名，必要时补签名下载、回源和缓存策略。

### Step 2.3 — 内容域：发布流程与 Kafka 事件（Day 5～6）

**做什么**：实现发布接口 `POST /api/note/publish`（校验草稿完整性 → 推进状态至 PUBLISHED → 写入 note 表 → 发送 `NotePublishedEvent` 到 Kafka `content-events` Topic）。此时 Kafka 消费者可以先只做日志打印，后续 Phase 由搜索域、Feed 域、AI 域各自消费。

**怎么测试**：创建草稿 → 上传图片 → 提交发布 → 验证 note 表有记录 → Kafka `content-events` Topic 中有消息（用 Kafka 控制台或命令行工具查看）。发布后草稿状态变为 PUBLISHED，不可再次编辑。

### Step 2.4 — AI 摘要：集成 DeepSeek（Day 7）

**做什么**：引入 Spring AI，配置 DeepSeek 兼容的 ChatClient。实现 AiSummaryService（接收 Markdown 正文 → 调用大模型生成摘要 → 写入 note.summary）。在发布流程中异步调用摘要生成（发布接口立即返回，摘要在后台生成后更新）。

**怎么测试**：发布一篇长文 → 等待几秒后查询文章详情 → summary 字段已填充。DeepSeek API 不可用时 → 摘要字段为空，不影响发布主流程（降级处理）。

### Step 2.5 — 内容域：文章详情与评论（Day 8～10）

**做什么**：实现文章详情接口 `GET /api/note/{noteId}`（返回标题、正文、摘要、作者信息、媒体列表、创建时间）。设计 `comment` 表。实现评论发布 `POST /api/note/{noteId}/comment`，评论列表查询（支持楼中楼回复、分页）。

**怎么测试**：查看文章详情返回完整数据。发表评论 → 评论列表中可见。回复评论 → 层级关系正确。分页参数验证。

### Step 2.6 — 前端：编辑器 + 详情页 + 评论区（Day 11～14）

**做什么**：集成 Markdown 编辑器组件。实现图片拖拽上传（编辑器中插入图片 → 自动触发预签名直传 → 上传完成后插入 Markdown 图片语法）。开发文章详情页（Markdown 渲染 + 作者信息 + 媒体展示）。开发评论区组件（评论列表 + 发表评论 + 回复）。开发草稿箱页面。

**怎么测试**：完整内容创作旅程：新建草稿 → 编辑 Markdown → 拖拽插入图片 → 图片上传成功并预览 → 发布 → 跳转详情页 → 看到渲染后的文章 + AI 摘要 → 发表评论 → 看到评论列表。

### Phase 2 交付验收标准

```
□  草稿 CRUD 与状态机流转正确
□  MinIO 预签名直传链路完整可用
□  文章发布后 Kafka 事件成功发送
□  AI 摘要异步生成，降级不影响主流程
□  文章详情接口返回完整数据
□  评论发布 + 楼中楼回复 + 分页正确
□  前端编辑器 + 图片直传 + 详情页 + 评论区联调通过
```

---

## Phase 3 · 社交域 + 计数域（第 8～10 周）

### 目标

实现关注/取关、点赞/收藏、实时计数，打通 Outbox + Canal + Kafka 事件驱动链路。

### Step 3.1 — 关注功能：主表 + Outbox 事务写入（Day 1～2）

**做什么**：设计 `following` 表、`follower` 表、`outbox` 表，Flyway 建表。实现关注接口 `POST /api/social/follow`（同一事务中写入 following 表和 outbox 表）。实现取关接口。实现关注状态查询。

**怎么测试**：用户 A 关注用户 B → following 表有记录 → outbox 表有对应事件记录 → 二者在同一事务中（模拟 outbox 插入失败 → following 也回滚）。重复关注 → 被拒绝（幂等）。不能关注自己。

### Step 3.2 — Canal + Kafka 事件链路（Day 3～4）

**做什么**：配置 Canal 订阅 `mozhi_auth.outbox` 表的 Binlog。配置 Canal 将变更事件发送到 Kafka `relation-events` Topic。编写 Kafka 消费者：消费关注/取关事件 → 更新 follower 粉丝表 → 更新 Redis 计数 → 清除关注列表缓存。

**怎么测试**：用户 A 关注用户 B → 检查 Canal 是否捕获到 outbox 的 INSERT Binlog → Kafka Topic 中是否有消息 → follower 表是否同步写入 → Redis 计数是否 +1。取关后验证反向操作。故意关闭消费者 → 关注操作仍然成功（主表写入不受影响）→ 重启消费者后消息被消费，数据最终一致。

### Step 3.3 — 计数系统：SDS 紧凑计数 + Lua（Day 5～6）

**做什么**：实现 Redis SDS 紧凑二进制计数（笔记维度：点赞/收藏/评论，用户维度：关注/粉丝/笔记数）。编写 Lua 原子更新脚本。实现计数读取接口（解码二进制 → 返回各字段计数）。实现采样一致性校验定时任务（随机抽样对比 Redis 与 MySQL）。实现自愈重建逻辑（差异超阈值 → 以 MySQL 为准重建 Redis）。

**怎么测试**：手动调用 Lua 脚本 +1/-1 → 读取验证计数正确。并发测试：用 100 个线程同时对同一个 key +1 → 最终计数应为 100（原子性验证）。故意制造 Redis 与 MySQL 不一致 → 定时任务检测到并自动重建。计数不会出现负数（边界校验）。

### Step 3.4 — 点赞系统：分片位图 + 异步写聚合（Day 7～9）

**做什么**：实现点赞接口 `POST /api/social/like`（Redis 分片位图 GETBIT 判重 → SETBIT 标记 → 发送 Kafka `like-events`）。实现取消点赞接口。编写 Kafka 写聚合消费者（按时间窗口/批次聚合 → 批量写入 MySQL 明细表 + 聚合更新计数）。实现收藏功能（结构与点赞相似，独立位图和 Topic）。

**怎么测试**：点赞 → 位图对应 bit 为 1 → Kafka 有消息 → 消费者聚合写入 MySQL。重复点赞 → 位图判重拦截，不发送 Kafka 消息（幂等）。取消点赞 → bit 归 0。写聚合验证：短时间内 1000 次点赞 → MySQL 只执行少量批量写入（查看 MySQL 慢查询日志或写入次数）。Redis 计数缺失时 → 基于位图 BITCOUNT 重建。

### Step 3.5 — 前端：社交互动组件联调（Day 10～12）

**做什么**：开发关注/取关按钮组件（状态切换 + hover 效果）。开发点赞/收藏按钮组件（乐观更新 + 微动效）。开发关注列表和粉丝列表页面。开发个人主页（展示用户信息 + 关注数/粉丝数/获赞数 + 文章列表）。

**怎么测试**：点击关注按钮 → UI 即时切换为"已关注" → 刷新页面状态保持。点赞按钮 → 数字 +1 + 红色动画 → 再次点击取消 → 数字 -1。个人主页计数与实际数据一致。不同浏览器登录不同账号，互相关注后验证粉丝列表。

### Phase 3 交付验收标准

```
□  关注/取关事务写入 + Outbox 原子性保证
□  Canal → Kafka → 消费者链路全通
□  粉丝表、计数、缓存作为伪从正确同步
□  SDS 紧凑计数 Lua 原子更新 + 并发安全
□  采样校验 + 自愈重建机制可用
□  点赞位图判重 + Kafka 写聚合 + 批量入库
□  前端社交组件联调通过
□  消费者宕机后重启能追上消息（最终一致性验证）
```

---

## Phase 4 · Feed 域 + 搜索域（第 11～13 周）

### 目标

实现首页 Feed 流的高性能加载（三级缓存 + 热点探测 + 单飞锁），以及内容搜索与联想建议。

### Step 4.1 — Feed 流：基础查询 + L3 片段缓存（Day 1～2）

**做什么**：实现 Feed 流基础接口 `GET /api/feed?channel=recommend&page=1`（从 MySQL 查询 → 返回笔记列表）。加入 L3 Redis 片段缓存（单条笔记详情缓存，TTL 30min）。Feed 接口先查 L3 缓存拼装页面，缺失则查 MySQL 并回写 L3。

**怎么测试**：首次请求 → 查 MySQL → L3 缓存写入。第二次请求 → 命中 L3 缓存，响应时间明显降低。手动删除 L3 缓存 → 下次请求重新回源。

### Step 4.2 — Feed 流：L2 页面缓存 + L1 Caffeine（Day 3～4）

**做什么**：加入 L2 Redis 页面缓存（缓存完整的一页数据，TTL 5min）。加入 L1 Caffeine 本地缓存（TTL 30s，容量限制 1000 条）。请求优先查 L1 → L2 → L3 → MySQL，逐级回填。

**怎么测试**：连续请求同一页面 → 第一次回源 MySQL，第二次命中 L2，第三次命中 L1（通过日志或 metrics 观察各级命中率）。等 30s L1 过期 → 降级到 L2。等 5min L2 过期 → 降级到 L3/MySQL。

### Step 4.3 — Feed 流：HotKey 探测 + 随机抖动 + SingleFlight（Day 5～6）

**做什么**：实现进程内滑动窗口 HotKey 探测器（统计每个 key 的 10s 内访问频率，超阈值标记为热点）。热点 key 自动延长各级缓存 TTL（如 L1 30s→120s，L2 5min→15min）。所有 TTL 叠加 ±20% 随机抖动。实现 SingleFlight（同一个 key 并发回源时只有一个线程执行查询，其余等待共享结果）。

**怎么测试**：对同一页面高频请求 → 观察该 key 被标记为热点 → TTL 自动延长。停止高频请求 → 热点标记消失 → TTL 恢复正常。并发 50 个线程同时请求一个未缓存的页面 → MySQL 只收到 1 次查询（SingleFlight 生效）→ 50 个线程都拿到正确结果。

### Step 4.4 — Feed 流：缓存一致性策略（Day 7）

**做什么**：消费 Kafka `content-events`（文章发布/更新/删除）→ 删除 L3 片段缓存 → 删除关联的 L2 页面缓存 → 广播事件清除各实例 L1 Caffeine。实现延迟双删（500ms 后再次删除 L2/L3 兜底主从延迟）。

**怎么测试**：发布新文章 → Feed 流中出现新文章。编辑文章标题 → 刷新 Feed → 看到新标题（而非旧缓存）。删除文章 → Feed 流中消失。验证延迟双删：人为制造主从延迟场景下缓存不会出现脏数据。

### Step 4.5 — 搜索系统：ES 索引 + 数据同步（Day 8～9）

**做什么**：创建 ES 索引 Mapping（title、content、summary 使用 IK 分词，tags 为 keyword，likeCount 为 long，publishTime 为 date，suggestion 为 completion 类型）。编写 ES Sink Consumer 消费 Kafka `content-events` → 增量更新 ES 索引（发布→UPSERT，删除→DELETE）。全量同步脚本（首次或数据修复时从 MySQL 全量导入 ES）。

**怎么测试**：发布文章 → Kafka 事件 → ES Sink Consumer 消费 → ES 中可查到该文档。更新文章 → ES 文档同步更新。删除文章 → ES 文档被移除。全量同步脚本执行后 MySQL 与 ES 文档数一致。

### Step 4.6 — 搜索系统：检索 + 排序 + 联想（Day 10～11）

**做什么**：实现搜索接口 `GET /api/search?q=xxx&tags=xxx&page=xxx`。使用 function_score 混合排序（BM25×0.6 + log1p(likeCount)×0.25 + 时间衰减×0.15）。使用 search_after 游标分页替代 from+size。实现联想建议接口 `GET /api/search/suggest?prefix=xxx`（ES Completion Suggester）。

**怎么测试**：搜索关键词 → 返回相关结果，高亮关键词。相同关键词下，高赞 + 新发布的文章排名靠前。深分页测试：翻到第 100 页，响应时间仍稳定（对比 from+size 方案）。输入前缀 → 联想建议返回 Top5 匹配结果，延迟 < 50ms。搜索不存在的词 → 返回空结果，不报错。

### Step 4.7 — 前端：Feed 流 + 搜索联调（Day 12～14）

**做什么**：开发首页 Feed 流组件（Tab 切换频道 + 无限滚动加载 + 骨架屏加载态）。开发搜索框组件（输入防抖 300ms → 调用联想建议 → 下拉展示建议列表）。开发搜索结果页（关键词高亮 + 标签筛选 + 游标分页）。

**怎么测试**：首页加载 → 展示推荐 Feed → 下滑自动加载下一页 → 切换到"关注"Tab 内容变化。搜索框输入"React" → 下拉出现联想建议 → 点击建议跳转搜索结果页 → 关键词高亮 → 点击标签筛选 → 结果更新。

### Phase 4 交付验收标准

```
□  Feed 三级缓存命中率可观测（日志/metrics）
□  HotKey 探测 + 动态 TTL 生效
□  SingleFlight 防并发回源生效（MySQL 查询次数不膨胀）
□  缓存一致性：内容变更后 Feed 及时更新
□  ES 索引增量同步 + 全量同步可用
□  搜索结果相关性合理，排序正确
□  search_after 深分页性能稳定
□  联想建议延迟 < 50ms
□  前端 Feed 流 + 搜索全链路联调通过
```

---

## Phase 5 · AI 域（第 14～15 周）

### 目标

实现 RAG 知识问答系统，用户可以围绕任意文章进行 AI 智能问答。

### Step 5.1 — 知识索引：分块 + 向量化 + 入库（Day 1～3）

**做什么**：实现 ChunkingService（512 token 滑动窗口 + 50 token 重叠分块）。集成 Embedding 模型（通过 Spring AI 调用 text-embedding 接口）。实现向量入库（分块 → 向量化 → 写入 Milvus，metadata 包含 noteId、chunkIndex、原文）。消费 Kafka `content-events`，文章发布时异步触发预索引。文章更新时幂等删除旧版本全部向量 → 重新分块入库。

**怎么测试**：发布一篇文章 → Kafka 事件触发 → Milvus 中出现该文章的多个向量分块。更新文章 → Milvus 旧向量全部删除 → 新向量写入（版本唯一性）。删除文章 → Milvus 对应向量全部清除。分块数量验证：一篇 2000 token 的文章 → 应产生约 4 个分块。

### Step 5.2 — 问答流程：检索 + Prompt + 流式生成（Day 4～6）

**做什么**：实现问答接口 `GET /api/qa/ask?noteId=xxx&question=xxx`（SSE 流式响应）。流程：索引检查 → 问题向量化 → Milvus Top-K 检索 → 相关性过滤（score > 阈值） → Prompt 构造（System + Context + Question） → 大模型流式生成 → SSE 逐 token 推送。处理未索引情况：返回"正在准备索引"提示，后台触发即时索引。

**怎么测试**：针对已索引的文章提问 → SSE 流式返回答案 → 答案内容基于文章上下文（而非模型胡编）。提问与文章无关的内容 → 回答中明确告知"上下文中没有相关信息"。首次对未索引文章提问 → 收到"正在准备"提示 → 稍后再问 → 正常回答。并发多人对同一文章提问 → 各自独立收到流式回答，不串流。

### Step 5.3 — 前端：AI 问答对话组件（Day 7～10）

**做什么**：在文章详情页右下角开发 AI 问答悬浮面板（可展开/收起）。实现 SSE 流式接收与逐字渲染（打字机效果 + 末尾闪烁光标）。对话历史在本地 state 中维护（当前会话内）。输入框回车发送或点击发送按钮。

**怎么测试**：打开文章详情 → 展开 AI 面板 → 输入问题 → 看到逐字输出的回答。连续提多个问题 → 对话历史完整展示。网络中断时 → 优雅提示错误，不崩溃。收起面板 → 再展开 → 对话历史仍在。

### Phase 5 交付验收标准

```
□  文章发布后 Milvus 中自动创建向量索引
□  文章更新后旧向量清除 + 新向量写入
□  问答回答基于文章上下文，不编造信息
□  SSE 流式输出正常，前端逐字渲染
□  未索引文章的降级处理正确
□  并发问答互不干扰
```

---

## Phase 6 · 商城域（第 16～19 周）

### 目标

实现商品管理、拼团购买、单独购买、订单管理、人群标签、差异化消费策略。这是最复杂的一个 Phase，拆为 5 个 Step。

### Step 6.1 — 商品管理：CRUD + 库存（Day 1～3）

**做什么**：设计 `product` 表、`group_buying_activity` 表，Flyway 建表。实现商品 CRUD（创建、编辑、上下架、列表查询、详情查询）。实现 Redis 库存预加载（商品上架时将库存写入 Redis `stock:{productId}`）。

**怎么测试**：创建商品 → 查询列表可见。编辑商品信息 → 详情更新。下架商品 → 列表中消失。Redis 库存值与 MySQL 一致。

### Step 6.2 — 拼团系统：发起 + 参团 + 过期（Day 4～7）

**做什么**：设计 `group_order` 表，Flyway 建表。实现发起拼团接口（创建拼团单 → Redis 状态机初始化 → 团长自动加入）。实现参与拼团接口（Redis Lua 原子操作：检查人数 + 检查重复 + 加入 + 判断是否成团）。实现拼团过期处理（Kafka 延迟消息或定时任务扫描 → 过期的团标记为 EXPIRED → 回补库存）。成团后发送 Kafka `group-events` → 触发创建订单逻辑。

**怎么测试**：用户 A 发起 3 人团 → Redis 中 group 状态为 FORMING，currentCount=1。用户 B 参团 → currentCount=2。用户 C 参团 → currentCount=3 → 状态自动变为 FULL → Kafka 收到成团事件。用户 D 尝试参团 → 被拒绝（已满）。用户 A 重复参团 → 被拒绝（已在团中）。创建一个团不参与 → 等待过期时间到达 → 状态自动变为 EXPIRED。并发 10 人同时参加一个 3 人团 → 只有 2 人成功加入（Lua 原子性验证）。

### Step 6.3 — 订单系统：下单 + 支付 + 超时取消（Day 8～11）

**做什么**：设计 `order` 表，Flyway 建表。实现下单接口（区分单独购买/拼团购买 → Redis 预扣库存 Lua → 创建订单 → 状态 CREATED）。实现支付回调接口（模拟支付成功 → 订单状态 PAID → Kafka `order-events` → MySQL 扣减持久化库存）。实现超时取消（30 分钟未支付 → 延迟队列/定时任务 → 订单 CANCELLED → Redis 回补库存）。拼团订单特殊逻辑：全员支付完成 → 团单状态 SUCCESS。

**怎么测试**：单独购买下单 → 订单 CREATED → Redis 库存 -1。模拟支付 → 订单 PAID → MySQL 库存 -1。不支付等 30 分钟 → 订单自动 CANCELLED → Redis 库存 +1。拼团购买：3 人都下单支付 → 团单 SUCCESS。3 人中有 1 人超时未付 → 该人订单取消 → 团单状态相应处理。库存为 0 时下单 → 返回"库存不足"。并发 100 人抢购库存为 10 的商品 → 只有 10 人下单成功（Lua 原子预扣）。

### Step 6.4 — 人群标签 + 消费策略（Day 12～15）

**做什么**：设计 `user_tag` 表、`pricing_strategy` 表，Flyway 建表。实现实时标签消费者（消费 Kafka `user-behavior` → 滑动窗口聚合 → 更新用户标签，如浏览偏好、活跃度）。实现离线标签定时任务（全量回算消费能力、价格敏感度、生命周期等标签）。实现策略匹配引擎（用户请求商品详情时 → 加载用户标签 → 匹配活跃策略 → 返回个性化定价方案）。

**怎么测试**：模拟用户浏览行为 → 发送 `user-behavior` 事件 → 用户标签表中"内容偏好"标签更新。执行离线标签任务 → 用户的"消费能力"、"生命周期"等标签被计算。新用户查看商品 → 命中"新用户首单立减"策略 → 接口返回优惠信息。高消费力用户 → 命中"推荐精品"策略。无命中策略 → 返回原价（默认兜底）。

### Step 6.5 — 前端：商城全链路联调（Day 16～20）

**做什么**：开发商品列表页（卡片网格 + 原价/拼团价展示 + 分类筛选）。开发商品详情页（大图 + 价格信息 + 单独购买/发起拼团按钮 + 进行中的团列表 + 个性化优惠横幅）。开发拼团交互（发起拼团 → 分享链接 → 他人加入 → 倒计时 → 成团提示）。开发下单结算页（确认信息 + 优惠展示 + 模拟支付）。开发订单列表和订单详情页。

**怎么测试**：完整购物旅程：浏览商品列表 → 点击进入详情 → 看到个性化优惠 → 发起拼团 → 复制链接给另一个账号 → 另一账号加入 → 凑齐人数成团 → 各自下单支付 → 订单列表中看到已支付订单。单独购买旅程：直接单独购买 → 下单 → 支付 → 完成。超时取消旅程：下单不支付 → 等待超时 → 订单状态变为已取消 → 库存恢复。

### Phase 6 交付验收标准

```
□  商品 CRUD + 上下架 + 库存管理
□  拼团发起 + 参团 + 成团 + 过期全流程
□  并发参团 Lua 原子性保证
□  订单创建 + 支付 + 超时取消 + 库存一致性
□  用户标签实时 + 离线计算可用
□  策略匹配引擎返回个性化方案
□  前端商城全链路联调通过
```

---

## Phase 7 · 可观测性 + 压测 + 上线（第 20～22 周）

### 目标

生产就绪：全链路监控、性能压测、调优、部署。

### Step 7.1 — 监控体系搭建（Day 1～4）

**做什么**：接入 SkyWalking Agent（全链路追踪）。配置 Prometheus + Grafana（JVM 指标、接口 QPS/延迟/错误率、Redis 命中率、Kafka 消费延迟、ES 查询延迟）。配置 ELK 集中日志（Filebeat → Logstash → Elasticsearch → Kibana）。配置告警规则（接口 P99 > 500ms、错误率 > 1%、消费者 Lag > 1000 等）。

**怎么验证**：Grafana 面板可看到各服务实时指标。SkyWalking 可追踪一个请求跨多个服务的完整链路。Kibana 可搜索到各服务的日志。触发一个慢请求 → 告警通知送达。

### Step 7.2 — 压测（Day 5～8）

**做什么**：编写压测脚本（JMeter 或 Gatling），覆盖以下关键路径：Feed 流首页读取（目标 QPS、P99 延迟）。点赞高并发写入（目标 TPS）。拼团参团并发（验证 Lua 原子性不丢不多）。搜索关键词查询（目标 P99 延迟）。AI 问答流式响应（目标首 token 延迟）。

**怎么验证**：每个场景输出压测报告（QPS、TPS、P50/P95/P99 延迟、错误率）。对比目标值，标记通过/不通过。

### Step 7.3 — 性能调优（Day 9～11）

**做什么**：根据压测报告定位瓶颈。常见调优方向：MySQL 慢查询加索引、连接池参数调整。Redis 大 key 拆分、Pipeline 批量操作。Kafka 分区数调整、消费者并发数调整。ES 索引分片数优化、查询 DSL 优化。Caffeine 命中率过低 → 调整容量和 TTL。JVM GC 调优（G1/ZGC 参数调整）。

**怎么验证**：调优后重新压测，指标对比提升。

### Step 7.4 — 容器化部署（Day 12～14）

**做什么**：为每个服务编写 Dockerfile（多阶段构建）。编写 K8s 部署文件（Deployment + Service + ConfigMap + HPA 自动扩容规则）。配置 CI/CD 流水线（Git push → 自动构建 Docker 镜像 → 推送镜像仓库 → 滚动更新 K8s）。配置 Nginx/Ingress 路由。前端构建产物部署至 Nginx 静态服务。

**怎么验证**：代码推送后自动触发流水线 → 镜像构建成功 → K8s 滚动更新 → 服务正常运行 → 监控面板显示新版本实例。手动制造流量高峰 → HPA 自动扩容出新 Pod → 流量下降后自动缩容。

### Phase 7 交付验收标准

```
□  Grafana 监控面板覆盖全部服务关键指标
□  SkyWalking 全链路追踪可用
□  ELK 日志可检索
□  告警规则触发并通知
□  关键路径压测报告达标
□  CI/CD 流水线自动化部署
□  HPA 自动扩缩容验证通过
□  README / 运维文档更新完毕
```

---

## 整体时间线总览

```
周次:  1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19  20  21  22
      ├──────┤──────────┤──────────────┤──────────────┤──────────────┤─────────┤─────────────────┤────────────┤
      Phase0   Phase1      Phase2          Phase3         Phase4      Phase5       Phase6           Phase7
       基座    认证+用户   内容+存储+AI摘要 社交+计数+点赞  Feed+搜索     AI RAG    商城+拼团+标签+策略 监控+压测+上线

每个 Phase 末尾预留 1～2 天做:
  ✦ 代码审查 (Code Review)
  ✦ 技术债务清理 (TODO 项处理)
  ✦ 文档更新 (接口文档、架构图、README)
  ✦ 回归测试 (确保新模块不破坏已有功能)
```

---

## 每个 Step 的标准工作流

无论哪个 Step，都按以下节奏推进：

**第一步：设计确认**。在动手编码之前，明确该 Step 涉及的表结构、接口定义（URL/Method/入参/出参）、领域模型（聚合根/实体/值对象）、关键技术方案。用文档或画图的方式在团队内对齐，避免做到一半推翻重来。

**第二步：建表与迁移**。编写 Flyway 迁移脚本，执行后验证表结构正确。这一步要先于业务代码，因为所有后续开发都依赖表结构。

**第三步：领域层开发**。先写 domain 层（聚合根、值对象、领域服务、仓储接口），这一层是纯 Java 无框架依赖的业务逻辑核心。为领域层编写单元测试（不需要启动 Spring 容器，不需要数据库，纯内存测试），验证业务规则。

**第四步：基础设施层开发**。实现仓储接口的 MyBatis/Redis/ES/Milvus 实现，编写 Kafka 生产者/消费者，实现防腐层适配器。为仓储实现编写集成测试（用 Testcontainers 启动真实中间件容器）。

**第五步：应用层 + 接口层开发**。编写 Application Handler（用例编排），编写 Controller（入参校验 + 调用 Handler + 返回 Result）。编写接口层集成测试（MockMvc 或 WebTestClient 测试完整的 HTTP 请求/响应链路）。

**第六步：前端联调**。前端开发对应的页面和组件，对接后端接口。手动走完完整的用户旅程，确认功能闭环。

**第七步：回归验证**。跑一遍全量自动化测试，确保新模块不破坏已有功能。更新接口文档。代码审查后合入主干。
