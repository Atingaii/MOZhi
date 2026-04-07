# MOZhi DevOps Scaffold

This directory holds local deployment skeletons only. It is intentionally limited
to environment and application bootstrapping so the project can evolve without
locking the team into runtime details too early.

## Included

- `docker-compose-environment.yml`: local MySQL, Redis, Kafka, MinIO.
- `docker-compose-app.yml`: placeholder application runtime wiring.
- `mysql/`: base MySQL configuration and SQL bootstrap slot.
- `redis/`: base Redis configuration.
- `app/`: Windows-oriented helper scripts for local startup and shutdown.

## Local Backend

- Start local middleware plus the backend app: `.\app\start.ps1`
- Stop the backend app and local middleware: `.\app\stop.ps1`

The startup script packages `mozhi-backend/mozhi-app`, starts the Docker middleware
set, launches the Spring Boot jar in the background, and writes logs to the repo
root `logs/` directory.

## MinIO Direct Upload

To verify the real avatar direct-upload path instead of the local mock fallback:

```powershell
$env:MOZHI_STORAGE_MINIO_ENABLED = "true"
.\app\start.ps1
```

The startup script will initialize the `mozhi-assets` bucket and set anonymous
download access for local direct-upload verification.

## Auth Runtime Flags

The backend auth flow now uses:

- in-memory `accessToken` on the web client
- `mozhi_refresh_token` `HttpOnly` cookie for refresh

Relevant environment variables:

- `MOZHI_AUTH_COOKIE_SECURE`
  Set to `false` for local HTTP development and `true` behind HTTPS.
- `MOZHI_AUTH_CHALLENGE_PROVIDER`
  Defaults to `noop` for local challenge escalation verification.
- `MOZHI_AUTH_CHALLENGE_NOOP_PASS_TOKEN`
  Pass token used by the `noop` provider. Default is `dev-pass`.

Example local startup:

```powershell
$env:MOZHI_AUTH_COOKIE_SECURE = "false"
.\app\start.ps1
```
