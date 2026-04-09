# MOZhi 简历素材：认证主链路中的 IP 风控与附加验证设计

> 说明：本文档只保留一个高阶技术主题，用于后端 / 平台工程 / Agent 开发岗位简历与面试表达。
> 术语说明：文中“附加验证”指 `Cloudflare Turnstile` 这套服务端校验的人机验证能力。

---

## 一句话亮点

主导实现认证主链路的多维风控体系，基于 `DDD 策略服务 + Redis TTL 计数器 + 服务端附加验证` 完成登录/注册限流、账号锁定、挑战升级与审计留痕，既保证了前后端认证闭环，又为后续 MFA、设备治理和更复杂风控策略预留了清晰扩展边界。

---

## 高阶简历写法

- 设计并落地认证主链路风控体系，基于 `IP / identifier / username / email` 多维计数模型实现登录限流、注册限流、账号临时锁定与附加验证升级，并通过 DDD 策略服务统一编排决策逻辑。
- 将认证风控从页面与接口逻辑中剥离，沉淀为 `领域策略 + 存储端口 + 基础设施适配` 的分层架构，采用 Redis TTL 计数器实现低成本高可用的实时防刷能力，同时保留无 Redis 时的内存降级路径。
- 建立认证审计闭环，对 `rate_limited / challenge_required / locked / login_failed / login_succeeded / register_succeeded` 等事件做结构化日志记录，兼顾安全观测、问题追踪与隐私脱敏。

---

## 情境

项目在 Phase 1 已经具备注册、登录、刷新会话和个人资料能力，但如果只停留在“用户能输账号密码登录”，很快就会遇到几个真实问题：

1. 登录接口会成为暴力破解入口。
2. 注册接口会成为批量撞库和机器人刷号入口。
3. 错误处理如果分散在 Controller、页面和第三方组件里，后续每加一种风控规则都要多点修改。
4. 如果没有统一审计能力，线上出现“为什么这个用户被拦”“为什么这个 IP 一直报 A0429”时很难追。

因此，这一块不能只做成表单，而必须做成一套可解释、可扩展、可观测的认证主链路风控系统。

---

## 任务

在不破坏现有 DDD 分层、不把风控细节散落到前端页面和 Controller 的前提下，设计一套认证风控体系，满足以下目标：

1. 登录与注册都支持按不同维度做限流与拦截。
2. 当风险升高时，不是直接封死，而是先升级到附加验证。
3. 当同一账号标识持续失败时，要支持临时锁定。
4. 风控规则要可配置，而不是写死在代码里。
5. 风控状态要能落在 Redis 中长期工作，同时在本地无 Redis 时具备可运行降级。
6. 整个过程要有统一审计记录，便于排障和复盘。

---

## 最终实现

### 1. 架构分层

这套能力被拆成四层：

