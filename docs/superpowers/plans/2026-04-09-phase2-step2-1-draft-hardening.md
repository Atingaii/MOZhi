# Phase 2 Step 2.1 Draft Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the existing Step 2.1 draft backend so it supports frozen lifecycle states, optimistic concurrency, paged draft listing, and conflict-safe mutation behavior.

**Architecture:** Keep the current six-module DDD slice intact. Add a new versioned draft contract across API, domain, repository, and HTTP layers, move mapper bean wiring back into MyBatis configuration, and close the lifecycle gap with domain-enforced editability rules. Use TDD throughout: fail on lifecycle leakage and stale-write races first, then implement the smallest production changes to make the tests pass.

**Tech Stack:** Spring Boot 3, Spring Security, MyBatis XML mappers, Flyway, H2 integration tests, Java 21 in containerized Maven, local Docker dev stack.

---

### Task 1: Add failing tests for lifecycle freeze, optimistic concurrency, and paged listing

**Files:**
- Create: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftEntityTest.java`
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftHttpIntegrationTest.java`

- [ ] **Step 1: Write a pure unit test for draft editability by state**

```java
class DraftEntityTest {

    @Test
    void should_allow_content_update_only_for_editable_states() {
        DraftEntity draft = new DraftEntity(1L, 7L, "t", "c", DraftStatusEnum.DRAFT, 0L, now, now);
        DraftEntity uploading = new DraftEntity(2L, 7L, "t", "c", DraftStatusEnum.UPLOADING, 0L, now, now);
        DraftEntity rejected = new DraftEntity(3L, 7L, "t", "c", DraftStatusEnum.REJECTED, 0L, now, now);

        assertThatCode(() -> draft.withContent("a", "b")).doesNotThrowAnyException();
        assertThatCode(() -> uploading.withContent("a", "b")).doesNotThrowAnyException();
        assertThatCode(() -> rejected.withContent("a", "b")).doesNotThrowAnyException();

        assertThatThrownBy(() -> new DraftEntity(4L, 7L, "t", "c", DraftStatusEnum.PENDING_REVIEW, 0L, now, now).withContent("a", "b"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("draft content is read only");
        assertThatThrownBy(() -> new DraftEntity(5L, 7L, "t", "c", DraftStatusEnum.PUBLISHED, 0L, now, now).withContent("a", "b"))
                .isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> new DraftEntity(6L, 7L, "t", "c", DraftStatusEnum.ARCHIVED, 0L, now, now).withContent("a", "b"))
                .isInstanceOf(BaseException.class);
    }
}
```

- [ ] **Step 2: Extend the HTTP integration test with stale-write and pagination expectations**

```java
@Test
void should_return_conflict_for_stale_update_and_transition_requests() throws Exception {
    String accessToken = registerAndLogin("versioned", "versioned@mozhi.dev", "Secret123!", "Versioned");
    long draftId = createDraft(accessToken, "Versioned draft", "Body").draftId();

    mockMvc.perform(put("/api/content/drafts/{draftId}", draftId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"title":"fresh","content":"fresh","expectedVersion":0}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(1));

    mockMvc.perform(post("/api/content/drafts/{draftId}/status", draftId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"targetStatus":"PENDING_REVIEW","expectedVersion":0}
                            """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("A0409"));
}

@Test
void should_page_and_filter_draft_list() throws Exception {
    String accessToken = registerAndLogin("pager", "pager@mozhi.dev", "Secret123!", "Pager");
    long draftId1 = createDraft(accessToken, "draft-1", "body-1").draftId();
    long draftId2 = createDraft(accessToken, "draft-2", "body-2").draftId();
    transitionDraft(accessToken, draftId2, "PENDING_REVIEW", 0L);

    mockMvc.perform(get("/api/content/drafts?page=1&pageSize=1&status=PENDING_REVIEW")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(1))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].status").value("PENDING_REVIEW"));
}
```

- [ ] **Step 3: Run the focused tests and confirm they fail for the right reasons**

Run from repo root:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace/mozhi-backend maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=DraftEntityTest,DraftHttpIntegrationTest" test
```

Expected:
- `DraftEntityTest` fails because `withContent(...)` still allows frozen states
- `DraftHttpIntegrationTest` fails because no `version`, no `409 conflict`, and no paged response shape exist yet

---

### Task 2: Add conflict error semantics and domain-level lifecycle restrictions

**Files:**
- Modify: `mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/ResponseCode.java`
- Modify: `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/GlobalExceptionHandler.java`
- Modify: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/DraftEntity.java`

