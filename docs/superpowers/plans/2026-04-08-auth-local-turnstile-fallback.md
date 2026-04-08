# Auth Local Turnstile Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore local login usability when Turnstile is not fully configured, while keeping production challenge enforcement strict and improving the auth page vertical centering.

**Architecture:** Add a backend challenge bypass flag that is enabled only in the dev profile when Turnstile is unconfigured, and make the frontend auth shell resilient to missing site keys without changing the existing UI structure.

**Tech Stack:** Spring Boot configuration properties, Turnstile verifier adapter, React auth page, Vitest, JUnit.

---

### Task 1: Add failing tests for the new fallback and layout behavior

**Files:**
- Modify: `mozhi-web/src/pages/Auth/AuthPage.test.tsx`
- Modify: `mozhi-web/src/styles/globalsStyleTokens.test.ts`
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/TurnstileAuthChallengeVerifierPortImplTest.java`
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/BackendRuntimeEnvironmentVerifierTest.java`

- [ ] **Step 1: Add a frontend auth test for missing site key challenge escalation**
- [ ] **Step 2: Add a CSS regression test for auth page centering**
- [ ] **Step 3: Add backend tests for dev-only bypass when Turnstile is unconfigured**
- [ ] **Step 4: Verify the new tests fail first**

---

### Task 2: Implement backend dev-only Turnstile bypass

**Files:**
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/AuthSecurityProperties.java`
- Modify: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/gateway/turnstile/TurnstileSiteVerifyGateway.java`
- Modify: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/TurnstileAuthChallengeVerifierPortImpl.java`
- Modify: `mozhi-backend/mozhi-app/src/main/resources/application-dev.yml`
- Modify: `mozhi-backend/mozhi-app/src/main/resources/application-prod.yml`

- [ ] **Step 1: Add a config flag for bypassing unconfigured challenge only in dev**
- [ ] **Step 2: Teach the verifier to bypass only when Turnstile is unconfigured and the flag is enabled**
- [ ] **Step 3: Keep prod fail-closed semantics explicit**

---

### Task 3: Implement frontend auth-page resilience and centering

**Files:**
- Modify: `mozhi-web/src/pages/Auth/index.tsx`
- Modify: `mozhi-web/src/components/auth/AuthChallengeWidget.tsx`
- Modify: `mozhi-web/src/styles/globals.css`

- [ ] **Step 1: Stop disabling the submit button when challenge is required but no site key exists**
- [ ] **Step 2: Improve the missing-site-key helper copy**
- [ ] **Step 3: Center the auth shell vertically without changing its structure**

---

### Task 4: Verify the full stack behavior

**Files:**
- Review: `mozhi-web/src/pages/Auth/index.tsx`
- Review: `mozhi-web/src/styles/globals.css`
- Review: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/TurnstileAuthChallengeVerifierPortImpl.java`

- [ ] **Step 1: Run targeted frontend tests**
- [ ] **Step 2: Run targeted backend tests**
- [ ] **Step 3: Run frontend test suite**
- [ ] **Step 4: Run frontend lint**
- [ ] **Step 5: Run frontend build**
- [ ] **Step 6: Run backend Maven tests**
