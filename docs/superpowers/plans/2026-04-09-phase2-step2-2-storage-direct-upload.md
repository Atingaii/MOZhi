# Step 2.2 Storage Direct Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-minded backend-only direct upload flow for draft media using generic storage presign plus content-side confirm and `media_ref` persistence.

**Architecture:** Keep storage capability generic and provider-oriented inside `domain/storage`, then let `domain/content` own draft binding and list queries. `presign` returns an opaque `uploadTicket`, provider adapters resolve upload/public URLs and inspect object metadata, and content confirm writes `media_ref` only after ownership, draft-state, and object-inspection checks pass.

**Tech Stack:** Spring Boot 3, MyBatis XML mappers, Flyway, MinIO/local mock storage adapters, JWT-based upload ticket, MockMvc integration tests, H2/MySQL compatibility, Dockerized Maven verification.

---

## File Structure

**Create**
- `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/StoragePresignRequestDTO.java`
- `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/StoragePresignResponseDTO.java`
- `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftMediaConfirmRequestDTO.java`
- `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftMediaDTO.java`
- `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftMediaListDTO.java`
- `mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/StorageUploadPurposeEnum.java`
- `mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/StorageMediaTypeEnum.java`
- `mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/MediaUploadStatusEnum.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/adapter/port/IStorageUploadTicketPort.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/adapter/port/IStorageObjectInspectPort.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/model/valobj/StorageUploadTicketClaims.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/model/valobj/StorageObjectInspection.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/adapter/repository/IMediaRefRepository.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/MediaRefEntity.java`
- `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/JwtStorageUploadTicketPortImpl.java`
- `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/MinioStorageObjectInspectPortImpl.java`
- `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/LocalStorageObjectInspectPortImpl.java`
- `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/MediaRefRepositoryImpl.java`
- `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/MediaRefDao.java`
- `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/po/MediaRefPO.java`
- `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/StorageController.java`
- `mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V5__evolve_media_ref_for_direct_upload.sql`
- `mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/MediaRefDao.xml`
- `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/StorageDomainServiceTest.java`
- `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/StorageHttpIntegrationTest.java`

**Modify**
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/service/StorageDomainService.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/DraftEntity.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/adapter/port/IStoragePresignPort.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/model/valobj/StoragePresignedUpload.java`
- `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java`
- `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/DraftController.java`
- `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/UserController.java`
- `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/LocalStoragePresignPortImpl.java`
- `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/MinioStoragePresignPortImpl.java`
- `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/StorageProperties.java`
- `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/StorageConfiguration.java`
- `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java`
- `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/MybatisConfiguration.java`
- `mozhi-backend/mozhi-app/src/main/resources/application.yml`
- `mozhi-backend/mozhi-app/src/main/resources/application-dev.yml`
- `mozhi-backend/mozhi-app/src/main/resources/application-test.yml`
- `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftFlywayIntegrationTest.java`
- `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftHttpIntegrationTest.java`
- `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/BackendRuntimeEnvironmentVerifierTest.java`

### Task 1: Evolve Schema and API Contracts

**Files:**
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/StoragePresignRequestDTO.java`
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/StoragePresignResponseDTO.java`
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftMediaConfirmRequestDTO.java`
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftMediaDTO.java`
- Create: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftMediaListDTO.java`
- Create: `mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/StorageUploadPurposeEnum.java`
- Create: `mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/StorageMediaTypeEnum.java`
- Create: `mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/MediaUploadStatusEnum.java`
- Create: `mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V5__evolve_media_ref_for_direct_upload.sql`
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftFlywayIntegrationTest.java`

- [ ] **Step 1: Write the failing Flyway assertions for the evolved `media_ref` schema**

```java
Integer storageProviderColumnCount = jdbcTemplate.queryForObject(
        "select count(*) from information_schema.columns where table_name = 'media_ref' and column_name = 'storage_provider'",
        Integer.class
);
Integer uploadStatusColumnCount = jdbcTemplate.queryForObject(
        "select count(*) from information_schema.columns where table_name = 'media_ref' and column_name = 'upload_status'",
        Integer.class
);
Integer sizeBytesColumnCount = jdbcTemplate.queryForObject(
        "select count(*) from information_schema.columns where table_name = 'media_ref' and column_name = 'size_bytes'",
        Integer.class
);

