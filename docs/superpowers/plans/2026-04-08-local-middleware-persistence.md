# Local Middleware Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist MySQL, Redis, Kafka, and MinIO data across local Docker container rebuilds.

**Architecture:** The shared middleware compose file will own named volumes for each stateful service. Redis keeps AOF persistence enabled, while MySQL, Kafka, and MinIO persist their native data directories to Docker-managed volumes. Runtime contract tests and operator docs will lock in the behavior.

**Tech Stack:** Docker Compose, MySQL 8, Redis 7, Apache Kafka 3.7, MinIO, JUnit.

---

### Task 1: Lock the persistence contract with tests

**Files:**
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/BackendRuntimeEnvironmentVerifierTest.java`

- [ ] **Step 1: Add failing assertions for middleware named volumes and Redis persistence expectations**
- [ ] **Step 2: Run the targeted verifier and confirm it fails before implementation**

### Task 2: Persist middleware state in the shared environment compose file

**Files:**
- Modify: `docs/dev-ops/docker-compose-environment.yml`
- Modify: `docs/dev-ops/redis/redis.conf`

- [ ] **Step 1: Add named volume mounts for MySQL, Redis, Kafka, and MinIO**
- [ ] **Step 2: Ensure Redis keeps append-only persistence on the mounted data directory**
- [ ] **Step 3: Declare the named volumes in the compose file**

### Task 3: Update operator docs

**Files:**
- Modify: `README.md`
- Modify: `docs/dev-ops/README.md`

- [ ] **Step 1: Document that local middleware data now persists across rebuilds**
- [ ] **Step 2: Explain the difference between `docker compose down` and `docker compose down -v`**

### Task 4: Verify the local persistence workflow

**Files:**
- Review: `docs/dev-ops/docker-compose-environment.yml`
- Review: `docs/dev-ops/redis/redis.conf`

- [ ] **Step 1: Run the targeted backend runtime verifier**
- [ ] **Step 2: Run `docker compose -f .\\docs\\dev-ops\\docker-compose-environment.yml config`**
- [ ] **Step 3: Recreate middleware services and inspect the mounted data volumes**
- [ ] **Step 4: Run backend and frontend regression checks needed for confidence**
