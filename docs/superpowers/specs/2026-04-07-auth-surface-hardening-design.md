# MOZhi Auth Surface Hardening Design

**Date:** 2026-04-07  
**Status:** Approved in chat, written for review  
**Scope:** Authentication UI full redesign plus production-grade authentication entry hardening

---

## 1. Context

The current authentication flow is functionally usable, but it still carries two mismatches with the new requirement:

1. The login/register UI does not fully match the approved visual reference at [demo/register.html](/F:/new_opint/VibeCoding/MOZhi/demo/register.html).
2. The runtime session model is still prototype-grade because the frontend persists `refreshToken` in Zustand/localStorage, which is not acceptable for a real production launch.

This design treats the authentication surface as one product slice:

- The desktop authentication UI will be rebuilt to closely match the reference design.
- The auth protocol behind that UI will be hardened so the surface is safe to ship, not just visually polished.

This slice stays inside `Phase 1` domain boundaries. It does not start `Phase 2` business work.

---

## 2. Goals

### 2.1 Product goals

- Rebuild both login and register pages so the desktop UI closely follows the structure, spacing, hierarchy, and atmosphere of `demo/register.html`.
- Keep the top navbar project-specific, but keep the auth screen itself visually aligned with the demo.
- Make the register and login pages use the same shell and the same right-side brand panel.
- Keep the desktop version within one screen height without page scroll in normal viewport heights.
- Keep mobile responsive and usable, but do not require strict one-screen fidelity there.

### 2.2 Security goals

- Remove frontend persistence of `refreshToken`.
- Move refresh session continuity to `HttpOnly` cookie storage.
- Add rate limiting and abuse controls for login and register.
- Add password policy enforcement suitable for a no-MFA product.
- Add audit events around auth-critical actions.
- Keep current logout, logout-all, refresh rotation, and protected-route behavior intact after the session model change.

---

## 3. Non-goals

- No OAuth provider integration in this slice. The three social buttons remain visual-only.
- No mobile pixel-perfect recreation of the desktop reference.
- No password reset, email verification, or MFA in this slice.
- No public marketing redesign outside the auth surface.
- No new identity model beyond the existing user table and profile model.

---

## 4. Chosen Approach

We will use the approved **UI + production-grade auth entrance refactor** approach.

That means:

- The auth page shell is rebuilt to match the demo.
- The backend auth contract changes where necessary to support secure cookie-based refresh.
- The frontend auth store changes from `accessToken + refreshToken persisted` to `accessToken in memory + refresh restored from cookie`.
- Security hardening is part of the same slice, not deferred.

This is intentionally a hard cut instead of a cosmetic overlay. A pure UI rewrite would create a polished screen on top of a session model that still exposes long-lived refresh state to XSS.

---

## 5. UI Design

## 5.1 Desktop shell

The desktop authentication page will mirror the reference structure:

- `56px` translucent top navbar with brand on the left and auth mode actions on the right
- Main area split into two columns under the navbar
- Left column is the form surface
- Right column is the brand/story surface
- Overall page height is locked to `calc(100vh - 56px)` for desktop
- Desktop body scrolling stays disabled for the auth page

The left/right ratio will stay close to the reference:

- left form side: `flex: 1.1`
- right brand side: `flex: 0.9`

The visual tone stays exactly in the reference family:

- white background
- subtle gray border system
- soft card shadow on the right-side illustration
- compact typography with `Inter`
- tight, editorial spacing instead of large hero-style gaps

## 5.2 Navbar rule

The navbar does not need to copy the demo literally, but it must stay visually compatible with it:

- left: MOZhi mark + wordmark
- right: two auth mode actions
- no large search bar
- no extra nav clutter
- no dark product shell styling on this route

When `mode=register`, the navbar primary action is `注册` and secondary action is `登录`.  
When `mode=login`, the emphasis flips, but layout and sizing stay identical.

## 5.3 Left form column

The left column will keep the exact structure of the reference:

1. Stepper row
2. Form title and subtitle
3. Social button row
4. Divider text
5. Form fields
6. Password visibility toggle and strength meter
7. Terms checkbox on register mode
8. Primary submit button

