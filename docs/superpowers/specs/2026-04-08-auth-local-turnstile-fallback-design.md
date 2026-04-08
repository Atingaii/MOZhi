# 认证页本地 Turnstile 降级与布局修复设计

## 目标

修复两个现存问题：

- 本地环境未配置 `VITE_TURNSTILE_SITE_KEY` 时，频繁登录后会进入 challenge 状态，但前端无法完成人机验证，导致老账号也被卡死
- 登录注册主面板在桌面端整体偏上，不够居中

## 约束

- 不改变登录注册页面的信息结构和现有 UI 编排
- 生产环境 challenge 仍保持严格，不允许无配置自动放行
- 本地开发环境需要在缺少 Turnstile 配置时保留可用性

## 方案

### 1. 后端 challenge 本地降级

新增 `mozhi.auth.challenge.allow-bypass-when-unconfigured` 配置项：

- 默认值为 `false`
- `dev` profile 默认开启
- `prod` profile 默认关闭

Turnstile verifier 在以下条件同时满足时直接返回通过：

- 当前 provider 为 `turnstile`
- challenge 基础配置不完整，例如 secret key 缺失或 hostname allowlist 为空
- `allow-bypass-when-unconfigured=true`

这样本地开发环境即使未配置 Turnstile，也不会在达到 challenge 阈值后把正确账号锁死；生产环境依旧 fail closed。

### 2. 前端认证页容错

前端在 `A0410` 且 site key 缺失时：

- 不再把提交按钮锁死
- 显示更明确的本地配置提示

这样即使本地配置还没补齐，也不会出现“前端自己先把用户困死”的问题。

### 3. 认证页垂直居中

仅调整认证壳层样式：

- 让 `AuthLayout` 主内容区真正居中承载认证面板
- 保持现有左右分栏和内容结构不变
- 移动端继续沿用现有单列逻辑

## 验证

- 前端测试：`site key` 缺失且触发 challenge 时，按钮仍可再次提交，并显示更明确提示
- 样式测试：认证页主容器采用 flex 居中壳层
- 后端测试：未配置 Turnstile 时，dev 可降级通过，prod/strict 仍拒绝
