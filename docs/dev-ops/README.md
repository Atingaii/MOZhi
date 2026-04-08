# MOZhi DevOps Scaffold

This directory holds local deployment skeletons only. It is intentionally limited
to environment and application bootstrapping so the project can evolve without
locking the team into runtime details too early.

## Included

- `docker-compose-environment.yml`: local MySQL, Redis, Kafka, MinIO.
- `docker-compose-local.yml`: local frontend + backend runtime without Nginx.
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

## Local Docker Runtime

If you want a host-light local runtime with Docker managing frontend, backend, and
middleware together, run:

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-local.yml up --build
```

This local stack intentionally does **not** include Nginx.

Reasoning:

- the local goal is fastest startup and easiest debugging
- exposing frontend and backend on separate ports keeps failures obvious
- Nginx belongs in a later production-oriented compose profile, not the default local path

Local Docker URLs:

- Frontend: `http://127.0.0.1:5173`
- Backend: `http://127.0.0.1:8090/api/health`
- Swagger: `http://127.0.0.1:8090/swagger-ui/index.html`

Stop command:

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-local.yml down
```

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
  Set to `false` for local HTTP development and `true` behind HTTPS. The repository default now targets the secure shape.
- `MOZHI_AUTH_CHALLENGE_PROVIDER`
  Defaults to `turnstile`.
- `MOZHI_AUTH_TURNSTILE_SECRET_KEY`
  Backend-only Turnstile secret key. Never commit it.
- `MOZHI_AUTH_TURNSTILE_ALLOWED_HOSTNAMES`
  Comma-separated hostname allow-list used after server-side verification.
- `VITE_TURNSTILE_SITE_KEY`
  Frontend Turnstile site key.

The local Docker runtime also reads:

- `MOZHI_AUTH_TURNSTILE_SECRET_KEY`
- `VITE_TURNSTILE_SITE_KEY`

Example local startup:

```powershell
$env:MOZHI_AUTH_COOKIE_SECURE = "false"
$env:MOZHI_AUTH_TURNSTILE_ALLOWED_HOSTNAMES = "localhost,127.0.0.1"
.\app\start.ps1
```

For local development, use Cloudflare test keys or real keys explicitly bound to `localhost` / `127.0.0.1`.
Never commit secret values into the repository.
