# MOZhi 简历素材：面向真实内容平台的后端设计案例

> 用途：这份文档不是变更流水账，而是给后端 / 平台工程 / Agent 开发岗位使用的项目案例集。
> 写法原则：先讲真实场景，再讲为什么不能简单做，最后讲我怎样设计、怎样落地、怎样验证。

---

## 先看这个项目到底在解决什么问题

MOZhi 不是一个“做几个页面、调几个接口”的 demo，而是在逐步搭一个面向真实内容平台的后端系统。

如果把问题说得更直白一点，这个项目想解决的是三类典型平台问题：

1. 用户可以登录，但登录入口不能变成暴力破解、撞库和机器人刷号的入口。
2. 创作者可以写内容，但“草稿”不能只是一个随便增删改查的表，否则审核、发布、多端编辑很快就会乱。
3. 用户可以上传图片或附件，但“上传文件”不能只是前端拿个 URL 往对象存储里塞，因为权限、归属、脏数据、后续 OSS / CDN 演进都会出问题。

所以这份材料不再按“做了哪些功能”来写，而是按“我如何把这些真实问题收束成稳定设计”来写。

---

## 术语解释

为了让第一次接触这份材料的人也能看懂，先把几个关键术语讲清楚。

### 附加验证

这里指的是在登录或注册风险升高时，系统不会立刻放行，而是要求用户再通过一次人机验证。

当前项目里，这个能力接的是 `Cloudflare Turnstile`。

你可以把它理解成：

`系统发现这次请求不像正常用户，就在正式放行前再加一道关。`

---

### access token 和 refresh token

这是双令牌会话模型里的两个角色：

- `access token`：短期凭证，用来访问业务接口。
- `refresh token`：长期凭证，用来换新的 `access token`。

为什么要拆开？

因为“接口鉴权”和“会话续期”其实是两件事。
如果只用一个长期 token，泄露风险大；如果只用一个短 token，用户体验又会很差。

---

### session version

可以把它理解成“这个用户当前会话世代号”。

比如某个用户点了“退出所有设备”，后端把他的 `session version` 加 1，那么之前所有旧版本 token 都会统一失效。

这比一条一条去删除所有 token 更适合做“全设备失效”。

---

### 乐观锁

乐观锁不是“先把数据锁住不让别人改”，而是“允许大家并发修改，但只有基于最新版本的那次写入才能成功”。

在这个项目里，它通过 `version + expectedVersion` 实现。

打个比方：

- 你和我同时打开同一篇草稿，看到的版本都是 `7`
- 你先提交，数据库变成版本 `8`
- 我再提交时还拿着 `expectedVersion=7`
- 系统会拒绝我的写入，而不是悄悄把你的改动覆盖掉

---

### uploadTicket

`uploadTicket` 不是文件本身，也不是上传 URL，它更像是后端签发的一张“带上下文的上传许可证”。

这张票据里会绑定：

- 当前用户是谁
- 这次上传属于哪个草稿
- 用途是什么
- 媒体类型是什么
- 允许的 `content-type`
- 声明的文件大小
- 存储 provider / bucket / objectKey 是什么

打个比方：

如果预签名 URL 像“门口通行证”，那 `uploadTicket` 更像“这张通行证是给谁的、拿来做什么、允许进哪一层楼”的后台登记信息。

所以后端不会只看“前端说我传完了”，而是会拿这张票据去核对：
这次上传到底是不是这个用户、这个草稿、这个用途下的合法对象。

---

### media_ref

`media_ref` 不是简单的“图片 URL 表”，而是内容域里的媒体引用记录。

它记录的不只是 `publicUrl`，还包括：

- `storageProvider`
- `bucketName`
- `objectKey`
- `fileName`
- `sizeBytes`
- `etag`
- `uploadStatus`
- `boundAt`

这样后面切 CDN、换存储厂商、做回源、做去重、做审核时，业务才有稳定事实可依赖。

---

## 案例一：认证入口为什么不能只是一个登录表单

### 场景

假设一个内容平台刚上线，登录页已经能输入用户名和密码，接口也能返回 JWT。

如果只做到这一步，真实环境里很快会出现这些问题：

1. 某个 IP 在 10 分钟内连续打几百次登录接口，系统没有任何拦截。
2. 某个账号标识被持续暴力试错，系统没有锁定机制。
3. 注册接口被机器人批量调用，换用户名、换邮箱、换 IP 反复刷号。
4. 线上用户反馈“为什么我一直提示稍后再试”，但后端没有结构化审计，很难知道是 IP 限流、账号锁定还是附加验证触发。

