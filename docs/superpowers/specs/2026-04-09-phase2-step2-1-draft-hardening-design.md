# MOZhi Phase 2 Step 2.1 Draft Hardening Design

**Date:** 2026-04-09  
**Status:** Approved in chat, written for review  
**Scope:** Strengthen the existing `Phase 2 / Step 2.1` backend-only draft slice so it is closer to real production behavior without crossing into `Step 2.2+`

---

## 1. Context

`Phase 2 / Step 2.1` was already bootstrapped with:

- `draft`, `note`, `media_ref` foundational schema
- authenticated draft CRUD endpoints
- draft lifecycle enum and transition rules
- ownership isolation with `404` anti-enumeration behavior
- Flyway and HTTP integration tests
- local smoke validation against the dev runtime

That implementation is a valid first vertical slice, but a review of the code found several realistic-environment gaps that should be closed before moving to `Step 2.2`:

1. published and archived drafts can still be content-edited
2. updates and deletes have no optimistic concurrency protection
3. the draft list is unpaged and unfiltered
4. conflict scenarios have no first-class error semantics
5. a small amount of bean wiring style drift appeared during bootstrap

This document defines a constrained hardening pass for `Step 2.1` only.

---

## 2. Goals

### 2.1 Product goals

- Make draft lifecycle behavior credible for a real content authoring system.
- Prevent state-inconsistent edits after publication or archival.
- Provide a minimally usable draft box API shape with pagination and status filtering.

### 2.2 Engineering goals

- Add optimistic concurrency control to draft writes.
- Standardize draft conflict behavior as a typed API contract.
- Keep the implementation aligned with the repository's existing DDD and configuration patterns.

### 2.3 Non-goals

This hardening pass does **not** add:

- media upload binding
- note publication materialization
- note detail pages
- comments
- frontend draft editor UI
- AI summary generation
- moderation queues beyond the existing status model

Those remain in later Phase 2 steps.

---

## 3. Problems Found In The Existing Step 2.1 Slice

## 3.1 Lifecycle leak

Current `updateMine(...)` updates title/content regardless of current draft status.

That means a draft in `PUBLISHED` or `ARCHIVED` state can still be edited through the generic update endpoint, which breaks the separation between authoring and publication state.

## 3.2 No concurrency protection

Current persistence writes operate by `id` only:

- update by `id`
- delete by `id`

This allows classic stale-write and stale-delete races:

- client A reads version N
- client B changes status or content
- client A submits stale update and silently overwrites newer state

This is not acceptable for real draft editing behavior.

## 3.3 Draft list is too thin

Current list behavior returns all drafts for the current user ordered by `updated_at`.

This is acceptable for a bootstrap but not for a realistic draft box. At minimum, the API should support:

- page
- page size
- optional status filter

## 3.4 Error semantics are too coarse

The current response-code system has no conflict-specific code. Concurrent mutation failures are forced into generic `BAD_REQUEST`, which weakens the client contract and makes telemetry less useful.

---

## 4. Chosen Approach

This hardening pass will make four focused changes:

1. **Freeze non-editable states**
   - only `DRAFT`, `UPLOADING`, and `REJECTED` remain content-editable
   - `PENDING_REVIEW`, `PUBLISHED`, and `ARCHIVED` become read-only for title/content mutation

2. **Add optimistic concurrency**
   - introduce a `version` column on `draft`
   - every successful update or transition increments `version`
   - write APIs require `expectedVersion`
   - repository writes succeed only when `id + version` match

3. **Upgrade list API to minimal market-ready shape**
   - `GET /api/content/drafts?page=1&pageSize=20&status=DRAFT`
   - return a typed paged response with total count and page metadata

4. **Add conflict-specific error contract**
   - add `ResponseCode.CONFLICT`
   - map it to HTTP `409`
   - use it for stale version write attempts

This stays fully inside the draft slice and does not require new subdomains.

---

## 5. Lifecycle Rules After Hardening

## 5.1 Editable states

Content update is allowed only when the current status is one of:

- `DRAFT`
- `UPLOADING`
- `REJECTED`

These states represent author-controlled preparation.

## 5.2 Read-only states

Content update is rejected when the current status is:

- `PENDING_REVIEW`
- `PUBLISHED`
- `ARCHIVED`

Rationale:

- `PENDING_REVIEW` should be frozen while under review
- `PUBLISHED` should not drift from the audited/published artifact
- `ARCHIVED` should behave as a terminal frozen state

## 5.3 Delete rules

Delete remains physical deletion for now, but only for non-published drafts.

Allowed delete states:

- `DRAFT`
- `UPLOADING`
- `REJECTED`
- `PENDING_REVIEW`
- `ARCHIVED`

Rejected delete state:

- `PUBLISHED`

This matches the current step boundary. A later step may introduce soft delete or recycle bin behavior.

---

## 6. Concurrency Design

## 6.1 Data model

Add to `draft`:

- `version BIGINT NOT NULL DEFAULT 0`

This column increments on every successful content update or status transition.

## 6.2 Aggregate model

`DraftEntity` will gain:

- `version`
- transition/update helpers that preserve immutability and increment version on successful mutation
- explicit `assertEditableForContentUpdate()` behavior

## 6.3 HTTP contract

`DraftUpdateRequestDTO` adds:

- `expectedVersion`

`DraftStatusTransitionRequestDTO` adds:

- `expectedVersion`

`DraftDetailDTO` and `DraftSummaryDTO` expose:

