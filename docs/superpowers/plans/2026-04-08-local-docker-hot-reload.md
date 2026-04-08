# Local Docker Hot Reload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the local Docker runtime into a source-mounted development stack so frontend changes hot reload immediately and backend changes auto-restart without rebuilding app images.

**Architecture:** Keep middleware in the existing environment compose file, but convert the local app overlay into a true development runtime. Frontend will use bind-mounted source plus a node_modules volume and Vite polling. Backend will use bind-mounted source plus a Maven cache volume and an `entr`-driven rebuild-and-restart loop around `mvn package` plus `java -jar`.

**Tech Stack:** Docker Compose, Dockerfile.dev, Node 22, Maven 3.9 + JDK 21, Vite, Spring Boot, JUnit.

---

### Task 1: Lock the development-runtime contract with tests

**Files:**
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/BackendRuntimeEnvironmentVerifierTest.java`

- [ ] **Step 1: Add assertions for local Docker source mounts and dev commands**
- [ ] **Step 2: Run the targeted verifier test first and confirm it fails before implementation**

### Task 2: Convert local Dockerfiles into development runtime images

**Files:**
- Modify: `mozhi-backend/Dockerfile.dev`
- Modify: `mozhi-web/Dockerfile.dev`

- [ ] **Step 1: Change the backend dev Dockerfile from packaged-JAR runtime to Maven + entr runtime base**
- [ ] **Step 2: Change the frontend dev Dockerfile from source-baked image to Node runtime base**

### Task 3: Rework the local compose overlay for hot reload

**Files:**
- Modify: `docs/dev-ops/docker-compose-local.yml`

- [ ] **Step 1: Add backend bind mount, Maven cache volume, and file-watch restart command**
- [ ] **Step 2: Add frontend bind mount, node_modules volume, polling env, and dependency bootstrap command**
- [ ] **Step 3: Declare the named volumes used by the local stack**

### Task 4: Update operator docs

**Files:**
- Modify: `README.md`
- Modify: `docs/dev-ops/README.md`

- [ ] **Step 1: Explain first-run vs day-to-day startup commands**
- [ ] **Step 2: Document frontend hot reload and backend auto-restart semantics**
- [ ] **Step 3: Explain when `--build` is still required**

### Task 5: Verify the end-to-end local developer workflow

**Files:**
- Review: `docs/dev-ops/docker-compose-local.yml`
- Review: `mozhi-backend/Dockerfile.dev`
- Review: `mozhi-web/Dockerfile.dev`

- [ ] **Step 1: Run the targeted backend runtime verifier**
- [ ] **Step 2: Run `docker compose ... config` for the local stack**
- [ ] **Step 3: Run frontend `npm run test`**
- [ ] **Step 4: Run frontend `npm run lint`**
- [ ] **Step 5: Run frontend `npm run build`**
- [ ] **Step 6: Run backend `.\mvnw.cmd -q -pl mozhi-app -am test`**
- [ ] **Step 7: Rebuild and start the local app overlay, then verify `5173` and `8090` endpoints respond**