换句话说，真正的平台问题从来不是“有没有登录页”，而是“登录入口能不能承受恶意流量和异常行为”。

---

### 为什么不能简单做

如果把这些判断散落在 Controller、页面逻辑和第三方组件里，后果通常是：

1. 每新增一条风控规则，就要改多个层次。
2. 前后端错误语义不稳定，排障困难。
3. 状态无法沉淀成统一模型，最后只能靠日志猜。

所以我没有把它实现成“登录失败超过 5 次就 if 一下”，而是把它单独设计成认证主链路的风控子系统。

---

### 我是怎么设计的

核心设计是：

`领域策略服务 + Redis TTL 计数器 + 服务端附加验证 + 结构化审计`

具体来说：

1. `trigger` 层只负责提取上下文
   把 `IP + User-Agent` 统一抽成认证请求上下文。

2. `domain` 层负责决策
   所有登录和注册风控规则都集中在 `AuthSecurityPolicyService`，不让 Controller 到处写判断。

3. `Redis` 负责保存短周期安全状态
   比如：
   - `auth:login:ip:<ip>`
   - `auth:login:identifier:failure:<identifier>`
   - `auth:login:identifier:lock:<identifier>`
   - `auth:register:ip:<ip>`
   - `auth:register:email:<email>`
   - `auth:register:username:<username>`

4. 风险不是“非黑即白”，而是分层升级
   - 轻度异常：要求附加验证
   - 严重异常：直接限流或临时锁定

5. 所有关键事件写结构化审计日志
   包括：
   - `rate_limited`
   - `challenge_required`
   - `locked`
   - `login_failed`
   - `login_succeeded`
   - `register_succeeded`

---

### 一个真实例子怎么讲

你可以这样向面试官举例：

“比如一个正常用户在公司网络里连续登录失败 5 次，不应该立刻把他永久封死，因为他可能只是输错密码；但如果系统观察到同一个标识在短时间内持续失败，就要升级到附加验证。如果失败继续增加，再进入临时锁定。与此同时，如果某个来源 IP 在短时间内打了大量登录请求，那就应该先按来源维度挡掉。这其实是从来源维度、目标维度到风险升级层层收口，而不是只看一个失败次数阈值。”

---

### 我最终落地了什么

- 登录和注册都支持多维限流
- 风险升高时会要求 `Cloudflare Turnstile` 附加验证
- 同一登录标识持续失败会进入临时锁定
- 风控状态通过 Redis TTL 计数器建模
- 没有 Redis 时保留内存降级路径
- 审计日志支持排障和安全复盘

---

### 这部分适合怎么写进简历

- 设计并落地认证主链路风控体系，基于 `IP / identifier / username / email` 多维计数模型实现登录限流、注册限流、附加验证升级与账号临时锁定，并通过 DDD 策略服务统一编排决策逻辑。

---

### 证据锚点

- [AuthSecurityPolicyService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthSecurityPolicyService.java)
- [IAuthAttemptGuardPort.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/adapter/port/IAuthAttemptGuardPort.java)
- [RedisAuthAttemptGuardPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAttemptGuardPortImpl.java)
- [StructuredLogAuthAuditPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/StructuredLogAuthAuditPortImpl.java)

---

## 案例二：会话为什么不能只靠一个长期 JWT

### 场景

很多项目一开始会这么做：

1. 登录成功后发一个 JWT
2. 前端把它存在本地
3. 后面所有接口都带这个 token

这在 demo 里能跑，但在真实环境里很快会撞到几个问题：

1. token 一旦泄露，攻击者可以长期使用。
2. 用户点击“退出登录”时，很难做到真正失效。
3. 用户点击“退出所有设备”时，更难把所有终端同时踢下线。
4. 前端刷新页面后，会话恢复和受保护路由体验会很差。

---

### 为什么不能简单做

因为“接口鉴权”和“会话续期”不是一回事。

- 接口鉴权需要短期凭证，尽量降低泄露面。
- 会话续期需要稳定机制，保证用户不用频繁重新登录。

如果把两件事绑在一个长期 JWT 上，你会同时失去安全性和可治理性。

---

### 我是怎么设计的

我把会话拆成两层：

1. `access token`
   - 生命周期短
   - 用来访问业务接口
   - 适合做接口级鉴权

2. `refresh token`
   - 生命周期更长
   - 放在 `HttpOnly Cookie`
   - 用来静默换发新的 `access token`

在这个基础上，再叠 3 个运行时状态：

1. `refresh token store`
   用于判断 refresh token 是否仍然存活。

2. `access token blacklist`
   用于支持“当前设备退出”后的即时失效。

