# 认证页确认密码与高阶 STAR 材料重写 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为注册页补充确认密码前端校验，并将简历素材文件重写为仅保留高阶技术条目。

**Architecture:** 认证页只在前端新增本地状态与一致性校验，不修改 API 契约；STAR 材料文件整体覆盖，聚焦认证安全、运行时工程化与 Agent 交付闭环三类高阶素材。

**Tech Stack:** React 18、TypeScript、Vitest、Testing Library、Markdown 文档

---

### Task 1: 为确认密码校验补充失败测试

**Files:**
- Modify: `mozhi-web/src/pages/Auth/AuthPage.test.tsx`

- [ ] **Step 1: 写注册态应展示确认密码输入的失败测试**

```tsx
it("renders a confirm-password input on register mode", () => {
  renderWithRouter("/auth?mode=register", [
    {
      path: "/auth",
      element: <AuthLayout />,
      children: [{ index: true, element: <AuthPage /> }]
    }
  ]);

  expect(screen.getByLabelText("确认密码")).toBeInTheDocument();
});
```

- [ ] **Step 2: 运行单测并确认当前失败**

Run: `npm run test -- src/pages/Auth/AuthPage.test.tsx`
Expected: FAIL，提示找不到“确认密码”输入。

- [ ] **Step 3: 写密码不一致时阻断注册请求的失败测试**

```tsx
it("blocks registration when password confirmation does not match", async () => {
  const user = userEvent.setup();

  renderWithRouter("/auth?mode=register", [
    {
      path: "/auth",
      element: <AuthLayout />,
      children: [{ index: true, element: <AuthPage /> }]
    }
  ]);

  await user.type(screen.getByLabelText("用户名"), "ating");
  await user.type(screen.getByLabelText("昵称"), "zzz");
  await user.type(screen.getByLabelText("邮箱地址"), "123ee@123.com");
  await user.type(screen.getByLabelText("设置密码"), "1314521yang");
  await user.type(screen.getByLabelText("确认密码"), "1314521yangx");
  await user.click(screen.getByRole("checkbox", { name: /服务条款/i }));
  await user.click(screen.getByRole("button", { name: "注册 MOZhi 账号" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("两次输入的密码不一致，请重新确认。");
  expect(registerAccount).not.toHaveBeenCalled();
});
```

- [ ] **Step 4: 再次运行单测并确认新增测试失败**

Run: `npm run test -- src/pages/Auth/AuthPage.test.tsx`
Expected: FAIL，提示缺少确认密码逻辑或错误文案。

### Task 2: 实现确认密码输入与前端一致性校验

**Files:**
- Modify: `mozhi-web/src/pages/Auth/index.tsx`

- [ ] **Step 1: 为注册态增加 `confirmPassword` 和本地校验状态**

实现要点：
- 新增 `confirmPassword` 本地状态。
- 新增 `validationMessage` 本地状态，用于承载前端校验错误。
- 在模式切换、注册成功后重置这两个状态。

- [ ] **Step 2: 在注册表单中加入“确认密码”输入**

实现要点：
- 仅在 `mode === "register"` 时展示。
- 复用当前密码输入外壳样式与显示/隐藏密码按钮。
- 不改变后端 `RegisterPayload` 结构。

- [ ] **Step 3: 在提交前增加密码一致性校验**

实现要点：
- `handleSubmit` 中先判断注册态下 `password !== confirmPassword`。
- 命中时设置 `validationMessage`，直接 `return`，不触发 `authMutation.mutate()`。
- 输入变化时清理本地校验错误，避免旧错误残留。

- [ ] **Step 4: 运行认证页单测确认通过**

Run: `npm run test -- src/pages/Auth/AuthPage.test.tsx`
Expected: PASS

### Task 3: 重写高阶 STAR 素材文件

**Files:**
- Modify: `docs/career/MOZHI_STAR_STORIES.md`

- [ ] **Step 1: 清空现有偏 UI 导向内容，保留高阶技术范围**

重写方向：
- 认证域生产化收口
- challenge 可替换接入与安全边界
- Docker 本地/生产运行时与持久化治理
- 规格驱动与 Agent 协作交付闭环

- [ ] **Step 2: 用中文 `情境 / 任务 / 行动 / 结果` 重写**

内容要求：
- 强调架构边界、风险控制、可验证性、工程治理。
- 每条都附“简历写法 / 证据锚点 / 面试追问与回答要点”。
- 禁止写页面文案、按钮级 UI 调整等低阶内容。

### Task 4: 运行整体验证

**Files:**
- Modify: `mozhi-web/src/pages/Auth/AuthPage.test.tsx`
- Modify: `mozhi-web/src/pages/Auth/index.tsx`
- Modify: `docs/career/MOZHI_STAR_STORIES.md`

- [ ] **Step 1: 运行前端测试**

Run: `npm run test`
Expected: PASS

- [ ] **Step 2: 运行静态检查**

Run: `npm run lint`
Expected: PASS

- [ ] **Step 3: 运行构建**

Run: `npm run build`
Expected: PASS