- [ ] **Step 1: Add a dedicated conflict response code**

```java
public enum ResponseCode {

    SUCCESS("0000", "success"),
    BAD_REQUEST("A0400", "bad request"),
    UNAUTHORIZED("A0401", "unauthorized"),
    FORBIDDEN("A0403", "forbidden"),
    NOT_FOUND("A0404", "not found"),
    CONFLICT("A0409", "conflict"),
    AUTH_CHALLENGE_REQUIRED("A0410", "auth challenge required"),
    TOO_MANY_REQUESTS("A0429", "too many requests"),
    SYSTEM_ERROR("B0001", "system error");
}
```

- [ ] **Step 2: Map the new code to HTTP 409**

```java
private HttpStatus resolveStatus(String errorCode) {
    return switch (errorCode) {
        case "A0401" -> HttpStatus.UNAUTHORIZED;
        case "A0403" -> HttpStatus.FORBIDDEN;
        case "A0404" -> HttpStatus.NOT_FOUND;
        case "A0409" -> HttpStatus.CONFLICT;
        case "A0429" -> HttpStatus.TOO_MANY_REQUESTS;
        default -> HttpStatus.BAD_REQUEST;
    };
}
```

- [ ] **Step 3: Make `DraftEntity` enforce editable-state rules and carry `version`**

```java
public class DraftEntity {

    private final Long version;

    public DraftEntity withContent(String title, String content) {
        assertEditableForContentUpdate();
        return new DraftEntity(
                id,
                authorId,
                normalizeTitle(title),
                normalizeContent(content),
                status,
                version + 1,
                createdAt,
                LocalDateTime.now()
        );
    }

    public DraftEntity transitionTo(DraftStatusEnum targetStatus) {
        if (targetStatus == null || !canTransitionTo(targetStatus)) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "draft status transition is invalid");
        }
        return new DraftEntity(
                id,
                authorId,
                title,
                content,
                targetStatus,
                version + 1,
                createdAt,
                LocalDateTime.now()
        );
    }

    private void assertEditableForContentUpdate() {
        if (status == DraftStatusEnum.PENDING_REVIEW
                || status == DraftStatusEnum.PUBLISHED
                || status == DraftStatusEnum.ARCHIVED) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "draft content is read only in current status");
        }
    }
}
```

- [ ] **Step 4: Re-run the unit test to make sure lifecycle freeze is now green before touching persistence**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace/mozhi-backend maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=DraftEntityTest" test
```

Expected: `DraftEntityTest` passes, while HTTP conflict/pagination tests still fail.

---

### Task 3: Add versioned draft persistence and conditional writes

**Files:**
- Create: `mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V4__harden_draft_write_model.sql`
- Modify: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/adapter/repository/IDraftRepository.java`
- Modify: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java`
- Modify: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/po/DraftPO.java`
- Modify: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/DraftDao.java`
- Modify: `mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/DraftDao.xml`
- Modify: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/DraftRepositoryImpl.java`

- [ ] **Step 1: Add the Flyway migration for draft versioning**

```sql
ALTER TABLE `draft`
    ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `status`;

UPDATE `draft`
SET `version` = 0
WHERE `version` IS NULL;

CREATE INDEX `idx_draft_author_status_updated_at` ON `draft` (`author_id`, `status`, `updated_at`);
```

- [ ] **Step 2: Extend repository contracts to report conditional-write success**

```java
public interface IDraftRepository {

    Long save(DraftEntity draftEntity);

    boolean update(DraftEntity draftEntity, Long expectedVersion);

    boolean deleteById(Long draftId, Long expectedVersion);

    Optional<DraftEntity> findById(Long draftId);

    DraftPageResult findPageByAuthorId(Long authorId, DraftQuery draftQuery);
}
```

- [ ] **Step 3: Make MyBatis writes conditional on `version`**

```xml
<update id="update" parameterType="cn.zy.mozhi.infrastructure.dao.po.DraftPO">
    UPDATE `draft`
    SET title = #{title},
        content = #{content},
        status = #{status},
        version = #{version},
        updated_at = #{updatedAt}
    WHERE id = #{id}
      AND version = #{expectedVersion}
</update>

<delete id="deleteById">
    DELETE FROM `draft`
    WHERE id = #{id}
      AND version = #{expectedVersion}
</delete>
</update>
```

- [ ] **Step 4: Raise domain conflicts when conditional write count is zero**

