# Phase 2 Step 2.1 Content Draft Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first backend-only content-domain vertical slice by adding authenticated draft CRUD, draft status transitions, and foundational content schema support for later publishing work.

**Architecture:** Reuse the existing six-module DDD structure. `domain/content` owns the draft aggregate, repository contract, and lifecycle rules; `infrastructure` owns MyBatis persistence and Flyway schema; `trigger/http` exposes `/api/content/drafts` endpoints; `app/config` wires the new domain service into the current bean graph. Security stays server-owned through authenticated principal checks and ownership-aware repository access.

**Tech Stack:** Java 21, Spring Boot 3.3, MyBatis, Flyway, Spring Security JWT principal, H2 MySQL-mode integration tests, MockMvc, Docker Compose local middleware, Maven Wrapper

---

### File Structure

**Create**
- `mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/DraftStatusEnum.java`
- `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftCreateRequestDTO.java`
- `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftUpdateRequestDTO.java`
- `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftStatusTransitionRequestDTO.java`
- `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftDetailDTO.java`
- `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftSummaryDTO.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/DraftEntity.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/adapter/repository/IDraftRepository.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java`
- `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/DraftDao.java`
- `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/po/DraftPO.java`
- `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/DraftRepositoryImpl.java`
- `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/DraftController.java`
- `mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/DraftDao.xml`
- `mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V3__create_content_tables.sql`
- `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftFlywayIntegrationTest.java`
- `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftHttpIntegrationTest.java`

**Modify**
- `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java`

**Reference**
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/service/UserDomainService.java`
- `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/UserRepositoryImpl.java`
- `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/UserController.java`
- `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/UserHttpIntegrationTest.java`

---

### Task 1: Add failing migration and draft HTTP tests

**Files:**
- Create: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftFlywayIntegrationTest.java`
- Create: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftHttpIntegrationTest.java`

- [ ] **Step 1: Write the failing Flyway integration test for the three foundational content tables**

```java
@SpringBootTest(
        classes = DraftFlywayIntegrationTest.DraftFlywayTestApplication.class,
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.url=jdbc:h2:mem:mozhi-draft-flyway;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        }
)
@ActiveProfiles("test")
class DraftFlywayIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpringBootApplication(scanBasePackages = "cn.zy.mozhi")
    static class DraftFlywayTestApplication {}

    @Test
    void should_create_foundational_content_tables() {
        Integer draftCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'draft'",
                Integer.class
        );
        Integer noteCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'note'",
                Integer.class
        );
        Integer mediaRefCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'media_ref'",
                Integer.class
        );

        assertThat(draftCount).isEqualTo(1);
        assertThat(noteCount).isEqualTo(1);
        assertThat(mediaRefCount).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run the Flyway test and confirm it fails because `V3__create_content_tables.sql` does not exist yet**

