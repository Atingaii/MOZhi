# Production Docker Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a production-oriented Docker runtime with Nginx as the unified entrypoint for static frontend delivery and backend reverse proxy.

**Architecture:** Keep middleware in the existing environment compose, run the backend as a Spring Boot container, build the frontend into static assets, and serve everything through Nginx. The production stack should expose one HTTP entrypoint and route `/api`, `/swagger-ui`, and `/actuator/health` to the backend while serving frontend assets directly.

**Tech Stack:** Docker Compose, Nginx, Node 22, Vite build output, Maven Wrapper, Eclipse Temurin 21, Spring Boot.

---

### Task 1: Add production image definitions

**Files:**
- Create: `mozhi-backend/Dockerfile.prod`
- Create: `mozhi-web/Dockerfile.prod`
- Create: `docs/dev-ops/nginx/nginx.conf`

- [x] **Step 1: Add backend production Dockerfile**
- [x] **Step 2: Add frontend production Dockerfile with multi-stage build**
- [x] **Step 3: Add Nginx config for SPA static serving and backend reverse proxy**
- [x] **Step 4: Validate production image builds**

---

### Task 2: Replace placeholder app compose with a real production stack

**Files:**
- Modify: `docs/dev-ops/docker-compose-app.yml`

- [x] **Step 1: Replace placeholder app image wiring with real build targets**
- [x] **Step 2: Add gateway service ports and backend service dependencies**
- [x] **Step 3: Pass env vars for `/api`-based frontend build and backend runtime**
- [x] **Step 4: Validate merged compose config**

---

### Task 3: Document deployment behavior and Nginx rationale

**Files:**
- Modify: `README.md`
- Modify: `PROJECT_GUIDE.md`
- Modify: `docs/dev-ops/README.md`

- [x] **Step 1: Document production-oriented Docker startup command**
- [x] **Step 2: Explain when local runtime should not use Nginx**
- [x] **Step 3: Document the single-entry Nginx production runtime**

---

### Task 4: Review implementation details and verify end to end

**Files:**
- Review: `docs/dev-ops/docker-compose-app.yml`
- Review: `docs/dev-ops/nginx/nginx.conf`
- Review: `mozhi-backend/Dockerfile.prod`
- Review: `mozhi-web/Dockerfile.prod`

- [x] **Step 1: Build production images**
- [x] **Step 2: Start the production-oriented stack**
- [x] **Step 3: Smoke check Nginx entrypoint, backend proxy, and frontend HTML**
- [x] **Step 4: Run frontend regression checks**
- [x] **Step 5: Run backend regression checks**
- [ ] **Step 6: Stage only intended files, commit, and push**
