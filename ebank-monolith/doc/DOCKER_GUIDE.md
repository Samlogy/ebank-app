# Docker Guide — eBank Monolith

## Overview

The stack contains two containers:

| Container | Image | Host Port | Purpose |
|-----------|-------|-----------|---------|
| `ebank_postgres` | postgres:15-alpine | 5432 | PostgreSQL database |
| `ebank_app` | ebank-monolith-app | 8081→8080 | Spring Boot API |

> The app is mapped to **8081** on the host so it does not conflict with other services that may use 8080. Change the mapping in `docker-compose.yml` if 8080 is free on your machine.

---

## Prerequisites

- Docker ≥ 24 and Docker Compose v2 (`docker compose` not `docker-compose`)
- Ports 8081 and 5432 available (or change the host-side mappings)

---

## Quick Start

```bash
# 1. Clone / enter the project
cd ebank-monolith

# 2. Create your environment file
cp .env.example .env
# Edit .env if you want different credentials (JWT_SECRET must be >= 64 chars)

# 3. Build and start the full stack
docker compose up -d --build

# 4. Wait for the app to become healthy (~30 s)
docker compose ps
# Both containers should show "(healthy)"

# 5. Verify
curl http://localhost:8081/actuator/health
# {"status":"UP"}
```

---

## Environment Variables (`.env`)

| Variable | Default | Required | Notes |
|----------|---------|----------|-------|
| `POSTGRES_USER` | `ebank_user` | Yes | DB username |
| `POSTGRES_PASSWORD` | `ebank_password` | Yes | DB password |
| `POSTGRES_DB` | `ebank_dev` | Yes | Database name |
| `JWT_SECRET` | *(see .env)* | Yes | Must be **>= 64 bytes** (HS512). Generate: `openssl rand -hex 64` |

The `.env` file is **git-ignored** — never commit it. Commit only `.env.example`.

---

## API Endpoints

Base URL: `http://localhost:8081`

### Authentication

```bash
# Register
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Secret123!","fullName":"Alice Smith"}'

# Login
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Secret123!"}'

# Profile (requires Bearer token)
curl http://localhost:8081/api/v1/auth/me \
  -H "Authorization: Bearer <TOKEN>"
```

### Accounts

```bash
# Create account
curl -X POST http://localhost:8081/api/v1/accounts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{"accountType":"CHECKING"}'  # CHECKING | SAVINGS | INVESTMENT

# List my accounts
curl http://localhost:8081/api/v1/accounts \
  -H "Authorization: Bearer <TOKEN>"

# Get single account
curl http://localhost:8081/api/v1/accounts/<id> \
  -H "Authorization: Bearer <TOKEN>"
```

### Transactions

```bash
# Transfer between accounts
curl -X POST http://localhost:8081/api/v1/transactions/accounts/<fromId>/transfer \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{"toAccountId":<toId>,"amount":50.00,"description":"Rent"}'

# Transaction history for an account
curl http://localhost:8081/api/v1/transactions/accounts/<id>/history \
  -H "Authorization: Bearer <TOKEN>"
```

### Other

| URL | Description |
|-----|-------------|
| `GET /actuator/health` | Health check (no auth) |
| `GET /swagger-ui/index.html` | Interactive API docs |

---

## Day-to-day Commands

```bash
# Start (detached)
docker compose up -d

# Start with a fresh image build
docker compose up -d --build

# Stop (preserves data volume)
docker compose stop

# Stop and remove containers (preserves data volume)
docker compose down

# Stop and remove everything including the database volume
docker compose down -v

# Follow live logs
docker compose logs -f

# App logs only
docker compose logs -f app

# Check container health
docker compose ps

# Open a psql shell against the running database
docker exec -it ebank_postgres psql -U ebank_user -d ebank_dev
```

---

## Dockerfile — What Each Stage Does

```
Stage 1 (deps)    — maven:3.9.6-eclipse-temurin-17
                    Copies pom.xml → downloads ALL dependencies offline.
                    This layer is cached: only invalidated when pom.xml changes,
                    not when source code changes.

Stage 2 (builder) — inherits from deps
                    Copies src/ → runs mvn package -DskipTests.
                    Only rebuilt when source code changes (fast: deps already resolved).

Stage 3 (runtime) — eclipse-temurin:17-jre-alpine  (~80 MB smaller than JDK)
                    Copies only the final JAR from builder.
                    Runs as non-root user "ebank" (security best practice).
                    JVM flags: -XX:+UseContainerSupport (respects cgroup memory limits)
                               -XX:MaxRAMPercentage=75.0 (uses 75% of container RAM)
```

**Rebuild times after the first build:**

| What changed | Rebuild time |
|---|---|
| Source code only | ~15 s (deps cached, only compile + package) |
| `pom.xml` | ~80 s (deps must be re-downloaded) |
| Nothing | instant (fully cached) |

---

## Resource Allocation (Optional)

To cap the container's RAM and CPU, add `deploy.resources` under `app` in `docker-compose.yml`:

```yaml
    deploy:
      resources:
        limits:
          memory: 512m
          cpus: '1.0'
        reservations:
          memory: 256m
```

The JVM will automatically adapt via `-XX:+UseContainerSupport`.

---

## Production Checklist

- [ ] Replace `JWT_SECRET` with a 64-byte+ random value: `openssl rand -hex 64`
- [ ] Use strong, unique `POSTGRES_PASSWORD`
- [ ] Set `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` (never `update` in prod)
- [ ] Change the host port mapping to `"8080:8080"` or put a reverse proxy in front
- [ ] Mount TLS certificates or place behind nginx/traefik with HTTPS
- [ ] Remove the `version: '3.8'` line from `docker-compose.yml` (obsolete in Compose v2)
- [ ] Use Docker secrets or a secrets manager instead of a `.env` file

---

## Troubleshooting

### `address already in use` on port 8081 / 5432
```bash
# Find what's using the port
ss -tlnp | grep 8081

# Or change the host port in docker-compose.yml:
ports:
  - "9090:8080"   # host:container
```

### App container exits immediately
```bash
docker logs ebank_app
```
Common causes: DB not ready (healthcheck should prevent this), bad JWT_SECRET length (must be ≥ 64 bytes for HS512), missing env var.

### App starts but returns 500 on all requests
```bash
docker logs ebank_app | grep "ERROR\|Exception"
```
Check for `WeakKeyException` → JWT_SECRET is too short. Fix: ensure `JWT_SECRET` in `.env` is ≥ 64 characters.

### Database connection refused
```bash
docker compose ps        # is postgres healthy?
docker logs ebank_postgres
```
If postgres is not healthy, check disk space and PostgreSQL logs.

### Reset the database
```bash
docker compose down -v   # removes the named volume
docker compose up -d     # starts fresh, Hibernate recreates all tables
```