assertThat(storageProviderColumnCount).isEqualTo(1);
assertThat(uploadStatusColumnCount).isEqualTo(1);
assertThat(sizeBytesColumnCount).isEqualTo(1);
```

- [ ] **Step 2: Run the Flyway test and verify it fails because the columns do not exist yet**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -v "$env:USERPROFILE\.m2:/root/.m2" -w /workspace/mozhi-backend maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am "-Dtest=DraftFlywayIntegrationTest" test
```

Expected: FAIL with an assertion showing the new `media_ref` columns are missing.

- [ ] **Step 3: Add the migration and public API DTO/enums**

```sql
ALTER TABLE `media_ref`
    ADD COLUMN `storage_provider` VARCHAR(32) NOT NULL DEFAULT 'LOCAL' AFTER `note_id`,
    ADD COLUMN `bucket_name` VARCHAR(128) NOT NULL DEFAULT 'mozhi-assets' AFTER `storage_provider`,
    ADD COLUMN `file_name` VARCHAR(255) NOT NULL DEFAULT 'upload.bin' AFTER `bucket_name`,
    ADD COLUMN `size_bytes` BIGINT NOT NULL DEFAULT 0 AFTER `file_name`,
    ADD COLUMN `etag` VARCHAR(128) NULL AFTER `size_bytes`,
    ADD COLUMN `upload_status` VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED' AFTER `etag`,
    ADD COLUMN `bound_at` TIMESTAMP NULL AFTER `upload_status`;

CREATE UNIQUE INDEX `uk_media_ref_provider_bucket_object`
    ON `media_ref` (`storage_provider`, `bucket_name`, `object_key`);
```

```java
public record StoragePresignRequestDTO(
        @NotBlank(message = "purpose must not be blank") String purpose,
        @NotNull(message = "draftId must not be null") Long draftId,
        @NotBlank(message = "fileName must not be blank") String fileName,
        @NotBlank(message = "contentType must not be blank") String contentType,
        @NotBlank(message = "mediaType must not be blank") String mediaType,
        @NotNull(message = "declaredSizeBytes must not be null") @Positive(message = "declaredSizeBytes must be greater than 0") Long declaredSizeBytes
){}
```

```java
public record StoragePresignResponseDTO(
        String objectKey,
        String uploadUrl,
        String publicUrl,
        String httpMethod,
        String uploadTicket,
        Instant expiresAt
){}
```

- [ ] **Step 4: Re-run the Flyway test to verify the schema contract now passes**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit the schema/API contract slice**

```powershell
git add mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/StoragePresignRequestDTO.java mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/StoragePresignResponseDTO.java mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftMediaConfirmRequestDTO.java mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftMediaDTO.java mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/DraftMediaListDTO.java mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/StorageUploadPurposeEnum.java mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/StorageMediaTypeEnum.java mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/MediaUploadStatusEnum.java mozhi-backend/mozhi-app/src/main/resources/db/migration/platform/V5__evolve_media_ref_for_direct_upload.sql mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftFlywayIntegrationTest.java
git commit -m "feat: evolve media ref schema for direct upload"
```

### Task 2: Build Generic Storage Policy, Ticket, and Inspection Abstractions

**Files:**
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/adapter/port/IStorageUploadTicketPort.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/adapter/port/IStorageObjectInspectPort.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/model/valobj/StorageUploadTicketClaims.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/model/valobj/StorageObjectInspection.java`
- Modify: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/adapter/port/IStoragePresignPort.java`
- Modify: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/model/valobj/StoragePresignedUpload.java`
- Modify: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/service/StorageDomainService.java`
- Create: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/StorageDomainServiceTest.java`

- [ ] **Step 1: Write failing storage domain tests for policy, object key generation, and upload tickets**

```java
@Test
void should_presign_draft_image_upload_with_ticket() {
    StoragePresignedUpload upload = storageDomainService.presignDraftMediaUpload(
            101L, 501L, "cover.png", "image/png", "IMAGE", "DRAFT_MEDIA", 2048L
    );

    assertThat(upload.objectKey()).startsWith("drafts/101/501/");
    assertThat(upload.uploadTicket()).isNotBlank();
    assertThat(upload.storageProvider()).isEqualTo("LOCAL");
}

