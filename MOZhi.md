

# 墨知平台 — 全栈系统架构设计文档

---

## 一、项目总览

墨知平台是一个融合**知识社区 + 智能问答 + 社交电商**的综合性平台，形态类似「知乎 + 拼多多」。用户可以在平台上发布知识内容、互动交流、进行 AI 智能问答，同时基于用户人群标签享受个性化的拼团购物与单独购买服务。

---

## 二、技术栈全景

| 层级 | 技术选型 |
|------|----------|
| **语言 / 框架** | Java 21 + Spring Boot 3.x + Spring Security 6.x |
| **AI 能力** | Spring AI + RAG（检索增强生成）+ DeepSeek / OpenAI 兼容 API |
| **持久层** | MyBatis-Plus + MySQL 8.x（InnoDB） |
| **缓存** | Redis 7.x（Cluster）+ Caffeine（本地缓存） |
| **消息队列** | Apache Kafka 3.x |
| **搜索引擎** | Elasticsearch 8.x |
| **对象存储** | MinIO（私有化部署，兼容 S3 协议） |
| **数据同步** | Canal 1.1.x（MySQL Binlog 订阅） |
| **向量数据库** | Milvus / Redis Stack（向量检索） |
| **前端** | React 18 + Vite 5 + TypeScript + TailwindCSS + Zustand |
| **网关 / 部署** | Spring Cloud Gateway / Nginx + Docker + K8s |
| **监控** | Prometheus + Grafana + SkyWalking |

> **关于 MinIO**：MinIO 完全兼容 Amazon S3 API，可以在本地或私有服务器上部署，无需依赖任何云厂商。后续如需迁移至阿里云 OSS、AWS S3 或腾讯 COS，只需更换 endpoint 和凭证即可，业务代码零改动。

---

## 三、核心子系统详细设计

---

### 1. 认证系统 — JWT 双令牌 + RS256

**目标**：高安全、高性能的无状态会话管理，支持即时令牌撤销。

**架构设计**：

```
客户端                       认证服务                            Redis
  │                            │                                  │
  │── POST /auth/login ───────▶│                                  │
  │                            │── 验证凭证 ──▶ MySQL              │
  │                            │── 生成 AccessToken (RS256, 15m)   │
  │                            │── 生成 RefreshToken (7d) ────────▶│── SET rt:{jti} userId EX 7d
  │◀── {accessToken, refreshToken} ─│                              │
  │                            │                                  │
  │── 携带 Bearer Token 请求 ──▶│── 本地 RS256 公钥验签             │
  │                            │── 检查 exp / claims / 黑名单      │
  │                            │                                  │
  │── POST /auth/refresh ─────▶│                                  │
  │                            │── 验证 RefreshToken ─────────────▶│── GET rt:{jti} 校验白名单
  │                            │── 旧令牌写入黑名单 ──────────────▶│── SET bl:{old_jti} EX 15m
  │                            │── 签发新双令牌 ──────────────────▶│── DEL rt:{old} + SET rt:{new}
  │◀── {newAccessToken, newRefreshToken} ─│                        │
  │                            │                                  │
  │── POST /auth/logout ──────▶│                                  │
  │                            │── DEL rt:{jti} ─────────────────▶│── 白名单移除
  │                            │── SET bl:{access_jti} EX 15m ───▶│── AccessToken 黑名单
```

**核心要点**：

- **AccessToken**：15 分钟有效期，RS256 非对称签名，各服务持有公钥即可验签，无需回查 Redis，无状态高性能
- **RefreshToken**：7 天有效期，存入 Redis 白名单（`rt:{jti} → userId`），支持即时撤销
- **令牌旋转**：每次 refresh 时旧 RefreshToken 立即失效、签发全新双令牌，防止泄露后被长期利用
- **即时撤销**：登出或安全事件（修改密码、异地登录）时删除白名单并将未过期 AccessToken 的 `jti` 写入短 TTL 黑名单
- **多设备管理**：`user:session:{uid}` 维护该用户所有活跃 `jti`，支持「踢出全部设备」一键清除

**Redis 数据结构**：

```
rt:{jti}              → userId        TTL = 7d      # RefreshToken 白名单
bl:{jti}              → "revoked"     TTL = 15m     # AccessToken 黑名单
user:session:{uid}    → Set<jti>      TTL = 7d      # 用户活跃会话集合
```

