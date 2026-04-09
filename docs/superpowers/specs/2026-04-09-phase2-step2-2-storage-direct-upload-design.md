# MOZhi Phase 2 Step 2.2 Generic Storage Direct Upload Design

**Date:** 2026-04-09  
**Status:** Approved in chat, written for review  
**Scope:** Implement a production-minded backend-only `Phase 2 / Step 2.2` storage slice for direct upload, media confirmation, and draft media binding without crossing into publication, CDN delivery workflows, or frontend editor work

---

## 1. Context

`Phase 2 / Step 2.1` already established:

- `draft`, `note`, and `media_ref` foundational tables
- authenticated draft ownership checks
- lifecycle rules and optimistic concurrency for draft writes
- a small `storage` subdomain already used for avatar direct upload
- MinIO and local storage mock runtime support

The current avatar upload path proves that the repository already has the basic pieces for pre-signed direct upload:

- `StorageDomainService` can generate a pre-signed upload
- `IStoragePresignPort` has MinIO and local implementations
- `StorageMockController` supports local direct upload verification

However, that path is still avatar-specific:

- object key generation is hard-coded to `avatars/{userId}/...`
- confirmation only resolves a user avatar URL
- there is no generic upload policy model
- there is no storage object inspection flow
- there is no content-media binding flow for drafts
- `media_ref` lacks several metadata fields needed for realistic future storage evolution

`Step 2.2` should not copy-paste the avatar flow into content code. It should turn the existing storage slice into a generic capability that the content domain can safely consume.

---

## 2. Goals

### 2.1 Product goals

- Allow an authenticated author to obtain a direct-upload authorization for draft media.
- Allow the backend to confirm that the object really exists after upload and bind it to the target draft.
- Make uploaded media visible through draft media queries so later editor and publish flows can consume it.

### 2.2 Engineering goals

- Generalize the storage capability so it is not locked to avatars.
- Keep storage concerns and content concerns separated by bounded-context responsibilities.
- Add realistic safeguards for upload misuse, stale confirmations, and ownership leakage.
- Make the storage contract extensible to future MinIO, OSS, S3, COS, and CDN-backed deployments.

### 2.3 Non-goals

This step does **not** add:

- frontend drag-and-drop editor integration
- multipart or chunked upload
- resumable upload sessions
- virus scanning
- media moderation
- image transformation or video transcoding
- note publication materialization
- signed download URLs
- CDN invalidation or cache purge workflows

Those belong to later phases or later hardening passes.

---

## 3. Main Design Decision

The storage flow will be split into two bounded responsibilities instead of forcing all logic into one endpoint.

### 3.1 Generic storage responsibility

The `storage` subdomain is responsible for:

- upload policy validation
- object key generation
- pre-signed upload generation
- upload ticket issuance
- object metadata inspection
- public URL resolution

### 3.2 Content responsibility

The `content` subdomain is responsible for:

- verifying draft ownership
- enforcing draft lifecycle rules
- deciding whether media can still be attached to that draft
- writing `media_ref`
- exposing draft media query APIs

### 3.3 Why this is chosen

This is a deliberate refinement beyond the literal plan wording.

The plan says `POST /api/storage/confirm` should verify object existence and associate it to a draft. In a strict production design, that draft association belongs to content, not storage.

So the chosen split is:

- `POST /api/storage/presign`
- `POST /api/content/drafts/{draftId}/media/confirm`
- `GET /api/content/drafts/{draftId}/media`

This keeps storage generic and reusable while preserving the real business truth that `media_ref` is content-owned state.

---

## 4. Chosen API Shape

## 4.1 Pre-sign endpoint

`POST /api/storage/presign`

Request fields:

- `purpose`
- `ownerId` is **not** accepted from the client
- `draftId`
- `fileName`
- `contentType`
- `mediaType`
- `declaredSizeBytes`

For `Step 2.2`, `purpose` will support:

- `DRAFT_MEDIA`

Avatar upload remains available through the existing avatar endpoints. We do not break the current frontend contract in this step.

Response fields:

- `objectKey`
- `uploadUrl`
- `publicUrl`
- `httpMethod`
- `expiresAt`
- `uploadTicket`

The new field is `uploadTicket`, a short-lived signed token returned by the backend to prove that this upload authorization was server-issued under a specific purpose, owner, draft, media type, content type, and expiry.

## 4.2 Draft media confirmation endpoint

`POST /api/content/drafts/{draftId}/media/confirm`

Request fields:

- `objectKey`
- `uploadTicket`
- `sortOrder`

Server actions:

