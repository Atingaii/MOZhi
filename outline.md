

# 墨知平台 — DDD 分模块开发规划 & 前端设计规范

---

## 一、DDD 战略设计：限界上下文划分

首先从全局视角识别出墨知平台的核心领域、支撑领域和通用领域，划定限界上下文边界。

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     墨知平台 · 领域全景图                                  │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                       核心域 (Core Domain)                      │    │
│  │                                                                 │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │    │
│  │  │  内容域       │  │  社交域      │  │  商城域       │             │    │
│  │  │  Content BC  │  │  Social BC  │  │  Commerce BC │             │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘             │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                     支撑域 (Supporting Domain)                   │    │
│  │                                                                 │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │    │
│  │  │  智能域       │  │  搜索域      │  │  Feed 域     │             │    │
│  │  │  AI / RAG BC │  │  Search BC  │  │  Feed BC    │             │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘             │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                     通用域 (Generic Domain)                      │    │
│  │                                                                 │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │    │
│  │  │  认证域       │  │  用户域      │  │  存储域       │             │    │
│  │  │  Auth BC     │  │  User BC    │  │  Storage BC  │             │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘             │    │
│  │  ┌─────────────┐  ┌─────────────┐                               │    │
│  │  │  计数域       │  │  消息域      │                               │    │
│  │  │  Counter BC  │  │  Message BC │                               │    │
│  │  └─────────────┘  └─────────────┘                               │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 二、上下文映射（Context Map）

各限界上下文之间的协作关系：

```
                          ┌───────────┐
                          │  Auth BC  │
                          └─────┬─────┘
                                │ OHS (开放主机服务: JWT 签发/验证)
                ┌───────────────┼───────────────┐
                ▼               ▼               ▼
          ┌──────────┐   ┌──────────┐    ┌──────────┐
          │ User BC  │   │Content BC│    │Commerce BC│
          └────┬─────┘   └────┬─────┘    └────┬─────┘
               │              │               │
               │    ACL       │  Pub/Sub      │  Pub/Sub
               ▼              ▼               ▼
          ┌──────────┐   ┌──────────┐    ┌──────────┐
          │Social BC │   │ Feed BC  │    │Counter BC│
          └────┬─────┘   └──────────┘    └──────────┘
               │                              ▲
               │  Pub/Sub (关注事件)            │ Pub/Sub (点赞/收藏计数)
               └──────────────────────────────┘

          ┌──────────┐   ┌──────────┐    ┌──────────┐
          │Search BC │   │  AI BC   │    │Storage BC│
          └──────────┘   └──────────┘    └──────────┘
              ▲ ACL          ▲ ACL           ▲ OHS
              │              │               │
          Content BC     Content BC      Content BC
          (内容变更同步)  (文章索引/问答)   (媒体上传)

协作模式说明:
  OHS    = Open Host Service (开放主机服务, 提供标准 API)
  ACL    = Anti-Corruption Layer (防腐层, 隔离外部模型)
  Pub/Sub = 发布-订阅 (通过 Kafka 事件异步协作)
```

---

## 三、DDD 战术设计：各上下文内部建模

### 3.1 内容域（Content BC）

```
聚合根: Note (笔记)
├── 实体: Draft (草稿, 发布前的可变状态)
├── 值对象: NoteContent (标题 + Markdown 正文 + 摘要)
├── 值对象: MediaRef (媒体引用: objectKey + type + size)
├── 值对象: NoteStatus (DRAFT / PENDING_REVIEW / PUBLISHED / REJECTED / ARCHIVED)
├── 领域事件: NotePublishedEvent
├── 领域事件: NoteUpdatedEvent
├── 领域事件: NoteDeletedEvent
└── 领域服务: NotePublishService (渐进式发布状态机推进)

聚合根: Comment (评论)
├── 值对象: CommentContent (正文)
├── 值对象: ReplyTo (被回复的评论ID + 用户ID)
└── 领域事件: CommentCreatedEvent
```

### 3.2 社交域（Social BC）

```
聚合根: Relationship (用户关系)
├── 值对象: FollowPair (followerId + followeeId)
├── 值对象: RelationStatus (FOLLOWING / UNFOLLOWED)
├── 领域事件: UserFollowedEvent
├── 领域事件: UserUnfollowedEvent
└── 领域服务: FollowService (关注/取关 + Outbox 写入)

聚合根: LikeRecord (点赞记录)
├── 值对象: LikeTarget (targetId + targetType: NOTE/COMMENT)
├── 领域事件: ContentLikedEvent
└── 领域事件: ContentUnlikedEvent
```

### 3.3 商城域（Commerce BC）

```
聚合根: Product (商品)
├── 值对象: Price (原价 + 拼团价)
├── 值对象: Stock (库存数量)
├── 值对象: ProductStatus (ON_SHELF / OFF_SHELF)
└── 领域事件: ProductCreatedEvent

聚合根: GroupBuyingActivity (拼团活动)
├── 值对象: GroupRule (成团人数 + 有效期 + 拼团价)
├── 值对象: ActivityPeriod (开始时间 + 结束时间)
└── 领域事件: ActivityLaunchedEvent

聚合根: GroupOrder (拼团单)
├── 实体: GroupMember (团员)
├── 值对象: GroupStatus (FORMING / FULL / SUCCESS / EXPIRED / FAILED)
├── 领域事件: GroupFormedEvent (成团)
├── 领域事件: GroupExpiredEvent (过期)
└── 领域服务: GroupJoinService (参团 Lua 原子操作)

聚合根: Order (订单)
├── 值对象: OrderAmount (原价 + 优惠 + 实付)
├── 值对象: OrderStatus (CREATED / PAID / SHIPPED / COMPLETED / CANCELLED / REFUNDED)
├── 值对象: PayType (SOLO / GROUP)
├── 领域事件: OrderCreatedEvent
├── 领域事件: OrderPaidEvent
└── 领域服务: OrderCreateService (库存预扣 + 策略匹配 + 订单生成)

聚合根: UserProfile (用户画像 — 商城视角)
├── 值对象: UserTag (tagKey + tagValue + score)
├── 领域服务: TagComputeService (标签计算)
└── 领域服务: StrategyMatchService (策略匹配引擎)
```