**Spring Security 过滤链**：

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final RSAPublicKey publicKey;
    private final StringRedisTemplate redis;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractBearerToken(req);
        if (token == null) { chain.doFilter(req, res); return; }

        try {
            // 1. RS256 公钥验签 + 解析 Claims
            Jws<Claims> jws = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token);
            Claims claims = jws.getPayload();

            // 2. 黑名单检查（已撤销的 AccessToken）
            String jti = claims.getId();
            if (Boolean.TRUE.equals(redis.hasKey("bl:" + jti))) {
                res.sendError(401, "Token revoked");
                return;
            }

            // 3. 构建 Authentication 注入 SecurityContext
            var auth = new UsernamePasswordAuthenticationToken(
                claims.getSubject(),
                null,
                extractAuthorities(claims)
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            res.sendError(401, "Invalid token");
            return;
        }
        chain.doFilter(req, res);
    }
}
```

---

### 2. 计数系统 — Redis SDS 紧凑计数 + Lua 原子更新

**目标**：为笔记维度（点赞数、收藏数、评论数）和用户维度（关注数、粉丝数、笔记数）提供高性能、高一致的实时计数服务。

**架构设计**：

```
                    写入路径                                 读取路径
                      │                                       │
  Kafka 消费者 ───────▶│                                       │
 (点赞/关注/收藏事件)   │                                       │
                      ▼                                       ▼
             ┌─────────────────┐                    ┌──────────────────┐
             │   Lua 脚本       │                    │  直接读取 Redis    │
             │   原子更新计数    │                    │  二进制 SDS 解码   │
             │   + 边界校验     │                    │  → 返回多字段计数  │
             └────────┬────────┘                    └──────────────────┘
                      │
                      ▼
             ┌─────────────────┐
             │  Redis SDS 紧凑  │
             │  二进制打包存储   │
             │  (一个 key 多字段)│
             └────────┬────────┘
                      │
           ┌──────────┴──────────┐
           ▼                     ▼
  采样一致性校验             自愈重建任务
  (定时随机抽样对比 MySQL)   (差异超阈值 → 以 MySQL 为准重建)
```

**紧凑存储设计**：

将笔记的多个计数字段打包到一个 Redis SDS 二进制字符串中，一个 key 存储全部字段，大幅节省内存。

```
cnt:note:{noteId}  → [likes: 8B][collects: 8B][comments: 8B]    共 24 字节
cnt:user:{userId}  → [following: 8B][followers: 8B][notes: 8B]   共 24 字节
```

**Lua 原子更新脚本**：

```lua
-- counter_update.lua
-- KEYS[1] = 计数 key
-- ARGV[1] = 字段偏移量 (0/8/16)
-- ARGV[2] = 增量 (+1 或 -1)
local key = KEYS[1]
local offset = tonumber(ARGV[1])
local delta = tonumber(ARGV[2])

local raw = redis.call('GET', key)
if not raw then
    raw = string.rep('\0', 24)  -- 初始化 3 字段 × 8 字节
end

-- 读取当前值（大端 8 字节整数）
local current = struct.unpack('>i8', raw, offset + 1)
local new_val = math.max(0, current + delta)  -- 防止负数

-- 拼接写回
local new_raw = raw:sub(1, offset)
              .. struct.pack('>i8', new_val)
              .. raw:sub(offset + 9)

redis.call('SET', key, new_raw)
return new_val
```

**采样校验与自愈**：

定时任务每分钟随机抽取 N 个 key，对比 Redis 计数与 MySQL 落盘值。差异超过设定阈值时触发告警并自动以 MySQL 为准重建 Redis 计数，保证最终一致性。

---

### 3. 发布系统 — 渐进式发布 + MinIO 预签名直传

**目标**：支持图片、视频、Markdown 等富媒体内容的高效上传与渐进式发布，集成 AI 摘要生成。

**架构设计**：

```
┌──────────────────────────────────────────────────────────────────────┐
│                       渐进式发布流程                                   │
│                                                                      │
│  Step 1: 创建草稿            Step 2: 上传媒体           Step 3: 提交   │
│  POST /draft/create         POST /draft/presign        POST /publish │
│  → 返回 draftId             → 返回预签名 URL            → 状态机推进  │
│                             → 前端直传 MinIO                          │
│                             → 回调确认上传                             │
└──────────────────────────────────────────────────────────────────────┘

前端                       后端                        MinIO (S3 兼容)
 │                          │                             │
 │── 请求预签名 URL ─────────▶│                             │
 │                          │── 生成 PreSigned PUT URL ───▶│
 │◀── presignedUrl + key ───│                             │
 │                          │                             │
 │── PUT 直传文件 ────────────────────────────────────────▶│
 │◀── 200 OK ─────────────────────────────────────────────│
 │                          │                             │
 │── 确认上传完成 ───────────▶│── HEAD 校验对象存在 ─────────▶│
 │                          │── 关联 draftId + objectKey   │
 │                          │                             │
 │── 提交发布 ──────────────▶│                             │
 │                          │── 内容审核 (异步)             │
 │                          │── AI 摘要生成 (DeepSeek)      │
 │                          │── 写入 MySQL + 发布事件       │
 │                          │── Kafka → ES 索引 / Feed 更新 │
```

**核心要点**：

- **预签名直传**：后端通过 MinIO SDK 生成预签名 PUT URL（含过期时间、Content-Type 限制），前端直接上传文件到 MinIO，文件不经过后端中转，大幅节省服务器带宽
- **渐进式状态机**：草稿经历 `DRAFT → UPLOADING → PENDING_REVIEW → PUBLISHED / REJECTED` 状态流转，每步可断点续传，前端可随时保存进度
- **媒体路径组织**：`/{userId}/{draftId}/{uuid}.{ext}`，发布后冻结路径，草稿删除时异步清理 MinIO 中的孤立文件
- **AI 摘要生成**：发布时异步调用 DeepSeek API，基于 Markdown 内容一键生成 100 字以内的中文摘要

```java
@Service
@RequiredArgsConstructor
public class AiSummaryService {

    private final ChatClient chatClient;

