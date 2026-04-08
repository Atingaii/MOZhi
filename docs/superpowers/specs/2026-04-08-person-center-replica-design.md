# Person Center Replica Design

## Goal

将现有 React 个人中心页面重做为 `demo/personCenter.html` 的结构与视觉语言，同时把 demo 中的导航栏搜索框样式补到所有页面头部。真实后端已提供的资料编辑、头像上传、登录态与退出流程必须保留；demo 中没有后端接口支撑的模块保持为前端展示态。

## Scope

本次只覆盖：

- `mozhi-web/src/pages/Profile/index.tsx` 的个人中心重构
- `mozhi-web/src/components/layout/AppHeader.tsx` 的全局搜索框补齐
- 对应的 `globals.css` 样式扩展
- 导航栏与个人中心相关测试

本次不覆盖：

- 新增或修改后端接口
- 内容域、社交域、搜索域的业务实现
- 超出 `demo/personCenter.html` 的新功能块

## UI Direction

页面按 demo 的“工作台”方向实现：

- 顶部为玻璃感白色导航栏，包含品牌、搜索框、一级导航、通知和头像入口
- 主体为双栏布局
- 左栏是资料名片、认证状态、统计和“人群画像标签”
- 右栏默认显示工作台卡片区，点击“编辑资料”后切换到资料编辑视图

与 demo 的差异只保留在工程必要处：

- 编辑资料保存继续调用当前的 `updateUserProfile`
- 头像按钮继续走预签名上传链路
- 无后端接口的数据使用本地常量展示，不与真实用户接口混淆

## Data Model

页面数据分为两类：

### 真实数据

- `useUserProfileQuery` 返回的 `nickname`、`username`、`avatarUrl`、`bio`、`status`
- 当前登录态 `useAuthStore.user`
- 资料更新 mutation 与头像上传 mutation
- 登出 mutation

### 前端展示数据

- 左侧统计：内容数、关注者、获赞
- 人群画像标签
- 工作台卡片区的说明文案和状态

这些前端展示数据保存在页面内部常量中，不进入全局 store，不发请求，不伪装为真实业务数据。

## Header Search Behavior

搜索框在所有页面头部可见，包括 `/auth` 页面。

行为约束：

- 输入框样式复刻 demo
- 回车后跳转到 `/search?q=<keyword>`
- 点击时不影响现有一级导航
- 不新增后端搜索接口

搜索页后续读取 `q` 参数初始化输入框，形成最小闭环。

## Testing Strategy

新增或更新三类测试：

1. `AppHeader` 测试
   - 断言品牌、搜索输入框、导航链接存在
   - 断言匿名态与登录态头部都保留搜索框

2. `AuthLayout` 测试
   - 断言认证页头部也出现统一搜索框

3. `ProfilePage` 测试
   - 断言左侧资料卡、认证创作者、画像标签、右侧工作台卡片存在
   - 断言点击“编辑资料”后切换到编辑视图
   - 断言真实资料表单仍存在昵称、简介和头像上传入口

## Risk Notes

- 当前全局样式文件较大，需避免污染现有搜索页和认证页样式
- 个人中心需要同时承载真实编辑逻辑和 demo 展示块，必须清晰区分“真实数据”与“前端占位”
- 搜索框进入所有页面后，测试中原有“认证页无搜索输入框”的断言需要同步调整
