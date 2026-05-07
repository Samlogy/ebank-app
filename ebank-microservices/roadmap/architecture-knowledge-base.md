# 🏗️ Architecture Knowledge Base
*Compiled from: Architecture Notes, System Design Academy, bahyoune Cloud-Native Microservices Project, 12-Factor App, Redis Deep Dive, Kafka Deep Dive*

---

## Table of Contents

1. [Architecture Granularity — Monolith, SOA, Microservices](#1-architecture-granularity)
2. [How to Split Into Microservices — The Criteria](#2-how-to-split-into-microservices)
3. [Database Strategy — Per-Service vs Shared](#3-database-strategy)
4. [Sync vs Async Communication](#4-sync-vs-async-communication)
5. [Kafka — Deep Dive](#5-kafka-deep-dive)
6. [Redis — Beyond a Simple Cache](#6-redis-beyond-a-simple-cache)
7. [Relational Databases — What Every Engineer Must Know](#7-relational-databases)
8. [Observability — Complete Guide](#8-observability-complete-guide)
9. [API Gateway & Authentication](#9-api-gateway--authentication)
10. [Spring Boot Web vs WebFlux](#10-spring-boot-web-vs-webflux)
11. [The 12-Factor App — Cloud-Native Principles](#11-the-12-factor-app)
12. [System Design Patterns Reference](#12-system-design-patterns-reference)
13. [The Mental Map — How It All Connects](#13-the-mental-map)

---

## 1. Architecture Granularity

> "Most systems start as a monolith, and most systems should stay there."

The architecture spectrum has **three stations**, not two. The mistake most engineers make is jumping straight from monolith to microservices, skipping the middle ground entirely.

### Monolith

A single deployable unit containing everything from UI to database.

**Pros:** Fast early development, performant, easy to reason about, no distributed systems complexity.

**Cons:** As complexity and team size grow, agility decreases. Large changes become risky. Scaling requires scaling the whole system.

**When to use:** Almost always, at the start. Don't pre-optimize. Most applications never need to leave this stage.

### SOA (Service-Oriented Architecture)

Systems composed of loosely coupled, fully functional units that don't cross-communicate heavily and may still share a database.

**Pros:** Better team boundaries, improved maintainability, teams move faster in parallel.

**Cons:** Higher coordination overhead, slower overall response time, requires upfront planning.

**When to use:** When you have a well-understood domain, a growing team, and need to escape monolith bottlenecks without full microservices complexity.

### Microservices

Each service has one responsibility and its own data boundary. Independently deployable, independently scalable.

**Pros:** Maximum team independence, fine-grained scaling, technology diversity, fault isolation.

**Cons:** Distributed systems complexity, deployment overhead, operational burden, observability required, eventual consistency everywhere.

**When to use:** When you have proven scaling needs, mature DevOps practices, and multiple autonomous teams. The number of applications that genuinely need microservices is smaller than the hype suggests.

### The Real Path

```
Modular Monolith → SOA → Microservices
```

Build a well-structured monolith first. Find the natural seams. Extract only when you have a genuine, observable reason. Shopify ran a highly successful modular monolith at massive scale.

---

## 2. How to Split Into Microservices

The most common mistake is splitting too early or by technical layer. The result is a *distributed monolith* — all the operational complexity with none of the benefits.

### The Right Criteria

**Business capability / Domain boundary** (most important)
A microservice should own a complete business capability. "Order management" is a capability. "Email sending" is usually not. Ask: could this be owned by a separate team that deploys on its own schedule?

**Independent deployability**
If you cannot deploy Service A without deploying Service B, they are too coupled. The test: can two teams own these services and ship independently?

**Data ownership**
Each service should be the single source of truth for its data. If two services constantly read each other's database, they should probably be merged.

**Different scaling requirements**
If one part needs 10x more instances than another, that's a genuine reason to split. Payment processing has different scaling characteristics than user profile reads.

**Different failure domains**
If a bug in the notification system should not bring down order processing, split them.

**Organizational alignment (Conway's Law)**
Your architecture will mirror your org structure. A "payments team" and an "orders team" map naturally to two services. Don't fight your org chart.

### What NOT to Split On

- Technical layer ("validation microservice", "logging microservice")
- Code size — small code doesn't mean it should be a service
- Premature optimization — extract when you feel real pain, not before

### The Practical Test

Draw a box around a concept and ask: "Could this be owned by a separate team, deployed independently, and scaled independently?" If yes, it's a microservice candidate.

---

## 3. Database Strategy

> **Rule: Never share a database between microservices.**

This is the Database-per-Service pattern and it is a near-requirement, not a recommendation.

### Why Sharing Destroys the Architecture

When two services share a database, they are coupled at the data layer. Service A can change a schema and silently break Service B. Independent deployment becomes impossible. A heavy query in one service locks tables for the other. The ability to choose the right database for each service is lost.

### Database Per Service in Practice

- Order Service → its own PostgreSQL (orders, order_items)
- Payment Service → its own PostgreSQL (payments)
- User Service → its own PostgreSQL (users, roles)
- Services never query each other's databases directly

### How Services Share Data Without Direct DB Access

Via events or APIs, never by direct database access:

- **Synchronous:** Service A calls Service B's API to get the data it needs
- **Asynchronous:** Service A maintains a local projection of data it cares about, updated via events (eventual consistency)

### The Practical Nuance

"Own database" does not always mean a separate database server. Early on, one PostgreSQL server with separate schemas (one per service) is a valid approach. The schemas are isolated — Service A's code cannot access Service B's schema. As teams and services mature, migrate each to its own server.

### When is Sharing Acceptable?

Never for write ownership. The only acceptable exception is a **read-only analytics/reporting database** that aggregates data from multiple services, as long as services never write to it directly.

### Choosing the Right Database Type Per Service

| Service Type | Good Database Choice | Reason |
|---|---|---|
| User/Order data | PostgreSQL, MySQL | ACID, relational queries |
| Product catalog | MongoDB, DynamoDB | Flexible schema |
| Session storage | Redis | In-memory, TTL |
| Full-text search | Elasticsearch | Search indexes |
| Time-series metrics | InfluxDB, Prometheus | Optimized for time data |
| Graph relationships | Neo4j | Relationship traversal |

---

## 4. Sync vs Async Communication

The core question: **does the caller need the answer right now to continue?**

### Decision Framework

```
Does the caller need the answer to proceed?
  ├── YES → Sync (REST / Feign / gRPC)
  └── NO  → Could the downstream be slow or unavailable?
                ├── YES → Async (Kafka / RabbitMQ / BullMQ)
                └── NO  → Sync is fine, but consider async for scalability
```

### Choose Sync When

- You need the response to proceed. Example: "Does this user exist?" before creating an order
- The operation is a query, not a command. Reading data is almost always sync
- Strong consistency is required immediately (auth check, login, balance check)
- Latency is critical and the downstream service is reliable

### Choose Async When

- The caller doesn't need to wait. Example: trigger payment processing after order is saved
- You want to decouple services. The Order Service shouldn't care if Payment Service is currently up
- The operation can take a long time (email sending, PDF generation, fraud analysis)
- You need **fan-out**: one `OrderCreated` event triggers notification service, analytics service, and inventory service simultaneously
- You need **durability**: if a service is down, the message waits safely and processes when it recovers

### The Hidden Cost

Async gives resilience and scalability but destroys simplicity. Debugging "why didn't the payment process?" is far harder when the answer is somewhere in a Kafka topic from 3 hours ago. This is exactly why **observability becomes non-negotiable** the moment you go async — you need distributed tracing to reconnect cause and effect across time and services.

### Messaging Technology Comparison

| | Kafka | RabbitMQ | BullMQ (Redis) |
|---|---|---|---|
| Model | Append-only log | Queue / exchange | Job queue on Redis |
| Retention | Durable, configurable (days/weeks) | Consumed = gone | Redis TTL |
| Replay | Yes (consumer groups + offsets) | No | Limited |
| Fan-out | Native (multiple consumer groups) | Via exchanges | Manual |
| Throughput | Very high | High | Medium |
| Complexity | High (operational overhead) | Medium | Low |
| Best for | Event streams, Saga, audit logs | Task queues, work distribution | Background jobs in Node.js |
| When NOT to use | Simple job queues, small scale | High volume event streaming | Service-to-service messaging |

### Important Distinction

**BullMQ is a job queue, not a message broker.** It is perfect for background processing in Node.js (sending emails, resizing images, generating reports) but should not replace Kafka or RabbitMQ for service-to-service event-driven communication. It has no native fan-out, no replay, and its durability depends entirely on Redis persistence configuration.

---

## 5. Kafka Deep Dive

### What Kafka Solves

LinkedIn created Kafka to solve the **O(N²) data integration problem**. With N services all talking to each other via custom point-to-point pipelines, the number of integrations becomes unmanageable. Kafka flips the model: services write to one central location using one standard API, and consumers subscribe to what they need.

### Core Architecture

**The Log Data Structure**
Kafka is built on an append-only log. Records are added to the end only — no updates, no deletes. Each record has a unique, monotonically increasing **offset**. Sequential writes make it extremely fast, even on HDDs.

**Topics and Partitions**
A topic is a category. It is sharded into one or more **partitions**, each an independent log. Partitions enable horizontal scaling and parallel consumption. A topic with 10 partitions can be consumed by up to 10 consumers simultaneously within one consumer group.

**Brokers and Cluster**
A Kafka cluster is typically at least 3 broker nodes. Partitions are replicated across brokers (default replication factor: 3). One replica is the **leader** (accepts writes), the others are followers. If a broker dies, another broker becomes the leader automatically.

**KRaft (Kafka Raft)**
Modern Kafka uses KRaft — a Raft-inspired consensus algorithm — for controller election and cluster metadata management. This replaced the older ZooKeeper dependency. Controllers are special broker nodes that manage cluster metadata and leader election.

### Consumer Groups — The Scaling Mechanism

Consumer groups are the key to horizontal scalability on the read side:

- **Within a group:** each partition is read by exactly one consumer at a time (ensures ordering)
- **Multiple groups:** multiple groups can read the same topic independently at their own pace

Adding consumers to a group scales throughput. Adding consumer groups enables fan-out (the same messages reach separate processing pipelines: fraud detection, accounting, analytics — all independent).

The `__consumer_offsets` topic stores each group's progress (which offset they've read to). This enables reliable restarts and failover.

### Data Retention and Replay

Unlike RabbitMQ (consumed = gone), Kafka retains messages for a configurable period (default 7 days). This enables **replay** — reprocess historical events if a bug is found. This is powerful: if a consumer had a bug for 2 days, fix the bug and reprocess the last 2 days of messages.

**Tiered Storage** extends this: cold data is offloaded to object storage (S3), making it cost-effective to retain months of history. Hot data stays on broker SSDs for low-latency access.

### Exactly-Once Processing

Kafka supports transactions: a producer can send messages to multiple topics/partitions atomically — all committed or all aborted. Combined with idempotent producers (no duplicate messages on retry), Kafka Streams can process data with exactly-once guarantees entirely within the Kafka ecosystem.

### Kafka Ecosystem Components

| Component | Purpose |
|---|---|
| **Kafka Core** | Brokers, controllers, the actual messaging |
| **Kafka Clients** | Producer / Consumer Java libraries |
| **Kafka Streams** | Stream processing library — read → transform → write, with exactly-once |
| **Kafka Connect** | No-code/low-code integrations (PostgreSQL, Snowflake, Elasticsearch) |
| **Schema Registry** | Stores and validates message schemas (Avro, Protobuf, JSON Schema) |

### When to Use Kafka vs Not

**Use Kafka when:**
- High throughput event streaming (thousands to millions of events/sec)
- Event replay is required (audit logs, event sourcing, bug recovery)
- Fan-out: one event must reach multiple independent consumers
- Saga pattern across microservices
- Long retention needed

**Don't use Kafka when:**
- You need simple background job processing → use BullMQ or RabbitMQ
- Your scale is small and operational simplicity matters more
- You need request-response semantics → use REST or gRPC

---

## 6. Redis Beyond a Simple Cache

Redis ("Remote Dictionary Server") is an in-memory data structure server. The key insight: it's not just a cache — it's a versatile tool with multiple use cases depending on the data structure used.

### Redis Data Structures and Use Cases

| Data Structure | Use Cases |
|---|---|
| **String** | Simple cache, counters, session tokens, feature flags |
| **Hash** | User profiles, configuration objects |
| **List** | Message queues (FIFO), activity feeds |
| **Set** | Unique visitors, tagging, deduplication |
| **Sorted Set** | Leaderboards, rate limiting with scores, priority queues |
| **Stream** | Event log, lightweight alternative to Kafka |
| **Pub/Sub** | Real-time notifications, live updates |

### Redis Deployment Topologies

**Single Instance:** Dev/test, simple caching. Fast to set up, no HA. Fine for non-critical caching.

**Redis HA (Primary + Replica):** One primary accepts writes; replicas stay in sync. Read scaling via replicas. Manual failover required unless using Sentinel.

**Redis Sentinel:** Automated failover. Sentinel nodes monitor primary health and promote a replica if the primary fails. Requires a quorum of sentinel nodes to agree before failover (typically 3 sentinels, quorum of 2). Does not shard data.

**Redis Cluster:** Horizontal scaling via sharding. 16,384 hash slots distributed across primary nodes. Gossip protocol for cluster health. Add a node → move hash slots → no downtime. Use when your dataset exceeds the memory of a single server.

### Redis Persistence Models

| Mode | Description | Durability | Speed |
|---|---|---|---|
| **No persistence** | Memory only | None | Fastest |
| **RDB (Snapshot)** | Point-in-time snapshots at intervals | Medium (data loss between snapshots) | Fast |
| **AOF (Append-Only File)** | Log every write command | High (configurable fsync) | Slower |
| **RDB + AOF** | Both combined | Highest | Medium |

For production: **RDB + AOF combined** is the practical default. On restart, Redis uses AOF (more complete) to reconstruct data.

### How Redis Persistence Works Without Blocking

Redis uses **fork + copy-on-write**: the main process forks a child, which snapshots memory to disk. The parent continues serving requests. When a page changes, the kernel creates a new copy — the child sees the old data, the parent modifies the new copy. This allows gigabytes of data to be snapshotted with minimal overhead.

### Redis vs Memcached

| Feature | Redis | Memcached |
|---|---|---|
| Data structures | Rich (hash, list, set, sorted set, stream) | String only |
| Persistence | Yes (RDB + AOF) | No |
| Replication | Yes | No (requires setup) |
| Pub/Sub | Yes | No |
| Transactions | Yes | No |
| Clustering | Built-in | Manual setup |
| Threading | Single-threaded (I/O threaded in newer versions) | Multi-threaded |

**Choose Redis** unless you have a very specific use case where Memcached's multi-threaded architecture gives a measurable advantage for pure string caching.

### Key Insight: Speed vs Consistency

Redis is built for speed first. Replication is asynchronous — in a failover, you may lose the most recent writes (between the last successful replication and the crash). This is acceptable for caching. For financial data, use an ACID relational database.

---

## 7. Relational Databases

### Indexes — The Foundation of Performance

An index is a separate data structure that maps column values to row locations on disk. It trades **write performance** (index must be updated) for **read performance** (skip full table scans).

**B+Tree indexes** (the default in PostgreSQL, MySQL):
- Leaf nodes store the column value → row pointer mapping
- Intermediate nodes allow O(log N) traversal to find the right leaf
- Leaf nodes are linked (doubly linked list) for efficient range scans
- Depth grows logarithmically: a tree of height 4 with branching factor 5 covers 625 leaf nodes

**The key decisions:**
- Index columns you frequently filter (`WHERE`), sort (`ORDER BY`), or join on
- Every additional index slows writes (index must be maintained on INSERT/UPDATE/DELETE)
- Composite indexes: order matters — `(user_id, created_at)` helps `WHERE user_id=X ORDER BY created_at` but not `WHERE created_at=Y`

### ACID and Transactions

A transaction is a unit of work that must happen completely or not at all.

| Property | Meaning |
|---|---|
| **Atomicity** | All operations succeed or none do — no partial state |
| **Consistency** | Transaction moves DB from one valid state to another |
| **Isolation** | Concurrent transactions don't interfere with each other |
| **Durability** | Committed changes survive crashes |

### Read Phenomena (What Can Go Wrong)

| Phenomenon | Description | Example |
|---|---|---|
| **Dirty Read** | Reading uncommitted data from another transaction | See a price change before it's confirmed |
| **Non-repeatable Read** | Same row returns different values in one transaction | Balance changes between two reads |
| **Phantom Read** | Same range query returns different rows in one transaction | Count changes between two aggregates |

### Isolation Levels

| Level | Dirty Reads | Non-Repeatable | Phantom | Notes |
|---|---|---|---|---|
| **READ UNCOMMITTED** | Possible | Possible | Possible | Fastest, dangerous |
| **READ COMMITTED** | Prevented | Possible | Possible | PostgreSQL default |
| **REPEATABLE READ** | Prevented | Prevented | Possible | MySQL default |
| **SERIALIZABLE** | Prevented | Prevented | Prevented | Safest, slowest |

Most applications should run at READ COMMITTED or REPEATABLE READ. Use SERIALIZABLE only when absolute consistency is required (financial ledgers).

### Why This Matters for Microservices

In microservices, you lose ACID across service boundaries. An Order + Payment spanning two databases cannot be wrapped in a single transaction. This is the fundamental reason the **Saga Pattern** exists — it's the distributed system's substitute for cross-service ACID transactions.

---

## 8. Observability Complete Guide

### Why Observability vs Monitoring

**Monitoring** tells you *that* something is broken. **Observability** tells you *why*. In a distributed system with dozens of services, you cannot understand failures by looking at one service's metrics alone.

### The Three Pillars

**Metrics** — "Is my system healthy right now and over time?"
Aggregated numbers: request rate, error rate, p99 latency, JVM heap, Kafka consumer lag. These are patterns and trends. You lose individual request detail but gain alerting and dashboards.

**Traces** — "What happened during this specific request?"
A trace ID follows one user action across every service that touched it, with timing at each hop. Essential in microservices — without tracing, a 2-second request is a mystery across 5 services.

**Logs** — "What exactly did the code do?"
Raw text output, indispensable for debugging once you know where to look (found via the trace).

**The power is in correlation:** metric alert → traces from that window → click trace → exact log lines per service per hop. That's the full observability loop.

### Technology Choices and Why

| Tool | Role | Why Choose It |
|---|---|---|
| **Prometheus** | Metrics storage (time-series DB) | De facto standard, pull-based, PromQL, huge ecosystem |
| **Grafana** | Visualization (backend-agnostic) | Connects to Prometheus, Loki, Tempo, Elasticsearch — single pane of glass |
| **Loki** | Log aggregation | "Prometheus for logs" — indexes labels only (cheap), not full text |
| **Tempo** | Distributed tracing backend | Native Grafana integration — jump from log to trace in one click |
| **Jaeger** | Distributed tracing (alternative) | More mature, CNCF project, good UI |
| **OpenTelemetry** | Instrumentation standard | Vendor-neutral — instrument once, swap backends freely |

**The most critical choice:** OpenTelemetry. If you instrument with OTel from the start, you can replace Tempo with Jaeger tomorrow without touching your application code.

### Monolith Observability Setup

Simpler — no distributed tracing needed (one process). What you need:

1. Expose `spring-boot-actuator` with `/actuator/prometheus` endpoint
2. Scrape with Prometheus, visualize in Grafana
3. Structured JSON logs (Logback) shipped to Loki or a log file
4. Health checks: `/actuator/health` for uptime monitoring and K8s readiness probes

Tracing is optional in a monolith but useful for profiling slow internal methods.

### Microservices Observability Setup

All three pillars become mandatory. The critical addition is **trace context propagation**:

Every request carries a `traceId` that travels in HTTP headers (W3C `traceparent` header) and Kafka message headers. Every service creates a child span and reports it to the tracing backend. Without this, you have isolated logs with no way to connect them.

**Reference architecture (from the bahyoune project):**

```
Spring Boot Services
  + OTel Java Agent (zero code changes — attached at JVM startup)
       ↓ OTLP gRPC (port 4317)
  OTel Collector  ← central hub, decoupling layer
    ├── Metrics → Prometheus
    ├── Traces  → Tempo
    └── Logs    → Loki
                   ↓
              Grafana (dashboards, trace exploration, log search)
```

The OTel Collector is not optional. It decouples your services from backends. Change backends without touching any service configuration.

### Real Observability Use Cases

**Incident response:** p99 latency spiked at 14:32. Query traces from that window → slowdown is in a DB query, not service logic. Targeted fix in minutes.

**Saga debugging:** User says order #12345 never confirmed. Search Loki by `orderId=12345` → find `traceId` → in Tempo, see the complete saga: order created → Kafka event published → payment service received → payment failed → compensation triggered.

**Consumer lag:** Kafka consumer lag growing on `payment-events`. Payment Service is slow. Scale horizontally before users notice.

**Post-deployment regression:** Error rate went from 0.1% to 3%. Compare traces before/after → NullPointerException in a new method. Rollback in minutes.

**Capacity planning:** DB connection pool near saturation every Monday at 9am. Add read replicas proactively.

---

## 9. API Gateway & Authentication

### What the API Gateway Does

The API Gateway is the **only public-facing** entry point. All internal services are completely isolated behind it.

**Responsibilities:**
- JWT validation (authentication)
- Rate limiting (prevent abuse)
- Request routing (to correct microservice)
- Circuit breaker (fail fast if a service is down)
- SSL termination
- Request/response transformation

### Gateway-Only Auth — When It's Enough

The gateway validates the JWT, extracts the user identity, and passes it downstream via headers (`X-User-Id`, `X-User-Role`). Internal services trust these headers.

This works when:
- All services are only reachable through the gateway
- Authorization rules are simple and uniform ("must be authenticated")
- One application type, one user type

### When You Need a Dedicated Auth Service (Authorization Server)

**OAuth2 / OpenID Connect (OIDC):** Social login (Google, GitHub), SSO across multiple applications, third-party API access. You need a full Authorization Server (Spring Authorization Server, Keycloak, Auth0).

**Fine-grained authorization:** Rules like "user can only access orders they own" or "admin can only delete in their department." A dedicated policy engine (OPA — Open Policy Agent) or auth service centralizes this so it's not duplicated across every service.

**Multiple client types:** Mobile apps, SPAs, machine-to-machine (M2M) — each needs different OAuth2 flows (PKCE for SPAs, client credentials for M2M).

**B2B / Partner integrations:** External parties need scoped access tokens, not full user JWTs.

### The Practical Line

Single application, one user type → gateway JWT validation is sufficient.

The moment you have: SSO needs, third-party integrations, mobile + web + API clients, or complex authorization rules → invest in Keycloak or Spring Authorization Server. Keycloak solves all of this out of the box and is production-battle-tested.

### Service-to-Service Auth (Internal)

When services call each other directly (bypassing the gateway), they should use **mutual TLS (mTLS)** or **service account tokens** — not user JWTs. In Kubernetes, a service mesh (Istio, Linkerd) handles this transparently.

---

## 10. Spring Boot Web vs WebFlux

### The Core Difference

**Spring Boot Web (Servlet / Blocking):** Thread-per-request model. A request arrives, gets a thread from a pool, the thread does work (including blocking on DB or HTTP calls), then returns the thread. Simple, predictable, matches how most developers think.

**Spring WebFlux (Reactive / Non-blocking):** Event loop model (like Node.js). A small number of threads handle all requests. When a thread would block (waiting on DB, HTTP), it registers a callback and the thread is freed to serve other requests. Uses Project Reactor (Mono/Flux).

### Choose Spring Boot Web When

- Your team is not deeply experienced with reactive programming (the Reactor learning curve is steep and bugs are subtle)
- Your operations are CPU-bound or the latency difference doesn't matter at your scale
- You use libraries without reactive drivers (many legacy JDBC drivers, third-party SDKs)
- Simplicity and maintainability are the priority — this is most applications
- You're using Java 21 with virtual threads (see below)

### Choose Spring WebFlux When

- Very high concurrency with many slow I/O operations (thousands of simultaneous requests, each waiting on external APIs)
- Building streaming APIs (Server-Sent Events, real-time data feeds)
- Your entire stack is reactive (R2DBC for DB, WebClient for HTTP)
- Your team genuinely understands reactive programming — mixing blocking code into a reactive chain is one of the most common and dangerous mistakes

### The Java 21 Virtual Threads Factor

With **Java 21 (Project Loom)**, virtual threads give you the concurrency benefits of reactive without the programming model complexity. Spring Boot 3 supports virtual threads natively. Virtual threads are cheap (millions can exist) and block without tying up OS threads.

**The practical recommendation today:** If you're on Java 21, use Spring Boot Web + virtual threads. You get WebFlux-level concurrency with a blocking mental model. Many teams that would have chosen WebFlux in 2022 now choose Web + virtual threads.

The only remaining reasons to choose WebFlux: backpressure control in streaming scenarios, or existing reactive codebases.

---

## 11. The 12-Factor App

Originally written by Heroku's co-founder for cloud-native applications. These 12 principles are now the DNA of every well-designed distributed system.

| Factor | Principle | Modern Implication |
|---|---|---|
| **1. Codebase** | One codebase, multiple deploys | Monorepo or per-service repos; both valid |
| **2. Dependencies** | Declare all dependencies explicitly | Maven/npm lock files; Docker images |
| **3. Config** | Store config in the environment | Config Server, Vault, K8s Secrets — never in code |
| **4. Backing Services** | Treat databases, queues, caches as attached resources | Swap DB connection string = swap DB |
| **5. Build/Release/Run** | Strict separation of build, release, run stages | CI/CD pipeline; separate artifact from deployment |
| **6. Processes** | Stateless processes; persist state in backing services | Session in Redis, not JVM memory |
| **7. Port Binding** | Self-contained services expose via port | Each service owns its port; no web server deployment |
| **8. Concurrency** | Scale via process model | Scale only what needs it; services as units of scale |
| **9. Disposability** | Fast startup, graceful shutdown | Health checks, readiness probes, graceful drain |
| **10. Dev/Prod Parity** | Keep all environments as similar as possible | Docker/K8s reduces environment drift |
| **11. Logs** | Treat logs as event streams | Structured JSON logs + observability pipeline |
| **12. Admin Processes** | Run admin tasks as one-off processes | DB migrations, cleanup scripts in source control |

### Most Critical Factors for Microservices

**Factor 3 (Config):** Never hardcode configuration. Environment-specific values (DB URLs, API keys) must come from the environment. The bahyoune project implements this with a dedicated Config Server pulling from a Git repo.

**Factor 6 (Stateless Processes):** Never store session state in JVM memory. If a service has 3 instances, a request might hit any of them. Session must live in Redis, not in the process.

**Factor 9 (Disposability):** Health checks (`/actuator/health`) enable Kubernetes to route traffic only to healthy instances. Graceful shutdown (drain in-flight requests before dying) prevents errors during deployments. This factor is frequently overlooked until it causes production incidents.

**Factor 11 (Logs):** The original factor said "let the environment aggregate logs." The modern update: applications should be observable — emit structured logs with trace IDs, expose metrics endpoints, and support distributed tracing. Observability is the application's responsibility.

---

## 12. System Design Patterns Reference

### Saga Pattern

**Problem:** Distributed transactions across multiple services with separate databases — ACID is impossible.

**Solution:** Chain of events where each step publishes an event. On failure, each service executes a compensation action to undo its change.

**Example (Order → Payment):**
```
1. OrderService: save order as PENDING → publish OrderCreatedEvent
2. PaymentService: consume event → process payment
   → Success: publish PaymentSucceededEvent
   → Failure: publish PaymentFailedEvent
3. OrderService: consume result → update to CONFIRMED or CANCELLED
```

**Choreography vs Orchestration:**
- **Choreography** (used in bahyoune): each service reacts to events, no central coordinator. Loosely coupled but harder to visualize.
- **Orchestration**: a central Saga orchestrator tells each service what to do. Clearer flow but creates coupling to the orchestrator.

### Circuit Breaker

**Problem:** Cascading failures — if Service B is slow, Service A's threads pile up waiting, Service A becomes slow, and the cascade continues.

**Solution (Resilience4j):**
- **Closed** → Normal traffic
- **Open** → Requests fail fast (fallback executed immediately)
- **Half-Open** → Test a few requests to see if service recovered

Use at: API Gateway (protect backends), sync service-to-service calls (Order → Payment).

### Idempotency

**Problem:** Kafka guarantees at-least-once delivery. Duplicate events cause duplicate payments.

**Solution:** Each payment event contains a unique `orderId`. Before processing, check if this order was already processed. Ignore duplicates safely.

Rule: **any operation that can be retried must be idempotent.**

### Dead Letter Topic (DLT)

**Problem:** Some events fail repeatedly (not transient errors — genuine business errors like "invalid amount"). They block the consumer queue.

**Solution:** After N retries, route the event to a DLT (Dead Letter Topic). Allows debugging, auditing, and manual replay without blocking healthy message processing.

| Failure Type | Handling |
|---|---|
| Transient error (network blip) | Automatic retry with backoff |
| Business error (invalid amount) | Send directly to DLT |
| Repeated failures | Route to DLT after max retries |

### Caching Patterns

**Cache-Aside (Lazy Loading):** Application checks cache first. On miss, reads from DB and populates cache. Most common pattern. Risk: cache miss under load.

**Write-Through:** Write to cache and DB simultaneously. Cache always up to date. Slightly slower writes.

**Write-Behind (Write-Back):** Write to cache only. Async write to DB later. Fastest writes, risk of data loss.

**Read-Through:** Cache sits in front of DB. Application only reads from cache; cache fetches from DB on miss automatically.

### Rate Limiting

Protect services from abuse and ensure fair usage:

- **Token Bucket:** Tokens replenish at a fixed rate. Allows bursts up to bucket size. Implemented in Redis with a sorted set.
- **Fixed Window:** Count requests per time window. Simple but prone to boundary spikes.
- **Sliding Window:** More accurate — counts requests in the last N seconds from the current moment.

### Service Discovery

**Client-side (Eureka):** Each service registers itself. Clients query Eureka to find service locations. Spring Cloud default.

**Server-side (Kubernetes DNS):** The load balancer / service mesh handles discovery. Services use DNS names. Preferred in K8s environments — Eureka becomes redundant.

### Consistent Hashing

Used in Redis Cluster and distributed caches. Maps keys to nodes such that when a node is added or removed, only K/N keys need to be remapped (not all keys). Uses a virtual node ring to distribute load evenly.

---

## 13. The Mental Map

Everything connects. No topic in this document stands alone — they are all responses to the same fundamental problems of building fast, correct, resilient, and observable systems at scale.

```
12-Factor App Principles
    ↓ (guide design decisions)
┌─────────────────────────────────────┐
│  Monolith → SOA → Microservices     │
│  (choose based on team + scale)     │
└─────────────────────────────────────┘
    ↓ (splitting creates)
Service Boundaries + DB-per-Service
    ↓ (services need to communicate)
┌──────────────────┬──────────────────┐
│  Sync (REST)     │  Async (Kafka)   │
│  for queries     │  for commands    │
│  and immediate   │  and side        │
│  answers         │  effects         │
└──────────────────┴──────────────────┘
    ↓ (async requires)
Saga + Idempotency + DLT
    ↓ (distributed system requires)
Observability: Metrics + Traces + Logs
(OTel → Prometheus/Tempo/Loki → Grafana)
    ↓ (data needs to be fast)
Redis: Cache, Sessions, Rate Limiting,
       Leaderboards, Job Queues
    ↓ (data needs to be correct)
Relational DB: ACID, Indexes,
               Isolation Levels
    ↓ (security boundary)
API Gateway: Auth, Rate Limiting,
             Circuit Breaker, Routing
```

### The Key Insights to Internalize

**Don't over-architect early.** A well-structured monolith can scale further than most teams expect. Extract microservices only when you feel genuine pain.

**Async buys resilience, costs complexity.** The moment you go async, observability becomes mandatory — not optional.

**The database is the hardest constraint.** Database-per-service is the rule that enables everything else: independent deployment, independent scaling, technology freedom, fault isolation.

**Observability is the application's responsibility.** Emit structured logs with trace IDs. Expose metrics. Let OpenTelemetry do the heavy lifting.

**Redis and Kafka are complements, not competitors.** Redis for low-latency data structures and job queues. Kafka for high-throughput event streaming with replay and fan-out.

**The 12-Factor App is the foundation.** Stateless processes, externalized config, and disposability are prerequisites for everything else in cloud-native architecture.

---

## Quick Reference — Decision Cheat Sheet

### Which Architecture?
- **Small team, new project** → Modular Monolith
- **Growing team, proven domain** → SOA
- **Multiple autonomous teams, proven scaling needs** → Microservices

### Which Communication?
- **Need immediate response** → Sync REST / Feign
- **Fire and forget, decouple services** → Async Kafka / RabbitMQ
- **Background jobs in Node.js** → BullMQ

### Which DB?
- **Relational data, ACID required** → PostgreSQL
- **Cache, sessions, counters** → Redis
- **High-volume event log** → Kafka
- **Full-text search** → Elasticsearch
- **Flexible document store** → MongoDB

### Which Observability Tool?
- **Metrics storage** → Prometheus
- **Visualization** → Grafana
- **Distributed tracing** → Tempo (or Jaeger)
- **Log aggregation** → Loki
- **Instrumentation standard** → OpenTelemetry (always)

### Spring Boot Web or WebFlux?
- **Java 21** → Web + Virtual Threads (best default)
- **Streaming / SSE / reactive ecosystem** → WebFlux
- **Everything else** → Web (blocking)

### Redis Topology?
- **Dev / non-critical cache** → Single Instance
- **HA needed, dataset fits one server** → Sentinel
- **Dataset exceeds one server** → Cluster

---

*Sources: Architecture Notes (Redis, Relational DBs, Monolith/Microservices, 12-Factor App), System Design Academy (Kafka, Redis use cases, API Gateway, Service Discovery, Caching Patterns, Rate Limiting, Consistent Hashing), bahyoune/cloud_native_microservices_system, mohamedYoussfi/totale-micro-services project.*
