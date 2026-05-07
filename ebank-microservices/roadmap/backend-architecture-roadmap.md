# 🏗️ Backend Architecture Mastery Roadmap
*Practice-first, concept-driven, one evolving project across all phases*

**5 Phases · ~6 Months · 1 Project that grows with you**

---

## How to Use This Roadmap

Every module follows the same structure:
1. **Why this now** — the problem this concept solves
2. **What you learn** — the mental model, not just the syntax
3. **Practice steps** — numbered, ordered, executable
4. **Checkpoint** — concrete criteria to move on

One project — a Book Library system — grows across all five phases. By Phase 5 it's a production-grade distributed system on Kubernetes with full observability.

---

## Phase 1 — Foundation (Weeks 1–3)
> *Beginner. One service done right.*

**Goal:** Master what a single well-built service looks like. Every distributed pattern later depends on this.

---

### Module 1.1 — Spring Boot Anatomy

**Why this first:** You cannot debug what you don't understand. Most developers use Spring Boot without knowing what auto-configuration actually does.

**Concepts:**
- Auto-configuration — Spring scans the classpath and configures beans conditionally
- Application context lifecycle — beans created in dependency order, not declaration order
- Dependency Injection — constructor injection makes dependencies explicit and testable

**Practice:**
1. Create a Spring Boot 3 project (Web, Actuator, Lombok, Validation)
2. Build a `BookController` with GET, POST, PUT, DELETE using an in-memory `HashMap`
3. Add `@Valid` bean validation. Return proper errors via `@ExceptionHandler`
4. Explore Spring Actuator: `/actuator/health`, `/actuator/beans`, `/actuator/mappings`. Read every auto-created bean.
5. Write one `MockMvc` test per endpoint. Understand `@WebMvcTest` vs `@SpringBootTest`

**Checkpoint — move on when:**
- [ ] You can explain what happens line by line when Spring starts
- [ ] You know the difference between @Component, @Service, @Repository, @Controller
- [ ] Your API returns correct HTTP status codes (200, 201, 400, 404, 422)
- [ ] You can write a test that doesn't start the full Spring context

---

### Module 1.2 — Persistence & The Database Contract

**Why this now:** Every microservice owns its database. Understanding indexes and transactions prevents the production slowdowns you'd otherwise debug in 2 years.

**Concepts:**
- B+Tree indexes — maps column values to row pointers. Speeds reads, costs writes
- ACID transactions — Atomicity, Consistency, Isolation, Durability
- Isolation levels — READ COMMITTED vs REPEATABLE READ and when each is appropriate
- Flyway migrations — schema changes are code, versioned in source control

**Practice:**
1. Add PostgreSQL + Spring Data JPA. Run Postgres: `docker run -e POSTGRES_PASSWORD=pass -p 5432:5432 postgres:16`
2. Create `V1__create_books_table.sql` and `V2__add_author_index.sql`
3. Use `EXPLAIN ANALYZE` in psql — compare Seq Scan vs Index Scan on the author column
4. Write a test demonstrating transaction rollback: cause a failure mid-transaction, verify the DB reverts
5. Annotate service methods with `@Transactional`. Understand `readOnly=true`

**Checkpoint — move on when:**
- [ ] You can explain why EXPLAIN ANALYZE shows Seq Scan vs Index Scan
- [ ] You understand what `@Transactional(readOnly=true)` does
- [ ] Flyway runs automatically on startup
- [ ] You can demonstrate a rollback in a test

---

### Module 1.3 — Docker & 12-Factor Configuration

**Why this now:** Every service in this roadmap runs in Docker. Factor 3 (Config) is fundamental: configuration that varies between environments must never be in code.

**Concepts:**
- Multi-stage Dockerfile — build stage + minimal runtime stage
- Environment-based config — Spring reads `SPRING_DATASOURCE_URL` from env vars
- Health checks in Docker Compose — `depends_on: condition: service_healthy`

**Practice:**
1. Write a multi-stage Dockerfile. Compare image sizes between build and runtime stages.
2. Move ALL DB config to environment variables. `application.yml` contains `${DB_URL}`, `${DB_PASSWORD}` only.
3. Write `docker-compose.yml` with app + PostgreSQL + healthcheck using `pg_isready`
4. Verify `docker compose up` starts everything and serves requests
5. Add `docker-compose.override.yml` for local dev with different env vars