1. `trigger` 层负责提取请求上下文
   由 [AuthRequestContextResolver.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthRequestContextResolver.java#L12) 把 `IP + User-Agent` 统一抽取成 `AuthRequestContext`。
   IP 获取规则是：
   - 优先取 `X-Forwarded-For` 第一跳
   - 没有代理头时退回 `request.getRemoteAddr()`

2. `domain` 层负责风控决策
   所有认证风控规则都集中在 [AuthSecurityPolicyService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthSecurityPolicyService.java#L13)。
   它只关心三件事：
   - 当前请求上下文是什么
   - 当前计数和锁定状态是什么
   - 按策略应该放行、要求附加验证，还是直接拦截

3. `domain port` 定义状态存取边界
   风控计数与锁定能力不是直接写死 Redis，而是先抽象成 [IAuthAttemptGuardPort.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/adapter/port/IAuthAttemptGuardPort.java)。
   这样领域层不依赖任何具体缓存实现。

4. `infrastructure` 层负责落地存储与外部能力
   - Redis 实现见 [RedisAuthAttemptGuardPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAttemptGuardPortImpl.java#L11)
   - 无 Redis 时退回 [InMemoryAuthAttemptGuardPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/InMemoryAuthAttemptGuardPortImpl.java#L12)
   - 附加验证外部能力由 Turnstile gateway 承担
   - 审计落在 [StructuredLogAuthAuditPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/StructuredLogAuthAuditPortImpl.java#L9)

这个拆法的核心目的，是让“风控决策”和“风控状态存储”解耦。
领域层负责判定规则，Redis 只负责计数和过期，不负责业务语义。

---

### 2. 数据模型

当前没有单独建风控表，也没有用复杂规则引擎，而是采用了 `Redis String + TTL` 的轻量数据模型。

核心键模型如下：

- `auth:login:ip:<ip>`
- `auth:login:identifier:failure:<identifier>`
- `auth:login:identifier:lock:<identifier>`
- `auth:register:ip:<ip>`
- `auth:register:email:<email>`
- `auth:register:username:<username>`

其中：

1. `ip` 用来控制单来源请求频率
2. `identifier` 用来控制同一登录标识的连续失败次数
3. `lock` 用来表示某个标识是否进入锁定窗口
4. `email` 和 `username` 用来控制注册维度的定向刷号

值模型也很简单：

- 计数键：值是整数
- 锁定键：值固定写 `"1"`，真正的锁定时长靠 TTL 表示

代码位置在 [RedisAuthAttemptGuardPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAttemptGuardPortImpl.java#L21)。

之所以没有一开始就上关系型表或复杂规则引擎，原因是这类风控状态有三个特点：

1. 写多读少
2. 生命周期短
3. 最重要的是实时性和过期控制，而不是长期分析查询

Redis TTL 计数器在这里是性价比最高的实现。

---

### 3. 规则配置

所有规则都通过配置注入，而不是写死在逻辑里，见 [application.yml](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/resources/application.yml#L33)。

当前默认策略如下：

#### 登录侧

- 同一 IP：`10 分钟 20 次`
- 同一标识失败次数达到 `5` 次：要求附加验证
- 同一标识失败次数达到 `10` 次：锁定 `15 分钟`
- 登录失败计数窗口：`15 分钟`

#### 注册侧

- 同一 IP：`60 分钟 5 次`
- 同一 IP 超过 `3` 次：要求附加验证
- 同一邮箱：`24 小时 3 次`
- 同一用户名：`24 小时 5 次`

这样设计的原因是：

1. 登录和注册的攻击模式不同，不能共用一套阈值。
2. 登录更关注暴力破解，所以要对同一标识失败次数做升级和锁定。
3. 注册更关注批量刷号，所以要对 IP、邮箱、用户名分别建模。
4. 阈值放到配置里，后续压测或风控策略调整时不需要改领域代码。

---

### 4. 登录链路的执行顺序

登录前的策略执行在 [AuthSecurityPolicyService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthSecurityPolicyService.java#L66)。

顺序非常明确：

1. 先按 IP 计数
   调用 `incrementLoginIpAttempts(ip, ttl)`。
   如果超过 `loginIpMaxAttempts`，直接返回 `A0429`。

2. 再看该登录标识是否已锁定
   调用 `isLoginIdentifierLocked(identifier)`。
   如果已锁定，同样直接返回 `A0429`。

3. 再看该标识历史失败次数
   调用 `currentLoginIdentifierFailures(identifier)`。
   如果失败次数达到 challenge 阈值，但当前请求没通过附加验证，则返回 `A0410`。

4. 登录失败后再累加失败计数
   在 [AuthSecurityPolicyService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthSecurityPolicyService.java#L89) 中：
   - 失败计数 `+1`
   - 如果达到锁定阈值，则写入 `lock` 键并附带 TTL

5. 登录成功后清理失败计数
   在 [AuthSecurityPolicyService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthSecurityPolicyService.java#L99) 中调用 `clearLoginIdentifierFailures(identifier)`。

这里的关键设计点是：

- IP 限流优先于账号级逻辑，先挡住来源级洪峰
- 标识锁定优先于 challenge，避免已经确认是高风险状态时还继续交互
- challenge 是中间层，不是第一步也不是最后一步，而是风险升级后的缓冲带

这比“失败几次就直接封死”更平衡，因为它允许正常用户在一定风险阈值内通过附加验证继续完成登录。

---

### 5. 注册链路的执行顺序

注册前的策略执行在 [AuthSecurityPolicyService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthSecurityPolicyService.java#L104)。

顺序如下：

1. 先统计当前 IP 的注册次数
2. 如果超过 IP 上限，直接返回 `A0429`
3. 如果超过 challenge 阈值但未通过附加验证，返回 `A0410`
4. 再统计用户名维度的注册尝试次数
5. 超过用户名上限则拦截
6. 再统计邮箱维度的注册尝试次数
7. 超过邮箱上限则拦截

为什么顺序要这样设计：

1. `IP` 是最粗粒度的入口防线，最先挡住批量来源
2. `challenge` 在 IP 轻度异常时介入，可以避免用户稍微频繁操作就被直接封死
3. `username` 和 `email` 是更细粒度的注册目标维度，用于补足“同 IP 换账号名 / 换邮箱反复撞”的场景

这是一种从“来源维度”到“目标维度”的分层拦截模型，而不是单点阈值判断。

---

### 6. 归一化与隐私处理

这块实现还有两个很容易被忽略但很重要的细节：

#### 输入归一化

在 [AuthSecurityPolicyService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthSecurityPolicyService.java#L157) 中，所有 `IP / identifier / username / email` 都会先做：

- `trim`
- `lowercase`

这样可以避免：

- `Alice` 和 `alice` 被当成两个人
- 带空格的脏输入绕过限流
- 同一邮箱因为大小写不同被拆成多个计数桶

#### 审计脱敏

在 [AuthSecurityPolicyService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthSecurityPolicyService.java#L164) 中，审计主题不会直接打印完整邮箱或账号，而是做脱敏处理，比如：

- 邮箱显示成 `a***@domain.com`
- 用户名显示成 `at***`

然后再通过 [StructuredLogAuthAuditPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/StructuredLogAuthAuditPortImpl.java#L13) 输出结构化日志：

- `event`
- `subject`
- `outcome`
- `ip`
- `userAgent`

这样既满足排查需要，又避免把敏感标识完整写进日志。

---

### 7. 为什么不用数据库表

如果从“建模完整性”角度看，很多人第一反应会是建一张 `auth_risk_event` 或 `auth_attempt_counter` 表。

当前没有这么做，是因为这个场景更看重：

1. 高频写入性能
2. TTL 过期控制
3. 实时拦截延迟
4. 实现简单且可维护

关系型表更适合历史留存和离线分析，不适合做这种高频短生命周期的实时门控。
Redis TTL Counter 在这个阶段是更合理的选择。

如果未来要升级到更复杂的风控系统，再演进成：

- Redis + Lua 的严格滑动窗口
- 风控事件明细表
- 风险评分模型
- 异步画像与黑名单系统

都会比现在直接上重系统更合适。

---

### 8. 为什么不是直接把 Redis 写在业务里

这也是面试里很容易被追问的点。

如果在业务代码里直接写：

- `redis.incr(...)`
- `redis.hasKey(...)`
- `redis.expire(...)`

短期确实快，但会带来三个问题：

1. 领域层被 Redis API 污染
2. 本地测试和 CI 很难做无依赖回归
3. 后续想切换实现或做降级时，改动面会扩散到业务逻辑

所以这里先抽象了 [IAuthAttemptGuardPort.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/adapter/port/IAuthAttemptGuardPort.java)，再由：

- [RedisAuthAttemptGuardPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAttemptGuardPortImpl.java#L11)
- [InMemoryAuthAttemptGuardPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/InMemoryAuthAttemptGuardPortImpl.java#L12)

分别实现。

这样设计的原因是：

1. 让领域策略只面向“计数、查询、锁定、清理”这些业务语义
2. 让基础设施层自己决定“是 Redis 还是进程内存”
3. 让本地无 Redis 时系统仍可运行
4. 让测试环境能在低成本下稳定回归

这其实是一个很典型的“领域策略稳定，基础设施可替换”的 DDD 落地案例。

---

### 9. 当前设计的收益与边界

#### 收益

1. 认证风控规则集中，不会散落在 Controller 和页面里
2. 多维度限流比单纯按 IP 更抗绕过
3. 附加验证提供了比“直接封死”更平衡的风险升级路径
4. 审计日志可以直接支持排障和安全复盘
5. Redis 优先、内存降级的双实现让本地开发和线上运行都能兼顾

#### 当前边界

1. 目前还是阈值规则，不是风险评分模型
2. Redis 计数器现在是 `increment + expire`，更接近简单 TTL 窗口，不是严格滑动窗口
3. Redis 实现和内存实现的过期语义并不完全一致，后续如果继续工程化，应进一步统一
4. 当前还没有把邮箱验证、找回密码和 MFA 纳入同一套风控编排

---

## 面试表达建议

### 最推荐的讲法

“我在这个项目里不是只做了登录注册页面，而是把认证主链路里的风控体系单独设计出来了。核心做法是把决策逻辑放在领域策略服务里，把状态存取抽象成风控端口，再用 Redis TTL 计数器去实现 IP、identifier、username、email 这几个维度的实时门控。这样登录和注册都能做到限流、附加验证升级、账号锁定和审计留痕，而且未来要继续扩展 MFA 或设备管理时，边界还是清晰的。”

### 高频追问

#### 为什么不用数据库表来做？

答法要点：
- 这个场景是高频短状态实时判断，Redis 更适合。
- 关系型表更适合长期留存和离线分析，不适合高频实时门控。
- 当前阶段优先选择成本低、实时性强、实现边界清晰的方案。

#### 为什么先看 IP，再看 identifier？

答法要点：
- IP 是最粗粒度来源控制，先挡洪峰。
- identifier 是更精细的账号级风险，用来控制同一账号被持续撞库。
- 两层叠加比只靠一层更抗绕过。

#### 为什么需要附加验证，而不是失败几次就直接封死？

答法要点：
- 直接封死会误伤正常用户。
- 附加验证提供了更平衡的风险升级路径。
- 真正高风险时再进入锁定，用户体验和安全性更平衡。

#### 为什么要做审计脱敏？

答法要点：
- 日志需要能排障，但不能把完整邮箱和账号直接暴露出来。
- 脱敏后仍能定位问题，同时更符合安全与隐私治理要求。

---

## 证据锚点

- 认证策略服务：[AuthSecurityPolicyService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthSecurityPolicyService.java)
- 风控端口：[IAuthAttemptGuardPort.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/adapter/port/IAuthAttemptGuardPort.java)
- Redis 实现：[RedisAuthAttemptGuardPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAttemptGuardPortImpl.java)
- 内存降级实现：[InMemoryAuthAttemptGuardPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/InMemoryAuthAttemptGuardPortImpl.java)
- 请求上下文提取：[AuthRequestContextResolver.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthRequestContextResolver.java)
- 请求上下文模型：[AuthRequestContext.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/model/valobj/AuthRequestContext.java)
- 审计端口：[IAuthAuditPort.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/adapter/port/IAuthAuditPort.java)
- 审计实现：[StructuredLogAuthAuditPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/StructuredLogAuthAuditPortImpl.java)
- 限流阈值配置：[application.yml](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/resources/application.yml)

---

## 二、JWT 双令牌会话架构与多设备登出治理

### 一句话亮点

设计并落地 `短生命周期 access token + 长生命周期 refresh token` 的双令牌会话架构，通过 `HttpOnly Cookie`、refresh token rotation、access token 黑名单和 session version 失效机制，实现单端退出、全端退出、静默续期和受保护路由闭环。

---

### 高阶简历写法

- 主导实现 JWT 双令牌会话架构，采用短期 access token 承载接口鉴权、长期 refresh token 承载会话续期，并通过 `refresh token rotation + access token blacklist + session version` 组合机制支持单设备退出与全设备失效。
- 设计前后端一体化会话恢复链路：前端启动时静默刷新恢复登录态，未认证请求统一重定向到登录页并保留 redirect，形成完整的无状态会话治理闭环。

---

### 情境

如果只用单个 JWT 做认证，会遇到几个典型问题：

1. token 生命周期短时，用户体验很差，频繁掉登录。
2. token 生命周期长时，一旦泄露，风险窗口太大。
3. 只有 access token 时，很难优雅实现静默续期。
4. 如果没有显式会话治理能力，“退出当前设备”和“退出所有设备”通常很难做干净。

因此，这里没有采用“一个 token 走天下”的方案，而是显式拆成双令牌模型，把“接口鉴权”和“会话续期”分开设计。

---

### 任务

在保持后端无状态接口风格的前提下，实现一套可治理的会话体系，满足以下目标：

1. access token 用于接口鉴权，尽量短生命周期。
2. refresh token 用于续期，不直接暴露给前端脚本。
3. 支持静默刷新恢复登录态。
4. 支持“退出当前设备”和“退出所有设备”两种失效语义。
5. 令牌失效后，前端路由与登录态恢复逻辑要一致。

---

### 最终实现

### 1. 会话架构拆分

后端登录与刷新入口在 [AuthController.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthController.java#L28)。

登录成功后，后端不会把完整 token 对全部塞进 JSON，而是拆成两部分：

1. `access token`
   - 放在响应体里返回给前端
   - 由前端保存在认证状态中
   - 用于后续业务接口的 `Authorization: Bearer ...`

2. `refresh token`
   - 通过 [AuthCookieSupport.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthCookieSupport.java#L8) 写入 `HttpOnly Cookie`
   - `SameSite=Strict`
   - `Path=/api/auth`
   - `Secure` 由环境配置控制

这样设计的原因是：

- access token 需要被前端拿到并随请求发送，因此放响应体更直接。
- refresh token 只应该参与续期和注销，不应该暴露给前端脚本，因此放 `HttpOnly Cookie` 更安全。
- `Path=/api/auth` 能把 refresh token 的使用范围限制在认证相关接口，不让它在所有请求里被无差别带上。

---

### 2. JWT 载荷模型

令牌签发逻辑在 [JwtAuthTokenPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/JwtAuthTokenPortImpl.java#L31)。

当前 token 里包含这些核心字段：

- `user_id`
- `username`
- `token_type`
- `session_version`
- `jti`
- `issuedAt`
- `expiresAt`

其中最关键的是：

1. `token_type`
   用来区分 access token 和 refresh token，避免刷新接口误收 access token，或业务鉴权误收 refresh token。

2. `jti`
   每个 token 的唯一 ID，用来做黑名单和精确吊销。

3. `session_version`
   用来支持“退出所有设备”。
   只要该用户的 session version 被提升，所有旧 access token 即便还没过期，也会在鉴权时失效。

这说明它不是一个“只存 userId 的最小 JWT”，而是显式考虑了令牌类型、可吊销性和多端治理能力。

---

### 3. refresh token rotation

refresh 逻辑在 [AuthDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthDomainService.java#L59)。

执行顺序是：

1. 解析 refresh token
2. 判断它是否仍在 refresh token store 中处于 active 状态
3. 如果有效，先撤销当前 refresh token
4. 再重新签发一对新的 access/refresh token
5. 把新的 refresh token ID 存回 store

这里不是“refresh token 一直复用到过期”，而是 `rotation` 模式。
这样设计的原因是：

- 一旦 refresh token 被窃取，长期复用的风险会很高。
- rotation 模式可以缩短 refresh token 被重复使用的有效窗口。
- 每次续期都换新 token，也更利于后续扩展异常续期检测。

对应的 refresh token 存储在 [RedisAuthRefreshTokenStorePortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthRefreshTokenStorePortImpl.java#L11)，数据模型是：

- `auth:refresh:<userId>:<tokenId>` 记录某个 refresh token 是否仍然有效
- `auth:session:<userId>` 用 `Set` 维护该用户当前所有 refresh token ID

这个 `Set` 很重要，因为后续 `logout-all` 就是靠它批量收回该用户所有 refresh token。

---

### 4. 单设备退出怎么做

当前设备退出在 [AuthDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthDomainService.java#L79)。

它做了两件事：

1. 撤销当前 refresh token
2. 把当前 access token 加入黑名单

access token 黑名单能力由 [RedisAuthAccessTokenRevocationPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAccessTokenRevocationPortImpl.java#L21) 提供，键模型是：

- `auth:access:blacklist:<userId>:<tokenId>`

值固定是 `"1"`，TTL 跟 access token 剩余寿命一致。

这样做的原因是：

- refresh token 收回后，可以阻止后续继续续期。
- 但如果当前 access token 还没过期，仅撤销 refresh token 并不能立刻让当前请求方失效。
- 因此需要把当前 access token 也黑名单化，保证“当前设备退出”能立即生效。

这是一种“refresh 负责续期治理，blacklist 负责即时失效”的组合设计。

---

### 5. 退出所有设备怎么做

全设备退出在 [AuthDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthDomainService.java#L96)。

它不是遍历所有 access token 逐个拉黑，而是分两步：

1. `revokeAll(userId)`
   删除该用户 `auth:session:<userId>` 下维护的全部 refresh token

2. `bumpSessionVersion(userId)`
   提升该用户的 session version

access token 鉴权时，会额外校验 token 自带的 `session_version` 是否仍等于当前系统记录值，见 [AuthDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthDomainService.java#L105)。

这样设计的原因是：

- 如果全设备退出时去枚举所有历史 access token，几乎不可行，也很难维护。
- session version 提供了一种更轻量的“整批失效”机制。
- 只要版本号变了，所有旧 access token 会在下一次鉴权时自然失效。

这是一个很典型的“单 token 精确吊销 + 全局版本失效”双机制组合：

- 当前设备退出：黑名单当前 access token
- 所有设备退出：提升 session version

这两种能力的成本和语义都各自合理。

---

### 6. 前端会话恢复与路由治理

前端不是被动等用户每次手动登录，而是补了完整的会话恢复链路。

1. 启动恢复
   [SessionBootstrap.tsx](/F:/new_opint/VibeCoding/MOZhi/mozhi-web/src/auth/SessionBootstrap.tsx#L5) 在应用启动时自动调用 `/auth/refresh`：
   - 成功则恢复 access token 和用户身份
   - 失败则回退到匿名态

2. 受保护路由
   [guards.tsx](/F:/new_opint/VibeCoding/MOZhi/mozhi-web/src/router/guards.tsx#L9) 会在未认证时统一跳去登录页，并带上 `redirect`

3. 登录态存储
   [useAuthStore.ts](/F:/new_opint/VibeCoding/MOZhi/mozhi-web/src/stores/useAuthStore.ts#L17) 只存 access token 和从 access token 解析出来的用户信息，不直接存 refresh token

这样设计的原因是：

- refresh token 已在 `HttpOnly Cookie` 中，不应该进入前端状态管理。
- 前端只关心“当前是否有有效 access token”以及“是否完成 bootstrap”。
- 路由守卫和启动恢复共享同一套状态模型，能避免“页面以为登录了，但会话其实已经失效”的割裂状态。

---

### 7. 为什么不是传统服务端 Session

这个点面试里也很容易被问。

当前系统在 [SecurityConfiguration.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/SecurityConfiguration.java#L37) 里明确配置为 `STATELESS`。

也就是说：

- 不依赖服务端 HttpSession 存用户态
- 认证上下文主要由 JWT 承载
- 会话治理通过 refresh token store、access token blacklist 和 session version 实现

这样设计的原因是：

1. 前后端分离场景下，JWT 更适合跨服务和 API 鉴权
2. access token 无状态，读性能更好
3. refresh token、blacklist、session version 把“可治理性”补回来，避免纯 JWT 难以失效控制的问题

这套设计本质上是在 `无状态鉴权` 和 `可控会话治理` 之间做平衡，而不是二选一。

---

### 8. 当前设计的收益与边界

#### 收益

1. access token 和 refresh token 职责分离，安全性和体验更平衡
2. refresh token 不暴露给前端脚本，降低被前端运行时代码误用的风险
3. 当前设备退出和全部设备退出都能表达得很清晰
4. 会话恢复、路由保护和后端会话治理是同一条主链路，不是拼接出来的体验
5. 数据模型简单，Redis 就能支撑单 token 吊销和全局会话版本治理

#### 当前边界

1. 目前 JWT 签名密钥是运行时生成的 RSA KeyPair，更适合当前项目阶段；若正式上线，仍建议接入稳定密钥管理
2. 当前会话治理已支持 refresh rotation、blacklist 和 session version，但设备指纹、会话列表查询等能力还未实现
3. 若后续要做更强审计和异常检测，还可以继续补充 refresh token 异常复用识别

---

### 面试表达建议

### 最推荐的讲法

“我在这个项目里没有简单做一个登录接口，而是把会话架构拆成了 access token 和 refresh token 两层。access token 短期有效、直接用于业务鉴权；refresh token 放在 HttpOnly Cookie 里，只负责续期。然后在后端再配合 refresh token rotation、单 token 黑名单和 session version 机制，分别解决静默续期、当前设备退出和所有设备退出的问题。这样既保持了接口层无状态，又没有牺牲会话治理能力。”

### 高频追问

#### 为什么要拆成双令牌，而不是只用一个 JWT？

答法要点：
- 单 token 很难同时兼顾安全性和体验。
- access token 应该短生命周期，refresh token 负责续期，这样职责更清晰。
- 再结合 rotation 和失效治理，才能形成完整会话体系。

#### 为什么 refresh token 要放 Cookie，而不是也放前端状态里？

答法要点：
- refresh token 不应该被前端脚本直接访问。
- 放 `HttpOnly Cookie` 可以降低前端运行时代码误用风险。
- 前端只持有 access token 和派生身份信息，会话边界更清晰。

#### 为什么 logout-all 不直接把所有 access token 都加入黑名单？

答法要点：
- 枚举所有历史 access token 成本太高，也很难完整维护。
- session version 是更轻量的整批失效方案。
- 当前设备退出和所有设备退出应该用不同机制，各自服务不同语义。

---

### 证据锚点

- 认证控制器：[AuthController.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthController.java)
- 会话治理核心：[AuthDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthDomainService.java)
- Cookie 策略：[AuthCookieSupport.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthCookieSupport.java)
- JWT 签发解析：[JwtAuthTokenPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/JwtAuthTokenPortImpl.java)
- Refresh token 存储：[RedisAuthRefreshTokenStorePortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthRefreshTokenStorePortImpl.java)
- Access token 吊销：[RedisAuthAccessTokenRevocationPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAccessTokenRevocationPortImpl.java)
- 前端启动恢复：[SessionBootstrap.tsx](/F:/new_opint/VibeCoding/MOZhi/mozhi-web/src/auth/SessionBootstrap.tsx)
- 路由守卫：[guards.tsx](/F:/new_opint/VibeCoding/MOZhi/mozhi-web/src/router/guards.tsx)
- 前端认证状态：[useAuthStore.ts](/F:/new_opint/VibeCoding/MOZhi/mozhi-web/src/stores/useAuthStore.ts)

---

## 三、认证运行时状态治理与 Redis 数据模型设计

### 一句话亮点

将认证运行时状态从“散落在业务逻辑里的临时标记”抽象成一套显式的 Redis 数据模型，按 `refresh token / access token 黑名单 / session version / 尝试计数器 / 锁定标记` 分层治理，通过 `String + Set + TTL` 实现可实时决策、可自动过期、可低成本维护的安全状态存储。

---

### 高阶简历写法

- 设计认证运行时状态模型，将 refresh token 存活状态、access token 吊销、全设备会话版本、登录注册尝试计数与锁定状态统一沉淀到 Redis，形成面向认证场景的安全状态层，而不是把状态散落在 Controller、JWT 载荷或数据库表中。
- 基于 `Redis String / Set / TTL` 构建分层 key 模型，分别支撑 token rotation、即时失效、整批失效、短窗口限流与自动清理，避免引入重型关系表和定时清扫任务，兼顾实时性、可维护性和演进空间。

---

### 情境

认证链路一旦进入真实场景，就会出现大量“不能只靠 JWT 自己解决”的运行时状态：

1. refresh token 是否仍有效，需要有服务端真相源。
2. access token 一旦执行当前设备退出，需要立刻失效，而不是等自然过期。
3. 用户执行“退出所有设备”时，需要有比逐个黑名单更低成本的整批失效机制。
4. 登录失败次数、注册尝试次数和账号锁定都属于短生命周期安全状态，不适合直接写进业务主表。

如果这些状态没有统一模型，系统很快会退化成：

- JWT 自带一点状态
- Redis 临时存一点状态
- 数据库表里再补一点状态
- 代码里到处拼 key、手写 TTL 和清理逻辑

这种方案短期能跑，长期一定难维护。所以这里的核心不是“用了 Redis”，而是把 Redis 用成一套有边界、有语义、有生命周期设计的认证状态层。

---

### 任务

设计一套能够支撑认证全链路的运行时状态模型，满足以下目标：

1. refresh token 必须能被服务端精确识别是否仍有效。
2. access token 必须支持单 token 即时吊销。
3. 全设备退出必须能以低成本让所有旧 access token 失效。
4. 登录注册风控状态必须支持短时间窗口统计和自动过期。
5. 所有状态都应尽量自清理，避免依赖额外后台清扫任务。
6. 数据模型要足够简单，便于定位问题、手工排查和后续演进。

---

### 最终实现

### 1. 运行时状态分层

当前认证运行时状态没有混成一个大表，而是按职责拆成四类：

1. `refresh token 存活状态`
2. `access token 吊销状态`
3. `用户级 session version`
4. `登录/注册尝试计数与锁定状态`

对应的核心实现分别是：

- [RedisAuthRefreshTokenStorePortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthRefreshTokenStorePortImpl.java#L12)
- [RedisAuthAccessTokenRevocationPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAccessTokenRevocationPortImpl.java#L13)
- [RedisAuthAttemptGuardPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAttemptGuardPortImpl.java#L13)
- [AuthDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthDomainService.java#L19)

这样拆分的原因是：

1. refresh token 关注“是否还能续期”
2. access token 黑名单关注“是否立即失效”
3. session version 关注“是否整批失效”
4. attempt guard 关注“是否应该拦截或升级验证”

它们都是认证状态，但语义完全不同，不能混成一个通用 `auth_state` 结构。

---

### 2. refresh token 的数据模型

refresh token 的存储模型见 [RedisAuthRefreshTokenStorePortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthRefreshTokenStorePortImpl.java#L20)。

这里用了两类 key：

1. 单 token 状态键
   `auth:refresh:<userId>:<tokenId>`

2. 用户会话集合键
   `auth:session:<userId>`

对应语义是：

- 单 token 键存在，表示该 refresh token 仍有效
- `auth:session:<userId>` 这个 `Set` 维护当前用户所有仍然活跃的 refresh token ID

具体操作方式是：

1. 登录或 refresh 成功后，把新的 tokenId 写入 `auth:refresh:<userId>:<tokenId>`，值固定写 `"1"`，TTL 等于 refresh token 剩余寿命
2. 同时把 tokenId 加入 `auth:session:<userId>` 这个集合
3. 当前设备退出时，删除单 token 键并从集合里移除 tokenId
4. 全设备退出时，先读出集合中的所有 tokenId，再批量删除对应单 token 键，最后删除集合本身

这个模型的优点是：

1. `isActive(userId, tokenId)` 判断是 `O(1)` 级别
2. `logout-all` 不需要扫库，只需要围绕用户自己的 session set 做操作
3. 既支持精确到单 token 的撤销，也支持按用户维度整批回收

这比只存一个“用户当前 refresh token”更合理，因为同一用户可能同时在多个设备或浏览器上保持登录。

---

### 3. access token 黑名单与 session version

access token 治理不是靠 refresh token store 硬扛，而是拆成两套机制：

1. 当前设备即时失效
   由 [RedisAuthAccessTokenRevocationPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAccessTokenRevocationPortImpl.java#L26) 写入：
   `auth:access:blacklist:<userId>:<tokenId>`

2. 全设备整批失效
   由同一个类维护：
   `auth:access:session-version:<userId>`

为什么要分成两套：

#### 单 token 黑名单

当前设备退出时，只把当前 access token 拉黑即可。
值仍然固定写 `"1"`，TTL 取该 access token 的剩余寿命。

这样做的好处是：

1. 黑名单键天然会在 token 过期后自动消失
2. 不需要全局清理任务
3. 单设备退出可以做到“立即生效”

#### session version

全设备退出时，如果去枚举所有历史 access token 再逐个黑名单，成本太高，也很难做到完整。
所以这里采用了版本号机制：

- token 签发时，把当前 `session_version` 写进 JWT
- 鉴权时，再拿 token 内部的 `session_version` 去比 Redis 当前值
- 一旦用户执行 `logout-all`，就对 `auth:access:session-version:<userId>` 做 `increment`

这样一来：

- 旧 token 即使签名正确、没过期、也没在黑名单里
- 只要它自带的版本号落后于 Redis 当前版本
- 依然会在鉴权阶段被判定失效

这个模型本质上是：

- 黑名单负责“精确吊销”
- 版本号负责“整批失效”

两者组合，比单纯依赖其中一种机制更完整。

---

### 4. 尝试计数器与锁定状态

登录注册风控状态见 [RedisAuthAttemptGuardPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAttemptGuardPortImpl.java#L21)。

当前 key 模型包括：

- `auth:login:ip:<ip>`
- `auth:login:identifier:failure:<identifier>`
- `auth:login:identifier:lock:<identifier>`
- `auth:register:ip:<ip>`
- `auth:register:email:<email>`
- `auth:register:username:<username>`

对应的数据语义是：

1. `计数键`
   值是整数，每次操作用 `INCR`

2. `锁定键`
   值固定是 `"1"`，锁定时长完全由 TTL 表示

这里最关键的细节是 [RedisAuthAttemptGuardPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAttemptGuardPortImpl.java#L75) 里的实现：

- 每次 `incrementCounter(key, ttl)` 后都会重新 `expire(key, ttl)`

这意味着当前模型更接近“基于最近活跃时间刷新的简单 TTL 窗口”，而不是严格意义上的精确滑动窗口。
这是一个有意识的工程取舍：

1. 实现足够简单
2. Redis 原生能力就能支撑
3. 在当前项目阶段已经能满足登录注册风控需求

如果未来要更严谨地做统计，再升级到 Lua 脚本、sorted set 或专门限流组件会更合适。

---

### 5. 为什么统一选择 String / Set / TTL

这块设计的关键，不是“Redis 能存这些东西”，而是为什么统一收敛到这三种原语：

1. `String`
   适合布尔态和整数计数器，最轻量

2. `Set`
   适合维护一个用户当前活跃 refresh token 的集合

3. `TTL`
   适合表达“这类认证状态天然是短生命周期”的事实

统一采用这三种原语的好处是：

1. 认知成本低，排障时直接看 key 就能理解语义
2. 不需要额外清理服务，过期自然回收
3. 性能和实现复杂度都适合当前阶段
4. 后续扩展也比较自然，比如：
   - 把计数器升级成严格滑动窗口
   - 给 refresh token 增加设备指纹
   - 给 session set 增加最后活跃时间索引

这比一开始就上复杂数据结构或独立安全服务更符合当前阶段的工程节奏。

---

### 6. 为什么不用关系型表承载这些状态

如果把这些认证运行时状态全部放数据库表，理论上也能做，但会带来明显问题：

1. 高频短状态写入会给主库带来不必要压力
2. 过期清理需要额外定时任务或批处理
3. 单 token 吊销、计数器自增、锁定窗口判断这些操作不如 Redis 直接
4. 安全状态和业务主数据混在一起，模型会越来越臃肿

当前这些状态的共同特点是：

- 生命周期短
- 读写频繁
- 查询模型简单
- 重点是实时判断而不是历史分析

所以它们天然更适合 Redis，而不是关系型表。

数据库更适合做什么？

- 用户主数据
- 长周期审计事件
- 报表分析

Redis 更适合做什么？

- 当前是否有效
- 当前是否已锁定
- 当前是否超过限流窗口
- 当前会话版本是多少

这个职责划分是清晰的。

---

### 7. 领域层为什么不直接操作 Redis

还有一个很关键的设计点：
领域服务并没有直接写 `stringRedisTemplate.opsForValue()`，而是通过端口抽象来访问运行时状态。

比如：

- refresh token 走 `IAuthRefreshTokenStorePort`
- access token 吊销走 `IAuthAccessTokenRevocationPort`
- 风控计数走 `IAuthAttemptGuardPort`

再由 Redis 实现类去落地具体 key 和 TTL。

这样做的原因是：

1. 领域层只关心业务语义，比如“store / revoke / blacklist / bumpSessionVersion”
2. 不把 Redis API 和 key 拼接污染到业务决策代码里
3. 测试时更容易替换实现
4. 后续如果要切换存储策略，改动面会被收敛在基础设施层

这其实体现的是一个更高阶的点：

`认证运行时状态本身也是领域能力的一部分，只是它的载体落在 Redis。`

不是“因为用了 Redis，所以直接在业务里拼 Redis 命令”。

---

### 8. 当前设计的收益与边界

#### 收益

1. 认证安全状态被收敛成统一模型，排障和扩展都更容易
2. key 命名有明确业务语义，定位问题时不需要猜
3. TTL 驱动的自动过期让系统不依赖额外清理任务
4. 单 token、单用户、多维风控三类状态各有合适的数据结构
5. 领域逻辑与基础设施实现解耦，后续可以平滑升级

#### 当前边界

1. 当前 refresh token session set 只维护 tokenId，不包含设备指纹、IP、最近活跃时间等更丰富元数据
2. 计数器窗口目前是简单 TTL 语义，不是严格统计学意义的滑动窗口
3. session version 目前只做用户级整批失效，还没有扩展到更细粒度设备组
4. 若未来要做更复杂的安全分析，仍需要把关键事件同步到长期审计系统，而不是只保留 Redis 瞬时状态

---

### 面试表达建议

### 最推荐的讲法

“这个项目里我比较重视的一点，是没有把认证状态理解成只有 JWT 本身。我把它拆成了 refresh token 存活状态、access token 黑名单、session version，以及登录注册计数器几类运行时状态，然后统一放进 Redis，用 String、Set 和 TTL 去表达不同语义。这样当前设备退出、全设备退出、token rotation、登录注册风控都能落在一个清晰的数据模型里，而且这些状态会自己过期，不需要额外清理任务。”

### 高频追问

#### 为什么 refresh token 需要单独建 store，不能只靠 JWT 自验证？

答法要点：
- 只靠 JWT 无法知道 refresh token 是否已经被撤销。
- refresh token 需要服务端真相源，才能支持 rotation 和 logout-all。
- 所以 refresh token 必须有可查询的 active 状态。

#### 为什么 access token 黑名单和值为 session version 要共存？

答法要点：
- 黑名单适合做单 token 的即时失效。
- session version 适合做全设备整批失效。
- 两者服务的是不同粒度的问题，不能互相完全替代。

#### 为什么 Redis key 要这么细，不做一个统一大对象？

答法要点：
- 各类状态生命周期和访问模式不同。
- 细粒度 key 更利于单点操作、TTL 控制和排障。
- 把所有状态揉成一个对象，后续更新和失效边界会变复杂。

---

### 证据锚点

- Refresh token 存储：[RedisAuthRefreshTokenStorePortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthRefreshTokenStorePortImpl.java)
- Access token 吊销与 session version：[RedisAuthAccessTokenRevocationPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAccessTokenRevocationPortImpl.java)
- 登录注册计数器：[RedisAuthAttemptGuardPortImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAttemptGuardPortImpl.java)
- 认证主链路编排：[AuthDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthDomainService.java)
- 认证风控策略：[AuthSecurityPolicyService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthSecurityPolicyService.java)

---

## 四、面向真实内容平台的草稿写模型治理

### 一句话亮点

主导将 `Phase 2 / Step 2.1` 的草稿能力从普通 CRUD 升级为面向真实内容平台的写模型，围绕 `生命周期状态机 + 乐观锁并发控制 + 资源隔离 + 数据库约束 + 分页契约` 建立一体化治理机制，使草稿在多人多端、审核前后和异常数据场景下都具备可控的写入边界。

---

### 高阶简历写法

- 设计并落地内容域草稿写模型，将草稿生命周期抽象为 `DRAFT / UPLOADING / PENDING_REVIEW / PUBLISHED / REJECTED / ARCHIVED` 状态机，拆分正文更新与状态流转入口，并对审核中、已发布、已归档草稿实施写入冻结，避免客户端绕过流程直接修改内容。
- 为草稿聚合引入基于 `version + expectedVersion` 的乐观锁机制，通过 MyBatis 条件更新、标准化 `409 conflict` 错误码和版本化响应模型，解决多端编辑、状态竞态和覆盖写问题，同时配套分页筛选契约、资源防探测访问控制和数据库检查约束，形成接近真实内容平台的内容写侧治理能力。

---

### 情境

在进入 `Phase 2 / Step 2.1` 之后，草稿域如果只停留在“有一张表、能创建、能修改、能删除”，很快就会暴露出一系列真实环境问题：

1. 审核中的草稿如果还能继续改正文，审核结果就不再可信。
2. 已发布内容如果还能物理删除或被回写，后续发布链路会变得不可追踪。
3. 多端同时编辑草稿时，后一次提交可能无声覆盖前一次结果。
4. 非本人访问如果返回 `403` 或不同错误信息，会泄露资源是否存在。
5. 如果列表接口直接返回用户全部草稿，随着数据量增长会很快失去可用性。
6. 如果状态只靠应用层约束，一旦脚本写错或人工修库写入脏状态，系统会在运行时出现不可控异常。

所以这一步不能只做成“草稿 CRUD”，而要把草稿当成一个真实写模型来治理。

---

### 任务

在不提前引入发布页、评论区、AI 摘要等后续步骤能力的前提下，先把草稿侧的写入边界做扎实，满足以下目标：

1. 草稿要有明确且可验证的生命周期状态机。
2. 正文编辑和状态流转要分离，客户端不能在一次请求里随意改状态。
3. 并发修改必须可检测、可拒绝，而不是静默覆盖。
4. 草稿永远绑定当前登录用户，非本人访问不暴露资源存在性。
5. 列表接口至少要具备分页与状态筛选能力，不能返回全量数组。
6. 状态合法性既要有应用层规则，也要有数据库层兜底。
7. 整套实现必须保持当前 DDD 六模块结构，便于后续继续扩展 `note / media_ref / publish` 链路。

---

### 最终实现

### 1. 生命周期状态机与写入边界

草稿生命周期被明确建模为：

- `DRAFT`
- `UPLOADING`
- `PENDING_REVIEW`
- `PUBLISHED`
- `REJECTED`
- `ARCHIVED`

核心实现见 [DraftEntity.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/DraftEntity.java)。

我没有把草稿做成“所有字段都能随便改”的贫血实体，而是在实体内部约束两类行为：

1. `withContent(...)`
   只负责正文更新

2. `transitionTo(...)`
   只负责状态流转

并且在正文更新前显式执行 `assertEditableForContentUpdate()`，对以下状态直接冻结正文写入：

- `PENDING_REVIEW`
- `PUBLISHED`
- `ARCHIVED`

这样设计的原因是：

1. 审核中草稿如果还能继续改，会让审核语义失效。
2. 已发布内容继续通过草稿入口改正文，会破坏“草稿 -> 发布物”之间的边界。
3. 已归档内容本质上是冻结资源，不应该再被作为活跃写模型使用。

这比“把状态写在数据库里，Controller 想怎么调就怎么调”更接近真实内容平台的治理方式。

---

### 2. 基于版本号的乐观锁并发控制

在真实场景下，草稿往往不是单端单次写入：

1. 用户可能同时开着多个浏览器标签页。
2. 同一草稿可能在编辑后立刻进入审核流转。
3. 写请求之间可能存在网络重试和乱序到达。

所以这次没有停留在“先查再改”的朴素实现，而是把草稿写模型升级成显式的版本化聚合。

实现方式如下：

1. 在 [V4__harden_draft_write_model.sql](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V4__harden_draft_write_model.sql) 中为 `draft` 表增加 `version` 列。
2. 在 [DraftEntity.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/DraftEntity.java) 中让每次正文更新和状态流转都自增版本号。
3. 在 [DraftUpdateRequestDTO.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftUpdateRequestDTO.java) 与 [DraftStatusTransitionRequestDTO.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftStatusTransitionRequestDTO.java) 中显式要求客户端提交 `expectedVersion`。
4. 在 [DraftDao.xml](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/DraftDao.xml) 里将更新和删除都改成条件写：

- `WHERE id = #{id} AND version = #{expectedVersion}`

5. 当条件更新命中 0 行时，由 [DraftDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java) 抛出 `A0409 / conflict`。

对应错误语义见 [ResponseCode.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/ResponseCode.java) 和 [GlobalExceptionHandler.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/GlobalExceptionHandler.java)。

这样设计的原因是：

1. 多端并发编辑不是异常，而是默认会出现的正常场景。
2. 静默覆盖是内容系统里最危险的一类写入错误，因为它很难被用户感知。
3. 用版本号做乐观锁，比引入悲观锁或数据库事务锁更适合当前接口型内容写入场景。

这条设计其实体现的是一个更高阶的点：

`草稿更新不是“最后一次写入获胜”，而是“只有基于最新版本的写入才被接受”。`

---

### 3. 资源隔离与防探测访问控制

草稿是用户私有资源，因此权限设计不是简单的“登录即可访问”。

核心实现见 [DraftDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java) 和 [DraftController.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/DraftController.java)。

这一步的关键控制包括：

1. 草稿归属永远以当前登录用户为准，不接受客户端传 `authorId`。
2. 查询、更新、删除、状态流转全部先按“当前用户拥有该草稿”做校验。
3. 非本人访问统一返回 `404`，而不是 `403`。
4. 已发布草稿禁止物理删除，只允许走归档或后续发布治理流程。

为什么非本人访问返回 `404` 而不是 `403`？

因为在草稿这种私有资源场景下，`403` 会向攻击者泄露“这个资源确实存在，只是你没权限”，而 `404` 更符合防探测原则。

为什么已发布草稿不能物理删除？

因为一旦后续 `note`、审核结果、媒体引用和审计事件都围绕它建立关系，物理删除会破坏整条内容链路。当前阶段先禁止这类删除，是为后续发布链路保留结构稳定性。

---

### 4. 数据库约束与应用层规则双重兜底

这一步没有只依赖应用层 `if/else` 来约束状态。

应用层负责什么：

1. 在 [DraftEntity.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/DraftEntity.java) 中控制状态迁移白名单。
2. 在 [DraftDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java) 中控制资源归属、删除边界和冲突语义。

数据库层负责什么：

1. 在 [V4__harden_draft_write_model.sql](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V4__harden_draft_write_model.sql) 中为 `draft.status` 添加检查约束，防止出现脱离枚举体系的脏状态。
2. 为 `author_id + status + updated_at` 增加复合索引，支撑分页筛选场景。

仓储层还额外做了一层防腐：

- [DraftRepositoryImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/DraftRepositoryImpl.java) 不再对持久化状态盲目 `valueOf`，而是将坏状态提升为清晰的系统错误。

这样设计的原因是：

1. 真实系统里的坏数据来源不只有应用代码，还可能有手工修库、迁移脚本、调试脚本、历史版本残留。
2. 只靠应用层约束，不能阻止数据库被写进非法状态。
3. 只靠数据库约束，又无法表达复杂的业务流转语义。

所以这里采用的是：

`应用层负责业务规则，数据库层负责数据边界。`

这是一种更偏生产级的兜底思路。

---

### 5. 面向真实规模的分页与筛选契约

草稿列表接口没有继续沿用“返回一个数组”的 demo 风格，而是直接收敛成可扩展的分页合同。

实现见：

- [DraftListQuery.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/valobj/DraftListQuery.java)
- [DraftPageResult.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/valobj/DraftPageResult.java)
- [DraftListPageDTO.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftListPageDTO.java)
- [DraftDao.xml](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/DraftDao.xml)

当前支持：

1. `page`
2. `pageSize`
3. `status`

响应统一为：

- `page`
- `pageSize`
- `total`
- `items`

排序规则是：

- `updated_at DESC`
- `id DESC`

为什么这一步就要补分页？

因为内容平台的草稿箱天然是一个会增长的集合，如果一开始就把接口做成“全量返回数组”，后面再升级分页，前后端契约会被迫重改一次。现在直接做成分页模型，后续再扩展关键词筛选、游标分页、更新时间范围过滤都会更自然。

---

### 6. DDD 切片落地与后续可演进性

这一步还有一个值得写进简历的点，不是功能本身，而是落地方式。

整个草稿域实现保持了当前项目的六模块结构：

1. `trigger`
   HTTP 入口和参数映射，见 [DraftController.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/DraftController.java)

2. `domain`
   草稿实体、分页查询值对象、仓储接口和领域服务，见 [DraftEntity.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/DraftEntity.java)、[IDraftRepository.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/adapter/repository/IDraftRepository.java)、[DraftDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java)

3. `infrastructure`
   DAO、PO、MyBatis XML 和仓储实现，见 [DraftDao.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/DraftDao.java)、[DraftPO.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/po/DraftPO.java)、[DraftRepositoryImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/DraftRepositoryImpl.java)

4. `app`
   Flyway 迁移、MyBatis 配置和测试装配，见 [V4__harden_draft_write_model.sql](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V4__harden_draft_write_model.sql)、[MybatisConfiguration.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/MybatisConfiguration.java)

我还顺手统一了 mapper 装配风格，把 `DraftDao` 装配移回 MyBatis 配置层，而不是继续散落在领域装配配置里。

这样做的原因是：

1. 当前只是 `Step 2.1`，后面还会继续引入 `note / media_ref / publish`。
2. 如果现在切片结构就开始散，后面内容域会很快变成一团。
3. 先把草稿当成一个标准化子域切片落地，后续 `Step 2.2+` 才容易继续往上叠。

---

### 当前设计的收益与边界

#### 收益

1. 草稿不再是贫血 CRUD，而是具备明确生命周期和写入边界的聚合。
2. 并发覆盖和状态竞态被版本化写模型显式收口。
3. 私有资源访问采用 `404` 防探测语义，更接近真实安全策略。
4. 数据库约束、仓储防腐和领域规则形成了多层兜底。
5. 列表接口从一开始就是可扩展分页合同，避免后续大改契约。
6. 整体实现仍保持 DDD 切片清晰，为 `Step 2.2+` 演进预留了稳定边界。

#### 当前边界

1. 当前列表能力只有分页和状态筛选，还没有关键词搜索、游标分页和复杂排序。
2. 删除仍然是“未发布草稿可删、已发布不可删”，还没有回收站和软删除恢复。
3. 草稿元数据目前只覆盖标题、正文、状态和版本，还没有摘要、标签、字数、封面等增强字段。
4. `note` 和 `media_ref` 表已经建立，但这一步还没有正式启用它们的发布与媒体引用语义。

---

### 面试表达建议

### 最推荐的讲法

“我在这个项目里做草稿能力时，没有把它当成普通 CRUD，而是把它当成真实内容平台的写模型来设计。核心是四件事：第一，用状态机把草稿生命周期和可编辑边界讲清楚；第二，用版本号乐观锁解决多端编辑和状态竞态；第三，私有资源统一按当前用户隔离，非本人访问返回 404 做防探测；第四，在数据库层补状态约束和索引，不把所有正确性都压给应用代码。这样草稿域虽然还是 Step 2.1，但已经具备后续接审核、发布和媒体引用的基础。”

### 高频追问

#### 为什么正文更新和状态流转要拆成两个入口？

答法要点：
- 这两类行为的业务语义不同。
- 正文更新关注内容编辑，状态流转关注生命周期推进。
- 混在一个接口里，客户端很容易绕过流程边界。

#### 为什么要用乐观锁，而不是直接最后一次写入覆盖？

答法要点：
- 内容编辑天然存在多端、多标签页和重试请求。
- 静默覆盖会让用户丢内容，而且很难排查。
- 乐观锁更适合 API 型内容写入，不需要引入重型锁机制。

#### 为什么非本人访问返回 404，而不是 403？

答法要点：
- 草稿是私有资源，返回 403 会泄露资源存在性。
- 404 更符合防探测设计。

#### 为什么数据库层还要加状态检查约束？

答法要点：
- 坏数据来源不只有业务代码，还有脚本、人工修库和迁移错误。
- 应用层规则负责业务语义，数据库约束负责数据边界。
- 双层兜底比只依赖其中一层更稳。

---

### 证据锚点

- 设计文档：[2026-04-09-phase2-step2-1-draft-hardening-design.md](/F:/new_opint/VibeCoding/MOZhi/docs/superpowers/specs/2026-04-09-phase2-step2-1-draft-hardening-design.md)
- 实施计划：[2026-04-09-phase2-step2-1-draft-hardening.md](/F:/new_opint/VibeCoding/MOZhi/docs/superpowers/plans/2026-04-09-phase2-step2-1-draft-hardening.md)
- 草稿实体：[DraftEntity.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/DraftEntity.java)
- 草稿领域服务：[DraftDomainService.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java)
- 草稿仓储接口：[IDraftRepository.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/adapter/repository/IDraftRepository.java)
- MyBatis 仓储实现：[DraftRepositoryImpl.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/DraftRepositoryImpl.java)
- 草稿 HTTP 入口：[DraftController.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/DraftController.java)
- 持久化模型：[DraftDao.xml](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/DraftDao.xml)
- 迁移脚本：[V4__harden_draft_write_model.sql](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V4__harden_draft_write_model.sql)
- 聚焦测试：[DraftEntityTest.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftEntityTest.java)
- HTTP 集成测试：[DraftHttpIntegrationTest.java](/F:/new_opint/VibeCoding/MOZhi/mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftHttpIntegrationTest.java)
