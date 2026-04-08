# MOZhi Phase 1 Production Hardening Design

**Date:** 2026-04-08  
**Status:** Approved in chat, written for review  
**Scope:** Productionize the existing Phase 1 auth and user slice without expanding into a larger identity project

---

## 1. Context

`Phase 1` already has working authentication and user-profile capability:

- register
- login
- refresh rotation
- logout
- logout all
- protected routes
- profile editing
- avatar upload

That functional baseline is not yet production-ready. The current codebase still has several launch-blocking issues:

1. challenge verification still defaults to a local `noop` token pass-through
2. auth documentation still describes `dev-pass` as a normal local path
3. refresh cookie secure handling defaults to local-development values
4. Spring Security still emits the generated default user warning during backend test bootstrap
5. frontend does not expose `logout all`
6. auth UI and test coverage do not yet reflect a real third-party challenge provider

This design closes those gaps inside the existing Phase 1 boundary. It does not start a new recovery or MFA project.

---

## 2. Goals

### 2.1 Product goals

- Keep the existing Phase 1 auth and profile flows intact for end users.
- Replace the temporary challenge input with a real Cloudflare Turnstile flow.
- Expose a clear `退出所有设备` UI in the current frontend style.
- Keep the current personal-center and auth-page redesign work intact.

### 2.2 Security goals

- Remove `noop` as the runtime default challenge path.
- Verify Turnstile server-side and validate the reported hostname.
- Make production cookie security explicit instead of inheriting development defaults.
- Remove the default Spring Security generated-user residue from application bootstrap.
- Keep secrets out of source control and out of browser-visible runtime bundles.

### 2.3 Maintainability goals

- Keep new auth-hardening logic in small, focused files.
- Reuse the existing DDD boundaries instead of introducing parallel layers.
- Keep config intent obvious through typed properties and profile-specific overrides.
- Keep frontend auth UI logic readable by splitting Turnstile-specific behavior from page composition.

---

## 3. Non-goals

This slice does **not** include:

- password reset
- email verification
- MFA
- OAuth social login
- device/session management history UI
- a new standalone settings domain

Those are legitimate next-phase auth expansions, but they are outside this hardening patch.

---

## 4. Chosen Approach

We will use the focused hardening approach:

1. Cloudflare Turnstile becomes the only normal runtime challenge provider.
2. `dev` and `test` stay deterministic through profile-specific configuration, not through production defaults.
3. Backend performs all authoritative Turnstile verification.
4. Frontend only renders and resets the widget; it never decides whether a challenge is valid.
5. `logout all` becomes a first-class frontend action.

This approach is preferred over a larger auth rewrite because it closes the actual launch risks without opening new identity workflows that would delay delivery and broaden regression scope.

---

## 5. Backend Design

## 5.1 Provider model

The domain boundary remains unchanged:

- domain asks `IAuthChallengeVerifierPort` whether a challenge token is valid
- infrastructure implements the provider-specific verification

The implementation changes are:

- remove the runtime assumption that `noop` is the default provider
- add a dedicated Turnstile verifier implementation
- keep a deterministic verifier only for tests or explicitly local development

Production configuration must fail closed:

- if provider is `turnstile` and required keys are missing, challenge verification fails
- if Turnstile responds with `success=false`, verification fails
- if Turnstile returns a hostname outside the configured allow-list, verification fails

## 5.2 Turnstile verification contract

Backend will call Cloudflare `siteverify` and evaluate:

- `success`
- `hostname`
- error codes for logging and audit context

The backend does not trust the frontend hostname. It trusts only Cloudflare's verification response plus the backend-configured allow-list.

## 5.3 Typed config

`mozhi.auth` properties will be expanded so the challenge section is explicit:

- `provider`
- `turnstile.secret-key`
- `turnstile.site-verify-url`
- `turnstile.allowed-hostnames`
- optional `turnstile.connect-timeout`
- optional `turnstile.read-timeout`

The old `noop-pass-token` property is removed from shared runtime config. If a deterministic stub is needed for tests, it should live in test configuration only.

## 5.4 Environment strategy

### Shared base config

Base config should describe the secure target shape:

- `challenge.provider=turnstile`
- `cookie-secure=true`

That means the default repository configuration reflects the production target, not local shortcuts.

### Dev profile

`application-dev.yml` will explicitly override only what local HTTP needs:

- `cookie-secure=false`
- `allowed-hostnames=localhost,127.0.0.1`

Development can still use real Turnstile keys or Cloudflare test keys through environment variables. The repository will not store either.

### Test profile

Tests should not depend on an external Turnstile network call. The test profile will use a deterministic verifier implementation or mock bean so integration tests remain stable and fast.

## 5.5 Spring Security bootstrap cleanup