    public Mono<String> generateSummary(String markdownContent) {
        return chatClient.prompt()
            .system("你是墨知平台的 AI 助手，请为文章生成一段 100 字以内的中文摘要，准确概括核心观点。")
            .user(markdownContent)
            .stream()
            .content()
            .reduce(String::concat);
    }
}
```

**MinIO 预签名生成**：

```java
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    public PresignedUploadResult generatePresignedUrl(Long userId, Long draftId, String filename) {
        String objectKey = "%d/%d/%s_%s".formatted(userId, draftId, UUID.randomUUID(), filename);

        String presignedUrl = minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .bucket("mozhi-media")
                .object(objectKey)
                .method(Method.PUT)
                .expiry(10, TimeUnit.MINUTES)
                .build()
        );
        return new PresignedUploadResult(presignedUrl, objectKey);
    }
}
```

---

### 4. 用户关系系统 — 一主多从 + Outbox + Canal + Kafka

**目标**：实现高可靠的关注/取关功能，保证关注表、粉丝表、计数、缓存等多数据源的最终一致性。

**架构设计**：

```
用户 A 关注用户 B
       │
       ▼
┌─────────────────────────────────────────────┐
│  @Transactional (同一事务)                    │
│                                             │
│  1. INSERT INTO following                   │
│     (user_id = A, target_id = B)            │
│                                             │
│  2. INSERT INTO outbox                      │
│     (aggregate_type = 'FOLLOW',             │
│      payload = '{"from": A, "to": B}',      │
│      status = 'PENDING')                    │
└──────────────────┬──────────────────────────┘
                   │ commit
                   ▼ MySQL Binlog
            ┌──────────┐
            │  Canal    │  订阅 outbox 表 Binlog INSERT 事件
            └────┬─────┘
                 │
                 ▼
            ┌──────────┐
            │  Kafka    │  Topic: relation-events
            └────┬─────┘
                 │
       ┌─────────┼───────────┐
       ▼         ▼           ▼
    粉丝表     计数系统      缓存
   (伪从1)    (伪从2)      (伪从3)

    INSERT    Lua 原子     INVALIDATE
    follower  cnt:user:*   follow:list:*
    表记录    +1 / -1      清除列表缓存
```

**核心要点**：

- **Outbox 模式**：关注操作与 Outbox 记录在同一个数据库事务中写入，保证「业务变更」与「事件发布」的原子性，杜绝消息丢失或不一致
- **Canal 无侵入订阅**：Canal 伪装为 MySQL Slave 读取 Outbox 表的 Binlog，将 INSERT 事件转发到 Kafka，对业务代码完全无侵入
- **伪从架构**：粉丝表、计数系统、列表缓存均作为关注表的「逻辑从库」，通过 Kafka 消费者各自异步更新，互不阻塞
- **幂等消费**：每条 Outbox 记录携带唯一 `eventId`，消费端基于 `eventId` 去重，保证 exactly-once 语义

**关键表结构**：

```sql
-- 关注表（主表）
CREATE TABLE following (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT    NOT NULL,
    target_id   BIGINT    NOT NULL,
    status      TINYINT   DEFAULT 1 COMMENT '1:关注 0:取消',
    created_at  DATETIME  DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_target (user_id, target_id)
) ENGINE=InnoDB;