### 3.4 其他上下文

```
认证域 (Auth BC):
  聚合根: AuthCredential (认证凭证)
  领域服务: TokenService (JWT 签发/刷新/撤销)

用户域 (User BC):
  聚合根: User (用户)
  值对象: UserProfile (昵称 + 头像 + 简介)
  领域事件: UserRegisteredEvent

Feed 域 (Feed BC):
  领域服务: FeedAssembler (三级缓存组装)
  领域服务: HotKeyDetector (热点探测)

搜索域 (Search BC):
  领域服务: SearchService (全文检索 + function_score)
  领域服务: SuggestionService (联想建议)

AI 域 (AI BC):
  聚合根: KnowledgeIndex (知识索引)
  领域服务: ChunkingService (分块)
  领域服务: RagQueryService (检索 + Prompt 构造 + 流式生成)

计数域 (Counter BC):
  领域服务: CounterService (SDS 紧凑计数 + Lua 原子更新)
  领域服务: ConsistencyChecker (采样校验 + 自愈重建)

存储域 (Storage BC):
  领域服务: StorageService (MinIO 预签名 + 对象管理)
```

---

## 四、Maven 工程结构

采用多模块 Maven 项目，每个限界上下文作为一个独立模块，内部按 DDD 分层架构组织。

```
mozhi-platform/
│
├── pom.xml                              (父 POM, 统一依赖版本管理)
│
├── mozhi-common/                        (公共模块: 通用工具、异常、DTO 基类)
│   └── src/main/java/com/mozhi/common/
│       ├── exception/                   (全局异常定义)
│       ├── result/                      (统一响应 Result<T>)
│       ├── util/                        (工具类)
│       └── event/                       (跨上下文共享的事件基类)
│
├── mozhi-auth/                          (认证域)
│   └── src/main/java/com/mozhi/auth/
│       ├── domain/                      (领域层)
│       │   ├── model/                   (聚合根、实体、值对象)
│       │   │   ├── aggregate/
│       │   │   │   └── AuthCredential.java
│       │   │   └── vo/
│       │   │       └── TokenPair.java
│       │   ├── repository/              (仓储接口)
│       │   │   └── AuthCredentialRepository.java
│       │   └── service/                 (领域服务)
│       │       └── TokenService.java
│       ├── application/                 (应用层: 用例编排)
│       │   ├── command/                 (命令)
│       │   │   ├── LoginCommand.java
│       │   │   └── RefreshTokenCommand.java
│       │   ├── handler/                 (命令处理器)
│       │   │   └── AuthCommandHandler.java
│       │   └── dto/                     (数据传输对象)
│       │       └── TokenResponse.java
│       ├── infrastructure/              (基础设施层: 技术实现)
│       │   ├── persistence/             (仓储实现)
│       │   │   ├── mapper/              (MyBatis Mapper)
│       │   │   ├── po/                  (持久化对象)
│       │   │   └── AuthCredentialRepositoryImpl.java
│       │   ├── redis/                   (Redis 操作)
│       │   │   └── TokenRedisGateway.java
│       │   └── security/               (Spring Security 配置)
│       │       ├── JwtAuthenticationFilter.java
│       │       └── SecurityConfig.java
│       └── interfaces/                  (接口层: 对外暴露)
│           └── rest/
│               └── AuthController.java
│
├── mozhi-user/                          (用户域, 结构同上)
│   └── src/main/java/com/mozhi/user/
│       ├── domain/
│       ├── application/
│       ├── infrastructure/
│       └── interfaces/
│
├── mozhi-content/                       (内容域)
│   └── src/main/java/com/mozhi/content/
│       ├── domain/
│       │   ├── model/
│       │   │   ├── aggregate/
│       │   │   │   ├── Note.java               (聚合根)
│       │   │   │   └── Comment.java            (聚合根)
│       │   │   ├── entity/
│       │   │   │   └── Draft.java
│       │   │   └── vo/
│       │   │       ├── NoteContent.java
│       │   │       ├── MediaRef.java
│       │   │       └── NoteStatus.java
│       │   ├── event/
│       │   │   ├── NotePublishedEvent.java
│       │   │   └── NoteUpdatedEvent.java
│       │   ├── repository/
│       │   │   ├── NoteRepository.java
│       │   │   └── DraftRepository.java
│       │   └── service/
│       │       └── NotePublishService.java     (状态机推进)
│       ├── application/
│       │   ├── command/
│       │   │   ├── CreateDraftCommand.java
│       │   │   ├── UploadMediaCommand.java
│       │   │   └── PublishNoteCommand.java
│       │   ├── query/
│       │   │   └── NoteDetailQuery.java
│       │   └── handler/
│       │       ├── ContentCommandHandler.java
│       │       └── ContentQueryHandler.java
│       ├── infrastructure/
│       │   ├── persistence/
│       │   ├── minio/                          (MinIO 防腐层)
│       │   │   └── MinioStorageAdapter.java
│       │   ├── ai/                             (DeepSeek 防腐层)
│       │   │   └── AiSummaryAdapter.java
│       │   └── kafka/
│       │       └── ContentEventPublisher.java
│       └── interfaces/
│           └── rest/
│               ├── NoteController.java
│               └── DraftController.java
│
├── mozhi-social/                        (社交域)
│   └── src/main/java/com/mozhi/social/
│       ├── domain/
│       │   ├── model/aggregate/
│       │   │   ├── Relationship.java
│       │   │   └── LikeRecord.java
│       │   ├── event/
│       │   │   ├── UserFollowedEvent.java
│       │   │   └── ContentLikedEvent.java
│       │   └── service/
│       │       ├── FollowService.java
│       │       └── LikeService.java
│       ├── infrastructure/
│       │   ├── persistence/
│       │   ├── redis/
│       │   │   └── LikeBitmapGateway.java     (分片位图)
│       │   ├── canal/                          (Outbox + Canal)
│       │   └── kafka/
│       │       ├── RelationEventPublisher.java
│       │       └── LikeAggregateConsumer.java  (写聚合消费者)
│       └── interfaces/
│
├── mozhi-commerce/                      (商城域)
│   └── src/main/java/com/mozhi/commerce/
│       ├── domain/
│       │   ├── model/aggregate/
│       │   │   ├── Product.java
│       │   │   ├── GroupBuyingActivity.java
│       │   │   ├── GroupOrder.java
│       │   │   └── Order.java
│       │   ├── model/vo/
│       │   │   ├── Price.java
│       │   │   ├── Stock.java
│       │   │   ├── GroupStatus.java
│       │   │   ├── OrderStatus.java
│       │   │   ├── OrderAmount.java
│       │   │   └── UserTag.java
│       │   ├── event/
│       │   │   ├── GroupFormedEvent.java
│       │   │   ├── OrderCreatedEvent.java
│       │   │   └── OrderPaidEvent.java
│       │   ├── repository/
│       │   │   ├── ProductRepository.java
│       │   │   ├── GroupOrderRepository.java
│       │   │   └── OrderRepository.java
│       │   └── service/
│       │       ├── GroupJoinService.java       (参团领域服务)
│       │       ├── OrderCreateService.java     (下单领域服务)
│       │       ├── TagComputeService.java      (标签计算)
│       │       └── StrategyMatchService.java   (策略匹配引擎)
│       ├── application/
│       │   ├── command/
│       │   │   ├── CreateGroupCommand.java
│       │   │   ├── JoinGroupCommand.java
│       │   │   ├── PlaceOrderCommand.java
│       │   │   └── PayOrderCommand.java
│       │   └── handler/
│       │       ├── GroupCommandHandler.java
│       │       └── OrderCommandHandler.java
│       ├── infrastructure/
│       │   ├── persistence/
│       │   ├── redis/
│       │   │   ├── GroupOrderRedisGateway.java (拼团状态 Lua)
│       │   │   └── StockRedisGateway.java      (库存预扣)
│       │   └── kafka/
│       │       ├── GroupEventPublisher.java
│       │       └── OrderEventConsumer.java
│       └── interfaces/
│           └── rest/
│               ├── ProductController.java
│               ├── GroupBuyingController.java
│               └── OrderController.java
│
├── mozhi-feed/                          (Feed 域)
│   └── src/main/java/com/mozhi/feed/
│       ├── domain/service/
│       │   ├── FeedAssembler.java
│       │   └── HotKeyDetector.java
│       ├── infrastructure/
│       │   ├── cache/
│       │   │   ├── CaffeineFeedCache.java      (L1)
│       │   │   ├── RedisPageCache.java          (L2)
│       │   │   └── RedisFragmentCache.java      (L3)
│       │   └── singleflight/
│       │       └── SingleFlightCache.java
│       └── interfaces/
│
├── mozhi-search/                        (搜索域)
│   └── src/main/java/com/mozhi/search/
│       ├── domain/service/
│       │   ├── SearchService.java
│       │   └── SuggestionService.java
│       ├── infrastructure/
│       │   ├── es/
│       │   │   ├── EsSearchGateway.java
│       │   │   └── EsSinkConsumer.java         (Canal→Kafka→ES 同步)
│       │   └── acl/
│       │       └── ContentAntiCorruptionLayer.java
│       └── interfaces/
│
├── mozhi-ai/                            (AI 域)
│   └── src/main/java/com/mozhi/ai/
│       ├── domain/
│       │   ├── model/aggregate/
│       │   │   └── KnowledgeIndex.java
│       │   └── service/
│       │       ├── ChunkingService.java
│       │       └── RagQueryService.java
│       ├── infrastructure/
│       │   ├── embedding/
│       │   │   └── EmbeddingAdapter.java
│       │   ├── vectorstore/
│       │   │   └── MilvusVectorStoreAdapter.java
│       │   └── llm/
│       │       └── DeepSeekChatAdapter.java
│       └── interfaces/
│           └── rest/
│               └── QaController.java
│
├── mozhi-counter/                       (计数域)
│   └── src/main/java/com/mozhi/counter/
│       ├── domain/service/
│       │   ├── CounterService.java
│       │   └── ConsistencyChecker.java
│       └── infrastructure/redis/
│           └── SdsCounterGateway.java          (SDS 紧凑计数 + Lua)
│
├── mozhi-storage/                       (存储域)
│   └── src/main/java/com/mozhi/storage/
│       ├── domain/service/
│       │   └── StorageService.java
│       └── infrastructure/minio/
│           └── MinioGateway.java
│
├── mozhi-message/                       (消息域)
│   └── src/main/java/com/mozhi/message/
│       ├── domain/
│       └── infrastructure/kafka/
│
└── mozhi-gateway/                       (API 网关)
    └── src/main/java/com/mozhi/gateway/
        ├── filter/
        └── config/
```