The stepper remains visual, not business-stateful. It communicates onboarding progress, but it does not control server-side flow.

### Register page fields

- 用户名
- 昵称
- 邮箱地址
- 设置密码
- 服务条款复选框

### Login page fields

- 用户名或邮箱
- 密码

The login page uses the same shell, the same right panel, and the same stepper. Only the copy and field count change.

## 5.4 Right brand column

The brand column is intentionally identical across login and register:

- mini illustration card
- one short quote
- three feature lines
- one social-proof row

The right side exists to preserve the exact emotional balance of the reference. It is not a dumping ground for product explanations, status text, or extra CTAs.

## 5.5 Social buttons

The three social buttons stay in the layout because the request is to fully recreate the UI.

In this slice they will be rendered as:

- visually active buttons
- keyboard focusable
- clickable only for a non-auth informational response such as `即将支持 Google 登录`

They will not initiate any OAuth redirect. This avoids lying about capability while keeping the reference composition intact.

## 5.6 Password strength meter

The password strength meter remains visible because it is part of the reference design, but its scoring will be updated to align with the real password policy:

- weak: fewer than `15` chars or matches blocked pattern/list
- medium: length threshold passed but entropy hints are weak
- strong: length threshold passed and pattern diversity is healthy

The meter is advisory only. The backend remains the source of truth.

## 5.7 Desktop fidelity rules

The implementation is considered visually correct only if these remain true on desktop:

- the whole auth screen fits within one viewport height at common laptop sizes
- the right panel is visible
- the left form never pushes the submit button below the fold under normal content
- the stepper, social row, divider, fields, terms row, and CTA all appear in a single continuous column
- no old `.form-badge`-style badge remains anywhere

## 5.8 Mobile behavior

Mobile does not need one-screen fidelity.

The mobile behavior is:

- hide the right brand column below the agreed breakpoint
- allow page scroll
- keep field spacing readable
- keep submit CTA and error copy visible without layout breakage

---

## 6. Authentication and Session Design

## 6.1 Session model

The new session model is:

- `accessToken`: short-lived, returned in response body, kept in memory only
- `refreshToken`: long-lived, rotated, stored only in `HttpOnly` cookie

Frontend will no longer persist `refreshToken` in Zustand or localStorage.

### Cookie shape

- name: `mozhi_refresh_token`
- `HttpOnly: true`
- `SameSite: Strict`
- `Path: /api/auth`
- `Secure: true` in production HTTPS
- `Secure: false` only in local dev profiles that explicitly allow HTTP

This keeps refresh continuity server-controlled while still allowing SPA recovery after reload.

## 6.2 Bootstrapping after reload

Because `accessToken` is no longer persisted, frontend bootstrap changes:

- app starts with anonymous in-memory auth state
- if route guard or app bootstrap detects no access token, frontend calls `POST /api/auth/refresh`
- if refresh cookie is present and valid, backend rotates cookie and returns a new access token
- frontend restores authenticated state in memory
- if refresh fails, user remains anonymous

This preserves “refresh page and continue” behavior without storing long-lived session secrets in browser storage.

## 6.3 Login contract

`POST /api/auth/login`

### Request

```json
{
  "identifier": "alice or alice@mozhi.dev",
  "password": "user supplied password",
  "challengeToken": "present only when the server has escalated this session into challenge mode"
}
```

### Behavior

- accepts username or email as `identifier`
- validates password against stored hash
- enforces rate limiting and challenge rules
- returns new access token in response body
- sets rotated refresh cookie in response header
- returns no refresh token in JSON

### Response body

```json
{
  "tokenType": "Bearer",
  "accessToken": "jwt",
  "accessTokenExpiresAt": "2026-04-07T12:00:00Z"
}
```

## 6.4 Refresh contract

`POST /api/auth/refresh`

### Request

- no JSON body required
- refresh cookie is the credential
- origin must match allowed frontend origin
- challenge is not required for normal refresh

### Behavior

- validates refresh cookie
- validates whitelist/rotation status
- rotates refresh token
- sets new refresh cookie
- returns new access token body