**Checkpoint — move on when:**
- [ ] `docker compose up` starts from zero in under 60 seconds
- [ ] No credentials anywhere in application.yml or Dockerfile
- [ ] Image is under 200MB
- [ ] App waits for Postgres to be healthy before starting

---

### 🏆 Phase 1 Project: Book Library API

Build a complete Book Library REST API with PostgreSQL, full CRUD, search by author/title, pagination, proper validation, and error handling. Runs via `docker compose up`.

**Must have:** Spring Boot 3 + Java 21, PostgreSQL + Flyway, Docker Compose, Actuator health endpoint, unit + integration tests, no credentials in code.

---

## Phase 2 — Data Layer & Async Foundations (Weeks 4–6)
> *Intermediate. Add Redis caching. Learn Kafka.*

**Goal:** Understand why these tools exist before using them. Cache correctly. Send your first event.

---

### Module 2.1 — Redis: Caching That Actually Works

**Why this now:** A cache hit takes ~0.1ms vs ~5ms for a DB query. But caching done wrong causes stale data bugs that are very hard to debug.

**Concepts:**
- Cache-Aside pattern — check cache first, on miss read DB + write to cache, on write evict cache
- TTL (Time-to-Live) — every cached value must expire. Choose based on acceptable staleness.
- Cache invalidation — use `@CacheEvict` on write operations. The hardest problem in caching.

**Practice:**
1. Add Redis to Docker Compose. Configure `RedisCacheManager` with 5-minute default TTL.
2. Annotate `findById()` with `@Cacheable("books")`. Verify only ONE SQL query runs on second call.
3. Add `@CacheEvict` on update/delete methods. Test cache invalidation works.
4. Use `redis-cli`: run `KEYS *`, `GET`, `TTL` on cached entries. Watch them expire.
5. Implement rate limiter using Redis `INCR` + `EXPIRE`: max 10 requests/minute per IP as a Spring interceptor.

