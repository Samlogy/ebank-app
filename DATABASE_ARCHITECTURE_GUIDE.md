# Database Architecture for ebank Monolithic Project

## Table of Contents

1. [Single DB vs Multiple DBs](#single-db-vs-multiple-dbs)
2. [Data Ownership in Modular Monolith](#data-ownership-in-modular-monolith)
3. [Handling High Read Operations](#handling-high-read-operations)
4. [Handling High Write Operations](#handling-high-write-operations)
5. [Database Backup Strategy](#database-backup-strategy)
6. [Database Shutdown & Resilience](#database-shutdown--resilience)
7. [Architecture Recommendation for ebank](#architecture-recommendation-for-ebank)

---

## Single DB vs Multiple DBs

### Quick Comparison

```mermaid
graph TD
    A["Database Strategy Decision"]
    
    B["SINGLE Database"]
    B --> B1["✅ Pros:<br/>- ACID transactions across domains<br/>- Simple deployment<br/>- Easier backup<br/>- Lower operational complexity"]
    B --> B2["❌ Cons:<br/>- Single point of failure<br/>- Scaling bottleneck<br/>- One slow query affects all<br/>- Monolithic schema"]
    
    C["MULTIPLE Databases"]
    C --> C1["✅ Pros:<br/>- Domain isolation<br/>- Independent scaling<br/>- Failure isolation<br/>- Different tech stacks"]
    C --> C2["❌ Cons:<br/>- Distributed transactions<br/>- Complex backup<br/>- Cross-DB consistency hard<br/>- Higher operational overhead"]
    
    A --> B
    A --> C
    
    style B1 fill:#90EE90
    style B2 fill:#FF6B6B
    style C1 fill:#90EE90
    style C2 fill:#FF6B6B
```

### Monolith Architecture Options

```mermaid
graph LR
    A["Monolithic App<br/>(one codebase)"]
    
    B["Option 1:<br/>Single Shared DB"]
    B --> B1["accounts schema<br/>transactions schema<br/>users schema<br/>in SAME database"]
    
    C["Option 2:<br/>Multiple DBs<br/>per Domain"]
    C --> C1["accounts-db<br/>transactions-db<br/>users-db<br/>separate instances"]
    
    D["Option 3:<br/>Hybrid<br/>Primary + Read Replicas"]
    D --> D1["accounts-primary<br/>accounts-replica-1<br/>accounts-replica-2<br/>one logical DB<br/>multiple instances"]
    
    A --> B
    A --> C
    A --> D
    
    style B1 fill:#87CEEB
    style C1 fill:#FFA500
    style D1 fill:#FFD700
```

---

## Data Ownership in Modular Monolith

### Domain-Based Schema Isolation (Single DB)

Even with ONE database, use **schema-based isolation** to maintain domain boundaries:

```mermaid
graph TD
    A["ebank_production<br/>Single Database"]
    
    B["Schema: public.users"]
    B --> B1["users table<br/>owned by auth domain"]
    
    C["Schema: public.accounts"]
    C --> C1["accounts table<br/>owned by account domain"]
    
    D["Schema: public.transactions"]
    D --> D1["transactions table<br/>owned by transaction domain"]
    
    E["Schema: public.audit_log"]
    E --> E1["audit_log table<br/>compliance domain"]
    
    A --> B
    A --> C
    A --> D
    A --> E
    
    style A fill:#FFD700
    style B fill:#87CEEB
    style C fill:#87CEEB
    style D fill:#87CEEB
    style E fill:#87CEEB
```

### Directory Structure (Single DB + Modular Code)

```
ebank-monolith/
├── src/main/java/com/ebank/
│   ├── auth/
│   │   ├── service/AuthService.java
│   │   ├── repository/UserRepository.java     # Accesses public.users
│   │   └── entity/User.java
│   │
│   ├── account/
│   │   ├── service/AccountService.java
│   │   ├── repository/AccountRepository.java  # Accesses public.accounts
│   │   └── entity/Account.java
│   │
│   └── transaction/
│       ├── service/TransactionService.java
│       ├── repository/TransactionRepository.java  # Accesses public.transactions
│       └── entity/Transaction.java
│
├── src/main/resources/db/migration/
│   ├── V1__create_users_schema.sql
│   ├── V2__create_accounts_schema.sql
│   └── V3__create_transactions_schema.sql
```

### Data Ownership Rules

```mermaid
graph TD
    A["Domain Ownership Rule<br/>in Single DB"]
    
    B["Auth Domain"]
    B --> B1["Owns: users table"]
    B --> B2["✅ Can read: all tables"]
    B --> B3["❌ Cannot write: accounts,<br/>transactions"]
    
    C["Account Domain"]
    C --> C1["Owns: accounts table"]
    C --> C2["✅ Can read: all tables"]
    C --> C3["❌ Cannot write: users,<br/>transactions"]
    
    D["Transaction Domain"]
    D --> D1["Owns: transactions table"]
    D --> D2["✅ Can read: all tables"]
    D --> D3["❌ Cannot write: users,<br/>accounts<br/>(except debit/credit)"]
    
    style B1 fill:#90EE90
    style B3 fill:#FF6B6B
    style C1 fill:#90EE90
    style C3 fill:#FF6B6B
    style D1 fill:#90EE90
    style D3 fill:#FF6B6B
```

### Implementation: Database Constraints

```
USE ROLE-BASED ACCESS CONTROL in PostgreSQL:

1. Create role per domain:
   CREATE ROLE auth_user LOGIN;
   CREATE ROLE account_user LOGIN;
   CREATE ROLE transaction_user LOGIN;

2. Grant selective permissions:
   GRANT SELECT ON public.users TO auth_user;
   GRANT INSERT, UPDATE ON public.accounts TO account_user;

3. App connects with appropriate role per service
```

---

## Handling High Read Operations

### Read Scaling Architecture

```mermaid
graph TD
    A["High Read Scenario<br/>Analytics, Reports, Customer Portal"]
    
    B["Primary Database<br/>PostgreSQL Master"]
    B --> B1["Reads: ✅<br/>Writes: ✅<br/>For production transactions"]
    
    C["Read Replica 1"]
    C --> C1["Reads: ✅<br/>Writes: ❌<br/>Async replication"]
    
    D["Read Replica 2"]
    D --> D1["Reads: ✅<br/>Writes: ❌<br/>Async replication"]
    
    E["Read Replica 3"]
    E --> E1["Reads: ✅<br/>Writes: ❌<br/>Async replication"]
    
    F["Application Layer<br/>Connection Pooling"]
    F -->|Write: account create<br/>transfer money| B
    F -->|Read: get balance<br/>get history<br/>analytics| C
    F -->|Read: analytics<br/>reports| D
    F -->|Read: customer portal| E
    
    style B fill:#FF6B6B
    style C fill:#87CEEB
    style D fill:#87CEEB
    style E fill:#87CEEB
    style F fill:#FFD700
```

### Read/Write Split Implementation

```
APPLICATION LAYER ROUTING:

Write Operations (Primary):
├─ POST /accounts (create)
├─ POST /transactions/transfer
├─ PUT /accounts/{id} (update balance)
└─ Direct to: Primary DB

Read Operations (Replicas):
├─ GET /accounts/{id} (balance)
├─ GET /transactions (history)
├─ GET /accounts/analytics
└─ Route to: Load-balanced replicas
   (Round-robin or least-connections)

Implementation:
spring:
  datasource:
    # Primary (writes)
    write-url: jdbc:postgresql://primary-db:5432/ebank
    # Replicas (reads)
    read-urls:
      - jdbc:postgresql://replica1:5432/ebank
      - jdbc:postgresql://replica2:5432/ebank
      - jdbc:postgresql://replica3:5432/ebank
```

### Replication Lag Handling

```mermaid
graph TD
    A["Read Replica Lag Scenario"]
    
    B["User transfers $100"]
    B --> C["Primary DB<br/>updated immediately"]
    
    C --> D["Replication lag: 100-500ms"]
    D --> E["Replica lag occurs"]
    
    E --> F{User checks<br/>balance immediately?}
    F -->|Yes| G["Read from PRIMARY<br/>Immediate consistency"]
    F -->|No - after 1s| H["Read from REPLICA<br/>Lag tolerable"]
    
    style C fill:#90EE90
    style E fill:#FFA500
    style G fill:#90EE90
    style H fill:#87CEEB
```

### When to Use Read Replicas

| Scenario | Use Replicas? | Why |
|----------|---|---|
| **High read volume (1000s/sec)** | ✅ Yes | Distribute read load |
| **Analytics queries** | ✅ Yes | Don't slow primary |
| **Customer portal (display data)** | ✅ Yes | Tolerate small lag |
| **Transactions (balance after transfer)** | ❌ No | Need immediate consistency |
| **Account creation verification** | ❌ No | Must read own write |

---

## Handling High Write Operations

### Write Scaling Strategies

```mermaid
graph TD
    A["High Write Scenario<br/>Processing 10k transactions/sec"]
    
    B["Challenge"]
    B --> B1["Single primary DB<br/>becomes bottleneck"]
    
    C["Solution 1: Partitioning"]
    C --> C1["Partition transactions by<br/>date (daily)"]
    C --> C2["Improves INSERT speed<br/>Easier archival"]
    
    D["Solution 2: Sharding"]
    D --> D1["Shard by account_id hash"]
    D --> D2["Shard 1: accounts 0-999"]
    D --> D3["Shard 2: accounts 1000-1999"]
    D --> D4["Each shard is independent<br/>Primary instances"]
    
    E["Solution 3: Denormalization<br/>+ Cache"]
    E --> E1["Cache account balance<br/>in Redis"]
    E --> E2["Batch DB writes (1sec intervals)"]
    E --> E3["Reduce DB write pressure"]
    
    F["Recommended for ebank"]
    F --> F1["Combination:<br/>Partitioning + Cache<br/>+ Read replicas"]
    
    A --> B
    B --> C
    B --> D
    B --> E
    C --> F
    D --> F
    E --> F
    
    style F1 fill:#FFD700
```

### Partitioning Strategy (Simple, Recommended)

```mermaid
graph TD
    A["Transactions Table<br/>10M rows/month"]
    
    B["Partition by Date<br/>Monthly partitions"]
    B --> B1["transactions_2026_01<br/>1M rows<br/>Faster queries"]
    B --> B2["transactions_2026_02<br/>1M rows"]
    B --> B3["transactions_2026_03<br/>1M rows"]
    
    C["Benefits"]
    C --> C1["✅ Faster queries<br/>on date ranges"]
    C --> C2["✅ Easier archival<br/>drop old partitions"]
    C --> C3["✅ Better index usage<br/>smaller indexes"]
    
    A --> B
    B --> C
    
    style B1 fill:#87CEEB
    style B2 fill:#87CEEB
    style B3 fill:#87CEEB
```

### Flyway Migration for Partitioning

```
Create partitioned table in V5__partition_transactions.sql:

CREATE TABLE transactions (
    id BIGSERIAL,
    account_id BIGINT,
    amount NUMERIC(19,2),
    created_at TIMESTAMP,
    ...
) PARTITION BY RANGE (DATE_TRUNC('month', created_at));

CREATE TABLE transactions_2026_01 
    PARTITION OF transactions
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE transactions_2026_02
    PARTITION OF transactions
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
```

### Write-Through Cache Pattern

```mermaid
graph TD
    A["POST /transactions/transfer"]
    
    B["1. Update Database"]
    B --> B1["accounts SET balance = balance - 100"]
    
    C["2. Update Cache"]
    C --> C1["Redis SET account:123:balance"]
    
    D["3. Return to Client"]
    D --> D1["200 OK<br/>Response immediate"]
    
    E["Benefits"]
    E --> E1["✅ DB has source of truth"]
    E --> E2["✅ Cache speeds up reads"]
    E --> E3["✅ Reduces write pressure"]
    
    A --> B --> C --> D
    D --> E
    
    style E1 fill:#90EE90
    style E2 fill:#90EE90
    style E3 fill:#90EE90
```

---

## Database Backup Strategy

### Multi-Layer Backup Architecture

```mermaid
graph LR
    A["Production DB"]
    
    B["Layer 1: WAL Archiving<br/>Write-Ahead Logs"]
    B --> B1["Continuous archiving<br/>to S3/Object Storage<br/>RPO: ~minutes<br/>Can replay any point"]
    
    C["Layer 2: Automated Snapshots"]
    C --> C1["Daily snapshots<br/>to AWS RDS or Backblaze<br/>RPO: 1 day<br/>Fast restore"]
    
    D["Layer 3: Replicas"]
    D --> D1["Read replicas<br/>in different region<br/>RPO: ~seconds<br/>Immediate failover"]
    
    E["Layer 4: Cold Storage"]
    E --> E1["Weekly full dumps<br/>to S3 Glacier<br/>RPO: 1 week<br/>Long-term retention"]
    
    A --> B
    A --> C
    A --> D
    A --> E
    
    style B fill:#87CEEB
    style C fill:#87CEEB
    style D fill:#FFA500
    style E fill:#FFD700
```

### Backup Strategy by Use Case

| Recovery Need | Strategy | RTO | RPO | Cost |
|---|---|---|---|---|
| **Recent error** | WAL replay | Minutes | Seconds | Low |
| **Daily disaster** | Automated snapshot | 10-30 min | 24 hours | Medium |
| **Region failure** | Read replica | 1 min | 10 sec | Medium |
| **Compliance/audit** | Cold storage | Hours | 1 week | Low |
| **Full recovery plan** | All layers | Varies | Minimal | High |

### Backup Testing Schedule

```mermaid
timeline
    title Backup Recovery Testing

    Weekly : Test WAL replay : Point-in-time recovery
    
    Monthly : Test snapshot restore : Full database restore
    
    Quarterly : Test replica failover : Regional failover
    
    Annually : Test cold storage restore : Compliance audit
```

### Backup Configuration

```
Application config for automated backups:

# application-prod.yml

spring:
  datasource:
    # Connection pooling
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

# PostgreSQL backup settings
database:
  backup:
    # Continuous WAL archiving
    wal-archiving:
      enabled: true
      archive-to: s3://ebank-backups/wal/
      
    # Automated snapshots
    snapshots:
      enabled: true
      schedule: "0 2 * * *"  # 2 AM daily
      retention-days: 30
      
    # Point-in-time recovery
    pitr:
      enabled: true
      retention-days: 7
```

---

## Database Shutdown & Resilience

### Graceful Shutdown Flow

```mermaid
graph TD
    A["Database Maintenance Window"]
    
    B["Phase 1: Drain Connections<br/>5 minutes before shutdown"]
    B --> B1["Stop accepting new requests"]
    B --> B2["Kubernetes: Set readiness probe: FAIL"]
    B --> B3["Load balancer routes away from pod"]
    
    C["Phase 2: Wait for Queries<br/>5-10 minutes"]
    C --> C1["Current queries complete"]
    C --> C2["Connections drain naturally"]
    C --> C3["Check: SELECT * FROM pg_stat_activity"]
    
    D["Phase 3: Backup<br/>2-5 minutes"]
    D --> D1["Trigger final snapshot"]
    D --> D2["Verify backup success"]
    
    E["Phase 4: Shutdown<br/>1 minute"]
    E --> E1["Graceful SIGTERM"]
    E --> E2["PostgreSQL shuts down cleanly"]
    
    F["Result"]
    F --> F1["✅ Zero data loss"]
    F --> F2["✅ No corruption"]
    F --> F3["✅ Fast recovery"]
    
    A --> B --> C --> D --> E --> F
    
    style F1 fill:#90EE90
    style F2 fill:#90EE90
    style F3 fill:#90EE90
```

### High Availability Setup (Production)

```mermaid
graph TD
    A["Production DB Setup"]
    
    B["Primary Instance<br/>PostgreSQL<br/>us-east-1"]
    B --> B1["Processes all reads/writes"]
    
    C["Standby Replica<br/>PostgreSQL<br/>us-east-1 (different AZ)"]
    C --> C1["Real-time streaming replication<br/>Automatic failover ready"]
    
    D["Read Replica 1<br/>PostgreSQL<br/>us-west-2"]
    D --> D1["Analytics queries<br/>No failover capability"]
    
    E["Read Replica 2<br/>PostgreSQL<br/>eu-west-1"]
    E --> E1["Disaster recovery"]
    E --> E2["Regional failover"]
    
    F["Monitoring & Failover"]
    F --> F1["etcd/Consul: Cluster state"]
    F --> F2["pgBouncer: Connection pooling"]
    F --> F3["Patroni: Automatic failover"]
    
    B --> C
    B --> D
    B --> E
    C --> F
    D --> F
    E --> F
    
    style B fill:#FF6B6B
    style C fill:#FFD700
    style D fill:#87CEEB
    style E fill:#87CEEB
    style F fill:#FFA500
```

### Handling Database Shutdown Scenarios

```mermaid
graph TD
    A["Database Unavailable"]
    
    B["Brief Outage<br/>< 1 minute"]
    B --> B1["Connection pool retries"]
    B --> B2["Circuit breaker opens"]
    B --> B3["Return cached data"]
    B --> B4["User sees stale data<br/>but no error"]
    
    C["Extended Outage<br/>1-5 minutes"]
    C --> C1["Failover to standby"]
    C --> C2["DNS updated"]
    C --> C3["App reconnects"]
    C --> C4["Service resumes<br/>no code changes"]
    
    D["Full Outage<br/>> 5 minutes"]
    D --> D1["Manual intervention"]
    D --> D1a["Investigate root cause"]
    D --> D1b["Restore from backup"]
    D --> D1c["Rebuild if needed"]
    D --> D2["Service restored"]
    
    E["Mitigation Strategies"]
    E --> E1["✅ Read replicas (async)"]
    E --> E2["✅ Cache layer (Redis)"]
    E --> E3["✅ Circuit breakers"]
    E --> E4["✅ Automatic failover"]
    E --> E5["✅ Multi-region setup"]
    
    A --> B
    A --> C
    A --> D
    A --> E
    
    style E1 fill:#90EE90
    style E2 fill:#90EE90
    style E3 fill:#90EE90
    style E4 fill:#90EE90
    style E5 fill:#90EE90
```

### Application Resilience Pattern

```
Resilience Layer in Spring Boot:

1. Connection Pool Timeout
   hikari:
     maximum-pool-size: 20
     connection-timeout: 5000  # 5 sec

2. Circuit Breaker (Spring Cloud Resilience4j)
   @CircuitBreaker(name="database", fallbackMethod="getCachedBalance")
   public BigDecimal getAccountBalance(Long accountId) {
     return accountRepository.findBalance(accountId);
   }

3. Fallback (Return Cache)
   private BigDecimal getCachedBalance(Long accountId) {
     return redisService.getBalance(accountId);  // Stale OK
   }

4. Retry Logic
   @Retry(name="database", fallbackMethod="getCachedBalance")
   public void transfer(...) { ... }
```

### Health Checks & Monitoring

```mermaid
graph TD
    A["Kubernetes Pod"]
    
    B["Liveness Probe<br/>every 10 seconds"]
    B --> B1["Is pod running?"]
    B --> B2["DB timeout: FAIL"]
    B --> B3["Restart pod"]
    
    C["Readiness Probe<br/>every 5 seconds"]
    C --> C1["Is pod ready for traffic?"]
    C --> C2["DB connection: FAIL"]
    C --> C3["Remove from service"]
    
    D["Startup Probe<br/>once at start"]
    D --> D1["Can app initialize?"]
    D --> D2["DB not available: FAIL"]
    D --> D3["Pod stays starting"]
    
    E["Monitoring"]
    E --> E1["DB connection pool: warn at 80%"]
    E --> E2["Query latency: alert > 1 second"]
    E --> E3["Replication lag: alert > 100ms"]
    
    A --> B
    A --> C
    A --> D
    A --> E
    
    style B fill:#87CEEB
    style C fill:#87CEEB
    style D fill:#87CEEB
    style E fill:#FFA500
```

---

## Architecture Recommendation for ebank

### Recommended Setup (Single DB + Replicas)

```mermaid
graph TD
    A["ebank Production Architecture"]
    
    B["TIER 1: Primary Database"]
    B --> B1["PostgreSQL Primary<br/>us-east-1a<br/>- All reads<br/>- All writes<br/>- ACID transactions"]
    
    C["TIER 2: High Availability"]
    C --> C1["Standby Replica<br/>us-east-1b<br/>- Streaming replication<br/>- Auto-failover<br/>- Zero RPO"]
    
    D["TIER 3: Read Scaling"]
    D --> D1["Read Replica 1<br/>us-east-1c<br/>- Analytics<br/>- Reports<br/>- Customer portal"]
    
    D --> D2["Read Replica 2<br/>us-west-2<br/>- Disaster recovery<br/>- DR site"]
    
    E["TIER 4: Backup"]
    E --> E1["WAL Archiving<br/>S3 storage<br/>- PITR capability<br/>- 7 day retention"]
    
    E --> E2["Daily Snapshots<br/>RDS backups<br/>- 30 day retention<br/>- Cross-region copy"]
    
    F["Why This Setup?"]
    F --> F1["✅ Single logical DB<br/>(modular monolith friendly)"]
    F --> F2["✅ High availability<br/>(auto failover)"]
    F --> F3["✅ Read scaling<br/>(analytics, portal)"]
    F --> F4["✅ Disaster recovery<br/>(cross-region)"]
    F --> F5["✅ Data protection<br/>(multiple backups)"]
    
    A --> B
    A --> C
    A --> D
    A --> E
    A --> F
    
    style B fill:#FF6B6B
    style C fill:#FFD700
    style D1 fill:#87CEEB
    style D2 fill:#87CEEB
    style E1 fill:#90EE90
    style E2 fill:#90EE90
    style F fill:#FFD700
```

### Configuration for ebank

```yaml
# application-prod.yml

spring:
  datasource:
    # PRIMARY (writes + consistency-critical reads)
    url: jdbc:postgresql://ebank-primary.rds.amazonaws.com:5432/ebank
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 5000
      idle-timeout: 600000
      max-lifetime: 1800000
  
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway handles schema
    show-sql: false

# READ REPLICAS (analytics, reporting)
database:
  read-replicas:
    - url: jdbc:postgresql://ebank-replica-1.rds.amazonaws.com:5432/ebank
      purpose: analytics
    - url: jdbc:postgresql://ebank-replica-2.rds.amazonaws.com:5432/ebank
      purpose: dr-site

# BACKUP CONFIGURATION
backup:
  wal-archiving:
    enabled: true
    s3-bucket: ebank-backups
    retention-days: 7
  
  snapshots:
    enabled: true
    daily-at: "02:00"  # 2 AM UTC
    retention-days: 30
    copy-to-region: us-west-2

# MONITORING
monitoring:
  db-metrics:
    enabled: true
    connection-pool-threshold: 0.8  # warn at 80% usage
    query-timeout-ms: 5000
    replication-lag-threshold-ms: 100
```

### Migration Strategy: Single DB to HA Setup

```mermaid
graph TD
    A["Current: Single DB<br/>No replicas"]
    
    B["Step 1: Add Standby<br/>1 day"]
    B --> B1["PostgreSQL streaming replication"]
    B --> B2["Zero downtime"]
    
    C["Step 2: Add Read Replica<br/>1 day"]
    C --> C1["Analytics queries"]
    C --> C2["Async replication"]
    
    D["Step 3: Configure Failover<br/>1 day"]
    D --> D1["Patroni/etcd for auto-failover"]
    D --> D2["pgBouncer for connection pooling"]
    
    E["Step 4: Setup Backup<br/>1 day"]
    E --> E1["WAL archiving to S3"]
    E --> E2["Daily snapshots"]
    
    F["Step 5: Test & Validate<br/>2 days"]
    F --> F1["Failover test"]
    F --> F2["Backup restore test"]
    F --> F3["Load test"]
    
    G["Result"]
    G --> G1["✅ HA Setup Complete<br/>Production Ready"]
    
    A --> B --> C --> D --> E --> F --> G
    
    style G1 fill:#90EE90
```

---

## Summary: Single DB vs Multiple DBs for ebank

### Decision Matrix

| Criterion | Single DB | Multiple DBs |
|-----------|---|---|
| **Consistency** | ✅ ACID transactions | ❌ Eventually consistent |
| **Complexity** | ✅ Simple | ❌ Complex |
| **Failure Isolation** | ❌ One failure affects all | ✅ Domain isolation |
| **Scaling Reads** | ✅ Replicas possible | ✅ Independent scaling |
| **Scaling Writes** | ⚠️ Partitioning needed | ✅ Sharding possible |
| **Operational Overhead** | ✅ Low | ❌ High |
| **Backup/Recovery** | ✅ Simple | ❌ Complex |
| **Cost** | ✅ Lower | ❌ Higher |
| **Recommended for ebank** | ✅ **YES** | ❌ **Later (if needed)** |

### Recommendation: **Single Database + Replicas**

**Why for ebank:**
```
✅ Banking requires ACID transactions (across domains)
✅ Modular monolith: one codebase = one DB makes sense
✅ Simple operational complexity (important for banking)
✅ Easy backup/recovery (critical for compliance)
✅ Read scaling via replicas handles most use cases
✅ Can evolve to microservices + multiple DBs later if needed
```

**Evolution Path:**
```
Phase 1 (Now):     Single DB + Standby + Read replicas
                   → Handles 100k+ transactions/day

Phase 2 (Growth):  Add partitioning + caching
                   → Handles 1M+ transactions/day

Phase 3 (Scale):   Microservices + DB per service
                   → Only if single DB truly becomes bottleneck
```

---

## Implementation Checklist

```bash
# SINGLE DB SETUP (Recommended)
☐ PostgreSQL 15+ primary in us-east-1a
☐ Streaming replication standby in us-east-1b
☐ Read replica for analytics in us-east-1c
☐ Read replica for DR in us-west-2

# HA & FAILOVER
☐ Patroni cluster for auto-failover
☐ pgBouncer for connection pooling
☐ VIP/DNS for seamless failover
☐ Health check monitoring

# BACKUP & RECOVERY
☐ WAL archiving to S3 (7-day retention)
☐ Daily automated snapshots (30-day retention)
☐ Cross-region snapshot copies
☐ Monthly restore test (compliance)

# RESILIENCE
☐ Circuit breaker in app (Resilience4j)
☐ Connection pool timeouts (5 sec)
☐ Cache fallback layer (Redis)
☐ Query timeout (5 sec max)

# MONITORING
☐ Connection pool usage alerts
☐ Query latency monitoring
☐ Replication lag monitoring
☐ Backup success verification
☐ Daily health checks
```

---

**Single Database with replicas is the sweet spot for ebank monolith.** It provides high availability, disaster recovery, and read scaling without the complexity of distributed databases. 🎯
