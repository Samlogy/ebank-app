# Spring Boot Admin — Quick Reference

Bookmark this page. Everything below is copy-paste. For explanations, see [SPRING_BOOT_ADMIN_GUIDE.md](SPRING_BOOT_ADMIN_GUIDE.md).

---

## Start / Stop

```bash
./start-with-admin.sh          # local profile (Postgres, Redis, Admin, App)
./start-with-admin.sh dev      # + Vault, E2 config
./start-with-admin.sh prod     # + Vault, E1 config
./start-with-admin.sh stop     # docker compose down
./start-with-admin.sh clean    # docker compose down -v (also removes volumes)
```

Manual equivalents:

```bash
docker compose up -d postgres redis      # dependencies
docker compose up -d admin                # Admin server → :8090
docker compose up -d --build app          # App → :8080/8081
docker compose logs -f admin              # tail admin server logs
docker compose logs -f app                # tail app logs
docker compose down                       # stop everything
```

---

## URLs & Credentials

| Service | URL | Default login |
|---|---|---|
| Admin UI | http://localhost:8090 | `admin` / `admin` (`ADMIN_UI_USER` / `ADMIN_UI_PASSWORD`) |
| App (local profile) | http://localhost:8081 | JWT (register via `/api/v1/auth/register`) |
| App (docker-compose default) | http://localhost:8080 | JWT |

---

## Health Checks

```bash
# Admin server itself
curl -u admin:admin http://localhost:8090/actuator/health

# The app
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/health/liveness
curl http://localhost:8081/actuator/health/readiness
```

---

## Actuator Endpoint Cheat Sheet

All commands below target the app directly (`localhost:8081` locally, `localhost:8080` in Docker Compose default). No auth is required — `/actuator/**` is `permitAll` and relies on network isolation (see [SPRING_BOOT_ADMIN_GUIDE.md § 9](SPRING_BOOT_ADMIN_GUIDE.md#9-security-model)).

```bash
BASE=http://localhost:8081

# Health, info
curl $BASE/actuator/health
curl $BASE/actuator/info

# JVM / HTTP metrics
curl $BASE/actuator/metrics                                  # list all metric names
curl $BASE/actuator/metrics/jvm.memory.used
curl $BASE/actuator/metrics/http.server.requests
curl $BASE/actuator/prometheus                                # raw Prometheus scrape format

# Active properties (secret values redacted)
curl $BASE/actuator/env
curl "$BASE/actuator/env/spring.datasource.url"

# Loggers — read current level
curl $BASE/actuator/loggers/com.ebank

# Loggers — change level at runtime (no restart)
curl -X POST $BASE/actuator/loggers/com.ebank \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'

# Reset a logger back to inherited default
curl -X POST $BASE/actuator/loggers/com.ebank \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":null}'

# Redis cache stats / names
curl $BASE/actuator/caches

# Evict one cache
curl -X DELETE $BASE/actuator/caches/accounts

# Thread dump (full JSON)
curl $BASE/actuator/threaddump

# Flyway migration history
curl $BASE/actuator/flyway

# Scheduled jobs (empty today — no @Scheduled beans yet)
curl $BASE/actuator/scheduledtasks

# HTTP routes registered by the app
curl $BASE/actuator/mappings

# Auto-configuration report (why a bean did/didn't load)
curl $BASE/actuator/conditions

# Resolved @ConfigurationProperties values
curl $BASE/actuator/configprops

# Full Spring bean graph
curl $BASE/actuator/beans
```

> `dev`/`prod` profiles expose a narrower endpoint set — see the per-environment table in [README.md](README.md#spring-boot-admin) or `management.endpoints.web.exposure.include` in `application-dev.yaml` / `application-prod.yaml`.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Can't run `./start-with-admin.sh` | Script isn't executable | `chmod +x start-with-admin.sh` |
| Can't reach `http://localhost:8090` | Docker not running, or admin container failed | `docker --version`; `docker compose logs admin` |
| Admin UI login rejects `admin`/`admin` | `.env` overrides `ADMIN_UI_USER`/`ADMIN_UI_PASSWORD` | `grep ADMIN_UI .env` and use those values |
| App never appears under "Applications" in the UI | The `spring-boot-admin-starter-client` Maven dependency is commented out in `monolith/pom.xml` (known, documented) | See [SPRING_BOOT_ADMIN_GUIDE.md § 3.8](SPRING_BOOT_ADMIN_GUIDE.md#38-️-known-caveat--the-client-dependency-ships-commented-out) to enable real registration; until then, use the curl commands above directly |
| App shows "OFFLINE" in the UI after registering once | Health check / heartbeat failing, often a Docker network / DNS issue | `docker logs ebank_app \| tail -50`; confirm `SPRING_APP_BASE_URL` is reachable **from the admin container**, not just from your host |
| `401 Unauthorized` calling `/actuator/**` directly | You're hitting the Admin UI port (8090) instead of the app port, or a proxy is in front | Actuator on the **app** is `permitAll`; only the Admin UI itself (8090) requires login |
| Logger level change doesn't stick after restart | Runtime changes via `/actuator/loggers` are in-memory only | Set `logging.level.<package>` in the relevant `application-*.yaml` for a permanent change |
| Flyway / scheduled tasks tab always empty | Endpoint enabled but nothing to show, or Flyway starter is commented out (see `pom.xml`) | Expected in this project today — no `@Scheduled` jobs exist yet, and the Flyway starter ships commented out alongside the admin client for the same Spring Boot 4 compatibility reason |
| `curl` to `/actuator/env` shows keys but no values | `management.endpoint.env.show-values: never` | Intentional — prevents secret leakage; use `docker exec` + the running JVM, or Vault directly, to confirm an actual value |

---

## Related Docs

[SPRING_BOOT_ADMIN_INDEX.md](SPRING_BOOT_ADMIN_INDEX.md) · [SPRING_BOOT_ADMIN_GUIDE.md](SPRING_BOOT_ADMIN_GUIDE.md) · [ADMIN_MONITORING_SCENARIOS.md](ADMIN_MONITORING_SCENARIOS.md) · [SPRING_ADMIN_SETUP_COMPLETE.md](SPRING_ADMIN_SETUP_COMPLETE.md)
