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
