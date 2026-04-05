# MOZhi Bootstrap Structure

## Decision

The backend scaffold follows the `xfg-ddd-skills` layering style but is adapted to
this repository's stricter module rules:

- Keep the backend as a single Maven multi-module project under `mozhi-backend/`.
- Use fixed modules only: `mozhi-api`, `mozhi-app`, `mozhi-domain`,
  `mozhi-infrastructure`, `mozhi-trigger`, `mozhi-types`.
- Do not create a top-level `mozhi-case` module at this stage because the local
  repository rules define a fixed six-module backend layout.

## Backend layout

- `mozhi-types`: shared enums, exceptions, events, constants.
- `mozhi-api`: external contracts and DTO definitions.
- `mozhi-domain`: bounded contexts and domain-facing adapter contracts.
- `mozhi-infrastructure`: repository implementations, DAO, gateway, cache, event integration.
- `mozhi-trigger`: HTTP, listener, job, RPC entry adapters.
- `mozhi-app`: Spring Boot bootstrap, runtime configuration, mapper resources.

## Domain slices reserved

- `auth`
- `user`
- `content`
- `social`
- `commerce`
- `ai`
- `search`
- `feed`
- `counter`
- `storage`

Each slice already contains the folder shape required by the repository rules:

- `adapter/port`
- `adapter/repository`
- `model/aggregate`
- `model/entity`
- `model/valobj`
- `service`

## Frontend layout

The web client is isolated under `mozhi-web/` and starts as a typed React shell:

- `src/router`: route registration only.
- `src/layouts`: page shells and composition.
- `src/pages`: route-level placeholders.
- `src/api`: centralized API entry points.
- `src/stores`: local state entry points.
- `src/components`: shared UI and layout primitives.

## Current limit

This commit intentionally avoids business implementation. The scaffold is only a
structural base for the first real bounded-context slice.

