# Spring Boot Admin — Monitoring Scenarios

Real-world debugging playbooks. Find your symptom, follow the steps. Every step points at a specific Admin UI tab or actuator endpoint from [SPRING_BOOT_ADMIN_GUIDE.md § 6](SPRING_BOOT_ADMIN_GUIDE.md#6-feature-by-feature-guide) / [ADMIN_QUICK_REFERENCE.md](ADMIN_QUICK_REFERENCE.md).

---

## Scenario 1 — "A user is getting a 400/500 error"

1. **Loggers tab** → bump `com.ebank` to `DEBUG` for the affected package (e.g. `com.ebank.transaction`) — takes effect immediately, no restart.
   ```bash
   curl -X POST $BASE/actuator/loggers/com.ebank.transaction \
     -H "Content-Type: application/json" -d '{"configuredLevel":"DEBUG"}'
   ```
2. Reproduce the failing request.
3. Read the log output (`docker logs ebank_app -f` or your log aggregator — Loki/Kibana per [doc/OBSERVABILITY.md](doc/OBSERVABILITY.md)) for the stack trace / validation error tied to that request's `correlationId`.
4. **Mappings tab** — confirm the request actually hit the controller method you expect (rules out a routing/path mismatch).
5. Set the logger back to its default once done: `{"configuredLevel":null}`.

---

## Scenario 2 — "Memory keeps growing" (suspected leak)

1. **JVM tab** → watch `jvm.memory.used` (heap) over a few minutes. A sawtooth that returns to baseline after GC is normal; a staircase that never drops is a leak signal.
2. **Metrics tab** → check `jvm.gc.pause` — frequent, lengthening GC pauses confirm memory pressure.
3. **Thread dump tab** (or `curl $BASE/actuator/threaddump`) → take two dumps 60 seconds apart and diff them. Threads stuck in the same state/stack across both snapshots point at the leaking code path (e.g. an unclosed connection, an ever-growing in-memory cache).
4. **Caches tab** → rule out an application-level cache growing unbounded; check Redis cache sizes are respecting the TTLs documented in [README.md](README.md#caching) (`accounts` 5 min, `account` 10 min, `transactions` 5 min).
5. If it's the JVM heap and not Redis, capture a heap dump out-of-band (`jmap` / container restart with `-XX:+HeapDumpOnOutOfMemoryError`) — Admin UI doesn't do full heap dump analysis, only points you at *when* to take one.

---

## Scenario 3 — "An endpoint suddenly got slow"

1. **HTTP traffic / Metrics tab** → filter `http.server.requests` by `uri` for the slow endpoint; compare the latency percentile now vs. an hour ago.
2. **Metrics tab** → check `hikaricp.connections.active` vs `hikaricp.connections.max`. If active is pinned at max, requests are queueing for a DB connection.
   ```bash
   curl $BASE/actuator/metrics/hikaricp.connections.active
   ```
3. **Caches tab** → if the endpoint is `@Cacheable` (accounts/transactions listing), check whether the cache hit rate dropped — a cold cache after a deploy, or Redis itself being slow, will push load straight to Postgres.
4. **Thread dump** → look for many threads blocked on the same lock or waiting on a JDBC call — indicates DB contention rather than application logic.
5. If traces are enabled (Tempo), jump to the correlated trace for a slow request to see exactly which span (auth / service / DB query) ate the time — see [doc/OBSERVABILITY.md § Tracing](doc/OBSERVABILITY.md#tracing-tempo--opentelemetry).

---

## Scenario 4 — "DB connection pool exhausted"

1. **Metrics tab** → `hikaricp.connections.active`, `hikaricp.connections.pending`. Pending > 0 sustained means requests are waiting.
2. **Health tab** → the `db` health indicator will flip to `DOWN` if the pool can't service a validation query at all.
3. **Thread dump** → threads parked in `HikariPool.getConnection` confirm the bottleneck is connection acquisition, not query execution.
4. Check for a long-running transaction holding a connection open (missing `@Transactional` boundary, or a slow query) rather than assuming the pool size itself is too small.

---

## Scenario 5 — "Cache hit rate dropped / Redis looks wrong"

1. **Caches tab** → confirm the cache names (`accounts`, `account`, `transactions`) exist and aren't empty right after a deploy (cold start is expected; sustained low hit rate is not).
2. **Health tab** → `redis` indicator — if it's `DOWN`, the app has silently fallen back to hitting Postgres directly for every read (by design, see [README.md § Caching](README.md#caching)), and the rate-limit counter falls back to in-memory too.
3. `curl $BASE/actuator/caches` for raw JSON if you need to script a check.
4. If a specific cache is stale after a manual DB change, clear it: `curl -X DELETE $BASE/actuator/caches/accounts`.

---

## Scenario 6 — "Login rate limiting is triggering unexpectedly"

1. **Environment tab** (or `curl $BASE/actuator/env/rate-limiting.login.capacity`) → confirm the configured capacity for the active profile (local: 10/min, dev: 20/min, prod: 10/min — see [README.md § Environments](README.md#environments)).
2. **Health tab** → check `redis` — if Redis is down, the rate limiter falls back to an **in-memory** counter, which is per-instance and will behave differently behind a load balancer with multiple replicas (each instance gets its own limit).
3. **Loggers tab** → set `com.ebank` to `DEBUG` to see individual rate-limit decisions logged per IP.

---

## Scenario 7 — "Flyway migration failed on deploy"

> Requires the Flyway starter to be enabled — it currently ships commented out in `pom.xml` for a Spring Boot 4 / PostgreSQL 16 compatibility reason (see [SPRING_BOOT_ADMIN_GUIDE.md § 6](SPRING_BOOT_ADMIN_GUIDE.md#6-feature-by-feature-guide), Flyway row).

1. **Flyway tab** (or `curl $BASE/actuator/flyway`) → shows every migration script, its checksum, and status (`Success`/`Failed`/`Pending`).
2. **Health tab** → a failed migration usually takes the whole app health check down, since Flyway blocks context startup by default.
3. Cross-reference the failing script's version/description against your migration files under `src/main/resources/db/migration` (or wherever this project's Flyway location is configured) and fix forward with a new migration — never edit an already-applied script.

---

## Scenario 8 — "Logs are too noisy in prod / too quiet in dev"

1. **Loggers tab** → check the current effective level for `root` and `com.ebank`.
2. Change it live for a temporary investigation:
   ```bash
   curl -X POST $BASE/actuator/loggers/com.ebank \
     -H "Content-Type: application/json" -d '{"configuredLevel":"DEBUG"}'
   ```
3. For a **permanent** change, edit `logging.level.*` in the right `application-{profile}.yaml` (or the Vault seed for dev/prod — `logging.level.root` / `logging.level.com.ebank` in `vault/seeds/E1.json` / `E2.json`) and redeploy; runtime changes via the Loggers tab reset on restart.

---

## Related Docs

[SPRING_BOOT_ADMIN_INDEX.md](SPRING_BOOT_ADMIN_INDEX.md) · [SPRING_BOOT_ADMIN_GUIDE.md](SPRING_BOOT_ADMIN_GUIDE.md) · [ADMIN_QUICK_REFERENCE.md](ADMIN_QUICK_REFERENCE.md) · [doc/OBSERVABILITY.md](doc/OBSERVABILITY.md)
