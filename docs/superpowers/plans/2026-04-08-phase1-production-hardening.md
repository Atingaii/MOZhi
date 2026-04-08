# Phase 1 Production Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the temporary auth hardening shortcuts with a production-shaped Turnstile flow, expose logout-all in the frontend, and verify the Phase 1 slice under the intended toolchain.

**Architecture:** Keep the existing DDD auth boundaries intact. Backend challenge policy remains in `domain`, while provider verification moves to focused infrastructure adapters with profile-driven configuration. Frontend keeps auth form composition in `AuthPage`, moves challenge lifecycle into a dedicated component, and exposes logout-all from the profile account surface.

**Tech Stack:** Spring Boot 3.3, Java 21, React 18, Vite, Vitest, React Query, Zustand, Cloudflare Turnstile, MockMvc

---

### Task 1: Lock the new backend challenge contract with failing tests

**Files:**
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/AuthHttpIntegrationTest.java`
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/UserHttpIntegrationTest.java`
- Create: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/support/TestAuthChallengeConfiguration.java`

- [ ] **Step 1: Write the failing auth challenge test updates**

```java
@Test
void should_temporarily_lock_login_after_ten_failed_attempts() throws Exception {
    registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

    for (int index = 0; index < 10; index++) {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("alice", "WrongPass8", "test-pass-token")))
                .andExpect(status().isUnauthorized());
    }

    mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginRequest("alice", "Secret123!", "test-pass-token")))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("A0429"));
}
```

```java
@TestConfiguration
class TestAuthChallengeConfiguration {

    @Bean
    IAuthChallengeVerifierPort testAuthChallengeVerifierPort() {
        return (challengeToken, requestContext) -> "test-pass-token".equals(challengeToken);
    }
}
```

- [ ] **Step 2: Run the backend auth tests to verify they fail for the right reason**

Run:

```powershell
cmd /c ".\mozhi-backend\mvnw.cmd -q -pl mozhi-app -Dtest=AuthHttpIntegrationTest,UserHttpIntegrationTest test"
```

Expected: FAIL because the runtime still expects `dev-pass` / current verifier wiring does not yet honor the test bean.

- [ ] **Step 3: Wire the test configuration into both integration suites**

```java
@SpringBootTest(
        classes = {
                Application.class,
                TestAuthChallengeConfiguration.class
        },
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "...",
                "mozhi.auth.challenge.provider=test"
        }
)
```

- [ ] **Step 4: Re-run the backend auth tests and keep them red only on unimplemented production code**

Run:

```powershell
cmd /c ".\mozhi-backend\mvnw.cmd -q -pl mozhi-app -Dtest=AuthHttpIntegrationTest,UserHttpIntegrationTest test"
```

Expected: challenge-path tests now fail only where production verifier/config cleanup is still missing.

- [ ] **Step 5: Commit the isolated test harness**

```bash
git add mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/AuthHttpIntegrationTest.java mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/UserHttpIntegrationTest.java mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/support/TestAuthChallengeConfiguration.java
git commit -m "test: replace dev-pass auth challenge fixtures"
```

### Task 2: Implement Turnstile-backed backend verification and secure defaults

**Files:**
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/Application.java`
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/AuthSecurityProperties.java`
- Modify: `mozhi-backend/mozhi-app/src/main/resources/application.yml`
- Create: `mozhi-backend/mozhi-app/src/main/resources/application-dev.yml`
- Create: `mozhi-backend/mozhi-app/src/main/resources/application-test.yml`
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java`
- Delete: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/NoopAuthChallengeVerifierPortImpl.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/TurnstileAuthChallengeVerifierPortImpl.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/TestAuthChallengeVerifierPortImpl.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/gateway/turnstile/TurnstileSiteVerifyGateway.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/gateway/turnstile/dto/TurnstileSiteVerifyRequest.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/gateway/turnstile/dto/TurnstileSiteVerifyResponse.java`

- [ ] **Step 1: Extend typed auth properties with Turnstile settings**

```java
public static class Challenge {

    private String provider = "turnstile";
    private final Turnstile turnstile = new Turnstile();

    public static class Turnstile {
        private String secretKey;
        private String siteVerifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
        private List<String> allowedHostnames = List.of();
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(3);
    }
}
```

- [ ] **Step 2: Disable the irrelevant default Spring Security user auto-configuration**

```java
@SpringBootApplication(
        scanBasePackages = "cn.zy.mozhi",
        exclude = UserDetailsServiceAutoConfiguration.class
)
public class Application {
}
```

- [ ] **Step 3: Add the Turnstile gateway and focused verifier implementations**

```java
@Component
@ConditionalOnProperty(prefix = "mozhi.auth.challenge", name = "provider", havingValue = "turnstile", matchIfMissing = true)
public class TurnstileAuthChallengeVerifierPortImpl implements IAuthChallengeVerifierPort {