1. validate authenticated user
2. validate draft ownership
3. validate draft lifecycle still accepts new media
4. validate and decode the signed upload ticket
5. verify the ticket matches:
   - current user
   - current draft
   - purpose
   - object key
6. inspect object metadata in storage
7. validate inspected metadata against ticket declaration
8. write or reuse a `media_ref` row
9. return the bound media metadata

## 4.3 Draft media query endpoint

`GET /api/content/drafts/{draftId}/media`

Returns ordered media bound to the draft. This keeps later editor integration straightforward and gives `Step 2.2` an observable completion state beyond database assertions.

---

## 5. Upload Policy Model

The generic storage capability should not be “upload any file to anywhere”.

It will instead be driven by server-side upload purpose rules.

For this step, a single policy is enough:

- `DRAFT_MEDIA`

Policy fields:

- allowed media types
- allowed content types
- maximum size
- object key prefix pattern
- TTL

For `Step 2.2`, the runtime-enabled media type is intentionally restricted to:

- `IMAGE`

Allowed content types:

- `image/png`
- `image/jpeg`
- `image/webp`
- `image/gif`

Why not fully open video and attachments now:

1. the interface must be generic, but the enforcement must stay realistic
2. image upload is the actual immediate content-authoring need
3. video or arbitrary file support would require much larger validation, size, and delivery decisions
4. fake “generic” support with no policy would be worse than an explicit phased policy model

So the interface is generic, but the enabled policy surface in this step stays narrow and truthful.

---

## 6. Upload Ticket Design

## 6.1 Why an upload ticket is needed

If confirmation only accepts `objectKey`, then confirmation trusts that:

- the object was actually created through this system
- the object belongs to the same logical upload request
- the object matches the declared draft and purpose

That is too weak for a production-minded design.

So `presign` will issue a signed upload ticket that contains:

- `ownerUserId`
- `draftId`
- `purpose`
- `objectKey`
- `mediaType`
- `contentType`
- `declaredSizeBytes`
- `storageProvider`
- `bucketName`
- `expiresAt`

The token can be a signed JWT or HMAC-backed opaque token. The important part is:

- the client cannot forge it
- the server can validate it without depending on mutable client input

## 6.2 Ticket lifetime

Ticket TTL should match or be slightly shorter than the pre-signed upload TTL.

Recommended initial TTL:

- 15 minutes

## 6.3 Ticket usage

`confirm` must reject when:

- the ticket is expired
- the ticket does not match the authenticated user
- the ticket does not match the draft path parameter
- the ticket purpose is unsupported
- the ticket object key differs from the request object key

This prevents confirming arbitrary bucket objects that happen to exist.

---

## 7. Object Key Strategy

Client input must never determine the object key prefix.

For `DRAFT_MEDIA`, the server will generate keys in the form:

`drafts/{ownerUserId}/{draftId}/{yyyyMMdd}/{uuid}.{ext}`

Design reasons:

1. owner and draft partitioning keep the storage namespace explainable
2. date partitioning avoids huge flat prefix directories
3. UUID keeps collisions negligible
4. extension preservation improves debugging and object-store browsing
5. the key is still implementation-neutral enough for MinIO, OSS, COS, and S3

The file extension should be derived from validated content type first, not blindly trusted from the file name.

---

## 8. Storage Inspection Capability

Current code can pre-sign uploads, but it does not yet expose a generic object inspection capability.

This step will add a new outbound port, for example:

- `IStorageObjectInspectPort`

It should return an inspected object metadata value object with fields such as:

- `exists`
- `contentType`
- `sizeBytes`
- `etag`

Provider implementations:

- local mock implementation: inspect from filesystem and stored sidecar metadata
- MinIO implementation: inspect via object stat/head API

Why this is necessary:

1. `confirm` must not trust the client's claim that upload succeeded
2. the backend needs real metadata for `media_ref`
3. future providers will all need the same abstraction

This is also the key seam that keeps future CDN introduction orthogonal to business logic.

---

## 9. Content Binding Rules

## 9.1 Draft ownership

Draft ownership must be checked using the same anti-enumeration behavior as `Step 2.1`:

- non-owner access returns `404`

## 9.2 Lifecycle gating

Draft media binding is allowed only when the draft remains in a writable state.

For this step, media binding is allowed only for:

- `DRAFT`
- `UPLOADING`
- `REJECTED`

Media binding is rejected for:

- `PENDING_REVIEW`
- `PUBLISHED`
- `ARCHIVED`

This keeps media attachment consistent with the content-editability rules already introduced in `Step 2.1`.

## 9.3 Idempotency

Repeated confirmation of the same uploaded object for the same draft should be idempotent.

