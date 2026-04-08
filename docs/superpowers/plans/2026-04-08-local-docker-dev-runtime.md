# Local Docker Dev Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a local Docker-based runtime so the repo can be started with Docker Compose without requiring host-installed Java or Node.

**Architecture:** Keep local development simple: Docker Compose runs middleware, backend, and frontend as separate services with direct host port exposure. Do not add Nginx in the local stack; reserve that for a later production-oriented compose profile.

**Tech Stack:** Docker Compose, Eclipse Temurin 21, Maven Wrapper, Node 22, Vite, Spring Boot.

---

### Task 1: Add container build files for backend and frontend

**Files:**
- Create: `mozhi-backend/Dockerfile.dev`
- Create: `mozhi-web/Dockerfile.dev`

- [ ] **Step 1: Add backend dev Dockerfile**

Create a multi-stage image that builds the backend jar and runs `mozhi-app` with Java 21:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY mozhi-backend/pom.xml ./mozhi-backend/pom.xml
COPY mozhi-backend/mozhi-api/pom.xml ./mozhi-backend/mozhi-api/pom.xml
COPY mozhi-backend/mozhi-app/pom.xml ./mozhi-backend/mozhi-app/pom.xml
COPY mozhi-backend/mozhi-domain/pom.xml ./mozhi-backend/mozhi-domain/pom.xml
COPY mozhi-backend/mozhi-infrastructure/pom.xml ./mozhi-backend/mozhi-infrastructure/pom.xml
COPY mozhi-backend/mozhi-trigger/pom.xml ./mozhi-backend/mozhi-trigger/pom.xml
COPY mozhi-backend/mozhi-types/pom.xml ./mozhi-backend/mozhi-types/pom.xml

COPY mozhi-backend ./mozhi-backend

RUN mvn -q -f ./mozhi-backend/pom.xml -pl mozhi-app -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/mozhi-backend/mozhi-app/target/mozhi-app-1.0.0-SNAPSHOT.jar ./mozhi-app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "/app/mozhi-app.jar", "--spring.profiles.active=dev"]
```

- [ ] **Step 2: Add frontend dev Dockerfile**

Create a Node-based image that installs dependencies and runs Vite in container-friendly host mode:

```dockerfile
FROM node:22-alpine
WORKDIR /app

COPY mozhi-web/package.json mozhi-web/package-lock.json ./
RUN npm ci

COPY mozhi-web ./

EXPOSE 5173

CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]
```

- [ ] **Step 3: Verify both Dockerfiles are syntactically valid**

Run:

```powershell
docker build -f mozhi-backend/Dockerfile.dev -t mozhi-backend-dev:test .
docker build -f mozhi-web/Dockerfile.dev -t mozhi-web-dev:test .
```

Expected: both builds succeed.

---

### Task 2: Add a one-command local compose stack

**Files:**
- Modify: `docs/dev-ops/docker-compose-environment.yml`
- Create: `docs/dev-ops/docker-compose-local.yml`

- [ ] **Step 1: Keep middleware compose unchanged unless required**

Do not break the existing environment compose. Reuse its services and networking expectations.

- [ ] **Step 2: Add local compose for app services**

Create `docs/dev-ops/docker-compose-local.yml` with:

```yaml
services:
  mozhi-backend:
    build:
      context: ../..
      dockerfile: mozhi-backend/Dockerfile.dev
    container_name: mozhi-backend-dev
    restart: unless-stopped
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_DATABASE: mozhi
      MYSQL_USER: root
      MYSQL_PASSWORD: 123456
      REDIS_HOST: redis
      REDIS_PORT: 6379
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      MOZHI_AUTH_COOKIE_SECURE: "false"
      MOZHI_AUTH_TURNSTILE_ALLOWED_HOSTNAMES: localhost,127.0.0.1
      MOZHI_STORAGE_MINIO_ENABLED: "true"
      MOZHI_STORAGE_MINIO_ENDPOINT: http://minio:9000
      MOZHI_STORAGE_MINIO_PUBLIC_ENDPOINT: http://127.0.0.1:19000
      MOZHI_STORAGE_MINIO_BUCKET: mozhi-assets
      MOZHI_STORAGE_MINIO_ACCESS_KEY: minioadmin
      MOZHI_STORAGE_MINIO_SECRET_KEY: minioadmin
    ports:
      - "8090:8090"

  mozhi-web:
    build:
      context: ../..
      dockerfile: mozhi-web/Dockerfile.dev
    container_name: mozhi-web-dev
    restart: unless-stopped
    depends_on:
      - mozhi-backend
    environment:
      VITE_API_BASE_URL: http://127.0.0.1:8090/api
    ports:
      - "5173:5173"
```

- [ ] **Step 3: Validate the local compose file**

Run:

```powershell
docker compose -f docs/dev-ops/docker-compose-environment.yml -f docs/dev-ops/docker-compose-local.yml config
```

Expected: merged compose renders without validation errors.

---

### Task 3: Document the local Docker workflow

**Files:**
- Modify: `README.md`
- Modify: `PROJECT_GUIDE.md`
- Modify: `docs/dev-ops/README.md`

- [ ] **Step 1: Add a local Docker quick-start**

Document:

```text
docker compose -f docs/dev-ops/docker-compose-environment.yml -f docs/dev-ops/docker-compose-local.yml up --build
```

Include exposed URLs for:
- frontend `http://127.0.0.1:5173`
- backend `http://127.0.0.1:8090`
- swagger `http://127.0.0.1:8090/swagger-ui/index.html`

- [ ] **Step 2: Explain why local compose does not include Nginx**

State explicitly:
- local goal is fastest startup and easiest debugging
- Nginx is reserved for a future production-oriented profile

- [ ] **Step 3: Document required env vars**

Document that local users may still need:
- `VITE_TURNSTILE_SITE_KEY`
- `MOZHI_AUTH_TURNSTILE_SECRET_KEY`

and explain that secrets must stay out of version control.

---

### Task 4: Verify the local Docker path end to end

**Files:**
- Test: `docs/dev-ops/docker-compose-local.yml`

- [ ] **Step 1: Validate compose render**

Run:

```powershell
docker compose -f docs/dev-ops/docker-compose-environment.yml -f docs/dev-ops/docker-compose-local.yml config
```

Expected: PASS

- [ ] **Step 2: Build backend and frontend images**

Run:

```powershell
docker compose -f docs/dev-ops/docker-compose-environment.yml -f docs/dev-ops/docker-compose-local.yml build
```

Expected: PASS

- [ ] **Step 3: Start the stack**

Run:

```powershell
docker compose -f docs/dev-ops/docker-compose-environment.yml -f docs/dev-ops/docker-compose-local.yml up -d
```

Expected: all services are up.

- [ ] **Step 4: Smoke check key endpoints**

Run:

```powershell
curl.exe http://127.0.0.1:8090/api/health
curl.exe http://127.0.0.1:5173
```

Expected:
- backend health returns success payload
- frontend returns HTML

- [ ] **Step 5: Run application regression checks**

Run:

```powershell
cd mozhi-web
npm run test
npm run lint
npm run build
cd ..\mozhi-backend
cmd /c ".\mvnw.cmd -q -pl mozhi-app -am test"
```

Expected: all pass.