@Test
void should_reject_non_image_content_type_for_step2_2() {
    assertThatThrownBy(() -> storageDomainService.presignDraftMediaUpload(
            101L, 501L, "manual.pdf", "application/pdf", "IMAGE", "DRAFT_MEDIA", 2048L
    )).isInstanceOf(BaseException.class);
}
```

- [ ] **Step 2: Run the new unit test and verify it fails because the domain abstractions do not exist yet**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -v "$env:USERPROFILE\.m2:/root/.m2" -w /workspace/mozhi-backend maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am "-Dtest=StorageDomainServiceTest" test
```

Expected: FAIL with compilation errors for missing ports, ticket claims, or `presignDraftMediaUpload`.

- [ ] **Step 3: Implement generic storage value objects, ports, and domain policy**

```java
public interface IStorageUploadTicketPort {

    String issue(StorageUploadTicketClaims claims, Duration ttl);

    StorageUploadTicketClaims verify(String uploadTicket);
}
```

```java
public record StorageObjectInspection(
        String storageProvider,
        String bucketName,
        String objectKey,
        String contentType,
        long sizeBytes,
        String etag,
        boolean exists
) {}
```

```java
public record StoragePresignedUpload(
        String objectKey,
        String uploadUrl,
        String publicUrl,
        String httpMethod,
        String uploadTicket,
        String storageProvider,
        String bucketName,
        Instant expiresAt
) {}
```

```java
public StoragePresignedUpload presignDraftMediaUpload(Long userId,
                                                      Long draftId,
                                                      String fileName,
                                                      String contentType,
                                                      String mediaType,
                                                      String purpose,
                                                      Long declaredSizeBytes) {
    Long normalizedUserId = requireUserId(userId);
    Long normalizedDraftId = requirePositive(draftId, "draftId must be positive");
    String normalizedPurpose = requireSupportedPurpose(purpose);
    String normalizedMediaType = requireSupportedMediaType(mediaType);
    String normalizedContentType = requireSupportedDraftMediaContentType(contentType);
    long normalizedSize = requirePositive(declaredSizeBytes, "declaredSizeBytes must be positive");
    String objectKey = buildDraftObjectKey(normalizedUserId, normalizedDraftId, fileName, normalizedContentType);
    StoragePresignedUpload upload = storagePresignPort.presignUpload(objectKey, normalizedContentType, DRAFT_MEDIA_UPLOAD_TTL);
    String uploadTicket = storageUploadTicketPort.issue(new StorageUploadTicketClaims(
            normalizedUserId, normalizedDraftId, normalizedPurpose, normalizedMediaType,
            normalizedContentType, normalizedSize, objectKey, upload.storageProvider(), upload.bucketName()
    ), DRAFT_MEDIA_UPLOAD_TTL);
    return upload.withUploadTicket(uploadTicket);
}
```

- [ ] **Step 4: Re-run the storage domain unit tests to verify the policy contract passes**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit the generic storage policy slice**

```powershell
git add mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/adapter/port/IStorageUploadTicketPort.java mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/adapter/port/IStorageObjectInspectPort.java mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/model/valobj/StorageUploadTicketClaims.java mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/model/valobj/StorageObjectInspection.java mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/adapter/port/IStoragePresignPort.java mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/model/valobj/StoragePresignedUpload.java mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/storage/service/StorageDomainService.java mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/StorageDomainServiceTest.java
git commit -m "feat: add generic storage upload ticket policy"
```

### Task 3: Implement Provider Adapters and Runtime Configuration

**Files:**
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/JwtStorageUploadTicketPortImpl.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/MinioStorageObjectInspectPortImpl.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/LocalStorageObjectInspectPortImpl.java`
- Modify: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/LocalStoragePresignPortImpl.java`
- Modify: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/MinioStoragePresignPortImpl.java`
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/StorageProperties.java`
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/StorageConfiguration.java`
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java`
- Modify: `mozhi-backend/mozhi-app/src/main/resources/application.yml`
- Modify: `mozhi-backend/mozhi-app/src/main/resources/application-dev.yml`
- Modify: `mozhi-backend/mozhi-app/src/main/resources/application-test.yml`
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/BackendRuntimeEnvironmentVerifierTest.java`

- [ ] **Step 1: Write a failing runtime contract assertion for the new storage config surface**

```java
String applicationYaml = Files.readString(
        backendRoot.resolve("mozhi-app").resolve("src").resolve("main").resolve("resources").resolve("application.yml")
);