Expected behavior:

- if the same object is confirmed again by the same owner for the same draft, return the existing `media_ref`
- do not create duplicate rows

This avoids accidental duplicate insertions caused by retries or client uncertainty.

---

## 10. `media_ref` Data Model Evolution

The existing `media_ref` schema is not yet rich enough for a realistic storage contract.

This step should add at least:

- `storage_provider`
- `bucket_name`
- `file_name`
- `size_bytes`
- `etag`
- `upload_status`
- `bound_at`

Existing columns remain:

- `owner_id`
- `draft_id`
- `note_id`
- `object_key`
- `public_url`
- `media_type`
- `content_type`
- `sort_order`
- timestamps

Recommended initial enums:

- `storage_provider`: `LOCAL`, `MINIO`
- `upload_status`: `CONFIRMED`

We do **not** need to persist a `PRESIGNED` row in this step. The signed upload ticket already carries the temporary authorization state. `media_ref` becomes the durable representation of a confirmed and bound media object.

Recommended uniqueness rule:

- unique on `(storage_provider, bucket_name, object_key)`

This is more future-proof than assuming object keys are globally unique across providers.

---

## 11. CDN / OSS / S3 Extensibility

The design must stay provider-oriented rather than MinIO-oriented.

### 11.1 What the business layer should know

The business layer should know only:

- storage provider identity
- bucket name
- object key
- public URL

### 11.2 What the provider adapter should know

Provider-specific implementations should own:

- MinIO signing API
- OSS signing API
- S3 or COS metadata inspection API
- public URL composition
- CDN-backed public endpoint rewriting

### 11.3 CDN handling rule

CDN should remain a delivery concern, not a domain concern.

That means:

- `publicUrl` can be resolved by the provider adapter using a CDN endpoint configuration
- content and storage domain code should not branch on CDN rules
- later migration from direct bucket URL to CDN URL should not require changing content write logic

This is why `publicUrl` is still useful, but `provider + bucket + objectKey` remains the durable identity truth.

---

## 12. Error Semantics

The API should keep explicit and production-meaningful failure semantics.

Expected categories:

- `400`
  - invalid purpose
  - unsupported media type
  - unsupported content type
  - object key does not match ticket
  - metadata mismatch
- `401`
  - unauthenticated
- `404`
  - draft not found for current user
- `409`
  - duplicate or stale business binding conflict if introduced
- `500`
  - storage provider inspection failure
  - inconsistent internal storage state

This keeps the storage and content upload path aligned with the error-semantics discipline already established in authentication and draft writing.

---

## 13. Test Strategy

## 13.1 Unit tests

Add unit tests for storage policy behavior:

- reject unsupported content type
- reject unsupported media type
- generate correct object key prefix for draft media
- validate ticket mismatch scenarios

## 13.2 Flyway integration test

Extend migration verification to assert:

- `media_ref` new columns exist
- uniqueness/index expectations exist when practical to assert

## 13.3 HTTP integration tests

Add a dedicated `StorageHttpIntegrationTest` that covers:

1. presign draft media upload successfully
2. upload object through local mock URL
3. confirm media successfully and write `media_ref`
4. list draft media successfully
5. reject unsupported content type
6. reject foreign draft access with `404`
7. reject binding media to frozen drafts
8. keep confirm idempotent for retries
9. reject expired or mismatched upload tickets

## 13.4 Runtime smoke validation

Run a real backend smoke flow in the local dev runtime:

1. register
2. login
3. create draft
4. call `/api/storage/presign`
5. upload bytes through the returned URL
6. confirm media binding
7. query draft media list

This is required because direct upload chains often fail in ways that unit tests cannot fully expose.

---

## 14. Scope Boundaries For Implementation

The implementation is considered complete for `Step 2.2` when:

- generic draft-media pre-sign works
- confirmation inspects storage metadata before binding
- media metadata is stored in `media_ref`
- draft media can be queried
- local mock and MinIO paths both remain supported through the same storage abstraction
- the design is clearly extensible to future OSS/CDN providers

The implementation is **not** required to:

- render uploaded media in the frontend editor
- publish notes with media yet
- implement video upload or attachment upload policy
- generate signed downloads
- process media asynchronously

---

## 15. Recommended Implementation Direction

The safest implementation path is:

1. evolve the generic `storage` subdomain rather than duplicating avatar logic
2. keep avatar API externally stable for now
3. add signed upload tickets and inspection capability
4. let content own `media_ref` writes and media queries
5. prove the chain with integration tests before moving to `Step 2.3`

This keeps `Step 2.2` close to real production design while staying within the phase boundary.
