# MOZhi DevOps Scaffold

This directory holds local deployment skeletons only. It is intentionally limited
to environment and application bootstrapping so the project can evolve without
locking the team into runtime details too early.

## Included

- `docker-compose-environment.yml`: local MySQL, Redis, Kafka, MinIO.
- `docker-compose-local.yml`: local frontend + backend runtime without Nginx.
- `docker-compose-app.yml`: production-oriented runtime with Nginx as the single entrypoint.
- `nginx/`: reverse-proxy config for static frontend delivery and backend proxying.
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
middleware together, the first startup should run:

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-local.yml up --build
```

For day-to-day development, use:

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-local.yml up
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

源文件修改后：

- frontend source changes trigger Vite hot reload immediately
- backend source, mapper, and YML changes trigger an automatic package + app restart inside the dev container
- changes to Dockerfiles or base image layers still require `--build`

## Production-Oriented Docker Runtime

For a single-entry deployment shape with Nginx in front of the frontend and backend:

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-app.yml up --build -d
```

This stack uses:

- a Spring Boot backend container
- an Nginx gateway container
- frontend static assets built into the gateway image

URLs:

- Site entry: `http://127.0.0.1:8080`
- Backend health through gateway: `http://127.0.0.1:8080/api/health`
- Swagger through gateway: `http://127.0.0.1:8080/swagger-ui/index.html`

Stop command:

```powershell
docker compose -f .\docs\dev-ops\docker-compose-environment.yml -f .\docs\dev-ops\docker-compose-app.yml down
```

Implementation note:

- local runtime keeps frontend and backend on separate ports for easier debugging
- production-oriented runtime adds Nginx for a single entrypoint and reverse proxy behavior
- the compose file defaults `MOZHI_AUTH_COOKIE_SECURE=false` only so plain HTTP local verification remains usable
- real HTTPS deployment should override `MOZHI_AUTH_COOKIE_SECURE=true`

If you keep the local runtime and the production-oriented runtime up at the same time, Docker Compose may warn that the other overlay's app containers are "orphans". That is expected because both overlays share the same project name and middleware services. Do not add `--remove-orphans` unless you explicitly want to tear down the other runtime shape.

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

The production-oriented runtime additionally uses:

- `VITE_TURNSTILE_SITE_KEY` as a frontend build arg
- `MOZHI_STORAGE_LOCAL_PUBLIC_ENDPOINT` if local storage mock URLs should resolve through the gateway entrypoint

Example local startup:

```powershell
$env:MOZHI_AUTH_COOKIE_SECURE = "false"
$env:MOZHI_AUTH_TURNSTILE_ALLOWED_HOSTNAMES = "localhost,127.0.0.1"
.\app\start.ps1
```

For local development, use Cloudflare test keys or real keys explicitly bound to `localhost` / `127.0.0.1`.
Never commit secret values into the repository.