assertAll(
        () -> assertTrue(applicationYaml.contains("upload-ticket-secret: ${MOZHI_STORAGE_UPLOAD_TICKET_SECRET:"),
                "application.yml must expose the upload ticket secret"),
        () -> assertTrue(applicationYaml.contains("draft-media-max-bytes: ${MOZHI_STORAGE_DRAFT_MEDIA_MAX_BYTES:"),
                "application.yml must expose the draft media max size policy")
);
```

- [ ] **Step 2: Run the runtime contract test and verify it fails before config is added**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -v "$env:USERPROFILE\.m2:/root/.m2" -w /workspace/mozhi-backend maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am "-Dtest=BackendRuntimeEnvironmentVerifierTest" test
```

Expected: FAIL because the storage ticket secret and draft media policy config are not present yet.

- [ ] **Step 3: Implement provider adapters and expose runtime properties**

```java
@Component
public class JwtStorageUploadTicketPortImpl implements IStorageUploadTicketPort {

    private final Algorithm algorithm;

    public JwtStorageUploadTicketPortImpl(@Value("${mozhi.storage.security.upload-ticket-secret}") String secret) {
        this.algorithm = Algorithm.HMAC256(secret);
    }
}
```

```java
public class StorageProperties {

    private final Security security = new Security();
    private final Policy policy = new Policy();

    public static class Security {
        private String uploadTicketSecret = "mozhi-storage-dev-secret";
    }

    public static class Policy {
        private long draftMediaMaxBytes = 10 * 1024 * 1024;
    }
}
```

```yaml
mozhi:
  storage:
    security:
      upload-ticket-secret: ${MOZHI_STORAGE_UPLOAD_TICKET_SECRET:mozhi-storage-dev-secret}
    policy:
      draft-media-max-bytes: ${MOZHI_STORAGE_DRAFT_MEDIA_MAX_BYTES:10485760}
```

```java
return new StoragePresignedUpload(objectKey, uploadUrl, publicUrl, HTTP_METHOD, null, "MINIO", bucket, expiresAt);
```

- [ ] **Step 4: Re-run the runtime contract test**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit the adapter/config slice**

```powershell
git add mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/JwtStorageUploadTicketPortImpl.java mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/MinioStorageObjectInspectPortImpl.java mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/LocalStorageObjectInspectPortImpl.java mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/LocalStoragePresignPortImpl.java mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/MinioStoragePresignPortImpl.java mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/StorageProperties.java mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/StorageConfiguration.java mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java mozhi-backend/mozhi-app/src/main/resources/application.yml mozhi-backend/mozhi-app/src/main/resources/application-dev.yml mozhi-backend/mozhi-app/src/main/resources/application-test.yml mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/BackendRuntimeEnvironmentVerifierTest.java
git commit -m "feat: add provider adapters for direct upload"
```

### Task 4: Bind Confirmed Objects to Drafts and Query Media Lists

**Files:**
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/adapter/repository/IMediaRefRepository.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/MediaRefEntity.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/MediaRefRepositoryImpl.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/MediaRefDao.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/po/MediaRefPO.java`
- Create: `mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/MediaRefDao.xml`
- Modify: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/DraftEntity.java`
- Modify: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java`
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java`
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/MybatisConfiguration.java`
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftHttpIntegrationTest.java`

- [ ] **Step 1: Write failing HTTP integration tests for draft-media confirm, idempotency, and frozen-draft rejection**

```java
mockMvc.perform(post("/api/content/drafts/{draftId}/media/confirm", draftId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "objectKey", objectKey,
                        "uploadTicket", uploadTicket,
                        "sortOrder", 0
                ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].objectKey").value(objectKey))
        .andExpect(jsonPath("$.data.items[0].uploadStatus").value("CONFIRMED"));
```

```java
mockMvc.perform(post("/api/content/drafts/{draftId}/media/confirm", frozenDraftId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmPayload(objectKey, uploadTicket, 0)))
        .andExpect(status().isBadRequest());
```