    @Override
    public boolean verify(String challengeToken, AuthRequestContext requestContext) {
        if (challengeToken == null || challengeToken.isBlank()) {
            return false;
        }
        TurnstileSiteVerifyResponse response = gateway.verify(challengeToken, requestContext.ipAddress());
        return response.success() && allowedHostnames.contains(response.hostname());
    }
}
```

```java
@Component
@ConditionalOnProperty(prefix = "mozhi.auth.challenge", name = "provider", havingValue = "test")
public class TestAuthChallengeVerifierPortImpl implements IAuthChallengeVerifierPort {

    @Override
    public boolean verify(String challengeToken, AuthRequestContext requestContext) {
        return "test-pass-token".equals(challengeToken);
    }
}
```

- [ ] **Step 4: Move shared config to secure defaults and isolate dev/test overrides**

```yaml
# application.yml
mozhi:
  auth:
    cookie-secure: ${MOZHI_AUTH_COOKIE_SECURE:true}
    challenge:
      provider: ${MOZHI_AUTH_CHALLENGE_PROVIDER:turnstile}
      turnstile:
        secret-key: ${MOZHI_AUTH_TURNSTILE_SECRET_KEY:}
        allowed-hostnames: ${MOZHI_AUTH_TURNSTILE_ALLOWED_HOSTNAMES:}
```

```yaml
# application-dev.yml
mozhi:
  auth:
    cookie-secure: ${MOZHI_AUTH_COOKIE_SECURE:false}
```

```yaml
# application-test.yml
mozhi:
  auth:
    challenge:
      provider: test
```

- [ ] **Step 5: Run backend tests to verify green**

Run:

```powershell
cmd /c ".\mozhi-backend\mvnw.cmd -q -pl mozhi-app -Dtest=AuthHttpIntegrationTest,UserHttpIntegrationTest,HttpSurfaceIntegrationTest test"
```

Expected: PASS under Java 21 with no `UserDetailsServiceAutoConfiguration` generated password warning in the reports.

- [ ] **Step 6: Commit the backend hardening slice**

```bash
git add mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/Application.java mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/AuthSecurityProperties.java mozhi-backend/mozhi-app/src/main/resources/application.yml mozhi-backend/mozhi-app/src/main/resources/application-dev.yml mozhi-backend/mozhi-app/src/main/resources/application-test.yml mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/TurnstileAuthChallengeVerifierPortImpl.java mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/TestAuthChallengeVerifierPortImpl.java mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/gateway/turnstile/TurnstileSiteVerifyGateway.java mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/gateway/turnstile/dto/TurnstileSiteVerifyRequest.java mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/gateway/turnstile/dto/TurnstileSiteVerifyResponse.java
git commit -m "feat: harden auth challenge verification"
```

### Task 3: Replace the frontend text challenge with a Turnstile widget and add logout-all

**Files:**
- Modify: `mozhi-web/src/api/modules/auth.ts`
- Modify: `mozhi-web/src/pages/Auth/AuthPage.test.tsx`
- Modify: `mozhi-web/src/pages/Profile/ProfilePage.test.tsx`
- Modify: `mozhi-web/src/pages/Auth/index.tsx`
- Modify: `mozhi-web/src/pages/Profile/index.tsx`
- Modify: `mozhi-web/src/types/env.d.ts`
- Create: `mozhi-web/src/components/auth/AuthChallengeWidget.tsx`
- Modify: `mozhi-web/src/styles/globals.css`

- [ ] **Step 1: Write the failing frontend tests first**

```tsx
it("renders the challenge widget instead of a plain token input after A0410", async () => {
  vi.mocked(loginWithPassword)
    .mockRejectedValueOnce(new ApiClientError("A0410", "challenge required"))
    .mockResolvedValueOnce(successSession);

  renderAuthPage();

  await user.click(screen.getByRole("button", { name: "登录 MOZhi" }));

  expect(await screen.findByTestId("auth-challenge-widget")).toBeInTheDocument();
  expect(screen.queryByLabelText("验证口令")).not.toBeInTheDocument();
});
```

```tsx
it("shows a logout-all action in the profile dashboard", () => {
  renderProfilePage();
  expect(screen.getByRole("button", { name: "退出所有设备" })).toBeInTheDocument();
});
```

- [ ] **Step 2: Run the focused frontend tests to watch them fail**

Run:

```powershell
cd mozhi-web; npm run test -- src/pages/Auth/AuthPage.test.tsx src/pages/Profile/ProfilePage.test.tsx
```

Expected: FAIL because the widget component and logout-all action do not exist yet.

- [ ] **Step 3: Add the API binding and dedicated widget component**

```ts
export async function logoutAllSessions() {
  return postApi<void>(`${authEndpoints.logout}/all`, undefined, {
    skipAuthRefresh: true,
    withCredentials: true
  });
}
```

```tsx
export function AuthChallengeWidget({ siteKey, onTokenChange, resetSignal }: Props) {
  return <div data-testid="auth-challenge-widget" ref={containerRef} />;
}
```

- [ ] **Step 4: Refactor `AuthPage` and `ProfilePage` to use the new flows**

```tsx
{requiresChallenge ? (
  <AuthChallengeWidget
    onTokenChange={setChallengeToken}
    resetSignal={challengeResetNonce}
    siteKey={import.meta.env.VITE_TURNSTILE_SITE_KEY ?? ""}
  />
) : null}
```

```tsx
<button
  className="mozhi-button mozhi-button-secondary"
  onClick={() => logoutAllMutation.mutate()}
  type="button"