The application currently triggers Spring Boot's generated default user auto-configuration warning, which is misleading in a JWT-only service.

The application bootstrap will explicitly disable that default user auto-configuration so:

- no generated password warning appears
- the auth story is easier to reason about
- maintenance does not have to distinguish between the real JWT setup and an irrelevant default user

## 5.6 Logout-all surface

The backend already exposes `POST /api/auth/logout/all`. That contract remains unchanged. This patch only ensures the endpoint is consistently covered by frontend API bindings and tests.

---

## 6. Frontend Design

## 6.1 Auth page challenge flow

The auth page currently escalates by showing a plain text token input. That is replaced with a real Turnstile widget area.

Behavior:

- when the backend returns `A0410`, the page renders the Turnstile widget
- widget completion stores the returned challenge token in component state
- retrying login or register sends that token in the existing request contract
- after a failed attempt, the widget is reset so the user cannot keep resubmitting a stale token

The helper message that mentions `dev-pass` is removed entirely.

## 6.2 Component structure

Turnstile-specific rendering and lifecycle should not stay inline inside the full auth page.

Frontend will introduce a small dedicated component, for example:

- `AuthChallengeWidget`

Responsibilities:

- lazy-load or render the Turnstile widget host
- expose `token`, `expired`, and `error` transitions through props/callbacks
- keep cleanup and reset logic isolated from the form layout

This keeps `AuthPage` focused on form composition and submission state.

## 6.3 Environment handling

Frontend will use a typed Vite env variable for the public site key.

The site key is safe for the browser bundle. The secret key is backend-only and must never be present in frontend code or checked into the repository.

## 6.4 Logout-all UI

The profile/dashboard experience already acts as the main account center, so `退出所有设备` belongs there.

The action will be added near the existing authenticated actions rather than hidden in a new settings page. Recommended placement:

- profile dashboard header action group, next to `编辑资料`
- or the account action cluster in the profile shell if that is visually cleaner in the current implementation

Behavior:

- click action
- call `POST /api/auth/logout/all`
- clear in-memory auth state
- redirect to `/auth`
- show a short confirmation status

This keeps self-service session recovery available without adding a full session management feature.

---

## 7. Testing Design

This patch is TDD-driven. Every behavior change must be introduced by a failing test first.

## 7.1 Backend tests

Required coverage:

- challenge-required login still returns `A0410`
- challenge-required register still returns `A0410`
- deterministic test verifier can satisfy the challenge path in tests without `dev-pass`
- Turnstile verifier rejects hostname mismatches
- Turnstile verifier rejects Cloudflare failure responses
- Spring Security generated-user warning path is removed from test bootstrap
- `logout all` remains green through the existing integration suite

Tests that currently hardcode `dev-pass` must be rewritten so they verify the new deterministic test provider instead of a shared runtime shortcut.

## 7.2 Frontend tests

Required coverage:

- auth page renders the Turnstile area only after `A0410`
- helper copy no longer mentions `dev-pass`
- submitting after a solved challenge sends the token
- challenge widget reset path runs after an auth failure
- profile page or account action area exposes `退出所有设备`
- clicking `退出所有设备` calls the correct API flow and clears auth state

## 7.3 Verification gate

This slice is not complete until all of the following are fresh and green:

- frontend tests
- frontend lint
- frontend build
- backend auth tests under Java 21
- targeted smoke check against the running app for login/logout-all/challenge escalation

---

## 8. Documentation Changes

The repo documentation must stop teaching `dev-pass`.

Required doc updates:

- `PROJECT_GUIDE.md`
- any auth setup or local-dev instructions that mention `noop`
- local environment setup notes for Turnstile site key / secret key / allowed hostnames

The documentation should make the split explicit:

- local development may use Cloudflare test keys or real keys bound to `localhost`
- production must use real keys and production hostnames

---

## 9. Security Notes and Deferred Work

After this patch, the main launch blockers for the current Phase 1 slice are addressed, but the broader auth roadmap still has open items:

- password reset
- email verification
- MFA
- explicit cross-device session history

Those items should be tracked as a separate auth-expansion phase. They are not prerequisites for shipping this hardening patch, but they should not be forgotten or implied as already done.

---

## 10. Acceptance Criteria

This design is complete only if all of the following are true:

- repository defaults no longer describe `noop` + `dev-pass` as the normal challenge flow
- backend verifies Turnstile server-side and checks allowed hostnames
- Spring Boot no longer emits the generated default user warning during backend bootstrap
- production-target config uses `Secure` refresh cookies by default
- auth page no longer exposes the plain challenge token text input
- auth UI no longer mentions `dev-pass`
- frontend exposes `退出所有设备`
- frontend and backend tests pass under the intended toolchain
- no secret key is committed to the repository