- [ ] **Step 2: Run the HTTP integration suite and verify it fails before repository/controller support exists**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -v "$env:USERPROFILE\.m2:/root/.m2" -w /workspace/mozhi-backend maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am "-Dtest=DraftHttpIntegrationTest" test
```

Expected: FAIL with missing endpoint, missing repository bean, or missing mapper definitions.

- [ ] **Step 3: Implement `media_ref` repository and content-side confirm/list logic**

```java
public interface IMediaRefRepository {

    Optional<MediaRefEntity> findByStorageIdentity(String storageProvider, String bucketName, String objectKey);

    MediaRefEntity save(MediaRefEntity mediaRefEntity);

    List<MediaRefEntity> findByDraftId(Long ownerId, Long draftId);
}
```

```java
public void assertWritableForMediaBinding() {
    if (status == DraftStatusEnum.PENDING_REVIEW
            || status == DraftStatusEnum.PUBLISHED
            || status == DraftStatusEnum.ARCHIVED) {
        throw new BaseException(ResponseCode.BAD_REQUEST, "draft media binding is forbidden in current status");
    }
}
```

```java
public List<MediaRefEntity> confirmMineMedia(Long ownerId, Long draftId, String objectKey, String uploadTicket, Integer sortOrder) {
    DraftEntity draftEntity = getMineById(ownerId, draftId);
    draftEntity.assertWritableForMediaBinding();
    StorageUploadTicketClaims claims = storageUploadTicketPort.verify(uploadTicket);
    validateDraftScopedTicket(claims, ownerId, draftId, objectKey);
    StorageObjectInspection inspection = storageObjectInspectPort.inspect(claims.storageProvider(), claims.bucketName(), objectKey);
    validateInspectionAgainstTicket(claims, inspection);
    mediaRefRepository.findByStorageIdentity(claims.storageProvider(), claims.bucketName(), objectKey)
            .orElseGet(() -> mediaRefRepository.save(MediaRefEntity.confirmed(ownerId, draftId, claims, inspection, sortOrder)));
    return listMineMedia(ownerId, draftId);
}
```

```xml
<insert id="insert" parameterType="cn.zy.mozhi.infrastructure.dao.po.MediaRefPO" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO media_ref (
        owner_id, draft_id, storage_provider, bucket_name, file_name, object_key,
        public_url, media_type, content_type, size_bytes, etag, upload_status, bound_at, sort_order
    ) VALUES (
        #{ownerId}, #{draftId}, #{storageProvider}, #{bucketName}, #{fileName}, #{objectKey},
        #{publicUrl}, #{mediaType}, #{contentType}, #{sizeBytes}, #{etag}, #{uploadStatus}, #{boundAt}, #{sortOrder}
    )
</insert>
```

- [ ] **Step 4: Re-run the draft HTTP integration tests**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit the content-side media binding slice**

```powershell
git add mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/adapter/repository/IMediaRefRepository.java mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/MediaRefEntity.java mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/repository/MediaRefRepositoryImpl.java mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/MediaRefDao.java mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/dao/po/MediaRefPO.java mozhi-backend/mozhi-app/src/main/resources/mybatis/mapper/MediaRefDao.xml mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/model/entity/DraftEntity.java mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/content/service/DraftDomainService.java mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/MybatisConfiguration.java mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/DraftHttpIntegrationTest.java
git commit -m "feat: bind confirmed uploads to draft media refs"
```

### Task 5: Expose HTTP Entry Points and Verify End-to-End Flow

**Files:**
- Create: `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/StorageController.java`
- Modify: `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/DraftController.java`
- Create: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/StorageHttpIntegrationTest.java`

- [ ] **Step 1: Write failing integration tests for `/api/storage/presign` and the full upload-confirm-list loop**

```java
MvcResult presignResult = mockMvc.perform(post("/api/storage/presign")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "purpose", "DRAFT_MEDIA",
                        "draftId", draftId,
                        "fileName", "cover.png",
                        "contentType", "image/png",
                        "mediaType", "IMAGE",
                        "declaredSizeBytes", 12L
                ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.uploadTicket").isNotEmpty())
        .andReturn();
```

```java
mockMvc.perform(get("/api/content/drafts/{draftId}/media", draftId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].mediaType").value("IMAGE"));
```