>
  {logoutAllMutation.isPending ? "处理中..." : "退出所有设备"}
</button>
```

- [ ] **Step 5: Re-run the focused frontend tests and then the full frontend suite**

Run:

```powershell
cd mozhi-web; npm run test -- src/pages/Auth/AuthPage.test.tsx src/pages/Profile/ProfilePage.test.tsx
cd mozhi-web; npm run test
```

Expected: PASS, including the existing auth/header/profile regressions.

- [ ] **Step 6: Commit the frontend auth hardening slice**

```bash
git add mozhi-web/src/api/modules/auth.ts mozhi-web/src/pages/Auth/AuthPage.test.tsx mozhi-web/src/pages/Profile/ProfilePage.test.tsx mozhi-web/src/pages/Auth/index.tsx mozhi-web/src/pages/Profile/index.tsx mozhi-web/src/types/env.d.ts mozhi-web/src/components/auth/AuthChallengeWidget.tsx mozhi-web/src/styles/globals.css
git commit -m "feat: add turnstile auth challenge flow"
```

### Task 4: Update docs, verify the whole slice, and prepare the push

**Files:**
- Modify: `PROJECT_GUIDE.md`
- Modify: `docs/superpowers/specs/2026-04-08-phase1-production-hardening-design.md`
- Modify: `docs/superpowers/plans/2026-04-08-phase1-production-hardening.md`

- [ ] **Step 1: Remove local `dev-pass` guidance and document the new env contract**

```md
| `MOZHI_AUTH_CHALLENGE_PROVIDER` | challenge provider，默认 `turnstile` | `turnstile` |
| `MOZHI_AUTH_TURNSTILE_SECRET_KEY` | Turnstile secret key，仅后端使用 | - |
| `MOZHI_AUTH_TURNSTILE_ALLOWED_HOSTNAMES` | Turnstile 允许的 hostname 列表 | `localhost,127.0.0.1`（本地示例） |
| `VITE_TURNSTILE_SITE_KEY` | Turnstile site key，前端公开变量 | - |
```

- [ ] **Step 2: Run fresh frontend verification**

Run:

```powershell
cd mozhi-web; npm run test
cd mozhi-web; npm run lint
cd mozhi-web; npm run build
```

Expected: all commands exit `0`.

- [ ] **Step 3: Run fresh backend verification under Java 21**

Run:

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cmd /c ".\mozhi-backend\mvnw.cmd -q -pl mozhi-app -am test"
```

Expected: PASS with Java 21 and no generated default user password warning.

- [ ] **Step 4: Run targeted smoke checks against the live app**

Run:

```powershell
curl.exe -i -X POST http://127.0.0.1:8090/api/auth/refresh
curl.exe -I http://127.0.0.1:8090/swagger-ui/index.html
```

Expected:

- refresh without cookie returns `401`
- swagger returns `200`

- [ ] **Step 5: Review the diff and commit only the intended hardening files**

```bash
git status --short
git diff -- PROJECT_GUIDE.md mozhi-backend mozhi-web docs/superpowers/specs/2026-04-08-phase1-production-hardening-design.md docs/superpowers/plans/2026-04-08-phase1-production-hardening.md
git add PROJECT_GUIDE.md mozhi-backend mozhi-web docs/superpowers/specs/2026-04-08-phase1-production-hardening-design.md docs/superpowers/plans/2026-04-08-phase1-production-hardening.md
git commit -m "feat: productionize phase1 auth hardening"
```

- [ ] **Step 6: Push the verified branch**

```bash
git push origin master
```