```java
public DraftEntity updateMine(Long actorUserId, Long draftId, Long expectedVersion, String title, String content) {
    DraftEntity currentDraft = getMineById(actorUserId, draftId);
    DraftEntity updatedDraft = currentDraft.withContent(title, content);
    if (!draftRepository.update(updatedDraft, expectedVersion)) {
        throw new BaseException(ResponseCode.CONFLICT, "draft version is stale");
    }
    return updatedDraft;
}

public void deleteMine(Long actorUserId, Long draftId, Long expectedVersion) {
    DraftEntity draftEntity = getMineById(actorUserId, draftId);
    if (draftEntity.getStatus() == DraftStatusEnum.PUBLISHED) {
        throw new BaseException(ResponseCode.BAD_REQUEST, "published draft cannot be deleted");
    }
    if (!draftRepository.deleteById(draftEntity.getId(), expectedVersion)) {
        throw new BaseException(ResponseCode.CONFLICT, "draft version is stale");
    }
}
```

- [ ] **Step 5: Run the HTTP test again and confirm stale-write scenarios now return 409**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace/mozhi-backend maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=DraftHttpIntegrationTest" test
```

Expected: stale update / transition / delete assertions pass, while pagination/list DTO assertions may still fail until Task 4 lands.

---

### Task 4: Add paged/filterable draft listing and request validation

**Files:**
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftListPageDTO.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/valobj/DraftListQuery.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/valobj/DraftPageResult.java`
- Modify: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftUpdateRequestDTO.java`
- Modify: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftStatusTransitionRequestDTO.java`
- Modify: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftDetailDTO.java`
- Modify: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftSummaryDTO.java`
- Modify: `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/DraftController.java`
- Modify: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/DraftDao.java`
- Modify: `mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/DraftDao.xml`

- [ ] **Step 1: Add request DTO validation and expose `version` in response DTOs**

```java
public record DraftUpdateRequestDTO(
        @NotBlank String title,
        @NotBlank String content,
        @NotNull @Positive Long expectedVersion
) {}

public record DraftStatusTransitionRequestDTO(
        @NotBlank String targetStatus,
        @NotNull @Positive Long expectedVersion
) {}

public record DraftSummaryDTO(
        Long draftId,
        String title,
        String status,
        Long version,
        LocalDateTime updatedAt
) {}
```

- [ ] **Step 2: Add a small paged list response model**

```java
public record DraftListPageDTO(
        int page,
        int pageSize,
        long total,
        java.util.List<DraftSummaryDTO> items
) {}
```

- [ ] **Step 3: Update the controller to accept page/filter parameters and `@Valid` bodies**

```java
@GetMapping
public ApiResponse<DraftListPageDTO> listMine(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                              @RequestParam(defaultValue = "1") @Positive int page,
                                              @RequestParam(defaultValue = "20") @Positive @Max(100) int pageSize,
                                              @RequestParam(required = false) String status) {
    DraftPageResult pageResult = draftDomainService.listMine(tokenClaims.userId(), page, pageSize, status);
    return ApiResponse.success(new DraftListPageDTO(
            pageResult.page(),
            pageResult.pageSize(),
            pageResult.total(),
            pageResult.items().stream().map(this::toSummaryDTO).toList()
    ));
}
```

- [ ] **Step 4: Add paged query support in MyBatis**

```xml
<select id="selectPageByAuthorId" resultMap="DraftResultMap">
    SELECT id, author_id, title, content, status, version, created_at, updated_at
    FROM `draft`
    WHERE author_id = #{authorId}
      <if test="status != null">
        AND status = #{status}
      </if>
    ORDER BY updated_at DESC, id DESC
    LIMIT #{limit} OFFSET #{offset}
</select>

<select id="countByAuthorId" resultType="long">
    SELECT COUNT(*)
    FROM `draft`
    WHERE author_id = #{authorId}
      <if test="status != null">
        AND status = #{status}
      </if>
</select>
```

- [ ] **Step 5: Re-run the focused tests and make pagination/filtering green**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace/mozhi-backend maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=DraftEntityTest,DraftHttpIntegrationTest" test
```

Expected: lifecycle, conflict, and paged-list tests all pass.

---

### Task 5: Unify config style, run full regression, and validate real dev runtime

**Files:**
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/MybatisConfiguration.java`
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java`
- Modify only if needed by failures: the files above

- [ ] **Step 1: Move `DraftDao` mapper bean into `MybatisConfiguration`**