---

## 五、DDD 四层架构详解（每个 BC 内部）

```
┌──────────────────────────────────────────────────────────────────────┐
│                    DDD 四层架构 (每个 BC 内部)                         │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  interfaces/ (接口层)                                        │    │
│  │  职责: 接收外部请求, 参数校验, 调用 Application 层             │    │
│  │  内容: RestController, DTO 入参/出参, 参数 Validation          │    │
│  │  原则: 薄层, 不含业务逻辑                                     │    │
│  └──────────────────────────┬───────────────────────────────────┘    │
│                             │ 调用                                    │
│  ┌──────────────────────────▼───────────────────────────────────┐    │
│  │  application/ (应用层)                                       │    │
│  │  职责: 用例编排, 事务管理, 跨聚合协调                          │    │
│  │  内容: Command / Query 对象, Handler (用例处理器), DTO 转换     │    │
│  │  原则: 编排领域对象完成用例, 自身不含核心业务规则               │    │
│  └──────────────────────────┬───────────────────────────────────┘    │
│                             │ 调用                                    │
│  ┌──────────────────────────▼───────────────────────────────────┐    │
│  │  domain/ (领域层) ★ 核心                                     │    │
│  │  职责: 业务规则, 领域逻辑, 不依赖任何外部技术                   │    │
│  │  内容:                                                       │    │
│  │  ├── model/aggregate/   聚合根 (业务入口, 保护不变量)          │    │
│  │  ├── model/entity/      实体 (有唯一标识, 有生命周期)          │    │
│  │  ├── model/vo/          值对象 (无标识, 不可变, 描述特征)      │    │
│  │  ├── event/             领域事件                              │    │
│  │  ├── repository/        仓储接口 (只定义, 不实现)              │    │
│  │  └── service/           领域服务 (跨聚合/无法归属的业务规则)    │    │
│  │  原则: 纯 Java, 零框架依赖, 可独立单元测试                     │    │
│  └──────────────────────────┬───────────────────────────────────┘    │
│                             │ 依赖倒置 (domain 定义接口, infra 实现) │
│  ┌──────────────────────────▼───────────────────────────────────┐    │
│  │  infrastructure/ (基础设施层)                                 │    │
│  │  职责: 技术实现细节                                           │    │
│  │  内容:                                                       │    │
│  │  ├── persistence/       仓储实现 + MyBatis Mapper + PO       │    │
│  │  ├── redis/             Redis 操作封装                        │    │
│  │  ├── kafka/             Kafka 生产者/消费者                    │    │
│  │  ├── es/                Elasticsearch 操作                    │    │
│  │  ├── minio/             MinIO 操作                           │    │
│  │  ├── acl/               防腐层 (隔离外部上下文模型)             │    │
│  │  └── config/            技术配置                              │    │
│  │  原则: 实现 domain 层定义的接口, 隔离技术细节                   │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  依赖方向: interfaces → application → domain ← infrastructure        │
│  domain 层不依赖任何其他层, infrastructure 通过依赖倒置实现 domain 接口 │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 六、分阶段开发路线图

### Phase 0：基座搭建（第 1~2 周）

```
目标: 搭建项目骨架、开发环境、基础设施

 ✦ 初始化 Maven 多模块工程结构
 ✦ 父 POM 统一管理依赖版本 (Spring Boot 3.x BOM, Java 21)
 ✦ mozhi-common 模块: 统一响应 Result<T>, 全局异常处理, 工具类
 ✦ Docker Compose 编排本地开发环境:
   MySQL 8.x + Redis 7.x + Kafka + Zookeeper + MinIO + Elasticsearch + Milvus
 ✦ 前端项目初始化: Vite + React + TypeScript + TailwindCSS + Shadcn/UI
 ✦ 前后端联调约定: API 规范 (RESTful), 错误码体系, 接口文档 (Swagger/OpenAPI)