## 6.5 Logout contracts

`POST /api/auth/logout`

- requires valid access token in `Authorization`
- requires refresh cookie if present
- revokes current refresh token
- blacklists current access token
- clears refresh cookie

`POST /api/auth/logout/all`

- requires valid access token
- revokes all refresh sessions for the user
- bumps session version so older access tokens fail immediately
- clears refresh cookie

---

## 7. Password Policy

The product currently has no MFA, so password policy must follow the stricter no-MFA baseline.

## 7.1 Registration rules

- minimum length: `15`
- maximum length: `64`
- spaces are allowed
- no forced composition rules such as “must include uppercase/lowercase/number/symbol”
- passwords must not be blank-only
- passwords must not match a blocked common-password list
- passwords must not contain exact copies of the username or email local-part when length overlap is obvious

## 7.2 Handling rules

- backend must not silently trim passwords
- backend may normalize username/email, but password must be treated as exact user input
- frontend shows live feedback, but backend enforcement is authoritative

## 7.3 Hashing strategy

New registrations and password verifications will move to `Argon2id` as the primary encoder.

Because existing users were created under BCrypt, password verification must support both:

- existing `{bcrypt}` hashes remain valid
- new hashes use `{argon2}`

The implementation should use a delegating password encoder so old accounts do not break. Rehash-on-login is explicitly out of scope for this slice.

---

## 8. Abuse and Anti-automation Controls

## 8.1 Login rate limits

Two controls apply to login:

### Per-IP hard rate limit

- key: `auth:login:ip:{ip}`
- threshold: `20` attempts per `10` minutes
- exceed result: `429 Too Many Requests`

### Per-identifier failure ladder

- key: `auth:login:identifier:{normalizedIdentifier}`
- threshold A: `5` failed attempts within `15` minutes -> challenge required
- threshold B: `10` failed attempts within `15` minutes -> temporary lock for `15` minutes

If threshold A is active and no valid challenge token is supplied, login is rejected with a generic auth error plus a machine-readable challenge-needed code.

## 8.2 Register rate limits

Three controls apply to register:

- per IP: max `5` attempts per `60` minutes
- per normalized email: max `3` attempts per `24` hours
- per normalized username: max `5` attempts per `24` hours

When IP attempts reach `3` within one hour, challenge becomes mandatory for subsequent register attempts from that IP during the same window.

## 8.3 Challenge provider

The challenge abstraction will be provider-based:

- production target: Cloudflare Turnstile
- local dev/test: deterministic noop verifier behind profile/property switch

This keeps production behavior real without making local TDD slow or brittle.

## 8.4 Error semantics

Login must not reveal whether the account exists.

These cases return the same user-facing error copy:

- username not found
- email not found
- password mismatch
- challenged login without valid challenge token

Rate-limit hard blocks may return `429`, but the UI copy still remains generic and non-enumerating.

---

## 9. Audit and Observability

The auth flow must emit structured audit events for:

- register success
- register rejected by validation
- register rejected by abuse guard
- login success
- login failure
- refresh success
- refresh rejection
- logout
- logout all
- challenge required
- challenge verification failure

Audit payloads may include:

- timestamp
- user id if known
- normalized identifier hash or masked form
- IP
- user agent
- outcome code

Audit logs must never include:

- raw password
- refresh token
- full access token
- full challenge token

---

## 10. Backend Design Changes

## 10.1 Domain responsibilities

The auth hardening stays inside the existing DDD boundaries:

- `mozhi-domain`
  - auth policy
  - password policy
  - challenge decision logic
  - rate-limit decision logic
- `mozhi-infrastructure`
  - Redis-backed counters/locks
  - Turnstile verifier implementation
  - cookie writer helpers
  - delegating password encoder implementation
- `mozhi-trigger`
  - HTTP DTOs
  - cookie read/write on controllers
  - auth error translation
- `mozhi-app`
  - security config
  - auth-related properties

## 10.2 Contract changes

The following contract changes are intentional:

- login request changes from `username` to `identifier`
- login/refresh response body no longer includes `refreshToken`
- refresh request body no longer carries `refreshToken`
- logout request body no longer carries `refreshToken`

This is acceptable because the frontend and backend are shipped together in this stage.

## 10.3 Existing behavior to preserve

The redesign must preserve:

- access token RS256 verification
- refresh rotation
- logout revocation
- logout-all session version invalidation
- protected route enforcement
- avatar/profile routes behind auth

---

## 11. Frontend Design Changes

## 11.1 Auth store

The auth store becomes:

- `status`
- `accessToken`
- `user`
- `hasHydrated`

The store removes:

- persisted `refreshToken`

Persistence behavior changes:

- `accessToken` is not stored in localStorage
- no auth secret or authenticated identity is persisted in browser storage
- auth recovery happens through refresh-cookie bootstrap, not browser storage

## 11.2 API client

The Axios client changes to:

- send bearer token from in-memory store
- on `401`, perform a single-flight `POST /api/auth/refresh` with credentials
- retry the original request after successful refresh
- reset auth state if refresh fails

All auth-related requests that need cookies must use `withCredentials: true`.

## 11.3 Auth page behavior

Register and login pages share one component shell:

- `mode=register` shows register copy and register fields
- `mode=login` shows login copy and login fields
- terms checkbox is register-only
- challenge widget slot appears only when server says challenge is required
- server validation errors render inline without shifting the page off-screen

## 11.4 Profile and protected flows

Protected routes must continue to work after the session model change:

- visiting `/profile` while anonymous still redirects to `/auth`
- successful login returns to original `redirect`
- hard refresh on protected pages triggers silent refresh bootstrap if cookie exists
- logout clears in-memory access state and the refresh cookie-backed session

---

## 12. Testing Design

This slice is explicitly TDD-driven.

## 12.1 Backend tests

Required backend coverage:

- password policy acceptance/rejection
- bcrypt compatibility plus argon2 default hashing
- login by username
- login by email
- generic invalid-credentials response
- refresh cookie issuance
- refresh rotation using cookie
- logout cookie clearing
- logout-all invalidation
- per-IP rate limit
- per-identifier challenge threshold
- per-identifier temporary lock
- register rate limit and challenge threshold

These tests should stay mostly at integration level because the behavior crosses controller, security, cookie, Redis-like state, and domain policy boundaries.

## 12.2 Frontend tests

Required frontend coverage:

- auth page desktop shell renders with the expected sections
- register mode and login mode swap correctly without layout regressions
- terms checkbox blocks register submission until accepted
- challenge area appears when backend returns challenge-needed state
- API client retries once after refresh
- protected route recovers after reload when refresh cookie is valid
- protected route falls back to login when refresh cookie is absent/invalid

If the current frontend test stack is missing, adding `Vitest + React Testing Library` is part of the implementation scope.

## 12.3 Verification gate

The slice is only complete when all of the following are green:

- backend auth integration tests
- frontend auth unit/component tests
- `npm run lint`
- `npm run build`
- `.\mvnw.cmd -q -pl mozhi-app -am test`
- browser walkthrough:
  - register
  - login
  - refresh after reload
  - rate-limit/challenge path
  - logout

---

## 13. Release Impact

This work changes auth contracts and browser session behavior, so the rollout order must be:

1. backend contract and cookie flow
2. frontend auth client/store adaptation
3. auth UI replacement
4. regression on profile/protected flows

The old persisted-refresh-token behavior must not coexist with the new cookie flow in production builds.

---

## 14. Acceptance Criteria

This design is complete only if all items below are true:

- desktop login/register visually match the reference composition and fit in one screen
- no old auth badge UI remains
- refresh token is no longer stored in frontend persistent storage
- login accepts username or email
- refresh works through `HttpOnly` cookie rotation
- logout and logout-all still revoke sessions correctly
- password policy enforces `15-64` chars without composition-rule theater
- common-password blocking is active
- rate limiting and challenge escalation are active for login/register
- auth errors do not enumerate valid accounts
- audit events are emitted for critical auth outcomes
- existing protected pages continue to work after reload through silent refresh recovery