3. `session version`
   用于支持“退出所有设备”这类全局失效。

这个组合的好处是：

- 当前设备退出：拉黑当前 access token，同时撤销对应 refresh token
- 全设备退出：提升 `session version`，旧会话统一失效
- 静默续期：前端可在刷新页面或 token 过期前自动换新

---

### 一个真实例子怎么讲

比如用户在电脑浏览器和手机浏览器同时登录。

如果他只是想退出电脑端，不应该把手机端一起踢掉。
但如果他怀疑账号泄露，点“退出所有设备”时，又必须让所有历史会话都尽快失效。

这两个需求看起来都叫“登出”，但本质上不是一件事，所以我在设计上把“当前端退出”和“全端退出”拆成了两套失效机制，而不是硬塞进一个单 token 模型里。

---

### 我最终落地了什么

- 双令牌会话模型
- `HttpOnly Cookie` 承载 refresh token
- refresh token rotation
- access token 黑名单
- session version 全设备失效
- 前端静默续期与受保护路由闭环

---

### 这部分适合怎么写进简历

- 主导实现 `access token + refresh token` 双令牌会话架构，通过 `HttpOnly Cookie`、refresh token rotation、access token 黑名单与 session version 失效机制，支持单设备退出、全设备失效、静默续期与受保护路由闭环。

---

### 证据锚点

- [AuthController.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthController.java)
- [AuthCookieSupport.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthCookieSupport.java)
- [JwtAuthTokenPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/JwtAuthTokenPortImpl.java)
- [SessionBootstrap.tsx](/F:/new_opint/VibeCoding/MOZhi/mozhi-web/src/auth/SessionBootstrap.tsx)
- [guards.tsx](/F:/new_opint/VibeCoding/MOZhi/mozhi-web/src/router/guards.tsx)

---

## 案例三：草稿为什么不能只是 CRUD

### 场景

内容平台里的“草稿”听起来像一个很简单的表，但真实环境里它经常会面临这些情况：

1. 用户开着两个标签页同时编辑同一篇草稿。
2. 草稿已经送审，但用户还在另一个页面改正文。
3. 某个用户试探另一个用户的草稿 ID，看看能不能访问。
4. 随着数据增长，草稿列表不能一直全量返回。

如果把草稿做成一个普通 CRUD 接口，这些问题迟早都会出现。

---

### 为什么不能简单做

因为草稿本质上不是“几列字段的增删改查”，而是一个有生命周期、有写入边界、会被审核和发布链路继续消费的写模型。

换句话说，它要回答的不只是“能不能改”，还要回答：

- 什么时候能改
- 谁能改
- 多个请求同时改时以谁为准
- 哪些状态下必须冻结

---

### 我是怎么设计的

我把草稿建模成了一个受控聚合，而不是贫血表记录。

核心设计有 5 个：

1. 生命周期状态机
   `DRAFT / UPLOADING / PENDING_REVIEW / PUBLISHED / REJECTED / ARCHIVED`

2. 正文更新和状态流转拆成两个入口
   不让客户端在一次正文提交里顺手篡改生命周期。

3. `version + expectedVersion` 乐观锁
   防止多端编辑或状态竞态导致静默覆盖。

4. 私有资源按当前用户隔离
   非本人访问统一返回 `404`，避免资源探测。

5. 数据库约束 + 应用层规则双重兜底
   状态合法性不只靠代码约定。

---

### 一个真实例子怎么讲

比如编辑 A 和编辑 B 同时打开草稿：

- A 先把版本 `12` 改成 `13`
- B 还拿着旧的 `expectedVersion=12` 再提交

如果没有乐观锁，B 的提交就会把 A 的改动无声覆盖掉。
如果草稿已经进入 `PENDING_REVIEW`，系统还允许正文继续修改，那审核人员看到的内容和最终发布内容可能根本不是同一版。

所以我把草稿这件事当成“写模型治理”来做，而不是当成一个普通表单接口。

---

### 我最终落地了什么

- 草稿状态机与写入冻结
- 乐观锁并发控制
- 非本人访问 `404` 防探测
- 分页与状态筛选
- 数据库状态约束
- DDD 切片下的内容子域实现

---

### 这部分适合怎么写进简历

- 主导将草稿能力从普通 CRUD 升级为面向真实内容平台的写模型，围绕 `生命周期状态机 + 乐观锁并发控制 + 资源隔离 + 数据库约束 + 分页契约` 建立一体化治理机制，使草稿在多端编辑、审核前后和异常数据场景下都具备可控写入边界。

---

### 证据锚点