- [ ] **Step 2: Run the storage HTTP integration test and verify it fails before the controllers exist**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -v "$env:USERPROFILE\.m2:/root/.m2" -w /workspace/mozhi-backend maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am "-Dtest=StorageHttpIntegrationTest" test
```

Expected: FAIL with 404 or missing controller bean errors.

- [ ] **Step 3: Implement the storage/draft HTTP endpoints without breaking avatar upload**

```java
@RestController
@RequestMapping("/api/storage")
public class StorageController {

    @PostMapping("/presign")
    public ApiResponse<StoragePresignResponseDTO> presign(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                          @Valid @RequestBody StoragePresignRequestDTO requestDTO) {
        StoragePresignedUpload upload = storageDomainService.presignDraftMediaUpload(
                tokenClaims.userId(),
                requestDTO.draftId(),
                requestDTO.fileName(),
                requestDTO.contentType(),
                requestDTO.mediaType(),
                requestDTO.purpose(),
                requestDTO.declaredSizeBytes()
        );
        return ApiResponse.success(new StoragePresignResponseDTO(
                upload.objectKey(), upload.uploadUrl(), upload.publicUrl(), upload.httpMethod(),
                upload.uploadTicket(), upload.expiresAt()
        ));
    }
}
```

```java
@PostMapping("/{draftId}/media/confirm")
public ApiResponse<DraftMediaListDTO> confirmMedia(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                   @PathVariable("draftId") Long draftId,
                                                   @Valid @RequestBody DraftMediaConfirmRequestDTO requestDTO) {
    return ApiResponse.success(toMediaListDTO(draftDomainService.confirmMineMedia(
            tokenClaims.userId(), draftId, requestDTO.objectKey(), requestDTO.uploadTicket(), requestDTO.sortOrder()
    )));
}
```

```java
@GetMapping("/{draftId}/media")
public ApiResponse<DraftMediaListDTO> listMedia(@AuthenticationPrincipal AuthTokenClaims tokenClaims,
                                                @PathVariable("draftId") Long draftId) {
    return ApiResponse.success(toMediaListDTO(draftDomainService.listMineMedia(tokenClaims.userId(), draftId)));
}
```

```java
private DraftMediaListDTO toMediaListDTO(List<MediaRefEntity> mediaItems) {
    return new DraftMediaListDTO(mediaItems.stream()
            .map(mediaRef -> new DraftMediaDTO(
                    mediaRef.getId(),
                    mediaRef.getObjectKey(),
                    mediaRef.getPublicUrl(),
                    mediaRef.getMediaType().name(),
                    mediaRef.getContentType(),
                    mediaRef.getSizeBytes(),
                    mediaRef.getUploadStatus().name(),
                    mediaRef.getSortOrder(),
                    mediaRef.getBoundAt()
            ))
            .toList());
}
```

- [ ] **Step 4: Run focused integration tests, then the full backend suite**

Run:

```powershell
docker run --rm -v "${PWD}:/workspace" -v "$env:USERPROFILE\.m2:/root/.m2" -w /workspace/mozhi-backend maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=StorageDomainServiceTest,DraftFlywayIntegrationTest,DraftHttpIntegrationTest,StorageHttpIntegrationTest" test
docker run --rm -v "${PWD}:/workspace" -v "$env:USERPROFILE\.m2:/root/.m2" -w /workspace/mozhi-backend maven:3.9.9-eclipse-temurin-21 ./mvnw -q -pl mozhi-app -am test
```

Expected: both commands PASS.

- [ ] **Step 5: Do dev-runtime smoke verification and commit**

Run:

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-local.yml up -d mozhi-backend
```

Then verify this sequence manually against `http://127.0.0.1:8090`:

```text
1. register -> login -> create draft
2. POST /api/storage/presign with purpose=DRAFT_MEDIA
3. PUT bytes to returned uploadUrl
4. POST /api/content/drafts/{draftId}/media/confirm
5. GET /api/content/drafts/{draftId}/media
```

Finally:

```powershell
git diff --cached --check
git add mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/StorageController.java mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/DraftController.java mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/StorageHttpIntegrationTest.java
git commit -m "feat: add direct upload flow for draft media"
```