-- Outbox 事件表
CREATE TABLE outbox (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL UNIQUE COMMENT '幂等键',
    aggregate_type  VARCHAR(32)  NOT NULL COMMENT 'FOLLOW / UNFOLLOW',
    aggregate_id    VARCHAR(64)  NOT NULL,
    payload         JSON         NOT NULL,
    status          VARCHAR(16)  DEFAULT 'PENDING',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 粉丝表（伪从）
CREATE TABLE follower (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT    NOT NULL COMMENT '被关注者',
    follower_id BIGINT    NOT NULL COMMENT '粉丝',
    created_at  DATETIME  DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_follower (user_id, follower_id)
) ENGINE=InnoDB;
```

---

### 5. 点赞系统 — 异步写聚合 + 分片位图 + 灾难回放

**目标**：应对高并发写场景（热门内容瞬时大量点赞），保证幂等判重、高吞吐写入、最终一致性。

**架构设计**：

```
用户点赞请求
     │
     ▼
┌──────────────────────┐
│ Redis 分片位图判重     │
│ GETBIT like:bm:{noteId}:{shard} offset │
│   → 1: 已点赞, 直接返回（幂等）          │
│   → 0: 未点赞, 继续                     │
└──────────┬───────────┘
           │ 未点赞
           ▼
┌──────────────────────┐
│ SETBIT → 1 (标记已赞) │
│ 发送 Kafka 消息       │
│ Topic: like-events   │
└──────────┬───────────┘
           │
           ▼
┌────────────────────────────────────────┐
│ Kafka 消费者 — 写聚合                    │
│                                        │
│ 收集窗口内事件 (5s 或 1000 条)           │
│ 按 noteId 聚合:                         │
│   noteA: +15, noteB: +8, noteC: +3     │
│ 批量写入 MySQL:                         │
│   BATCH INSERT like_record (明细)       │
│   UPDATE note SET likes += delta (计数) │
└────────────────────────────────────────┘

分片位图结构:
┌─────────────────────────────────────────────────────────┐
│ like:bm:{noteId}:0   →  bit[0..1048575]    用户 0 ~ 1M │
│ like:bm:{noteId}:1   →  bit[0..1048575]    用户 1M ~ 2M│
│ ...                                                     │
│                                                         │
│ shard  = userId / 1_048_576                             │
│ offset = userId % 1_048_576                             │
│ 每个分片 ≈ 128KB，可存储约 100 万用户的点赞状态            │
└─────────────────────────────────────────────────────────┘
```

**核心要点**：

- **分片位图判重**：用户 ID 映射到 Redis Bitmap 的 bit 位，O(1) 完成幂等校验，内存占用极低
- **异步写聚合**：点赞事件发到 Kafka，消费者按时间窗口/批次聚合后批量写入 MySQL，将海量随机写转化为少量批量写
- **计数按需重建**：读取时发现 Redis 计数缺失，可基于位图 `BITCOUNT` 直接重建，无需回查 MySQL
- **灾难回放兜底**：Kafka 消息保留 7 天，极端情况下可重新消费回放全部事件进行数据恢复

```java
@Component
@RequiredArgsConstructor
public class LikeAggregateConsumer {

    private final LikeRecordMapper likeRecordMapper;
    private final NoteCounterMapper noteCounterMapper;

    @KafkaListener(topics = "like-events", batch = "true")
    public void onLikeEvents(List<ConsumerRecord<String, LikeEvent>> records) {
        // 按 noteId 聚合
        Map<Long, List<LikeEvent>> grouped = records.stream()
            .map(ConsumerRecord::value)
            .collect(Collectors.groupingBy(LikeEvent::noteId));

        grouped.forEach((noteId, events) -> {
            // 批量插入明细
            likeRecordMapper.batchInsert(events.stream()
                .map(e -> new LikeRecord(e.userId(), noteId, e.action(), e.timestamp()))
                .toList());
            // 聚合更新计数
            int delta = events.stream()
                .mapToInt(e -> e.action() == Action.LIKE ? 1 : -1)
                .sum();
            noteCounterMapper.incrementLikes(noteId, delta);
        });
    }
}
```

---

### 6. Feed 流 — 三级缓存 + HotKey 探测 + SingleFlight

**目标**：高性能 Feed 流分发，抗热点、抗雪崩、抗并发穿透。

**架构设计**：

```
请求 ──▶ L1 Caffeine ──miss──▶ L2 Redis 页面缓存 ──miss──▶ L3 Redis 片段缓存 ──miss──▶ MySQL
         (进程内)               (完整页数据)                 (单条内容)
         TTL: 30s              TTL: 5min                   TTL: 30min
         容量: ~1000           粒度: 一页20条               粒度: 单条详情
             │                      │                           │
             ◀──── 命中即返回 ────────┘                           │
             ◀──── 命中后组装页面 ──────────────────────────────────┘
```

**HotKey 探测机制**：

```
┌────────────────────────────────────────────┐
│  进程内滑动窗口计数器                        │
│                                            │
│  feed:page:recommend:1  →  152 次/10s  ★   │
│  feed:page:recommend:2  →   23 次/10s      │
│  feed:page:tech:1       →  201 次/10s  ★   │
│                                            │
│  阈值: 100 次/10s → 标记为 HotKey           │
│                                            │
│  HotKey 策略:                               │
│  ├── L1 Caffeine TTL: 30s  → 120s          │
│  ├── L2 Redis   TTL: 5min → 15min          │
│  └── 叠加随机抖动: ±20% 防止集体过期雪崩     │
│                                            │
│  TTL 公式:                                  │
│  actualTTL = baseTTL × (1 + random(-0.2, 0.2))│
└────────────────────────────────────────────┘
```

**SingleFlight（单飞锁）**：

```
线程 A ──▶ 查 feed:page:3 ──miss──▶ 获取锁 ✓ ──▶ 回源 MySQL ──▶ 写缓存 ──▶ 返回
线程 B ──▶ 查 feed:page:3 ──miss──▶ 获取锁 ✗ ──▶ 等待 A 完成 ──────────────▶ 共享结果
线程 C ──▶ 查 feed:page:3 ──miss──▶ 获取锁 ✗ ──▶ 等待 A 完成 ──────────────▶ 共享结果

同一个 key 只有一个线程回源，避免并发回源风暴
```

```java
@Component
public class SingleFlightCache {

    private final ConcurrentMap<String, CompletableFuture<?>> flights = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Supplier<T> loader) {
        CompletableFuture<T> future = (CompletableFuture<T>) flights.computeIfAbsent(key, k ->
            CompletableFuture.supplyAsync(() -> {
                try {
                    return loader.get();
                } finally {
                    flights.remove(k);
                }
            })
        );
        return future.join();
    }
}
```

**缓存一致性策略**：

内容更新时通过 Kafka 事件驱动缓存失效：删除 L3 片段缓存，删除关联的 L2 页面缓存，广播本地事件清除各实例的 L1 Caffeine 条目。配合延迟双删（500ms 后再次删除 L2/L3）兜底因主从延迟导致的脏缓存。

---

### 7. 搜索系统 — Elasticsearch + 联想建议

**目标**：高相关性内容检索、标签过滤、深分页稳定性，以及低延迟前缀联想。

**架构设计**：

```
数据同步链路:
MySQL ──Canal──▶ Kafka (content-events) ──▶ ES Sink Consumer ──▶ Elasticsearch