交付物: 项目骨架可运行, docker-compose up 一键启动全部中间件
```

### Phase 1：认证域 + 用户域（第 3~4 周）

```
目标: 用户注册/登录, JWT 双令牌, 基础用户信息管理

后端:
 ✦ mozhi-auth: JWT RS256 签发/刷新/撤销, Spring Security 过滤链
 ✦ mozhi-user: 用户注册, 个人资料 CRUD, 头像上传
 ✦ mozhi-storage: MinIO 预签名上传服务 (供 user/content 复用)
 ✦ mozhi-gateway: 网关路由配置, JWT 公钥验签, 基础限流

前端:
 ✦ 登录/注册页面
 ✦ Axios JWT 拦截器 (401 自动刷新)
 ✦ 个人资料编辑页

交付物: 可注册、登录、刷新令牌、查看/编辑个人资料
```

### Phase 2：内容域 + 存储域（第 5~7 周）

```
目标: 渐进式发布系统, 富媒体上传, AI 摘要

后端:
 ✦ mozhi-content: 草稿 CRUD, 渐进式发布状态机, 文章详情查询
 ✦ 媒体上传: 后端签发 MinIO 预签名 URL → 前端直传 → 回调确认
 ✦ 评论功能: 评论发布, 楼中楼回复, 分页查询
 ✦ AI 摘要: 集成 Spring AI + DeepSeek, 发布时异步生成摘要

前端:
 ✦ Markdown 编辑器 (推荐 Milkdown 或 ByteMD)
 ✦ 图片/视频拖拽上传组件 (对接 MinIO 预签名直传)
 ✦ 文章详情页 + 评论区
 ✦ 草稿箱管理

交付物: 可创建草稿 → 上传图片 → 编辑内容 → 发布文章 → 自动生成摘要 → 查看详情 → 评论
```

### Phase 3：社交域 + 计数域（第 8~10 周）

```
目标: 关注/取关, 点赞/收藏, 实时计数

后端:
 ✦ mozhi-social 关注模块:
   - 关注/取关 + Outbox 同一事务写入
   - Canal 订阅 Outbox Binlog → Kafka → 粉丝表/计数/缓存消费者
   - 关注列表、粉丝列表查询
 ✦ mozhi-social 点赞模块:
   - Redis 分片位图判重 + Kafka 异步写聚合
   - 收藏功能 (结构类似点赞)
 ✦ mozhi-counter:
   - SDS 紧凑二进制计数 + Lua 原子更新
   - 采样一致性校验 + 自愈重建定时任务
 ✦ Canal 部署与配置

前端:
 ✦ 关注/取关按钮 + 粉丝/关注列表页
 ✦ 点赞/收藏按钮 (乐观更新 UI)
 ✦ 个人主页 (展示关注数/粉丝数/获赞数)

交付物: 完整社交互动闭环, 计数实时更新且最终一致
```

### Phase 4：Feed 域 + 搜索域（第 11~13 周）

```
目标: 首页 Feed 流, 内容搜索, 联想建议

后端:
 ✦ mozhi-feed:
   - 三级缓存架构 (Caffeine → Redis 页面 → Redis 片段)
   - HotKey 探测 + 动态 TTL + 随机抖动
   - SingleFlight 防并发回源
   - 缓存一致性: Kafka 事件驱动缓存失效 + 延迟双删
 ✦ mozhi-search:
   - ES 索引创建 (IK 分词)
   - Canal → Kafka → ES Sink 增量同步
   - function_score 混合排序 (BM25 + 点赞权重 + 时间衰减)
   - search_after 游标分页
   - Completion Suggester 前缀联想