Run from `mozhi-backend/`:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -Dtest=DraftFlywayIntegrationTest test
```

Expected: FAIL because one or more of `draft`, `note`, `media_ref` are missing.

- [ ] **Step 3: Write the failing authenticated HTTP integration test for draft CRUD and status transitions**

```java
@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.url=jdbc:h2:mem:mozhi-draft-http;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "mozhi.auth.challenge.provider=test",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
        }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DraftHttpIntegrationTest {

    @Test
    void should_create_list_update_transition_and_delete_own_draft() throws Exception {
        String accessToken = registerAndLogin("author", "author@mozhi.dev", "Secret123!");

        MvcResult createResult = mockMvc.perform(post("/api/content/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"First draft","content":"Draft body"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        long draftId = readId(createResult);

        mockMvc.perform(get("/api/content/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].draftId").value(draftId));

        mockMvc.perform(put("/api/content/drafts/{draftId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated title","content":"Updated body"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated title"));

        mockMvc.perform(post("/api/content/drafts/{draftId}/status", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus":"PENDING_REVIEW"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        mockMvc.perform(delete("/api/content/drafts/{draftId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void should_hide_foreign_owned_draft_and_reject_illegal_transitions() throws Exception {
        String ownerToken = registerAndLogin("owner", "owner@mozhi.dev", "Secret123!");
        String strangerToken = registerAndLogin("stranger", "stranger@mozhi.dev", "Secret123!");
        long draftId = createDraft(ownerToken, "Private draft", "Private body");

        mockMvc.perform(get("/api/content/drafts/{draftId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/content/drafts/{draftId}/status", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus":"PUBLISHED"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 4: Run the draft HTTP test and confirm it fails because the content-domain DTOs, controller, migration, and repository do not exist yet**

Run from `mozhi-backend/`:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -Dtest=DraftHttpIntegrationTest test
```

Expected: FAIL with missing endpoint, missing beans, or missing migration errors.

---

### Task 2: Add shared content contracts and status enum

**Files:**
- Create: `mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/DraftStatusEnum.java`
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftCreateRequestDTO.java`
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftUpdateRequestDTO.java`
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftStatusTransitionRequestDTO.java`
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftDetailDTO.java`
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftSummaryDTO.java`

- [ ] **Step 1: Add the draft status enum with the full lifecycle states defined in the spec**

```java
public enum DraftStatusEnum {
    DRAFT,
    UPLOADING,
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED,
    ARCHIVED
}
```

- [ ] **Step 2: Add the smallest request/response DTOs required by the HTTP tests**

```java
public record DraftCreateRequestDTO(String title, String content) {}

public record DraftUpdateRequestDTO(String title, String content) {}

public record DraftStatusTransitionRequestDTO(String targetStatus) {}

public record DraftSummaryDTO(
        Long draftId,
        String title,
        String status,
        java.time.Instant updatedAt
) {}

public record DraftDetailDTO(
        Long draftId,
        Long authorId,
        String title,
        String content,
        String status,
        java.time.Instant createdAt,
        java.time.Instant updatedAt
) {}
```

- [ ] **Step 3: Re-run the focused HTTP test to confirm the failure moves deeper into domain or persistence wiring**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -Dtest=DraftHttpIntegrationTest test
```

Expected: compile succeeds for DTO/type references, but test still fails because the content-domain implementation is not wired yet.

---

### Task 3: Add the content-domain aggregate, repository contract, and service rules

**Files:**
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/DraftEntity.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/adapter/repository/IDraftRepository.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java`
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java`

- [ ] **Step 1: Define the draft repository contract around ownership-aware access**

```java
public interface IDraftRepository {

    Long save(DraftEntity draftEntity);

    void update(DraftEntity draftEntity);

    void deleteById(Long draftId);

    java.util.Optional<DraftEntity> findById(Long draftId);

    java.util.List<DraftEntity> findByAuthorId(Long authorId);
}
```

- [ ] **Step 2: Add the draft aggregate with normalization, immutable update helpers, and transition checks**

```java
public class DraftEntity {

    public DraftEntity withContent(String title, String content) {
        return new DraftEntity(
                this.id,
                this.authorId,
                normalizeTitle(title),
                normalizeContent(content),
                this.status,
                this.createdAt,
                java.time.Instant.now()
        );
    }

    public DraftEntity transitionTo(DraftStatusEnum targetStatus) {
        if (!canTransitionTo(targetStatus)) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "draft status transition is invalid");
        }
        return new DraftEntity(
                this.id,
                this.authorId,
                this.title,
                this.content,
                targetStatus,
                this.createdAt,
                java.time.Instant.now()
        );
    }
}
```

- [ ] **Step 3: Implement the domain service with server-side ownership checks and delete/transition rules**

```java
public class DraftDomainService {

    public DraftEntity getMineById(Long actorUserId, Long draftId) {
        DraftEntity draftEntity = draftRepository.findById(requirePositive(draftId, "draftId"))
                .orElseThrow(() -> new BaseException(ResponseCode.NOT_FOUND, "draft not found"));
        if (!draftEntity.getAuthorId().equals(requirePositive(actorUserId, "actorUserId"))) {
            throw new BaseException(ResponseCode.NOT_FOUND, "draft not found");
        }
        return draftEntity;
    }

    public void deleteMine(Long actorUserId, Long draftId) {
        DraftEntity draftEntity = getMineById(actorUserId, draftId);
        if (draftEntity.getStatus() == DraftStatusEnum.PUBLISHED) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "published draft cannot be deleted");
        }
        draftRepository.deleteById(draftId);
    }
}
```

- [ ] **Step 4: Wire `DraftDomainService` into `DomainConfiguration`**

```java
@Bean
@ConditionalOnBean(IDraftRepository.class)
public DraftDomainService draftDomainService(IDraftRepository draftRepository) {
    return new DraftDomainService(draftRepository);
}
```

- [ ] **Step 5: Run the draft HTTP test again and confirm the remaining failures are now at migration, DAO, or controller level**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -Dtest=DraftHttpIntegrationTest test
```

Expected: failures should now point to missing DB schema, MyBatis mapping, or missing HTTP entry code rather than missing domain types.

---

### Task 4: Add Flyway migration and MyBatis persistence

**Files:**
- Create: `mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V3__create_content_tables.sql`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/DraftDao.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/po/DraftPO.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/DraftRepositoryImpl.java`
- Create: `mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/DraftDao.xml`

- [ ] **Step 1: Add the Flyway migration for `draft`, `note`, and `media_ref` with foundational indexes and foreign keys**

```sql
CREATE TABLE `draft` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `author_id` BIGINT NOT NULL,
    `title` VARCHAR(128) NOT NULL,
    `content` TEXT NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_draft_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`)
);

CREATE INDEX `idx_draft_author_updated_at` ON `draft` (`author_id`, `updated_at`);
CREATE INDEX `idx_draft_author_status` ON `draft` (`author_id`, `status`);

CREATE TABLE `note` (...);
CREATE TABLE `media_ref` (...);
```

- [ ] **Step 2: Add the PO, DAO, and mapper for insert/update/delete/find/list operations**

```java
@Mapper
public interface DraftDao {

    int insert(DraftPO draftPO);

    int update(DraftPO draftPO);

    int deleteById(@Param("id") Long id);

    DraftPO selectById(@Param("id") Long id);

    java.util.List<DraftPO> selectByAuthorId(@Param("authorId") Long authorId);
}
```

```xml
<mapper namespace="cn.zy.mozhi.infrastructure.dao.DraftDao">
    <resultMap id="draftPOResultMap" type="cn.zy.mozhi.infrastructure.dao.po.DraftPO">
        <id column="id" property="id"/>
        <result column="author_id" property="authorId"/>
        <result column="title" property="title"/>
        <result column="content" property="content"/>
        <result column="status" property="status"/>
        <result column="created_at" property="createdAt"/>
        <result column="updated_at" property="updatedAt"/>
    </resultMap>
</mapper>
```

- [ ] **Step 3: Implement the repository adapter following the existing user repository mapping style**

```java
@Repository
@ConditionalOnBean(DraftDao.class)
public class DraftRepositoryImpl implements IDraftRepository {

    @Override
    public java.util.List<DraftEntity> findByAuthorId(Long authorId) {
        return draftDao.selectByAuthorId(authorId).stream().map(this::toEntity).toList();
    }
}
```

- [ ] **Step 4: Run the Flyway test and make it pass before touching the HTTP controller**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -Dtest=DraftFlywayIntegrationTest test
```

Expected: PASS.

---

### Task 5: Add the trigger-layer draft controller and make the HTTP tests green

**Files:**
- Create: `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/DraftController.java`
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftHttpIntegrationTest.java` only if assertions need tightening once the final response shape is fixed

- [ ] **Step 1: Add the authenticated draft controller with isolated content-update and status-transition endpoints**

```java
@RestController
@RequestMapping("/api/content/drafts")
@ConditionalOnBean(DraftDomainService.class)
public class DraftController {

    @PostMapping
    public ApiResponse<DraftDetailDTO> create(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                              @RequestBody DraftCreateRequestDTO requestDTO) {
        DraftEntity draftEntity = draftDomainService.create(
                tokenClaims.userId(),
                requestDTO.title(),
                requestDTO.content()
        );
        return ApiResponse.success(toDetailDTO(draftEntity));
    }

    @PostMapping("/{draftId}/status")
    public ApiResponse<DraftDetailDTO> transition(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                  @PathVariable("draftId") Long draftId,
                                                  @RequestBody DraftStatusTransitionRequestDTO requestDTO) {
        DraftEntity draftEntity = draftDomainService.transitionMineStatus(
                tokenClaims.userId(),
                draftId,
                requestDTO.targetStatus()
        );
        return ApiResponse.success(toDetailDTO(draftEntity));
    }
}
```

- [ ] **Step 2: Implement list, get, update, and delete endpoints with owner-bound behavior**

```java
@GetMapping
public ApiResponse<java.util.List<DraftSummaryDTO>> listMine(@AuthenticationPrincipal AuthTokenClaims tokenClaims) {
    return ApiResponse.success(
            draftDomainService.listMine(tokenClaims.userId()).stream().map(this::toSummaryDTO).toList()
    );
}

@DeleteMapping("/{draftId}")
public ApiResponse<Void> delete(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                @PathVariable("draftId") Long draftId) {
    draftDomainService.deleteMine(tokenClaims.userId(), draftId);
    return ApiResponse.success();
}
```

- [ ] **Step 3: Run the draft HTTP tests until all create/list/get/update/transition/delete and ownership scenarios pass**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -Dtest=DraftHttpIntegrationTest test
```

Expected: PASS.

- [ ] **Step 4: Re-run the Flyway and draft HTTP tests together**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -Dtest=DraftFlywayIntegrationTest,DraftHttpIntegrationTest test
```

Expected: PASS.

---

### Task 6: Run full backend regression and a real-environment smoke flow

**Files:**
- Modify only if required by failures: the files created above
- Do not change frontend files in this task

- [ ] **Step 1: Run the full backend test suite in Java 21 containerized Maven**

Run from `mozhi-backend/`:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am test
```

Expected: PASS.

- [ ] **Step 2: Bring up the local middleware stack if it is not already running**

Run from repo root:

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml up -d
```

Expected: MySQL, Redis, Kafka, MinIO are healthy or already running.

- [ ] **Step 3: Start or restart the backend against the persisted local environment**

Run:

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-local.yml up -d --build mozhi-backend
```

Expected: `mozhi-backend-dev` restarts on `8090` and Flyway applies `V3`.

- [ ] **Step 4: Smoke test the real environment draft flow with authenticated HTTP requests**

Run:

```powershell
$registerBody = '{"username":"draft_owner","email":"draft_owner@mozhi.dev","password":"Secret123!","nickname":"Draft Owner"}'
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8090/api/user/register' -ContentType 'application/json' -Body $registerBody | Out-Null

$loginBody = '{"identifier":"draft_owner","password":"Secret123!"}'
$loginResponse = Invoke-WebRequest -Method Post -Uri 'http://127.0.0.1:8090/api/auth/login' -ContentType 'application/json' -Body $loginBody -SessionVariable draftSession
$accessToken = (($loginResponse.Content | ConvertFrom-Json).data.accessToken)
$headers = @{ Authorization = "Bearer $accessToken" }

$createBody = '{"title":"Smoke Draft","content":"Created in local docker runtime"}'
$createResponse = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8090/api/content/drafts' -Headers $headers -ContentType 'application/json' -Body $createBody
$draftId = $createResponse.data.draftId

Invoke-RestMethod -Method Get -Uri 'http://127.0.0.1:8090/api/content/drafts' -Headers $headers
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8090/api/content/drafts/$draftId/status" -Headers $headers -ContentType 'application/json' -Body '{"targetStatus":"PENDING_REVIEW"}'
```

Expected:
- register/login succeed
- draft create succeeds
- draft list returns the created draft
- status transition to `PENDING_REVIEW` succeeds

- [ ] **Step 5: Commit only the Step 2.1 content slice files**

```bash
git add mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/DraftStatusEnum.java
git add mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto
git add mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content
git add mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/DraftDao.java
git add mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/po/DraftPO.java
git add mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/DraftRepositoryImpl.java
git add mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/DraftController.java
git add mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V3__create_content_tables.sql
git add mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/DraftDao.xml
git add mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java
git add mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftFlywayIntegrationTest.java
git add mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftHttpIntegrationTest.java
git commit -m "feat(content): bootstrap draft domain"
```

---

### Self-Review Checklist

- [ ] `draft`, `note`, and `media_ref` are all covered by migration tasks.
- [ ] Ownership checks and `404` anti-enumeration behavior are explicitly tested.
- [ ] Status transitions are isolated from content updates.
- [ ] Real-environment smoke testing is included, not only H2 tests.
- [ ] No task assumes frontend work or later Phase 2 behavior.