- `version`

This lets clients perform optimistic writes without hidden server state.

## 6.4 Repository behavior

Repository update and delete become conditional:

- update: `WHERE id = ? AND version = ?`
- delete: `WHERE id = ? AND version = ?`

If the affected row count is `0`, the repository reports a concurrency conflict to the domain layer.

We will not silently re-read and overwrite. Conflict must be explicit.

## 6.5 Error contract

Add a new response code:

- `CONFLICT("A0409", "conflict")`

Map it to:

- HTTP `409`

Use it for:

- stale version content update
- stale version status transition
- stale version delete

---

## 7. Draft List Design

## 7.1 Request shape

`GET /api/content/drafts`

Query params:

- `page` default `1`
- `pageSize` default `20`
- `status` optional

Boundaries:

- `page >= 1`
- `1 <= pageSize <= 100`
- invalid `status` rejected with `400`

## 7.2 Response shape

Introduce a typed page response:

- `page`
- `pageSize`
- `total`
- `items`

Each item remains a `DraftSummaryDTO`.

## 7.3 Why pagination now

This is the smallest change that makes the draft box believable for real usage while still fitting within `Step 2.1`.

We are intentionally not adding:

- search
- cursor pagination
- sort switching
- bulk operations

Those would be beyond the current step.

---

## 8. Validation and Protection Rules

## 8.1 DTO validation

Request DTOs should use `jakarta.validation` for boundary validation where appropriate:

- `@NotBlank` on title/content/targetStatus
- `@NotNull` on `expectedVersion`
- `@Positive` on `expectedVersion`, `page`, `pageSize`
- `@Max(100)` on `pageSize`

Domain validation still remains authoritative.

## 8.2 Ownership

Ownership rules remain unchanged:

- client never supplies author identity
- foreign-owned draft access returns `404`

## 8.3 Conflict visibility

Conflict should be visible as conflict, not disguised as generic invalid input.

That improves:

- client retry behavior
- telemetry quality
- debugging clarity

---

## 9. Implementation Shape

## 9.1 Files to modify

- `mozhi-backend/mozhi-types/.../ResponseCode.java`
- `mozhi-backend/mozhi-trigger/.../GlobalExceptionHandler.java`
- `mozhi-backend/mozhi-types/.../DraftStatusEnum.java` if helper methods are added
- `mozhi-backend/mozhi-api/.../DraftUpdateRequestDTO.java`
- `mozhi-backend/mozhi-api/.../DraftStatusTransitionRequestDTO.java`
- `mozhi-backend/mozhi-api/.../DraftDetailDTO.java`
- `mozhi-backend/mozhi-api/.../DraftSummaryDTO.java`
- new paged-response DTO in `mozhi-api`
- `mozhi-backend/mozhi-domain/.../DraftEntity.java`
- `mozhi-backend/mozhi-domain/.../DraftDomainService.java`
- `mozhi-backend/mozhi-domain/.../IDraftRepository.java`
- `mozhi-backend/mozhi-infrastructure/.../DraftPO.java`
- `mozhi-backend/mozhi-infrastructure/.../DraftDao.java`
- `mozhi-backend/mozhi-infrastructure/.../DraftRepositoryImpl.java`
- `mozhi-backend/mozhi-app/.../DraftDao.xml`
- `mozhi-backend/mozhi-app/.../V3` follow-up migration for version/pagination indexes
- `mozhi-backend/mozhi-trigger/.../DraftController.java`
- `mozhi-backend/mozhi-app/.../DomainConfiguration.java`
- `mozhi-backend/mozhi-app/.../MybatisConfiguration.java`

## 9.2 Bean wiring cleanup

`DraftDao` mapper bean should move beside `UserDao` mapper bean in `MybatisConfiguration`.

`DomainConfiguration` should only own:

- repository adapter bean
- domain service bean

This keeps the config split closer to the existing architecture.

---

## 10. Testing Strategy

## 10.1 New failing tests first

Add or extend tests to fail on:

1. updating a `PUBLISHED` draft
2. updating an `ARCHIVED` draft
3. stale version update returning `409`
4. stale version transition returning `409`
5. stale version delete returning `409`
6. list endpoint pagination
7. list endpoint status filtering
8. unauthenticated draft access returning `401`

## 10.2 Test layers

### Domain tests

Add focused tests for:

- editable vs frozen states
- version increment behavior

### HTTP integration tests

Extend `DraftHttpIntegrationTest` for:

- `409` conflict flows
- page/filter list contract
- immutable published/archived edit rejection

### Real runtime smoke

Re-run the local dev smoke flow with:

- create draft
- update using correct version
- transition using next version
- verify paged list response

---

## 11. Acceptance Criteria

This hardening pass is complete when:

- content updates are rejected for `PENDING_REVIEW`, `PUBLISHED`, and `ARCHIVED`
- draft mutations are protected by optimistic concurrency
- stale writes return `409`
- draft list supports `page`, `pageSize`, and optional `status`
- response DTOs expose `version`
- mapper/config wiring remains compatible with `HttpSurfaceIntegrationTest`
- backend regression passes
- local dev smoke flow passes against the new contract

---

## 12. Follow-on Notes

After this hardening pass, the draft slice is solid enough to support `Step 2.2` media work.

Likely next moves:

- bind `media_ref` to draft uploads
- convert `UPLOADING` into a real media-ingestion workflow
- define publish flow from draft to note

Those should build on the new versioned draft contract rather than bypass it.