前端:
 ✦ 首页 Feed 流 (无限滚动 / 分页加载)
 ✦ 搜索框 + 实时联想下拉 (debounce 300ms)
 ✦ 搜索结果页 (关键词高亮 + 标签筛选)

交付物: 首页 Feed 流高性能加载, 搜索与联想功能可用
```

### Phase 5：AI 域（第 14~15 周）

```
目标: RAG 知识问答系统

后端:
 ✦ mozhi-ai:
   - 文章分块 (512 token 滑动窗口 + 50 重叠)
   - Embedding → Milvus 向量入库
   - 发布时 Kafka 触发预索引
   - 更新时幂等删除旧版本 + 重新索引
   - 问答: 向量检索 → Prompt 构造 → 大模型 SSE 流式生成
   - 防腐层隔离: Embedding 模型 / 向量库 / LLM 均通过 Adapter 接入

前端:
 ✦ 文章详情页内嵌 AI 问答对话框
 ✦ SSE 逐字流式渲染
 ✦ 对话历史记录 (本地 state)

交付物: 用户可针对任意文章进行 AI 问答, 流式输出
```

### Phase 6：商城域（第 16~19 周）

```
目标: 拼团购物 + 单独购买 + 人群标签 + 消费策略

后端:
 ✦ mozhi-commerce 商品模块: 商品 CRUD, 上下架, 库存管理
 ✦ mozhi-commerce 拼团模块:
   - 拼团活动管理
   - 发起拼团 / 参与拼团 (Redis Lua 原子操作)
   - 拼团过期处理 (延迟队列)
   - 成团事件 → Kafka → 创建支付订单
 ✦ mozhi-commerce 订单模块:
   - 下单: Redis 预扣库存 → 创建订单
   - 支付回调 → 状态流转
   - 超时取消 → 库存回补
 ✦ mozhi-commerce 标签 & 策略模块:
   - 实时标签: Kafka 行为流 → Consumer 滑动窗口聚合
   - 离线标签: 定时任务全量回算
   - 策略匹配引擎: 用户标签 × 策略规则 → 个性化定价方案

前端:
 ✦ 商品列表 / 详情页 (展示原价 + 拼团价 + 个性化优惠)
 ✦ 拼团大厅 (进行中的团列表)
 ✦ 发起拼团 / 加入拼团交互
 ✦ 购物车 + 下单结算页
 ✦ 订单列表 / 订单详情

交付物: 完整购物闭环, 拼团功能, 个性化消费策略生效
```

### Phase 7：可观测性 + 压测 + 上线（第 20~22 周）

```
目标: 生产就绪

 ✦ SkyWalking 全链路追踪接入
 ✦ Prometheus + Grafana 监控面板 (QPS / 延迟 / 错误率 / Redis 命中率)
 ✦ ELK 集中日志
 ✦ 关键路径压测 (JMeter / Gatling):
   - Feed 流首页 QPS
   - 点赞高并发写入
   - 拼团参团并发
   - 搜索响应时间
 ✦ 性能调优: 慢查询优化, 缓存命中率调优, Kafka 分区调整
 ✦ K8s 编排: 各服务 Deployment + HPA 自动扩容
 ✦ CI/CD: GitHub Actions / Jenkins → Docker Build → K8s Deploy

交付物: 生产环境部署, 监控告警就绪, 关键路径通过压测
```

**整体时间线总览**：

```
周次:  1  2  3  4  5  6  7  8  9  10  11  12  13  14  15  16  17  18  19  20  21  22
      ├──────┤──────┤───────────┤──────────┤───────────┤─────────┤──────────────┤────────┤
      Phase0  Phase1   Phase2     Phase3      Phase4    Phase5      Phase6       Phase7
      基座    认证+用户  内容+存储   社交+计数   Feed+搜索   AI        商城+策略     监控上线
```

---

## 七、前端设计规范 — 仿知乎风格指南

### 7.1 设计语言概述

墨知平台前端采用**知识社区型信息架构**设计语言，整体风格参考知乎的「内容优先、信息密度适中、留白克制」的设计哲学，在此基础上融入商城模块的设计。

### 7.2 全局色彩体系

```
┌──────────────────────────────────────────────────────────────┐
│                      墨知平台 · 色彩体系                      │
│                                                              │
│  主色 (Brand):                                               │
│  ┌──────────┐                                                │
│  │ #0066FF  │  墨知蓝 — 品牌主色, 链接、按钮、高亮             │
│  └──────────┘                                                │
│  ┌──────────┐                                                │
│  │ #0052CC  │  深蓝 — 主色 hover 态                           │
│  └──────────┘                                                │
│                                                              │
│  中性色 (Neutral):                                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ #1A1A1A  │ │ #646464  │ │ #999999  │ │ #EBEBEB  │        │
│  │ 标题文字  │ │ 正文文字  │ │ 辅助文字  │ │ 分割线    │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
│  ┌──────────┐ ┌──────────┐                                   │
│  │ #F6F6F6  │ │ #FFFFFF  │                                   │
│  │ 页面背景  │ │ 卡片背景  │                                   │
│  └──────────┘ └──────────┘                                   │
│                                                              │
│  功能色 (Semantic):                                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ #F73131  │ │ #FF9500  │ │ #07C160  │ │ #FF4D4F  │        │
│  │ 点赞红   │ │ 警告橙   │ │ 成功绿    │ │ 拼团红    │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
└──────────────────────────────────────────────────────────────┘
```

**Tailwind 配置**：

```javascript
// tailwind.config.js
export default {
  theme: {
    extend: {
      colors: {
        brand:    { DEFAULT: '#0066FF', dark: '#0052CC', light: '#E8F0FE' },
        neutral:  { 900: '#1A1A1A', 600: '#646464', 400: '#999999',
                    200: '#EBEBEB', 100: '#F6F6F6', 0: '#FFFFFF' },
        like:     '#F73131',
        warning:  '#FF9500',
        success:  '#07C160',
        group:    '#FF4D4F',
      },
    },
  },
};
```

### 7.3 字体与排版

```
字体栈 (与知乎一致):
font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue',
             'PingFang SC', 'Microsoft YaHei', 'Source Han Sans SC',
             'Noto Sans CJK SC', 'WenQuanYi Micro Hei', sans-serif;

