# MOZhi Phase 2 Step 2.1 Content Draft Bootstrap Design

**Date:** 2026-04-08  
**Status:** Approved in chat, written for review  
**Scope:** Implement the backend-only draft slice for `Phase 2 / Step 2.1` inside the existing six-module DDD scaffold

---

## 1. Context

`Phase 1` is already functional:

- user registration
- login / refresh / logout / logout-all
- protected routes
- profile update
- avatar presign and confirmation
- local / production Docker runtime separation

The next planned business slice in [plan.md](/F:/new_opint/VibeCoding/MOZhi/plan.md#L138) is `Phase 2 / Step 2.1`, which starts the content domain.

That step is not "build the whole publishing system". It is specifically the bootstrap of draft management:

- draft persistence
- draft CRUD
- draft status machine
- migration and test baseline for later content work

The repository does **not** yet expose any real content endpoints such as:

- `/api/note`
- `/api/content/drafts`
- `/api/note/{id}/comment`

So the correct move is to add the first content-domain vertical slice without dragging in `Step 2.2+` concerns too early.

---

## 2. Goals

### 2.1 Product goals

- Allow authenticated users to create, query, update, delete, and transition their own drafts.
- Establish the canonical draft lifecycle for later media upload, publishing, moderation, and archive flows.
- Keep current frontend and auth flows untouched in this step.

### 2.2 Engineering goals

- Add the `content` bounded context within the current six-module backend structure.
- Add Flyway migrations for `draft`, `note`, and `media_ref` so later Phase 2 work can build on a stable schema boundary.
- Keep repository, service, HTTP, and DTO layers aligned with the existing user/auth implementation style.

### 2.3 Security and safety goals

- Never trust client-supplied author identity.
- Keep draft ownership checks server-side.
- Prevent illegal status jumps.
- Avoid existence leakage for resources owned by other users.
- Put server-side length and required-field validation in place before richer editors arrive.

---

## 3. Non-goals

This step does **not** include:

- public note detail pages
- comment publishing
- media upload confirmation
- note publishing event emission
- AI summary generation
- frontend draft editor UI
- moderation workflows beyond draft status transitions

Those belong to `Step 2.2` through `Step 2.6`.

---

## 4. Chosen Approach

We will use the "schema-first, draft-only activation" approach:

1. Create all three foundational content tables now: `draft`, `note`, `media_ref`.
2. Implement only the `draft` domain and HTTP surface in this step.
3. Keep `note` and `media_ref` schema-only for now so later steps do not need to rewrite base migrations.
4. Separate "update draft content" from "transition draft status" so clients cannot smuggle lifecycle changes through a generic update endpoint.

This is preferred over a smaller `draft`-table-only change because it matches the plan, stabilizes the schema boundary, and reduces migration churn in later content steps.

---

## 5. Domain Design

## 5.1 New bounded context

A new `content` subdomain will be added under the existing DDD layout:

- `mozhi-domain/.../domain/content`
- `mozhi-infrastructure/.../adapter/repository`
- `mozhi-infrastructure/.../dao`
- `mozhi-trigger/.../http`
- `mozhi-api/.../dto`

This keeps the repository consistent with the existing `auth`, `user`, and `storage` slices.

## 5.2 Draft aggregate

The first aggregate is `DraftEntity`.

Core fields:

- `id`
- `authorId`
- `title`
- `content`
- `status`
- `createdAt`
- `updatedAt`

The aggregate owns:

- input normalization for title/content
- ownership-aware update decisions in cooperation with the domain service
- legal status transition checks

It does **not** own:

- media attachment lifecycle
- publishing side effects
- comment behavior

## 5.3 Draft status model

Draft status will be introduced in `mozhi-types` as `DraftStatusEnum`.

Initial states for this step:

- `DRAFT`
- `UPLOADING`
- `PENDING_REVIEW`
- `PUBLISHED`
- `REJECTED`
- `ARCHIVED`

Allowed transitions:

- `DRAFT -> UPLOADING`
- `DRAFT -> PENDING_REVIEW`
- `DRAFT -> ARCHIVED`
- `UPLOADING -> DRAFT`
- `UPLOADING -> PENDING_REVIEW`
- `UPLOADING -> ARCHIVED`
- `PENDING_REVIEW -> DRAFT`
- `PENDING_REVIEW -> PUBLISHED`
- `PENDING_REVIEW -> REJECTED`
- `PENDING_REVIEW -> ARCHIVED`
- `REJECTED -> DRAFT`
- `REJECTED -> ARCHIVED`
- `PUBLISHED -> ARCHIVED`

Disallowed rules:

- `ARCHIVED` cannot transition anywhere else
- published content cannot be physically deleted
- update operations cannot arbitrarily rewrite status

This keeps lifecycle intent explicit and prevents the future publishing flow from being bypassed.

---

## 6. Persistence Design

## 6.1 Migration strategy

A new Flyway migration will add three tables:

- `draft`
- `note`
- `media_ref`

Even though only `draft` is activated in this step, all three belong to the foundational content schema.

## 6.2 `draft` table

Recommended columns:

- `id BIGINT PRIMARY KEY AUTO_INCREMENT`
- `author_id BIGINT NOT NULL`
- `title VARCHAR(128) NOT NULL`
- `content TEXT NOT NULL`
- `status VARCHAR(32) NOT NULL`
- `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

Recommended indexes:

- `idx_draft_author_updated_at (author_id, updated_at DESC)`
- `idx_draft_author_status (author_id, status)`

Recommended constraints:

- foreign key from `author_id` to `user.id`
- status is stored as enum text, validated in domain logic

## 6.3 `note` table

This step only establishes the later target shape. Minimal columns:

- `id BIGINT PRIMARY KEY AUTO_INCREMENT`
- `author_id BIGINT NOT NULL`
- `draft_id BIGINT NULL`
- `title VARCHAR(128) NOT NULL`
- `content LONGTEXT NOT NULL`
- `summary TEXT NULL`
- `status VARCHAR(32) NOT NULL`
- `published_at TIMESTAMP NULL`
- `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

`note` is not activated by service code in this step.

## 6.4 `media_ref` table

Minimal future-ready columns:

- `id BIGINT PRIMARY KEY AUTO_INCREMENT`
- `draft_id BIGINT NULL`
- `note_id BIGINT NULL`
- `object_key VARCHAR(255) NOT NULL`
- `media_type VARCHAR(64) NOT NULL`
- `sort_order INT NOT NULL DEFAULT 0`
- `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

`media_ref` is also schema-only in this step.

## 6.5 Why not defer `note` and `media_ref`

Deferring them would make later migrations more fragmented and blur the Phase 2 schema boundary. Creating them now gives us:

- stable content-domain table names
- stable foreign key directions
- cleaner follow-on migrations in `Step 2.2+`

without forcing premature service implementation.

---

## 7. HTTP API Design

## 7.1 Routes

All routes are authenticated and live under a dedicated content prefix:

- `POST /api/content/drafts`
- `GET /api/content/drafts`
- `GET /api/content/drafts/{draftId}`
- `PUT /api/content/drafts/{draftId}`
- `DELETE /api/content/drafts/{draftId}`
- `POST /api/content/drafts/{draftId}/status`

This avoids mixing content creation APIs into the user or auth controllers.

## 7.2 DTOs

New DTOs in `mozhi-api`:

- `DraftCreateRequestDTO`
- `DraftUpdateRequestDTO`
- `DraftStatusTransitionRequestDTO`
- `DraftDetailDTO`
- `DraftSummaryDTO`

Response shape continues to use the existing `ApiResponse<T>`.

## 7.3 Ownership model

Client never sends `authorId`.

The authenticated principal determines ownership through `AuthTokenClaims.userId()`.

This prevents identity spoofing and keeps resource ownership a server-side concern.

## 7.4 List behavior

`GET /api/content/drafts` returns only the current user's drafts.

Sorting:

- newest updated first

This step can start without cursor pagination. A simple bounded list is acceptable because the main goal is vertical-slice bootstrap, not feed-scale browsing.

---

## 8. Validation and Protection Rules

## 8.1 Required fields

Create and update endpoints must enforce:

- non-blank title
- non-blank content

## 8.2 Length limits

To match a realistic backend boundary before a richer editor exists:

- title max length: `128`
- content max length: `20000`

These limits are intentionally conservative and can be revisited in the editor phase, but they prevent abuse and accidental oversized payloads now.

## 8.3 Resource existence leakage

For read, update, delete, and status transition:

- if the draft does not exist, return `404`
- if the draft exists but belongs to another user, also return `404`

This avoids telling callers whether another user's draft ID is valid.

## 8.4 Delete semantics

`DELETE /api/content/drafts/{draftId}` is allowed only for drafts that are not published.

If status is `PUBLISHED`, the request is rejected with a business error. Published material must be archived instead.

This prevents future conflicts between draft deletion and note publication history.

## 8.5 Status integrity

Status may be changed only through `POST /api/content/drafts/{draftId}/status`.

`PUT /api/content/drafts/{draftId}` updates content fields only.

This ensures that lifecycle policy stays centralized and testable.

---

## 9. Backend Implementation Shape

## 9.1 Domain

Create:

- `domain/content/model/entity/DraftEntity.java`
- `domain/content/adapter/repository/IDraftRepository.java`
- `domain/content/service/DraftDomainService.java`

`DraftDomainService` will own:

- create
- listMine
- getMineById
- updateMine
- deleteMine
- transitionMineStatus

## 9.2 Infrastructure

Create:

- `infrastructure/dao/DraftDao.java`
- `infrastructure/dao/po/DraftPO.java`
- `infrastructure/adapter/repository/DraftRepositoryImpl.java`
- `app/resources/mybatis/mapper/DraftDao.xml`

The repository implementation mirrors the existing `UserRepositoryImpl` pattern.

## 9.3 Trigger layer

Create:

- `trigger/http/DraftController.java`

It will:

- read authenticated principal
- map DTOs to domain calls
- return unified API responses

No business logic should live in the controller beyond request mapping and response shaping.

---

## 10. Testing Strategy

## 10.1 Flyway integration

Add a Flyway integration test that proves:

- `draft` exists
- `note` exists
- `media_ref` exists

This protects the migration contract for later Phase 2 work.

## 10.2 HTTP integration

Add a `DraftHttpIntegrationTest` using the same style as `UserHttpIntegrationTest`.

Required coverage:

1. authenticated user can create a draft
2. authenticated user only sees their own drafts in the list
3. authenticated user can query their own draft detail
4. authenticated user can update title and content
5. illegal status transition is rejected
6. cross-user access returns `404`
7. published draft cannot be deleted
8. valid delete removes an unpublished draft

## 10.3 Regression

Re-run the existing backend test suite after the slice is green.

This is necessary because:

- new migration files can break startup
- new authenticated endpoints can affect HTTP surface assumptions
- new MyBatis wiring can break application bootstrap if package scanning is off

---

## 11. Risks and Mitigations

## 11.1 Risk: schema overreach

Creating `note` and `media_ref` now could introduce fields that later feel too small.

Mitigation:

- keep those tables minimal
- include only foundational columns
- defer richer metadata to later additive migrations

## 11.2 Risk: draft and publish lifecycle bleed together

If update and status transition share one endpoint, future publishing rules will be easy to bypass.

Mitigation:

- separate content updates from status transitions now

## 11.3 Risk: cross-user data exposure

Drafts are private authoring assets.

Mitigation:

- server-side owner checks on every mutating and read endpoint
- return `404` for both missing and foreign-owned records

## 11.4 Risk: oversized payloads

Large content bodies can stress logs, DB, and future editor flows.

Mitigation:

- put explicit content length limits in the domain service
- avoid trusting frontend-only limits

---

## 12. Acceptance Criteria

This step is complete when:

- Flyway creates `draft`, `note`, and `media_ref`
- authenticated users can create/list/read/update/delete their own drafts
- legal status transitions work and illegal ones are rejected
- cross-user draft access returns `404`
- published drafts cannot be physically deleted
- focused content tests pass
- full backend regression passes

---

## 13. Follow-on Work

The next planned steps after this design are:

- `Step 2.2`: storage presign generalization and media confirmation
- `Step 2.3`: draft publish flow and Kafka event emission
- `Step 2.4`: AI summary async enrichment
- `Step 2.5`: note detail and comments
- `Step 2.6`: frontend editor, draft box, and content detail views

This design intentionally leaves clean seams for those steps instead of partially implementing them now.
