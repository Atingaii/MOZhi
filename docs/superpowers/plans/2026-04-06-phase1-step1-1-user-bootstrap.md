# Phase 1 Step 1.1 User Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first real user-domain vertical slice: user persistence, registration, and user profile query, while keeping the current frontend visual style unchanged.

**Architecture:** Keep the repository inside the existing six-module DDD scaffold. Domain owns the user aggregate, repository contract, and registration/query rules; infrastructure owns MyBatis DAO, mapper XML, and password encoder port; trigger exposes `/api/user/register` and `/api/user/{userId}` with unified responses. Tests stay focused and gated so each capability turns green before the next one starts.

**Tech Stack:** Java 21, Spring Boot 3.3, MyBatis, Flyway, Spring Security BCrypt, H2 test database, JUnit 5, MockMvc

---

### Task 1: Add failing persistence and HTTP tests

**Files:**
- Create: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/UserRepositoryIntegrationTest.java`
- Create: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/UserHttpIntegrationTest.java`
- Modify: `mozhi-backend/mozhi-app/src/test/resources` only if a user-specific test property override is required

- [ ] Step 1: Write a repository integration test for create/find/duplicate lookup against an H2-backed schema.
- [ ] Step 2: Run only that test and confirm it fails because user repository beans, table migration, or mapper classes do not exist yet.
- [ ] Step 3: Write an HTTP integration test for `POST /api/user/register` success, duplicate rejection, bad request rejection, and `GET /api/user/{userId}` success.
- [ ] Step 4: Run only the HTTP test and confirm it fails because the user controller and service path do not exist yet.

**Gate command:** `.\mvnw.cmd -q -pl mozhi-app -Dtest=UserRepositoryIntegrationTest,UserHttpIntegrationTest test`

### Task 2: Add user domain and shared contracts

**Files:**
- Create: `mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/UserStatusEnum.java`
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/UserProfileDTO.java`
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/UserRegisterRequestDTO.java`
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/UserRegisterResponseDTO.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/model/entity/UserEntity.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/adapter/repository/IUserRepository.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/adapter/port/IUserPasswordEncoderPort.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/service/UserDomainService.java`

- [ ] Step 1: Add the smallest user status enum and API DTOs required by the tests.
- [ ] Step 2: Add the user entity and repository/password encoder contracts.
- [ ] Step 3: Implement minimal domain service methods for register and query, including username/email uniqueness checks and BCrypt-based password hashing through the encoder port.
- [ ] Step 4: Re-run the focused tests and confirm they still fail at infrastructure or HTTP wiring boundaries, not at compile time.

**Gate command:** `.\mvnw.cmd -q -pl mozhi-app -Dtest=UserRepositoryIntegrationTest,UserHttpIntegrationTest test`

### Task 3: Add migration and infrastructure persistence

**Files:**
- Create: `mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V2__create_user_table.sql`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/UserDao.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/po/UserPO.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/UserRepositoryImpl.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/UserPasswordEncoderPortImpl.java`
- Create: `mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/UserDao.xml`

- [ ] Step 1: Add the Flyway migration for the `user` table with unique constraints on username and email.
- [ ] Step 2: Add MyBatis PO/DAO/mapper and repository implementation for insert and lookup operations.
- [ ] Step 3: Add the BCrypt password encoder port implementation.
- [ ] Step 4: Run the repository integration test and make it pass before touching HTTP entry code.

**Gate command:** `.\mvnw.cmd -q -pl mozhi-app -Dtest=UserRepositoryIntegrationTest test`

### Task 4: Add trigger-layer HTTP endpoints

**Files:**
- Create: `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/UserController.java`
- Modify: `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/GlobalExceptionHandler.java` only if user-specific exceptions need distinct mapping
- Modify: existing user tests if response details need tightening after the controller shape is final

- [ ] Step 1: Add the user controller with `POST /api/user/register` and `GET /api/user/{userId}` using unified responses.
- [ ] Step 2: Add minimal request validation in the controller/service path for blank username, blank email, blank password, and malformed email.
- [ ] Step 3: Re-run the HTTP integration test until registration success, duplicate rejection, invalid input rejection, and query success all pass.
- [ ] Step 4: Re-run both focused tests together and keep them green.

**Gate command:** `.\mvnw.cmd -q -pl mozhi-app -Dtest=UserRepositoryIntegrationTest,UserHttpIntegrationTest test`

### Task 5: Regression checks and frontend contract sanity

**Files:**
- Modify only if required: `mozhi-web/src/api/modules/auth.ts`
- Modify only if required: `mozhi-web/src/pages/Auth/index.tsx`

- [ ] Step 1: Run the existing backend Phase 0 test set plus the new user tests to catch regressions in health, Flyway, and unified response behavior.
- [ ] Step 2: Run frontend lint and build to prove that untouched UI surfaces still compile cleanly.
- [ ] Step 3: Only if backend contract naming requires it, apply the smallest frontend type-alignment patch without changing the current page style.
- [ ] Step 4: Re-run the frontend checks if any frontend file changed.

**Gate commands:**
- `.\mvnw.cmd -q -pl mozhi-app -am test`
- `npm run lint`
- `npm run build`