搜索链路:
用户查询 ──▶ 搜索服务 ──▶ function_score Query ──▶ Elasticsearch
                         ┌─────────────────────────────────┐
                         │  function_score 混合排序          │
                         │                                 │
                         │  BM25 文本相关性       × 0.60    │
                         │  + log1p(likeCount)   × 0.25    │
                         │  + 时间新鲜度高斯衰减   × 0.15    │
                         └─────────────────────────────────┘

联想建议链路:
用户输入 ──▶ Completion Suggester ──▶ FST 前缀匹配 ──▶ Top 5 建议 (< 5ms)
```

**ES 索引 Mapping**：

```json
{
  "mappings": {
    "properties": {
      "title":       { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "content":     { "type": "text", "analyzer": "ik_max_word" },
      "summary":     { "type": "text", "analyzer": "ik_smart" },
      "tags":        { "type": "keyword" },
      "authorId":    { "type": "long" },
      "likeCount":   { "type": "long" },
      "publishTime": { "type": "date" },
      "suggestion":  { "type": "completion", "analyzer": "ik_max_word" }
    }
  }
}
```

**核心要点**：

- **search_after 游标分页**：替代 `from + size`，使用上一页最后一条的排序值作为游标，深分页性能稳定不退化
- **function_score 混合排序**：融合 BM25 相关性、业务权重（点赞数对数衰减）、时间新鲜度（高斯衰减），兼顾内容质量与时效
- **Completion Suggester**：基于 ES 内置 FST 数据结构实现前缀联想，查询延迟通常 < 5ms
- **增量同步**：复用 Canal → Kafka 链路，ES Sink Consumer 消费 `content-events` 增量更新索引

---

### 8. AI 问答系统 — RAG 知识问答

**目标**：用户围绕单篇或多篇知文进行智能问答，通过 RAG 检索增强生成提升回答的准确性与可信度。

**全流程架构**：

```
┌──────────────────────────────────────────────────────────────────────┐
│                        RAG 问答全流程                                 │
│                                                                      │
│  ① 索引阶段（文章发布/更新时异步触发）                                  │
│                                                                      │
│  文章 Markdown ──▶ 分块器 (512 token/块, 50 token 重叠)               │
│                 ──▶ Embedding 模型 (text-embedding-v3)               │
│                 ──▶ 向量入库 (Milvus)                                │
│                 ──▶ 元数据: {noteId, chunkIndex, text}               │
│                                                                      │
│  ② 问答阶段（用户提问时）                                              │
│                                                                      │
│  用户问题 ──▶ 索引检查（文章是否已索引？）                               │
│              ├── 未索引 → 即时触发索引 + 短暂等待                       │
│              └── 已索引 ──▶ Embedding(问题)                           │
│                          ──▶ 向量检索 Top-K (K=5, threshold > 0.7)   │
│                          ──▶ Prompt 构造                              │
│                          ──▶ 大模型流式生成 (SSE → 前端)               │
│                                                                      │
│  ③ 版本管理                                                          │
│  文章更新 → 幂等删除旧版本全部向量 → 重新索引 → 始终保持单一最新版本      │
└──────────────────────────────────────────────────────────────────────┘
```

**核心要点**：

- **合理分块**：512 token 滑动窗口 + 50 token 重叠，保证关键语义不被截断
- **幂等删除 + 单一版本**：文章更新时先按 `noteId` 删除 Milvus 中全部旧向量，再重新分块入库
- **预索引**：文章发布时通过 Kafka 异步触发预索引，消除用户首次提问的冷启动等待
- **流式输出**：通过 SSE（Server-Sent Events）逐 token 推送到前端，用户无需等待完整生成

```java
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final IndexService indexService;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @GetMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> ask(@RequestParam Long noteId, @RequestParam String question) {
        // 1. 确保文章已索引
        indexService.ensureIndexed(noteId);

        // 2. 向量检索
        List<Document> chunks = vectorStore.similaritySearch(
            SearchRequest.query(question)
                .withFilterExpression("noteId == " + noteId)
                .withTopK(5)
                .withSimilarityThreshold(0.7)
        );

        // 3. 构造上下文
        String context = chunks.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n---\n"));

        // 4. Prompt + 流式生成
        return chatClient.prompt()
            .system("""
                你是墨知平台的 AI 助手。请严格基于以下上下文回答用户问题。
                如果上下文中没有相关信息，请明确告知用户。不要编造内容。
                """)
            .user(u -> u.text("上下文:\n{context}\n\n问题: {question}")
                .param("context", context)
                .param("question", question))
            .stream()
            .content();
    }
}
```

---

### 9. 拼团购物 / 单独购买系统 — 人群标签 + 差异化消费策略

**目标**：在知识社区基础上引入社交电商能力，支持单独购买和拼团购买，基于用户人群标签提供差异化定价与营销策略。

#### 9.1 用户人群标签引擎

```
数据采集                          标签计算                      标签存储
┌──────────────┐              ┌──────────────┐           ┌──────────────┐
│ 注册信息      │              │ 实时标签      │           │ MySQL        │
│ (年龄/职业)   │──────┐       │ Kafka 行为流  │           │ user_tag 表  │
│              │      │       │ → Consumer   │           │              │
│ 行为数据      │      │       │ 滑动窗口聚合  │──────────▶│ tag_key      │
│ (浏览/点赞)   │──────┤       └──────────────┘           │ tag_value    │
│              │      │                                  │ score (置信度)│
│ 消费数据      │      ├──▶                               └──────┬───────┘
│ (客单价/频次) │──────┤       ┌──────────────┐                  │
│              │      │       │ 离线标签      │                  ▼
│ 社交数据      │      │       │ 定时任务      │           ┌──────────────┐
│ (关注/互动)   │──────┘       │ 全量回算      │──────────▶│ Redis 缓存    │
│              │              └──────────────┘           │ user:tag:{uid}│
└──────────────┘                                        └──────────────┘
```

**标签体系**：

| 维度 | 标签值 | 计算依据 |
|------|--------|----------|
| **消费能力** | 高 / 中 / 低 | 历史订单客单价分位数 |
| **活跃度** | 高活跃 / 一般 / 沉默 | 近 7 日登录天数 + 互动次数 |
| **内容偏好** | 技术 / 设计 / 产品 / 运营 / … | 浏览 + 点赞 + 收藏的内容标签聚合 |
| **价格敏感度** | 高敏感 / 中 / 不敏感 | 优惠券使用率 + 拼团参与率 |
| **社交属性** | KOL / 活跃社交 / 独立浏览 | 粉丝数 + 内容互动率 |
| **生命周期** | 新用户 / 成长期 / 成熟期 / 流失预警 | 注册天数 + 活跃趋势斜率 |

#### 9.2 差异化消费策略引擎

```
用户请求商品详情
       │
       ▼