- [DraftEntity.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/DraftEntity.java)
- [DraftDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java)
- [IDraftRepository.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/adapter/repository/IDraftRepository.java)
- [DraftController.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/DraftController.java)
- [V4__harden_draft_write_model.sql](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V4__harden_draft_write_model.sql)

---

## 案例四：文件上传为什么不能只给前端一个 URL

### 场景

这是 `Step 2.2` 最值得讲的地方。

很多项目一开始做上传时，思路是这样的：

1. 后端给前端一个预签名 URL
2. 前端把文件直接传到对象存储
3. 前端再把 URL 存到数据库

这套流程在最小 demo 里是可用的，但一旦放到真实内容平台，就会出现很多问题：

1. 用户拿到了 presign，但没有真正完成上传，桶里会留下垃圾对象。
2. 前端说“我上传成功了”，但后端并不知道对象到底在不在、是不是这次草稿的、是不是允许的类型。
3. 如果对象 key 由前端自己拼，很容易出现路径污染、越权写入和命名混乱。
4. 如果业务表只存 `publicUrl`，以后接 CDN、换 OSS/S3、做回源或去重时会很被动。
5. 如果同一个对象被重复确认，甚至被别的草稿绑走，后面发布链路会出脏数据。

所以这一步真正要解决的，不是“能不能上传一张图片”，而是“怎么让直传变成可控、可审计、可扩展的业务能力”。

---

### 为什么不能简单做

因为“上传对象到存储”和“把媒体绑定到内容业务”其实不是一件事。

前者是通用存储问题，后者是内容域问题。
如果把两件事硬塞在一个 Controller 里，后面你一旦接：

- 阿里云 OSS
- 腾讯 COS
- AWS S3
- CDN 域名切换
- 图片审核
- 视频转码

整个接口和数据模型都会开始扭曲。

所以我在 `Step 2.2` 里先做了一个关键拆分：

`通用存储预签名能力`
和
`草稿媒体确认绑定能力`

分开设计。

---

### 我是怎么设计的

这一步的关键设计是 6 件事。

#### 1. 统一的预签名入口，而不是业务各写一套

先由 `POST /api/storage/presign` 生成：

- 上传 URL
- `provider`
- `bucket`
- `objectKey`
- `uploadTicket`

这样头像上传、草稿媒体上传、以后附件上传都能共用同一套存储入口。

---

#### 2. 对象路径由后端生成，不让前端自定义

对象路径格式类似：

`drafts/{userId}/{draftId}/{yyyyMMdd}/{uuid}.{ext}`

为什么这样做？

因为对象路径本身就是资源治理的一部分。

如果交给前端拼，很容易出现：

- 把别人的草稿路径拿来上传
- 目录结构越来越乱
- 后续回收和审计困难

---

#### 3. 用 uploadTicket 绑定上传上下文

这里的 `uploadTicket` 就是这一步最值得讲的设计之一。

它不是文件本身，也不是 URL，而是一张带签名的“上传许可证”，里面绑定了：

- 当前用户
- 当前草稿
- 当前用途
- 媒体类型
- 允许的 `content-type`
- 声明大小
- `provider / bucket / objectKey`

为什么不直接只返回 presign URL？

因为 presign URL 只解决“能不能往对象存储写”，并不能回答：

- 这是不是这个用户的上传
- 这是不是这个草稿的上传
- 这是不是声明的文件类型和大小
- 后续确认时应该把它绑到哪里

而 `uploadTicket` 可以把这些业务上下文一起带过去。

---

#### 4. 确认阶段不信任前端，而是信任后端探测

上传完成后，不是前端直接把 `publicUrl` 记进库里，而是调用：

`POST /api/content/drafts/{draftId}/media/confirm`

这一步后端会做几件事：

1. 先验 `uploadTicket`
2. 校验当前用户和草稿归属
3. 校验草稿当前仍处于可写状态
4. 去对象存储探测对象是否真实存在
5. 核对 `content-type`、大小、`etag`
6. 通过后再把媒体写入 `media_ref`

这一步很像柜台验票：

- 不是乘客自己说“我有票”
- 而是检票员拿票去验真伪，再核对车次和座位

---

#### 5. 媒体绑定要幂等，还要防脏绑定

真实环境里很常见的一种情况是：

- 用户点击了两次确认按钮
- 前端超时重试了一次
- 或者客户端刷新后又重放了一遍确认请求

如果没有幂等，数据库里很容易出现两条相同媒体记录。

所以这里做了两层约束：

1. 同一个对象重复确认，对同一草稿是幂等的
2. 同一个对象不能被别的草稿或别的用户拿去脏绑定

---

#### 6. media_ref 不只存 URL，而是存稳定事实