**Checkpoint — move on when:**
- [ ] You can articulate what data is appropriate to cache (vs what isn't)
- [ ] You've observed stale data and fixed it with proper cache eviction
- [ ] You've used redis-cli to inspect cache state directly
- [ ] Your rate limiter works and you can explain the INCR + EXPIRE pattern

---

### Module 2.2 — Kafka: Your First Event-Driven Flow

**Why this now:** Kafka is an append-only log, not a queue. Messages are retained for days and can be replayed. This mental model is fundamentally different from RabbitMQ or BullMQ.

**Concepts:**
- Topics and Partitions — a topic is sharded into ordered logs. Parallelism from partitions.
- Consumer Groups — partitions shared within a group. Multiple groups = fan-out.
- Offsets — Kafka tracks read position per consumer group. Reset offset = replay.

**Practice:**
1. Add Kafka to Docker Compose (KRaft mode, no ZooKeeper). Add Spring Kafka.
2. Publish `BookCreatedEvent` (JSON) to `book-events` topic on book creation.
3. Create a consumer. Start two instances — observe Kafka rebalance partitions.
4. Use `kafka-console-consumer.sh --from-beginning` to replay all events.
5. Stop the consumer mid-processing. Restart — verify it resumes from committed offset.

**Checkpoint — move on when:**
- [ ] You can explain what happens to messages when the consumer is down (they wait)
- [ ] You've replayed events from the beginning
- [ ] You can see consumer group lag (use kafka-consumer-groups.sh)
- [ ] You understand why fan-out works with multiple consumer groups

---

### 🏆 Phase 2 Project: Extend Book API — Caching + Events

Add Redis caching (proper invalidation) and a Kafka event pipeline. Build a separate Notification Service that consumes book events and logs them. Your first multi-service interaction.

---

## Phase 3 — Distributed Systems (Weeks 7–12)
> *Advanced. Real microservices. Distributed transactions. Resilience.*

**Goal:** Split into real microservices. Handle the hard distributed systems problems: data consistency, cascading failures, authentication.

---

### Module 3.1 — Splitting the Monolith: Service Boundaries

**Concepts:**
- Config Server — one Git repo for all service configs. Services fetch at startup.
- Eureka — services register themselves. Gateway resolves names to addresses.
- Database-per-service — Order Service cannot query Book Service's database. Ever.

**Services to build:** Book Service, User Service, Order Service, Inventory Service — each with its own PostgreSQL database.

**Practice:**
1. Create Config Server. Create config Git repo with per-service YAML files.
2. Set up Eureka Server. Verify all services appear in the dashboard at localhost:8761.
3. Give each service its own PostgreSQL database (separate schemas in Docker Compose).
4. Add API Gateway routing: `/api/books/**` → Book Service, etc.
5. Order Service verifies book exists via OpenFeign call to Book Service (sync HTTP).

**Checkpoint:**
- [ ] `docker compose up` starts 3 services + 3 DBs + Config + Eureka + Gateway
- [ ] Changing config in Git repo affects the service after restart
- [ ] Order Service has zero SQL joins touching the books table
- [ ] All external traffic flows through Gateway on port 8080

---

### Module 3.2 — Saga Pattern: Distributed Transactions

**The problem:** ACID transactions don't cross service boundaries. Order + Inventory must stay consistent without a shared database transaction.

**Concepts:**
- Choreography-based Saga — services react to events. No central coordinator.
- Compensation — if step N fails, publish events to undo steps 1 to N-1.
- Idempotency — Kafka delivers at-least-once. Handle the same event twice safely.
- Dead Letter Topic (DLT) — repeated failures route to DLT instead of blocking consumers.

**The flow to implement:**
```
OrderCreated → Inventory Reserved → Payment Processed → Order Confirmed
                                  ↓ (payment fails)
                         InventoryReleased + OrderCancelled
```

**Practice:**
1. Publish `OrderCreatedEvent` when order is created (order is PENDING).
2. Inventory Service consumes it: check stock → `InventoryReservedEvent` or `InventoryFailedEvent`.
3. Order Service consumes result → update to CONFIRMED or CANCELLED.
4. Add compensation: `PaymentFailedEvent` → Inventory Service releases reserved stock.
5. Make handlers idempotent: store processed event IDs in `processed_events` table. Skip duplicates.

**Checkpoint:**
- [ ] You can draw the complete event flow (happy path + compensation) on a whiteboard
- [ ] Sending the same Kafka event twice doesn't double-reserve stock
- [ ] Failed events appear in the DLT
- [ ] You can explain why eventual consistency is acceptable here

---

### Module 3.3 — Resilience: Circuit Breaker & Security

**Concepts:**
- Circuit Breaker — CLOSED → OPEN (fail fast) → HALF-OPEN (test recovery). Prevents cascading failures.
- Fallback — acceptable degraded response when a dependency is down.
- JWT at Gateway — User Service issues JWTs. Gateway validates. Internal services trust `X-User-Id` header.

**Practice:**
1. Wrap Feign call to Book Service with `@CircuitBreaker`. Open after 5 failures in 10 seconds.
2. Write fallback method. Stop Book Service — verify orders get a graceful response, not 500.
3. Add `@Retry` with exponential backoff (3 attempts, 1s/2s/4s). Combine with circuit breaker.
4. Add JWT auth to User Service. Configure Gateway to validate JWT and reject unauthenticated requests.
5. Test: no JWT → 401. Valid JWT → success. Expired JWT → 401.

**Checkpoint:**
- [ ] Stopping Book Service doesn't crash Order Service (fallback works)
- [ ] You can see circuit state change in `/actuator/circuitbreakers`
- [ ] Unauthenticated requests return 401, not 500
- [ ] You can explain the difference between authentication and authorization

---

### 🏆 Phase 3 Project: Book Library Microservices System

4 microservices + 4 databases + Config Server + Eureka + API Gateway + JWT auth + Kafka Saga + Idempotent handlers + Circuit breaker + retry + fallback + DLT. Single `docker compose up`.

---

## Phase 4 — Observability (Weeks 13–18)
> *Expert. See inside your distributed system.*

**Goal:** Instrument everything. Build the full OTel → Prometheus/Tempo/Loki → Grafana pipeline. Answer any question about your running system.

---

### Module 4.1 — Metrics: Know Your System's Health

**Concepts:**
- RED Metrics — Rate (req/sec), Errors (error rate %), Duration (p50/p95/p99 latency)
- PromQL — `rate(http_requests_total[5m])` for request rate. `histogram_quantile(0.99, ...)` for p99.
- Custom business metrics — `Counter.builder("orders.created").register(registry).increment()`

**Practice:**
1. Add Prometheus + Grafana to Docker Compose. Configure Prometheus to scrape all services' `/actuator/prometheus`.
2. Build a Grafana dashboard: req/sec, error rate, p99 latency — one panel per service.
3. Add custom metrics: counter for orders created, counter for saga failures, gauge for PENDING orders.
4. Generate load with a shell script (100 POST requests). Watch dashboards update live.
5. Set up a Grafana alert: error rate > 5% for 2 minutes triggers notification.

**Checkpoint:**
- [ ] You can answer "orders per minute in the last hour?" from Grafana alone
- [ ] You have a custom metric tracking saga failure rate
- [ ] You can write PromQL for p99 latency of any endpoint
- [ ] An alert fires when you intentionally spike the error rate

---

### Module 4.2 — Distributed Tracing: Follow a Request Everywhere

**Why this is the key module:** Without distributed tracing, a 2-second slow request is a mystery across 4 services. With it, you see exactly where 1.8 of those seconds were spent.

**Concepts:**
- Trace context propagation — `traceparent` HTTP header + Kafka message headers
- OTel Collector — central hub. Routes metrics → Prometheus, traces → Tempo, logs → Loki.
- Span correlation — click a trace span → see exact log lines from that service at that moment

**Practice:**
1. Download OTel Java Agent. Add to Dockerfiles: `java -javaagent:/otel-agent.jar -jar app.jar`
2. Add OTel Collector + Tempo to Docker Compose. Configure pipeline: OTLP → Tempo + Prometheus.
3. Add Tempo as Grafana datasource. Create an order. Find its trace — see all 4 services as spans.
4. Force a Saga failure (zero inventory). Find the failure trace. See which span failed and why.
5. Add Loki. Configure Logback with JSON + traceId injection. Click a span → see correlated logs.

**Checkpoint:**
- [ ] You can find any order's complete trace in Grafana in under 30 seconds
- [ ] Trace spans cross Kafka message boundaries (trace ID in consumer logs)
- [ ] Clicking a trace span shows correlated log lines from that service
- [ ] You can identify the slowest step in the Saga from the trace timeline

---

### 🏆 Phase 4 Project: Full Observability Stack

OTel Java Agent + OTel Collector + Prometheus + Tempo + Loki + Grafana on your entire microservices system. Multi-service dashboard showing Saga success/failure rate, Kafka consumer lag, cache hit rate. Trace-to-log correlation working. Debug a real slow query using traces.

---

## Phase 5 — Production Readiness (Weeks 19–24)
> *Mastery. Deploy to Kubernetes. Break things on purpose.*

**Goal:** The system that has never been broken in production will be. Build and deploy a system that survives real failure.

---

### Module 5.1 — Kubernetes: From Docker Compose to Real Orchestration

**What K8s adds over Docker Compose:** automatic rescheduling, rolling deployments, horizontal auto-scaling, self-healing, declarative desired state.

**Concepts:**
- Deployments + Services — Deployment declares desired replicas. Service = stable DNS + load balancer.
- ConfigMaps + Secrets — K8s implementation of 12-Factor Factor 3. Injected as env vars at runtime.
- HPA — scale replicas automatically based on CPU/memory metrics.

**Practice:**
1. Install minikube or kind. Write Deployment + Service YAML for each of 4 services.
2. Use ConfigMaps for config, Secrets for credentials. Verify no credentials in pod env output.
3. Enable HPA on Order Service: scale when CPU > 70%. Generate load → watch replicas increase.
4. Perform rolling update. Watch pods restart one-by-one with zero downtime. Roll back with `kubectl rollout undo`.
5. Configure liveness/readiness probes → `/actuator/health/liveness` and `/actuator/health/readiness`. Kill a pod → watch K8s restart it automatically.

**Checkpoint:**
- [ ] `kubectl get pods` shows all services healthy
- [ ] Rolling deployment completes with zero failed requests
- [ ] Killing a pod results in rescheduling in under 30 seconds
- [ ] HPA scales from 1 to 3 replicas under load

---

### Module 5.2 — Failure Engineering: Break Things on Purpose

**Concepts:**
- Transactional Outbox — save event to DB in same transaction as business entity. Relay publishes to Kafka. Prevents event loss on crash.
- Graceful shutdown — `server.shutdown=graceful`. Finish in-flight requests before exiting. K8s sends SIGTERM on pod termination.
- Chaos testing — deliberately kill services, inject latency. Verify resilience patterns work.

**Practice:**
1. Implement Transactional Outbox in Order Service. Add `outbox_events` table. Write event + order in same `@Transactional`. Add scheduler to relay unprocessed outbox events to Kafka.
2. Test the gap: stop Kafka, create order. Verify order saved but event pending. Restart Kafka → event published.
3. Configure `server.shutdown=graceful`. During rolling deploy, verify in-flight requests complete, no 500s.
4. Write chaos test: `kubectl delete pod` in a loop. Verify system recovers within SLA.
5. Simulate slow Book Service (Thread.sleep 5000). Verify circuit opens, metrics show state change, order creation succeeds with fallback.

**Checkpoint:**
- [ ] No events lost when Kafka is briefly unavailable (outbox catches them)
- [ ] Rolling deploys produce zero 500 errors tested under load
- [ ] You've documented what happens under each failure scenario
- [ ] Recovery from any single service failure within 60 seconds

---

### Module 5.3 — System Design Thinking: Beyond Your Project

**Concepts:**
- Back-of-envelope estimation — estimate scale before choosing technology
- CAP Theorem — Consistency vs Availability during a network Partition
- Consistent Hashing — how Redis Cluster distributes data across shards

**Design exercises (design, don't build):**

**URL Shortener:** 100M URLs/day write, 1B reads/day. DB schema, caching strategy, ID generation, redirect flow.

**Real-Time Leaderboard:** 1M concurrent players, score updates/second, top 100 in <10ms. Answer: Redis Sorted Sets.

**Chat System:** 50M users, ordered messages per conversation, offline delivery. What do WebSockets, queues, and databases each handle?

**Checkpoint — mastery achieved when:**
- [ ] You can estimate scale requirements before choosing a technology
- [ ] You can explain every decision in your system with its trade-off
- [ ] You can design a new system from scratch and justify each component
- [ ] You can look at the bahyoune project and suggest meaningful improvements

---

### 🏆 Phase 5 Capstone: Production-Grade Book Library Platform on Kubernetes

Your final system deployed on K8s with HPA, rolling deployments, graceful shutdown, transactional outbox, complete observability (OTel → Prometheus/Tempo/Loki → Grafana), chaos-tested resilience, and a system design document explaining every decision.

---

## Quick Reference

### Phase Summary

| Phase | Duration | Focus | Key Deliverable |
|---|---|---|---|
| 1 — Foundation | Weeks 1–3 | Single service, DB, Docker | Book API, Dockerized |
| 2 — Data & Async | Weeks 4–6 | Redis cache, Kafka events | Caching + Notification Service |
| 3 — Distributed | Weeks 7–12 | Microservices, Saga, Resilience | Full 4-service system |
| 4 — Observability | Weeks 13–18 | OTel, Prometheus, Tracing, Logs | Full observability stack |
| 5 — Production | Weeks 19–24 | K8s, Failure engineering | Production platform |

### Technology Stack

| Layer | Technology | Introduced In |
|---|---|---|
| Framework | Spring Boot 3, Java 21 | Phase 1 |
| Database | PostgreSQL + Flyway | Phase 1 |
| Containers | Docker + Docker Compose | Phase 1 |
| Cache | Redis | Phase 2 |
| Messaging | Apache Kafka | Phase 2 |
| Service Discovery | Eureka | Phase 3 |
| Config | Spring Cloud Config | Phase 3 |
| Gateway | Spring Cloud Gateway | Phase 3 |
| Security | JWT + Spring Security | Phase 3 |
| Resilience | Resilience4j | Phase 3 |
| Metrics | Prometheus + Grafana | Phase 4 |
| Tracing | OpenTelemetry + Tempo | Phase 4 |
| Logging | Loki + Logback JSON | Phase 4 |
| Orchestration | Kubernetes | Phase 5 |

### The One Project — Domain at Each Phase

```
Phase 1: Book API (single service, in-memory → PostgreSQL, Dockerized)
Phase 2: + Redis cache + Kafka events + Notification Service
Phase 3: + User Service + Order Service + Inventory Service + Saga + JWT
Phase 4: + Full OTel instrumentation + Grafana dashboards
Phase 5: + Kubernetes deployment + Outbox + Chaos tests
```

---

*Built from: Architecture Notes, System Design Academy, bahyoune/cloud_native_microservices_system, 12-Factor App, Kafka Deep Dive, Redis Deep Dive.*