┌──────────────────────────────────────────────────┐
│  策略匹配引擎                                      │
│                                                  │
│  1. 加载用户标签: user:tag:{uid}                   │
│  2. 加载活跃策略: pricing_strategy (status=1)      │
│  3. 规则匹配 (tag_rules JSON 与用户标签匹配)        │
│  4. 按 priority 排序，取最高优先级命中策略           │
│  5. 返回个性化方案                                 │
└──────────────────────────────────────────────────┘

策略匹配示例:

┌───────────────┬──────────────────────────────────────┐
│ 人群           │ 策略                                 │
├───────────────┼──────────────────────────────────────┤
│ 新用户         │ 首单立减 8 元 + 新人拼团专享价          │
│ 价格敏感型     │ 优先展示拼团入口 + 发放大额优惠券        │
│ 高消费力       │ 推荐精品/独家内容 + 会员专享折扣         │
│ KOL           │ 分销返佣 3% + 拼团发起人额外奖励         │
│ 流失预警       │ 召回优惠券 15 元 + 限时 48 小时折扣      │
└───────────────┴──────────────────────────────────────┘
```

#### 9.3 拼团核心流程

```
┌──────────────────────┐                ┌──────────────────────┐
│     发起拼团          │                │     参与拼团          │
│                      │                │                      │
│  用户选择拼团购买      │                │  用户浏览进行中的团     │
│  → 创建拼团单 (团长)  │                │  → 选择加入某个团      │
└──────────┬───────────┘                └──────────┬───────────┘
           │                                       │
           └──────────────┬────────────────────────┘
                          ▼
           ┌──────────────────────────────────────────┐
           │  Redis 拼团状态机 (Lua 原子操作)            │
           │                                          │
           │  group:{groupId}                         │
           │  ├── status: FORMING → FULL → SUCCESS    │
           │  │                    ↘ EXPIRED           │
           │  ├── requiredCount: 3                    │
           │  ├── currentCount: 2                     │
           │  ├── members: Set{uid1, uid2}            │
           │  ├── expireAt: 2026-04-06T12:00:00       │
           │  └── productId: 10086                    │
           │                                          │
           │  参团 Lua:                                │
           │  1. CHECK currentCount < requiredCount   │
           │  2. CHECK userId NOT IN members           │
           │  3. SADD members userId                  │
           │  4. INCR currentCount                    │
           │  5. IF currentCount == requiredCount      │
           │     → SET status = FULL                   │
           └──────────────────┬───────────────────────┘
                              │ 成团
                              ▼
                    ┌──────────────────┐
                    │ Kafka: group-events│
                    │ → 创建支付订单     │
                    │ → 通知全部团员支付  │
                    │ → 超时未付自动取消  │
                    └──────────────────┘
```

#### 9.4 订单状态机与库存

```
订单状态流转:

CREATED ──支付──▶ PAID ──发货──▶ SHIPPED ──确认──▶ COMPLETED
   │                │
   │ 30min 超时     │ 退款
   ▼                ▼
CANCELLED        REFUNDING ──▶ REFUNDED

