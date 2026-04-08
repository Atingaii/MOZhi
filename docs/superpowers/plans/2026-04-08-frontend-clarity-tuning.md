# Frontend Clarity Tuning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the current hazy, frosted appearance from the frontend while preserving the existing UI structure and page layout.

**Architecture:** Keep the change strictly inside the shared global stylesheet so page composition and component contracts remain unchanged. Add a small CSS regression test that guards the intended visual constraints.

**Tech Stack:** React 18, Vite, Vitest, global CSS.

---

### Task 1: Add a regression test for clarity-related global styles

**Files:**
- Create: `mozhi-web/src/styles/globalsStyleTokens.test.ts`

- [x] **Step 1: Add a failing test that rejects navbar blur and transparent core card backgrounds**
- [x] **Step 2: Verify the new test fails before style changes**

---

### Task 2: Tune shared visual tokens and surfaces without moving the UI

**Files:**
- Modify: `mozhi-web/src/styles/globals.css`

- [x] **Step 1: Make the navbar and search shell more solid and legible**
- [x] **Step 2: Convert core content surfaces from translucent to solid backgrounds**
- [x] **Step 3: Tighten shadow spread and increase text/surface contrast where needed**

---

### Task 3: Verify the frontend remains stable

**Files:**
- Review: `mozhi-web/src/styles/globals.css`
- Review: `mozhi-web/src/styles/globalsStyleTokens.test.ts`

- [x] **Step 1: Run the targeted style regression test**
- [x] **Step 2: Run frontend test suite**
- [x] **Step 3: Run frontend lint**
- [x] **Step 4: Run frontend build**