这一步没有把 `media_ref` 设计成“只有一个图片地址”，而是保留了：

- `storageProvider`
- `bucketName`
- `objectKey`
- `fileName`
- `sizeBytes`
- `etag`
- `uploadStatus`
- `boundAt`

这样做的原因很现实：

今天你可能用 MinIO，明天可能换 OSS；
今天你可能直出源站地址，明天可能统一走 CDN 域名。

如果数据库里只有 `publicUrl`，后面每次迁移都会很痛苦。
如果你保留了 `provider + bucket + objectKey` 这些稳定事实，URL 只是派生结果，后面演进就从容得多。

---

### 一个真实例子怎么讲

你可以这样讲这个场景：

“比如一个创作者在编辑草稿时连续上传 3 张图。第一张传成功了，但浏览器刷新了，前端没来得及确认；第二张用户点了两次确认；第三张对象 key 如果是前端自己拼的，甚至可能误绑到别的草稿。为了避免这种情况，我把上传链路拆成了通用预签名和业务确认两步：前者解决对象存储写入授权，后者解决归属、元数据验证和内容域绑定。后端不会相信前端说‘我传好了’，而是会主动去对象存储探测对象，再决定是否把它写进 `media_ref`。这样直传不再只是一个 URL，而是一套可控的媒体入库流程。”

---

### 我最终落地了什么

- 通用存储预签名接口
- `uploadTicket` 机制
- 对象路径后端统一生成
- 草稿媒体确认绑定接口
- 存储对象元数据探测
- 冻结态草稿禁止继续绑媒体
- 幂等确认与防脏绑定
- 面向 OSS / S3 / COS / CDN 的 provider 抽象

---

### 这部分适合怎么写进简历

- 设计并落地面向真实内容平台的受控直传上传链路，将“对象上传授权”和“内容域媒体绑定”拆分为两阶段协议：通过通用预签名接口生成带上下文的 `uploadTicket`，并在确认阶段由后端主动探测对象存储、校验元数据、完成 `media_ref` 绑定，避免前端直写 URL 带来的越权、脏绑定和后续 OSS / CDN 演进成本。

---

### 证据锚点

- [StorageController.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/StorageController.java)
- [StorageDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/service/StorageDomainService.java)
- [StorageUploadTicketClaims.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/model/valobj/StorageUploadTicketClaims.java)
- [IStorageUploadTicketPort.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/adapter/port/IStorageUploadTicketPort.java)
- [IStorageObjectInspectPort.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/adapter/port/IStorageObjectInspectPort.java)
- [DraftDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java)
- [MediaRefEntity.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/MediaRefEntity.java)
- [MediaRefRepositoryImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/MediaRefRepositoryImpl.java)
- [V5__evolve_media_ref_for_direct_upload.sql](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V5__evolve_media_ref_for_direct_upload.sql)
- [StorageHttpIntegrationTest.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/StorageHttpIntegrationTest.java)

---

## 这份材料怎么用在简历和面试里

### 如果简历篇幅有限

最推荐你保留这 4 条：

- 认证入口风控体系
- 双令牌会话与全设备失效
- 草稿写模型治理
- 受控直传上传链路

---

### 如果面试官让你讲“项目里最有技术含量的一部分”

推荐优先讲两个主题：

1. `草稿写模型治理`
   因为它最能体现你对业务写模型、状态机、并发控制和错误语义的理解。

2. `受控直传上传链路`
   因为它最能体现你不把“文件上传”做成 demo，而是考虑真实对象存储、归属校验、元数据确认和后续 OSS / CDN 演进。

---

### 如果面试官追问“你做的是不是过度设计”

你可以这样回答：

“我没有一步做到分片上传、转码、病毒扫描和复杂审核流，因为那会超出当前阶段；但我把最容易在真实环境里出问题的边界先收住了，比如风控分层、会话失效、草稿状态机、乐观锁、资源防探测、带上下文的上传票据和后端对象探测。这些都属于 production-minded MVP：不是终局系统，但关键边界已经具备继续演进的基础。”

---

## 当前边界与后续演进

这份材料强调的是“现在已经做成了什么”，也要诚实说明“还没做到什么”。

当前还没有展开的增强项主要包括：

- 邮箱验证、找回密码、MFA
- 草稿搜索、回收站、复杂筛选
- 病毒扫描、内容安全审核
- 分片上传、大文件处理
- 图片压缩、缩略图、视频转码
- CDN 签名下载与回源策略

这并不意味着当前设计不成熟，而是说明：

`我先把主链路上最容易出事故的边界收住，再按阶段继续往上叠生产能力。`
