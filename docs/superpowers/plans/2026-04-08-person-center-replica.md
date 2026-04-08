# Person Center Replica Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the profile page to match `demo/personCenter.html`, add the replica search bar to all headers, and verify the new UI with focused frontend tests.

**Architecture:** Keep all existing real user mutations and queries intact, but wrap them in a new demo-style profile workspace. Add a shared header search form that routes to the existing `/search` page using query params. Treat portrait tags and workspace cards as local presentation data only.

**Tech Stack:** React 18, TypeScript, React Router, TanStack Query, Vitest, Testing Library, global CSS.

---

### Task 1: Lock Header Search Behavior with Tests

**Files:**
- Modify: `mozhi-web/src/components/layout/AppHeader.test.tsx`
- Modify: `mozhi-web/src/router/auth-route.test.tsx`

- [ ] **Step 1: Write failing header expectations**

Add assertions for:

```tsx
expect(screen.getByRole("searchbox", { name: /全局搜索/i })).toBeInTheDocument();
expect(screen.getByPlaceholderText(/搜索话题、商品或 ai/i)).toBeInTheDocument();
```

- [ ] **Step 2: Run the focused header tests to verify they fail**

Run:

```powershell
npm run test -- --run src/components/layout/AppHeader.test.tsx src/router/auth-route.test.tsx
```

Expected: failing assertions because the current header has no replica search input and the auth-route test still expects no search box.

- [ ] **Step 3: Implement minimal header search UI and route behavior**

Touch:

- `mozhi-web/src/components/layout/AppHeader.tsx`
- `mozhi-web/src/styles/globals.css`

Implementation must include:

```tsx
<form className="mozhi-header-search" onSubmit={handleSearchSubmit}>
  <label className="mozhi-header-search-shell" htmlFor="mozhi-global-search">
    <SearchIcon />
    <input
      id="mozhi-global-search"
      aria-label="全局搜索"
      placeholder="搜索话题、商品或 AI..."
      type="search"
      value={searchQuery}
      onChange={(event) => setSearchQuery(event.target.value)}
    />
  </label>
</form>
```

- [ ] **Step 4: Re-run the focused header tests until green**

Run:

```powershell
npm run test -- --run src/components/layout/AppHeader.test.tsx src/router/auth-route.test.tsx
```

Expected: both files pass with the new shared search bar present.

### Task 2: Lock the Profile Replica Surface with Tests

**Files:**
- Create: `mozhi-web/src/pages/Profile/ProfilePage.test.tsx`

- [ ] **Step 1: Write the failing profile replica test**

Create a test that renders `/profile` and asserts:

```tsx
expect(screen.getByText("认证创作者")).toBeInTheDocument();
expect(screen.getByText("人群画像标签")).toBeInTheDocument();
expect(screen.getByText("我的发布")).toBeInTheDocument();
expect(screen.getByRole("button", { name: /编辑资料/i })).toBeInTheDocument();
```

Then click the edit trigger and assert the edit surface:

```tsx
await user.click(screen.getByRole("button", { name: /编辑资料/i }));
expect(screen.getByText("编辑个人资料")).toBeInTheDocument();
expect(screen.getByRole("button", { name: /保存资料/i })).toBeInTheDocument();
```

- [ ] **Step 2: Run the focused profile test to verify it fails**

Run:

```powershell
npm run test -- --run src/pages/Profile/ProfilePage.test.tsx
```

Expected: fail because the current profile page does not render the replica layout or edit-view toggle.

- [ ] **Step 3: Implement the minimal replica structure**

Touch:

- `mozhi-web/src/pages/Profile/index.tsx`
- `mozhi-web/src/styles/globals.css`

Implementation boundaries:

```tsx
const portraitTags = ["# 数码极客", "# 终身学习者", "# 极简主义", "# 咖啡深度爱好者"] as const;
const workspaceCards = [
  { title: "我的发布", tone: "purple", description: "..." },
  { title: "AI 知识库", tone: "blue", description: "..." }
];
```

Use local state:

```tsx
const [activeView, setActiveView] = useState<"dashboard" | "edit">("dashboard");
```

Keep existing query/mutation wiring for real profile editing.

- [ ] **Step 4: Re-run the focused profile test until green**

Run:

```powershell
npm run test -- --run src/pages/Profile/ProfilePage.test.tsx
```

Expected: test passes and edit view still exposes the real profile controls.

### Task 3: Verify Search Prefill and Full Frontend Slice

**Files:**
- Modify: `mozhi-web/src/pages/Search/index.tsx`
- Run checks on all touched tests and build

- [ ] **Step 1: Add the smallest search-page prefill support**

Read `q` from router search params and initialize:

```tsx
const [searchParams] = useSearchParams();
const initialQuery = searchParams.get("q") ?? searchDiscoverySnapshot.workspace.initialQuery;
const [query, setQuery] = useState(initialQuery);
```

- [ ] **Step 2: Run the full focused frontend slice**

Run:

```powershell
npm run test -- --run src/components/layout/AppHeader.test.tsx src/router/auth-route.test.tsx src/pages/Profile/ProfilePage.test.tsx src/pages/Auth/AuthPage.test.tsx src/api/client.test.ts src/router/guards.test.tsx
```

Expected: all targeted UI and auth-route tests pass.

- [ ] **Step 3: Run the frontend build**

Run:

```powershell
npm run build
```

Expected: Vite and TypeScript build succeed with no type errors from the new profile/header/search changes.