字号层级:
├── 页面标题:     24px / font-semibold / #1A1A1A / line-height: 1.4
├── 卡片标题:     18px / font-semibold / #1A1A1A / line-height: 1.5
├── 正文:         15px / font-normal  / #646464 / line-height: 1.8
├── 辅助文字:     13px / font-normal  / #999999 / line-height: 1.5
└── 标签/徽章:    12px / font-medium  / 对应功能色

内容区最大宽度: 694px (与知乎主栏一致)
侧边栏宽度:     296px
整体容器:       1000px 居中
```

### 7.4 页面布局系统

```
┌─────────────────────────────────────────────────────────────────┐
│  顶部导航栏 (Fixed, 高度 52px, 白色背景, 底部 1px 阴影)          │
│  ┌──────┬────────────────────────────┬──────────────────────┐   │
│  │ Logo │  搜索框 (居中, 宽 400px)    │  发布 | 通知 | 头像   │   │
│  └──────┴────────────────────────────┴──────────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────┐ ┌──────────────┐  │
│  │                                          │ │              │  │
│  │              主内容区                     │ │   侧边栏     │  │
│  │              (694px)                     │ │   (296px)    │  │
│  │                                          │ │              │  │
│  │  ┌────────────────────────────────────┐  │ │ ┌──────────┐ │  │
│  │  │  Feed 卡片 1                       │  │ │ │ 热门话题  │ │  │
│  │  │  ┌────────┐                        │  │ │ └──────────┘ │  │
│  │  │  │ 作者头像 │ 作者名 · 时间 · 标签  │  │ │ ┌──────────┐ │  │
│  │  │  └────────┘                        │  │ │ │ 推荐作者  │ │  │
│  │  │  文章标题 (18px, bold)              │  │ │ └──────────┘ │  │
│  │  │  摘要文字 (15px, #646464, 3行截断)  │  │ │ ┌──────────┐ │  │
│  │  │  [缩略图]                          │  │ │ │ 拼团专区  │ │  │
│  │  │  ────────────────────────────────  │  │ │ └──────────┘ │  │
│  │  │  👍 128  💬 32  ⭐ 56    分享       │  │ │              │  │
│  │  └────────────────────────────────────┘  │ │              │  │
│  │                                          │ │              │  │
│  │  ┌────────────────────────────────────┐  │ │              │  │
│  │  │  Feed 卡片 2                       │  │ │              │  │
│  │  │  ...                               │  │ │              │  │
│  │  └────────────────────────────────────┘  │ │              │  │
│  │                                          │ │              │  │
│  └──────────────────────────────────────────┘ └──────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 7.5 核心页面设计稿

#### 首页 Feed 流

```
┌─────────────────────────────────────────────────────────────────┐
│ 🔵 墨知    [═══════ 搜索墨知内容... ═══════]     ✏️发布  🔔  👤 │
├─────────────────────────────────────────────────────────────────┤
│  [推荐] [关注] [热榜] [技术] [设计]                              │
│ ─────────────────────────────────────────── ┌────────────────┐  │
│                                             │ 🔥 今日热门      │  │
│  ┌───────────────────────────────────────┐  │                │  │
│  │ 🟢 张三 · 前端开发 · 3小时前           │  │  1. 如何评价...  │  │
│  │                                       │  │  2. Spring AI...│  │
│  │ 深入理解 React 18 并发模式             │  │  3. 2026 年...   │  │
│  │                                       │  │                │  │
│  │ React 18 引入了并发渲染机制，         │  │ ────────────── │  │
│  │ 允许 React 在后台准备新的 UI          │  │ 👤 推荐关注     │  │
│  │ 而不阻塞主线程。本文将深入...  [图]    │  │                │  │
│  │                                       │  │  李四 · AI 研究  │  │
│  │ 👍 328   💬 67   ⭐ 156               │  │  [+关注]        │  │
│  └───────────────────────────────────────┘  │                │  │
│                                             │  王五 · 后端架构 │  │
│  ┌───────────────────────────────────────┐  │  [+关注]        │  │
│  │ 🟢 李四 · AI 研究员 · 5小时前          │  │                │  │
│  │                                       │  │ ────────────── │  │
│  │ RAG 系统的十个优化技巧                 │  │ 🛒 拼团专区     │  │
│  │                                       │  │                │  │
│  │ 检索增强生成 (RAG) 已成为大模型        │  │ 《系统设计》    │  │
│  │ 应用的主流架构。但在实际生产中...       │  │ ¥89 → 拼团 ¥59 │  │
│  │                                       │  │ 还差2人成团     │  │
│  │ 👍 215   💬 43   ⭐ 98                │  │ [去拼团]        │  │
│  └───────────────────────────────────────┘  └────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

#### 文章详情页 + AI 问答

```
┌─────────────────────────────────────────────────────────────────┐
│ 🔵 墨知    [═══════ 搜索墨知内容... ═══════]     ✏️发布  🔔  👤 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                                                           │  │
│  │  深入理解 React 18 并发模式                               │  │
│  │  ═══════════════════════════                               │  │
│  │                                                           │  │
│  │  🟢 张三 · 前端开发                                       │  │
│  │  发布于 2026-04-03 · 阅读 2,341 · [+关注]                 │  │
│  │                                                           │  │
│  │  ─────────────────────────────────────────────────────── │  │
│  │                                                           │  │
│  │  (Markdown 渲染的正文内容区域)                             │  │
│  │                                                           │  │
│  │  React 18 引入了并发渲染机制，允许 React 在后台准备       │  │
│  │  新的 UI 而不阻塞主线程。这一变化从根本上改变了           │  │
│  │  React 处理更新的方式...                                   │  │
│  │                                                           │  │
│  │  ─────────────────────────────────────────────────────── │  │
│  │                                                           │  │
│  │  👍 328   💬 67   ⭐ 156   分享                            │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  💬 评论 (67)                                             │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │ [输入评论...]                              [发表]    │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │  🟢 王五 · 2小时前                                        │  │
│  │  写得很清晰，并发模式确实是 React 最大的架构升级...        │  │
│  │  👍 12  · 回复                                            │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌─── AI 问答浮窗 (右下角悬浮, 可展开/收起) ────────────────┐   │
│  │  🤖 墨知 AI · 基于本文回答                               │   │
│  │  ────────────────────────────────────────────────────── │   │
│  │  🧑 什么是并发模式的 Suspense？                          │   │
│  │                                                         │   │
│  │  🤖 根据文章内容，Suspense 是 React 18 并发模式的        │   │
│  │     核心组件之一。它允许组件在等待异步数据时              │   │
│  │     声明一个"加载中"的状态，React 会在后台...            │   │
│  │     ▊ (流式输出光标)                                    │   │
│  │  ────────────────────────────────────────────────────── │   │
│  │  [请输入您的问题...]                         [发送]      │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

#### 商城 & 拼团页面

```
┌─────────────────────────────────────────────────────────────────┐
│ 🔵 墨知    [═══════ 搜索墨知内容... ═══════]     ✏️发布  🔔  👤 │
├─────────────────────────────────────────────────────────────────┤
│  [全部] [技术书籍] [课程] [工具] [周边]                          │
│                                                                 │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐   │
│  │    [商品图片]    │ │    [商品图片]    │ │    [商品图片]    │   │
│  │                 │ │                 │ │                 │   │
│  │ 《系统设计面试》 │ │ Spring Boot     │ │ 机械键盘 墨知    │   │
│  │                 │ │ 实战课程        │ │ 定制版           │   │
│  │ ¥89             │ │ ¥299            │ │ ¥459            │   │
│  │ 🔴 拼团 ¥59     │ │ 🔴 拼团 ¥199    │ │ 🔴 拼团 ¥359    │   │
│  │ 已有 128 人拼团  │ │ 已有 56 人拼团  │ │ 已有 23 人拼团   │   │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘   │
│                                                                 │
│  ─── 商品详情 (点击后展开) ───────────────────────────────────── │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ [商品大图]               │  《系统设计面试》              │  │
│  │                         │                                │  │
│  │                         │  原价: ¥89                     │  │
│  │                         │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │  │
│  │                         │  🔴 拼团价: ¥59  (3人成团)     │  │
│  │                         │  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │  │
│  │                         │                                │  │
│  │                         │  ┌────────┐  ┌──────────────┐  │  │
│  │                         │  │ 单独购买 │  │ 🔴 发起拼团  │  │  │
│  │                         │  │  ¥89   │  │    ¥59      │  │  │
│  │                         │  └────────┘  └──────────────┘  │  │
│  │                         │                                │  │
│  │                         │  ── 进行中的团 ──────────────── │  │
│  │                         │  团长: 张三  还差1人  剩余 12h  │  │
│  │                         │  [加入此团]                     │  │
│  │                         │                                │  │
│  │                         │  团长: 李四  还差2人  剩余 8h   │  │
│  │                         │  [加入此团]                     │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌── 为你推荐 (基于人群标签的个性化推荐) ─────────────────────┐  │
│  │  💡 新用户专享: 首单立减 8 元                              │  │
│  │  📦 根据您的阅读偏好推荐以下商品...                        │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 7.6 组件设计规范

#### 卡片（Card）

```
┌──────────────────────────────────────────────────────────────────┐
│  设计参数:                                                        │
│  ├── 背景色: #FFFFFF                                              │
│  ├── 圆角: 4px (知乎风格偏直角, 仅微圆角)                         │
│  ├── 阴影: none (知乎风格无阴影, 用 border-bottom 分隔)           │
│  ├── 内边距: 16px 20px                                            │
│  ├── 卡片间距: 无间距, 用 1px solid #EBEBEB 分割线分隔             │
│  └── hover 态: background → #F6F6F6 (微灰) + cursor: pointer     │
│                                                                  │
│  注: 知乎风格的 Feed 卡片不是独立浮起的卡片,                       │
│      而是列表式排列, 用细线分隔, 视觉上更紧凑、信息密度更高         │
└──────────────────────────────────────────────────────────────────┘
```

#### 按钮（Button）

```
主按钮 (Primary):
  背景: #0066FF → hover #0052CC
  文字: #FFFFFF, 14px, font-medium
  圆角: 4px
  高度: 34px
  内边距: 0 16px

次按钮 (Secondary):
  背景: transparent
  边框: 1px solid #0066FF
  文字: #0066FF
  hover: 背景 → #E8F0FE

关注按钮:
  未关注: 背景 #0066FF, 文字 "关注", 白色
  已关注: 背景 #F6F6F6, 文字 "已关注", #999999
  hover 已关注: 背景 #FFF2F0, 文字 "取消关注", #F73131

拼团按钮:
  背景: #FF4D4F → hover #D9363E
  文字: #FFFFFF
  圆角: 4px
```

#### 交互动效

```
┌──────────────────────────────────────────────────────────────────┐
│  交互动效规范:                                                    │
│                                                                  │
│  点赞动画:                                                       │
│  点击 → 图标放大 1.2x + 颜色 #999 → #F73131                     │
│       → 100ms 缩回 1.0x                                         │
│       → 计数 +1 (乐观更新, 先 UI 变化再请求后端)                  │
│                                                                  │
│  关注按钮:                                                       │
│  未关注 → 已关注: 背景色渐变过渡 200ms                            │
│  已关注 → hover: 文字渐隐切换 "已关注" → "取消关注" 150ms         │
│                                                                  │
│  页面切换: 无整页刷新, React Router 配合 Suspense + skeleton      │
│  加载态: 骨架屏 (skeleton) 而非 spinner, 与知乎一致               │
│  AI 流式输出: 逐字显示 + 末尾闪烁光标                             │
│  搜索联想: debounce 300ms + 下拉面板 fade-in 150ms               │
│  拼团倒计时: 实时倒计时组件, 红色数字, 每秒刷新                    │
│                                                                  │
│  全局过渡: transition-all duration-200 ease-in-out               │
└──────────────────────────────────────────────────────────────────┘
```

### 7.7 响应式断点

```
桌面端 (≥ 1000px):
  双栏布局: 主内容区 694px + 侧边栏 296px + 间距 10px
  居中容器 max-width: 1000px

平板端 (768px ~ 999px):
  单栏布局: 侧边栏隐藏, 主内容区 100% (max-width: 694px 居中)
  顶部导航搜索框缩短

移动端 (< 768px):
  全宽布局: padding 0 16px
  顶部导航简化: Logo + 搜索图标 + 头像
  底部 Tab 栏: 首页 | 搜索 | 发布 | 商城 | 我的
  Feed 卡片全宽, 字号微调 (标题 16px, 正文 14px)
```

### 7.8 前端项目结构

```
mozhi-web/
├── index.html
├── vite.config.ts
├── tailwind.config.js
├── tsconfig.json
├── package.json
│
└── src/
    ├── main.tsx                          (入口)
    ├── App.tsx                           (根组件 + 路由)
    │
    ├── assets/                           (静态资源: 图标、图片)
    │
    ├── styles/
    │   └── globals.css                   (全局样式 + Tailwind 指令)
    │
    ├── lib/                              (工具库)
    │   ├── axios.ts                      (Axios 实例 + JWT 拦截器)
    │   ├── sse.ts                        (SSE 流式请求封装)
    │   └── utils.ts
    │
    ├── stores/                           (Zustand 状态管理)
    │   ├── useAuthStore.ts               (认证状态: token, user)
    │   └── useCartStore.ts               (购物车状态)
    │
    ├── hooks/                            (自定义 Hooks)
    │   ├── useInfiniteScroll.ts          (无限滚动)
    │   ├── useDebouncedSearch.ts         (防抖搜索)
    │   └── useCountdown.ts              (拼团倒计时)
    │
    ├── components/                       (公共组件)
    │   ├── ui/                           (Shadcn/UI 基础组件)
    │   │   ├── Button.tsx
    │   │   ├── Input.tsx
    │   │   ├── Card.tsx
    │   │   ├── Dialog.tsx
    │   │   ├── Skeleton.tsx
    │   │   └── ...
    │   ├── layout/
    │   │   ├── Header.tsx                (顶部导航栏)
    │   │   ├── Sidebar.tsx               (右侧边栏)
    │   │   ├── MobileTabBar.tsx          (移动端底部 Tab)
    │   │   └── PageContainer.tsx         (1000px 居中容器)
    │   ├── feed/
    │   │   ├── FeedCard.tsx              (Feed 卡片)
    │   │   ├── FeedList.tsx              (Feed 列表 + 无限滚动)
    │   │   └── FeedSkeleton.tsx          (骨架屏)
    │   ├── editor/
    │   │   └── MarkdownEditor.tsx        (Markdown 编辑器)
    │   ├── social/
    │   │   ├── FollowButton.tsx          (关注按钮)
    │   │   ├── LikeButton.tsx            (点赞按钮 + 动画)
    │   │   └── CollectButton.tsx         (收藏按钮)
    │   ├── search/
    │   │   ├── SearchBar.tsx             (搜索框)
    │   │   └── SuggestionDropdown.tsx    (联想建议下拉)
    │   ├── ai/
    │   │   ├── AiChatPanel.tsx           (AI 问答悬浮面板)
    │   │   └── StreamingMessage.tsx      (流式消息渲染)
    │   └── commerce/
    │       ├── ProductCard.tsx            (商品卡片)
    │       ├── GroupBuyingBadge.tsx       (拼团标签)
    │       ├── CountdownTimer.tsx         (倒计时)
    │       └── PricingStrategyBanner.tsx  (个性化优惠横幅)
    │
    ├── pages/                            (页面组件, 对应路由)
    │   ├── Home/
    │   │   └── index.tsx                 (首页 Feed)
    │   ├── NoteDetail/
    │   │   └── index.tsx                 (文章详情)
    │   ├── Editor/
    │   │   └── index.tsx                 (编辑器/发布)
    │   ├── Profile/
    │   │   └── index.tsx                 (个人主页)
    │   ├── Search/
    │   │   └── index.tsx                 (搜索结果页)
    │   ├── Auth/
    │   │   ├── Login.tsx
    │   │   └── Register.tsx
    │   ├── Commerce/
    │   │   ├── ProductList.tsx            (商品列表)
    │   │   ├── ProductDetail.tsx          (商品详情 + 拼团)
    │   │   └── OrderList.tsx             (订单列表)
    │   └── Settings/
    │       └── index.tsx                 (个人设置)
    │
    ├── api/                              (API 请求层, 按域划分)
    │   ├── auth.ts
    │   ├── user.ts
    │   ├── content.ts
    │   ├── social.ts
    │   ├── feed.ts
    │   ├── search.ts
    │   ├── ai.ts
    │   └── commerce.ts
    │
    └── types/                            (TypeScript 类型定义)
        ├── auth.d.ts
        ├── user.d.ts
        ├── content.d.ts
        ├── social.d.ts
        ├── commerce.d.ts
        └── common.d.ts
```

---

## 八、设计沟通话术

如果你需要向设计师或团队传达前端风格需求，可以这样表述：

> **视觉风格**：采用知乎式的「知识社区型」设计语言。整体以纯白底 + 浅灰页面背景为基调，信息架构为经典的左主栏右侧边栏双列布局。内容卡片无独立阴影，以细线分隔呈列表式排列，追求高信息密度与阅读舒适度的平衡。字体采用系统默认中文字体栈，正文 15px、行高 1.8，保证长文阅读体验。品牌色为蓝色系（#0066FF），仅在链接、按钮、高亮等交互元素上使用，页面整体保持中性色克制。交互上以骨架屏替代 loading spinner，点赞/关注等高频操作采用乐观更新 + 微动效反馈。商城模块融入社区页面，拼团入口用红色系（#FF4D4F）做视觉区分，不破坏整体知识社区的调性。移动端采用底部 Tab 导航，响应式适配三个断点。