```java
@Bean
public MapperFactoryBean<DraftDao> draftDao(SqlSessionFactory sqlSessionFactory) {
    MapperFactoryBean<DraftDao> mapperFactoryBean = new MapperFactoryBean<>(DraftDao.class);
    mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
    return mapperFactoryBean;
}
```

- [ ] **Step 2: Remove mapper-bean concerns from `DomainConfiguration` so it only wires repository and service beans**

```java
@Bean
@ConditionalOnProperty(name = "mozhi.mybatis.enabled", havingValue = "true", matchIfMissing = true)
public IDraftRepository draftRepository(DraftDao draftDao) {
    return new DraftRepositoryImpl(draftDao);
}

@Bean
@ConditionalOnProperty(name = "mozhi.mybatis.enabled", havingValue = "true", matchIfMissing = true)
public DraftDomainService draftDomainService(IDraftRepository draftRepository) {
    return new DraftDomainService(draftRepository);
}
```

- [ ] **Step 3: Run full backend regression**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace/mozhi-backend maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am test
```

Expected: PASS, including `HttpSurfaceIntegrationTest`.

- [ ] **Step 4: Rebuild the local backend container and run a real smoke flow against the new versioned contract**

Run:

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-local.yml up -d --build mozhi-backend
```

Then run:

```powershell
$suffix = Get-Date -Format 'yyyyMMddHHmmss'
$username = "hardening$suffix"
$email = "hardening$suffix@mozhi.dev"
$password = "Secret123!"

$registerBody = @{ username = $username; email = $email; password = $password; nickname = "Hardening Smoke" } | ConvertTo-Json
Invoke-RestMethod -Uri 'http://127.0.0.1:8090/api/user/register' -Method Post -ContentType 'application/json' -Body $registerBody | Out-Null

$loginBody = @{ identifier = $username; password = $password } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://127.0.0.1:8090/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }

$create = Invoke-RestMethod -Uri 'http://127.0.0.1:8090/api/content/drafts' -Method Post -ContentType 'application/json' -Headers $headers -Body (@{ title = 'hardening smoke'; content = 'body' } | ConvertTo-Json)
$draftId = $create.data.draftId
$version = $create.data.version

$update = Invoke-RestMethod -Uri "http://127.0.0.1:8090/api/content/drafts/$draftId" -Method Put -ContentType 'application/json' -Headers $headers -Body (@{ title = 'hardening smoke updated'; content = 'body updated'; expectedVersion = $version } | ConvertTo-Json)
$nextVersion = $update.data.version

Invoke-RestMethod -Uri "http://127.0.0.1:8090/api/content/drafts?page=1&pageSize=10&status=DRAFT" -Method Get -Headers $headers | Out-Null
Invoke-RestMethod -Uri "http://127.0.0.1:8090/api/content/drafts/$draftId/status" -Method Post -ContentType 'application/json' -Headers $headers -Body (@{ targetStatus = 'PENDING_REVIEW'; expectedVersion = $nextVersion } | ConvertTo-Json) | Out-Null
```

Expected:
- register/login succeed
- create returns `version = 0`
- update returns incremented `version`
- paged list succeeds
- status transition succeeds with the next expected version

- [ ] **Step 5: Commit the hardening slice**

```bash
git add docs/superpowers/specs/2026-04-09-phase2-step2-1-draft-hardening-design.md
git add docs/superpowers/plans/2026-04-09-phase2-step2-1-draft-hardening.md
git add mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/ResponseCode.java
git add mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto
git add mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content
git add mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao
git add mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/DraftRepositoryImpl.java
git add mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/DraftController.java
git add mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/GlobalExceptionHandler.java
git add mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/MybatisConfiguration.java
git add mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java
git add mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V4__harden_draft_write_model.sql
git add mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/DraftDao.xml
git add mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftEntityTest.java
git add mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftHttpIntegrationTest.java
git commit -m "feat: harden draft write model for step2-1"
```

---

### Self-Review Checklist

- [ ] Frozen lifecycle states are explicitly covered by tests.
- [ ] Every mutating endpoint uses `expectedVersion`.
- [ ] `409 conflict` is a first-class API result, not a generic `400`.
- [ ] Draft list now has page/pageSize/status without adding unrelated search/sort complexity.
- [ ] Mapper bean wiring is consistent with the existing `MybatisConfiguration` pattern.
- [ ] The real dev-runtime smoke flow matches the final versioned API contract.