库存管理:
┌──────────────────────────────────────────────────┐
│  下单时: Redis DECR 预扣库存 (Lua 原子操作)        │
│    → 库存 ≥ 1: 扣减成功，创建订单                  │
│    → 库存 < 1: 返回售罄                           │
│                                                  │
│  支付成功: Kafka 异步通知 → MySQL 扣减持久化库存    │
│  支付超时: 延迟队列 (30min) → Redis 回补库存        │
│  拼团单: 全员支付完成 → 团单 SUCCESS → 触发发货     │
└──────────────────────────────────────────────────┘
```

#### 9.5 核心表结构

```sql
-- 商品表
CREATE TABLE product (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(256)   NOT NULL,
    description     TEXT,
    cover_url       VARCHAR(512),
    price           DECIMAL(10, 2) NOT NULL         COMMENT '原价',
    group_price     DECIMAL(10, 2)                  COMMENT '拼团价',
    stock           INT            NOT NULL DEFAULT 0,
    category        VARCHAR(64),
    status          TINYINT        DEFAULT 1        COMMENT '1:上架 0:下架',
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 拼团活动表
CREATE TABLE group_buying_activity (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id      BIGINT         NOT NULL,
    required_count  INT            NOT NULL DEFAULT 2  COMMENT '成团人数',
    group_price     DECIMAL(10, 2) NOT NULL,
    duration_hours  INT            NOT NULL DEFAULT 24 COMMENT '有效期(小时)',
    max_groups      INT            DEFAULT 0           COMMENT '最大开团数, 0=不限',
    status          TINYINT        DEFAULT 1,
    start_time      DATETIME       NOT NULL,
    end_time        DATETIME       NOT NULL,
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 拼团单表
CREATE TABLE group_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_no        VARCHAR(64)    UNIQUE NOT NULL,
    activity_id     BIGINT         NOT NULL,
    leader_id       BIGINT         NOT NULL            COMMENT '团长',
    status          VARCHAR(16)    DEFAULT 'FORMING'   COMMENT 'FORMING/FULL/SUCCESS/EXPIRED/FAILED',
    required_count  INT            NOT NULL,
    current_count   INT            NOT NULL DEFAULT 1,
    expire_at       DATETIME       NOT NULL,
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_expire (status, expire_at)
) ENGINE=InnoDB;

-- 订单表
CREATE TABLE `order` (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(64)    UNIQUE NOT NULL,
    user_id         BIGINT         NOT NULL,
    product_id      BIGINT         NOT NULL,
    group_order_id  BIGINT                             COMMENT '拼团单ID, 单独购买为NULL',
    original_amount DECIMAL(10, 2) NOT NULL             COMMENT '原始金额',
    discount_amount DECIMAL(10, 2) DEFAULT 0            COMMENT '优惠金额',
    pay_amount      DECIMAL(10, 2) NOT NULL             COMMENT '实付金额',
    status          VARCHAR(16)    DEFAULT 'CREATED',
    pay_type        TINYINT                             COMMENT '1:单独购买 2:拼团',
    strategy_id     BIGINT                              COMMENT '命中的策略ID',
    paid_at         DATETIME,
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_status (user_id, status),
    INDEX idx_status_created (status, created_at)
) ENGINE=InnoDB;

-- 用户标签表
CREATE TABLE user_tag (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT         NOT NULL,
    tag_key         VARCHAR(64)    NOT NULL             COMMENT '如 spending_power',
    tag_value       VARCHAR(128)   NOT NULL             COMMENT '如 high',
    score           DOUBLE         DEFAULT 0            COMMENT '标签置信度 0~1',
    source          VARCHAR(32)    DEFAULT 'offline'    COMMENT 'realtime / offline',
    updated_at      DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_tag (user_id, tag_key),
    INDEX idx_tag_key_value (tag_key, tag_value)
) ENGINE=InnoDB;

-- 消费策略表
CREATE TABLE pricing_strategy (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128)   NOT NULL,
    tag_rules       JSON           NOT NULL             COMMENT '匹配规则 {"spending_power":"low","lifecycle":"new"}',
    strategy_type   VARCHAR(32)    NOT NULL             COMMENT 'DISCOUNT / COUPON / GROUP_PRIORITY / CASHBACK',
    strategy_config JSON           NOT NULL             COMMENT '{"discount_rate":0.8} / {"coupon_amount":10}',
    priority        INT            DEFAULT 0            COMMENT '优先级, 值越大越优先',
    status          TINYINT        DEFAULT 1,
    start_time      DATETIME,
    end_time        DATETIME,
    created_at      DATETIME       DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
```

---

## 四、前端架构设计

```
┌──────────────────────────────────────────────────────────────┐
│                  前端架构 (React 18 + Vite 5)                 │
│                                                              │
│  技术选型:                                                    │
│  ├── React 18 + TypeScript 5                                 │
│  ├── Vite 5 (极速构建)                                        │
│  ├── React Router 6 (路由管理)                                │
│  ├── Zustand (轻量状态管理)                                    │
│  ├── TanStack Query v5 (服务端状态 + 缓存 + 自动重试)          │
│  ├── TailwindCSS + Shadcn/UI (组件库)                         │
│  ├── Axios (HTTP 请求 + JWT 拦截器)                            │
│  └── EventSource (SSE, AI 流式问答)                            │
│                                                              │
│  页面模块:                                                    │
│  ├── 认证: 登录 / 注册 / Token 无感刷新                        │
│  ├── 内容: Feed 流 / 文章详情 / Markdown 编辑器                │
│  ├── 搜索: 搜索框 + 实时联想 + 结果列表 + 标签筛选              │
│  ├── 社交: 关注/粉丝列表 / 点赞 / 收藏                         │
│  ├── AI 问答: 对话界面 + SSE 逐字渲染                          │
│  ├── 商城: 商品列表 / 详情 / 拼团大厅 / 购物车 / 订单           │
│  └── 个人中心: 资料编辑 / 标签画像 / 订单管理                   │
│                                                              │
│  JWT 无感刷新拦截器:                                           │
│  ┌──────────────────────────────────────────────┐             │
│  │ Axios Response Interceptor                   │             │
│  │                                              │             │
│  │ 收到 401 →                                   │             │
│  │   if (正在刷新中) → 加入等待队列               │             │
│  │   else →                                     │             │
│  │     标记"刷新中"                               │             │
│  │     POST /auth/refresh                       │             │
│  │     → 成功: 更新 token + 重试原请求 + 释放队列  │             │
│  │     → 失败: 跳转登录页                         │             │
│  └──────────────────────────────────────────────┘             │
└──────────────────────────────────────────────────────────────┘
```

---

## 五、系统全景架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          墨知平台 · 系统全景                                  │
│                                                                             │
│  ┌──────────────┐      ┌───────────────────────────────────────────────┐    │
│  │  前端 SPA     │─────▶│  Nginx / Spring Cloud Gateway                │    │
│  │  React + Vite │      │  ├── 限流 / 熔断 (Sentinel / Resilience4j)   │    │
│  └──────────────┘      │  ├── JWT 公钥验签                              │    │
│                        │  └── 路由转发 / 灰度发布                        │    │
│                        └────────────────────┬──────────────────────────┘    │
│                                             │                               │
│  ┌──────────────────────────────────────────┼──────────────────────────┐    │
│  │                    服务层 (Spring Boot 3.x)                         │    │
│  │                                                                     │    │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │    │
│  │  │ 认证服务   │  │ 内容服务  │  │ 社交服务   │  │ 搜索服务  │            │    │
│  │  │ Auth      │  │ Content  │  │ Social   │  │ Search   │            │    │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │    │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │    │
│  │  │ Feed 服务 │  │ AI 服务   │  │ 商城服务  │  │ 用户服务   │            │    │
│  │  │ Feed     │  │ AI / RAG │  │ Commerce │  │ User     │            │    │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                             │                               │
│  ┌──────────────────────────────────────────┼──────────────────────────┐    │
│  │                     基础设施层                                       │    │
│  │                                                                     │    │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │    │
│  │  │ MySQL    │  │ Redis    │  │ Kafka    │  │ ES       │            │    │
│  │  │ 主从     │  │ Cluster  │  │ Cluster  │  │ Cluster  │            │    │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │    │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐                          │    │
│  │  │ Milvus   │  │ Canal    │  │ MinIO    │                          │    │
│  │  │ 向量库   │  │ Binlog   │  │ 对象存储  │                          │    │
│  │  └──────────┘  └──────────┘  └──────────┘                          │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      可观测性                                       │    │
│  │  Prometheus + Grafana (指标告警)                                    │    │
│  │  SkyWalking (全链路追踪)                                            │    │
│  │  ELK (集中日志: Filebeat → Logstash → Elasticsearch → Kibana)       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 六、Kafka Topic 规划

| Topic | 生产者 | 消费者 | 用途 |
|-------|--------|--------|------|
| `relation-events` | Canal（Outbox 表） | 粉丝表 / 计数 / 缓存 Consumer | 关注 / 取关事件分发 |
| `like-events` | 点赞服务 | 写聚合 Consumer | 点赞异步聚合批量写入 |
| `content-events` | 内容服务 | ES Sink / Feed 缓存 / AI 索引 Consumer | 内容发布 / 更新 / 删除 |
| `group-events` | 拼团服务 | 订单 / 通知 Consumer | 成团 / 过期事件 |
| `order-events` | 订单服务 | 库存 / 通知 Consumer | 订单状态变更 |
| `user-behavior` | 各服务（行为埋点） | 标签计算 Consumer | 用户行为流 → 实时标签计算 |
| `notification` | 各服务 | 推送 Consumer | 站内信 / Push / 短信通知 |

---

## 七、数据库分库策略

```
mozhi_user       →  用户表、用户设置表
mozhi_auth       →  认证凭证表、Outbox 表
mozhi_content    →  笔记表、草稿表、媒体关联表、评论表
mozhi_social     →  关注表、粉丝表、点赞记录表、收藏表
mozhi_commerce   →  商品表、拼团活动表、拼团单表、订单表、优惠券表
mozhi_strategy   →  用户标签表、消费策略表、活动配置表
```

---

## 八、关键非功能性设计

**高可用**：MySQL 主从复制保障数据持久性，Redis Cluster 保障缓存高可用，Kafka 多分区多副本保障消息不丢，ES 多节点多分片保障搜索服务连续性。所有服务多实例部署 + 健康检查 + 自动重启。

**高性能**：三级缓存（Caffeine → Redis 页面 → Redis 片段）吸收热点读流量；Kafka 削峰填谷吸收写峰值；MinIO 预签名直传卸载服务器带宽；写聚合批量入库减少数据库压力；SingleFlight 避免并发回源风暴。

**一致性保障**：Outbox 模式保证事务消息原子投递；Lua 脚本保证 Redis 原子更新；采样校验 + 自愈重建兜底最终一致；延迟双删兜底缓存脏读；Kafka 消费幂等保证 exactly-once 语义。

**安全性**：RS256 非对称签名防篡改；RefreshToken 白名单 + 旋转机制防泄露；MinIO 预签名 URL 限时限类型；接口级 RBAC 鉴权；敏感操作二次验证。

**可观测**：SkyWalking 全链路追踪定位慢请求与异常；Prometheus + Grafana 实时监控 QPS、延迟、错误率并配置告警规则；ELK 集中日志检索与分析；各子系统关键路径埋点覆盖。