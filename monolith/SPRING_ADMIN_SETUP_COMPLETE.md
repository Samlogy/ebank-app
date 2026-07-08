# Spring Boot Admin — Setup Summary

Inventory of every file and configuration key that makes up the Spring Boot Admin feature in this repo, plus its current status. Use this as a checklist when reviewing the feature or porting it to another environment.

---

## Status

| Area | Status |
|---|---|
| Admin server (standalone app, UI, login) | ✅ Working — `docker compose up -d admin` serves the UI at `:8090` |
| Actuator exposure on the main app | ✅ Working — per-profile endpoint sets in `application*.yaml` |
| Docker Compose wiring | ✅ Working — `admin` service + `app` env vars in `docker-compose.yml` |
| Vault-backed config (dev/prod) | ✅ Working — `spring.boot.admin.client.*` keys in `vault/seeds/E1.json` / `E2.json` |
| Documentation | ✅ Complete — see table below |
| **App → Admin server self-registration** | ✅ **Active** — `spring-boot-admin-starter-client` 4.0.4 is enabled in `monolith/pom.xml`. Verified locally: the app logs `Application registered itself as <id>` on startup and appears in `GET /instances` on the admin server with `"registered": true`. |

---

## Files that make up this feature

### Admin server module — `monolith/admin/`

| File | Purpose |
|---|---|
| `pom.xml` | `spring-boot-admin-starter-server` 4.0.4, Spring Boot 4.0.6 parent, web + security + actuator starters |
| `src/main/java/com/ebank/admin/AdminServerApplication.java` | `@SpringBootApplication` + `@EnableAdminServer` |
| `src/main/java/com/ebank/admin/config/SecurityConfig.java` | Form login + HTTP Basic; CSRF exceptions for `/instances/**` and `/actuator/**` |
| `src/main/resources/application.yaml` | Port `8090`; login credentials from `ADMIN_UI_USER`/`ADMIN_UI_PASSWORD`; exposes only `health,info` on the server itself |
| `Dockerfile` | Multi-stage Maven → JRE Alpine build, matches the main app's pattern |

### Main app — `monolith/`

| File | Relevant change |
|---|---|
| `pom.xml` | `spring-boot-admin.version` property (`4.0.4`, matching the admin server and the Spring Boot 4 parent); `spring-boot-admin-starter-client` dependency active |
| `src/main/resources/application.yaml` | Base `spring.boot.admin.client.*` config (disabled by default); actuator exposure list; `health.probes.enabled=true`; `env.show-values=never`; `loggers.enabled=true`; `caches.enabled=true` |
| `src/main/resources/application-local.yaml` | Admin client enabled, points at `localhost:8090` |
| `src/main/resources/application-dev.yaml` | Admin client enabled via env vars, full actuator surface + `prometheus` |
| `src/main/resources/application-prod.yaml` | Admin client enabled via env vars, tighter actuator surface (`health,info,metrics,prometheus,loggers`) |
| `src/main/java/com/ebank/common/security/SecurityConfig.java` (or equivalent) | `/actuator/**` set to `permitAll` — relies on network isolation, not per-endpoint auth |

### Infrastructure

| File | Change |
|---|---|
| `docker-compose.yml` | `admin` service (build `./admin`, port `8090`, health check); `app` service gets `SPRING_BOOT_ADMIN_*` env vars and `depends_on: admin (service_healthy)` |
| `.env.example` | `ADMIN_UI_USER` / `ADMIN_UI_PASSWORD` |
| `vault/seeds/E1.json` (prod) | `spring.boot.admin.client.enabled/url/username/password/instance.service-base-url`, `management.endpoints.web.exposure.include` |
| `vault/seeds/E2.json` (dev) | Same keys, dev-appropriate values (wider actuator exposure) |
| `start-with-admin.sh` | One-liner script: brings up dependencies → admin → app in the right order for `local`/`dev`/`prod`, with health-check polling |

### Documentation

| File | Covers |
|---|---|
| `README.md` (§ Spring Boot Admin) | Repo-level architecture diagram + endpoint table |
| `doc/OBSERVABILITY.md` (§ Spring Boot Admin) | How SBA fits with Prometheus/Grafana/Tempo/Loki |
| `START_HERE.md` | Entry point, 30-second quick start |
| `SPRING_BOOT_ADMIN_INDEX.md` | Navigation across all Admin docs |
| `SPRING_BOOT_ADMIN_GUIDE.md` | Full setup + feature-by-feature guide (the main reference) |
| `ADMIN_QUICK_REFERENCE.md` | Commands, curl one-liners, troubleshooting table |
| `ADMIN_MONITORING_SCENARIOS.md` | Incident debugging playbooks |
| `SPRING_ADMIN_SETUP_COMPLETE.md` | This file |

---

## Known limitations (by design or pending follow-up)

1. **Flyway starter still ships commented out** in `pom.xml` for a separate Spring Boot 4 / PostgreSQL 16 compatibility note — the Flyway tab in Admin will show nothing until it's re-enabled. Schema is currently created via `spring.jpa.hibernate.ddl-auto` instead (`update` in Docker Compose / dev, `validate` — schema must pre-exist — if you run the app standalone via Maven).
2. **No notifier configured** — status-change notifications (email/Slack/etc.) are not set up; the Admin UI's own Journal is the only history today.
3. **No `@Scheduled` jobs exist yet** — the Scheduled Tasks tab will be empty until one is added to the codebase.
4. **HTTP exchanges / recent-traces tab and Log file tab are not enabled** — both require additional actuator endpoints/beans not configured today (see [SPRING_BOOT_ADMIN_GUIDE.md § 7](SPRING_BOOT_ADMIN_GUIDE.md#7-enabling-extra-features-optional)).

---

## Suggested next steps

- [ ] Re-enable the Flyway starter if migration history in the Admin UI is needed.
- [ ] Change `ADMIN_UI_PASSWORD` / `SPRING_BOOT_ADMIN_PASSWORD` away from `admin`/`admin` before any shared or public deployment.
- [ ] Decide whether a notifier (Slack/email/PagerDuty) is worth adding for production status-change alerts.
