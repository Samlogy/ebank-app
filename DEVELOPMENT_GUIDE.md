# ebank Development Guide

## Table of Contents

1. [Exception & Error Handling](#exception--error-handling)
2. [Environment & Profile Management](#environment--profile-management)
3. [API Versioning Strategy](#api-versioning-strategy)
4. [Database Migrations with Flyway](#database-migrations-with-flyway)
5. [Spring Boot Migration (3 → 4)](#spring-boot-migration-3--4)
6. [Java Version Migration (21 → 25)](#java-version-migration-21--25)
7. [Cache & Database Versioning](#cache--database-versioning)

---

## Exception & Error Handling

### Overview

Implement centralized, secure exception handling. Provide useful debugging info to logs (ELK) while exposing only safe information to clients.

### Architecture Flow

```mermaid
graph TD
    A["Controller"] -->|throws| B["Custom Exception<br/>AccountNotFoundException"]
    B -->|caught by| C["@RestControllerAdvice<br/>GlobalExceptionHandler"]
    C -->|logs full context<br/>& stack trace| D["ELK Stack<br/>for debugging"]
    C -->|returns safe info| E["ErrorResponse<br/>to Client"]
    E --> F["code, message,<br/>traceId only<br/>no stack trace"]
    
    style D fill:#90EE90
    style F fill:#FFB6C6
    style C fill:#87CEEB
```

### Exception Hierarchy

```mermaid
graph TD
    A["RuntimeException"]
    B["ApplicationException<br/>extends RuntimeException<br/>+ code, status, context"]
    C["AccountNotFoundException<br/>404"]
    D["InsufficientFundsException<br/>400"]
    E["ValidationException<br/>400"]
    F["BusinessException<br/>400/409"]
    
    A --> B
    B --> C
    B --> D
    B --> E
    B --> F
    
    style B fill:#FFD700
    style C fill:#87CEEB
    style D fill:#87CEEB
    style E fill:#87CEEB
    style F fill:#87CEEB
```

### Implementation Checklist

**Files to create:**
- `common/exception/ApplicationException.java` — Base exception class
- `common/exception/[DomainException].java` — Specific exceptions (AccountNotFoundException, etc.)
- `common/dto/ErrorResponse.java` — Standardized error response format
- `common/exception/GlobalExceptionHandler.java` — Central handler with @RestControllerAdvice

**Handler methods in GlobalExceptionHandler:**
- `handleApplicationException()` — All custom exceptions
- `handleValidationError()` — Spring validation errors
- `handleDataIntegrityViolation()` — Database constraint violations
- `handleUnexpected()` — Catch-all for 500 errors

### Response Format to Client

```json
{
  "code": "ACCOUNT_NOT_FOUND",
  "message": "Account not found",
  "timestamp": 1716547200000,
  "path": "/api/v1/accounts/999",
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Handled HTTP Status Codes

| Code | Exception Type | Example |
|------|---|---|
| **400** | Bad Request | Validation error, invalid input |
| **401** | Unauthorized | Missing/invalid auth token |
| **403** | Forbidden | Insufficient permissions |
| **404** | Not Found | Resource doesn't exist |
| **409** | Conflict | Constraint violation, duplicate |
| **500** | Server Error | Unexpected failure |

### Key Principles

✅ **Do:**
- Log full stack trace + context to ELK
- Include `traceId` in all responses
- Anonymize sensitive data in logs
- Use structured JSON logging

❌ **Don't:**
- Expose stack traces to clients
- Include internal system details in error messages
- Hardcode error messages
- Log passwords, tokens, or PII

---

## Environment & Profile Management

### Overview

Use Spring profiles to manage environment-specific configurations without hardcoding secrets.

### Environment Flow

```mermaid
graph LR
    A["application.yml<br/>Shared Defaults"]
    B["Local Dev<br/>application-local.yml"]
    C["Test<br/>application-test.yml"]
    D["Staging<br/>application-ref.yml"]
    E["Production<br/>application-prod.yml"]
    
    A --> B
    A --> C
    A --> D
    A --> E
    
    B --> F[".env.local<br/>Local Secrets"]
    C --> G["In-Memory DB<br/>H2"]
    D --> H["Vault<br/>Staging Secrets"]
    E --> I["Vault<br/>Prod Secrets"]
    
    style A fill:#FFD700
    style B fill:#87CEEB
    style C fill:#87CEEB
    style D fill:#FFA500
    style E fill:#FF6B6B
```

### Profile Files

| Environment | Profile | Database | Vault | Secrets |
|---|---|---|---|---|
| **Local** | `local` | PostgreSQL (localhost) | Disabled | `.env.local` file |
| **Test** | `test` | H2 In-Memory | Disabled | Hardcoded defaults |
| **Staging** | `ref` | PostgreSQL RDS | Enabled | Vault |
| **Production** | `prod` | PostgreSQL RDS | Enabled | Vault (K8s Auth) |

### Directory Structure

```
ebank-monolith/
├── src/main/resources/
│   ├── application.yml              # Defaults (all environments)
│   ├── application-local.yml        # Local development
│   ├── application-test.yml         # Testing
│   ├── application-ref.yml          # Staging
│   └── application-prod.yml         # Production
├── .env.local.example               # Template (commit this)
├── .env.local                       # Local secrets (in .gitignore)
└── .gitignore                       # Exclude .env.local
```

### Local Development Setup

```bash
# 1. Copy template
cp .env.local.example .env.local

# 2. Update with your values
DB_PASSWORD=dev-password
JWT_SECRET=dev-secret
REDIS_URL=redis://localhost:6379

# 3. Load and run
export $(cat .env.local | xargs)
java -jar app.jar --spring.profiles.active=local
```

### Configuration Overrides by Environment

```mermaid
graph TD
    A["application.yml"]
    B["JPA DDL"]
    C["Cache"]
    D["Vault"]
    
    A -->|Local| L["ddl-auto: create-drop<br/>Cache: disabled<br/>Vault: disabled"]
    A -->|Test| T["ddl-auto: create-drop<br/>Cache: disabled<br/>Vault: disabled"]
    A -->|Staging| S["ddl-auto: validate<br/>Cache: enabled<br/>Vault: enabled"]
    A -->|Prod| P["ddl-auto: validate<br/>Cache: enabled<br/>Vault: enabled<br/>clean-disabled: true"]
    
    style L fill:#87CEEB
    style T fill:#87CEEB
    style S fill:#FFA500
    style P fill:#FF6B6B
```

### .gitignore Rules

```bash
# Environment files (NEVER commit!)
.env.local
.env.*.local
.env

# But DO commit the template
!.env.local.example
```

### Activation Methods

```bash
# Command line
java -jar app.jar --spring.profiles.active=local

# Environment variable
export SPRING_PROFILES_ACTIVE=prod
java -jar app.jar

# Docker
docker run -e SPRING_PROFILES_ACTIVE=prod ebank:latest
```

### Key Principles

✅ **Do:**
- Keep `.env.local` in `.gitignore`
- Use environment variables for secrets
- Provide `.env.local.example` template
- Use Vault for staging/prod secrets

❌ **Don't:**
- Commit `.env.local` files
- Hardcode secrets in application.yml
- Mix secrets with configuration
- Use different code paths per environment

---

## API Versioning Strategy

### Overview

Support multiple API versions simultaneously to enable gradual client migration without breaking existing integrations.

### Multi-Version Architecture

```mermaid
graph TD
    A["API Request"]
    
    A -->|/api/v1/accounts| B["AccountControllerV1"]
    A -->|/api/v2/accounts| C["AccountControllerV2"]
    
    B --> D["AccountService<br/>Shared Business Logic"]
    C --> D
    
    D --> E["Database"]
    
    B --> F["DTOs V1<br/>Legacy Format"]
    C --> G["DTOs V2<br/>Enhanced Format"]
    
    style D fill:#FFD700
    style E fill:#90EE90
    style F fill:#87CEEB
    style G fill:#87CEEB
```

### Directory Structure

```
account/
├── controller/
│   ├── AccountControllerV1.java    # /api/v1/accounts
│   └── AccountControllerV2.java    # /api/v2/accounts
├── dto/
│   ├── v1/
│   │   ├── AccountResponseV1.java
│   │   └── TransferRequestV1.java
│   └── v2/
│       ├── AccountResponseV2.java
│       └── TransferRequestV2.java
└── service/
    └── AccountService.java         # Shared logic (no duplication)
```

### V1 vs V2 Comparison

| Aspect | V1 | V2 |
|--------|----|----|
| **URL** | `/api/v1/accounts` | `/api/v2/accounts` |
| **Response** | `{id, number, type, balance}` | `{id, number, type, balance, interestRate, lastActivity}` |
| **Validation** | Basic | Enhanced (daily limits) |
| **Status** | Deprecated | Current |

### Controller Routing Pattern

```mermaid
graph LR
    A["Client Request<br/>GET /api/v1/accounts/123"] --> B["Spring Routes to<br/>AccountControllerV1"]
    C["Client Request<br/>GET /api/v2/accounts/123"] --> D["Spring Routes to<br/>AccountControllerV2"]
    
    B --> E["Maps to V1 DTO<br/>AccountResponseV1"]
    D --> F["Maps to V2 DTO<br/>AccountResponseV2<br/>+ new fields"]
    
    E --> G["Service Layer<br/>Same business logic"]
    F --> G
    
    style A fill:#87CEEB
    style C fill:#87CEEB
    style G fill:#FFD700
```

### Deprecation Timeline

```mermaid
timeline
    title API Version Lifecycle
    
    v1 Release : Initial version
    
    v2 Release : Both versions available : Users have 6 months
    
    6 Months : v1 rate-limited : 10 req/min only
    
    12 Months : v1 returns 410 Gone : Remove from production
```

### Implementation Notes

**Controller level:**
- Use separate files for v1 and v2
- Both inherit from same service
- Map service response to version-specific DTO

**Service level:**
- Core business logic (no duplication)
- Both versions call same methods
- Service doesn't know about API versions

**DTO level:**
- v1: Legacy format
- v2: Extended format with new fields
- Optional backward compat mapping

---

## Database Migrations with Flyway

### Overview

Version control for database schema. Critical for consistency, auditing, and reproducibility in banking apps.

### Migration Flow

```mermaid
graph TD
    A["Developer writes SQL<br/>V1__initial_schema.sql"]
    B["Add to src/main/resources/db/migration/"]
    C["Spring Boot startup"]
    D["Flyway detects new migrations"]
    E["Check flyway_schema_history table"]
    F{New migrations?}
    G["Execute SQL migrations"]
    H["Record in flyway_schema_history"]
    I["App starts successfully"]
    
    A --> B --> C --> D --> E --> F
    F -->|Yes| G --> H --> I
    F -->|No| I
    
    style G fill:#90EE90
    style H fill:#FFD700
    style I fill:#87CEEB
```

### File Naming Convention

**Format:** `V{version}__{description}.sql`

```
✅ Correct:
V1__initial_schema.sql
V2__add_accounts_table.sql
V1_1__fix_typo.sql

❌ Wrong:
V1_initial_schema.sql          (wrong separator, use __)
001_initial_schema.sql         (missing V prefix)
V2 add accounts table.sql      (spaces in name)
```

### Migration Versioning Strategy

```mermaid
graph LR
    V1["V1__initial_schema.sql<br/>Users, Accounts, Transactions"]
    V2["V2__add_audit_columns.sql<br/>Add compliance fields"]
    V3["V3__add_indexes.sql<br/>Performance optimization"]
    V4["V4__add_constraints.sql<br/>Data integrity"]
    V5["V5__add_cache_fields.sql<br/>Support for caching"]
    
    V1 --> V2 --> V3 --> V4 --> V5
    
    style V1 fill:#87CEEB
    style V2 fill:#87CEEB
    style V3 fill:#87CEEB
    style V4 fill:#87CEEB
    style V5 fill:#87CEEB
```

### Directory Structure

```
src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__add_transactions_table.sql
├── V3__add_audit_columns.sql
├── V4__add_constraints_triggers.sql
├── V5__add_cache_fields.sql
└── ... (more migrations)
```

### Configuration by Environment

| Env | `ddl-auto` | `baseline-on-migrate` | `clean-disabled` | Use Case |
|-----|---|---|---|---|
| **local** | `validate` | `true` | `false` | Fresh starts allowed |
| **test** | `validate` | `true` | `false` | Test isolation |
| **staging** | `validate` | `false` | `true` | Production-like |
| **production** | `validate` | `false` | **true** | Never clean data |

### Migration Execution Timeline

```mermaid
graph TD
    A["App Startup"]
    B["Flyway Reads<br/>db/migration/"]
    C["Compare with<br/>flyway_schema_history"]
    D{Missing<br/>migrations?}
    E["Execute in order<br/>V1 → V2 → V3..."]
    F["Record version<br/>in history"]
    G["Validate schema<br/>matches expected"]
    H["App ready"]
    
    A --> B --> C --> D
    D -->|Yes| E --> F --> G --> H
    D -->|No| G --> H
    
    style E fill:#90EE90
    style H fill:#87CEEB
```

### Essential Flyway Commands

```bash
# Check migration status
mvn flyway:info

# Baseline existing database (local only)
mvn flyway:baseline -Dflyway.baselineVersion=1

# Run migrations (automatic on app startup)
mvn spring-boot:run
```

### flyway_schema_history Table

Records all migrations applied:

```
| version | description | type | installed_on | execution_time | success |
|---------|-------------|------|--------------|---|---------|
| 1 | initial schema | SQL | 2026-05-24 10:20 | 150 | true |
| 2 | add transactions | SQL | 2026-05-24 10:21 | 200 | true |
```

### Key Principles

✅ **Do:**
- Test migrations locally first
- Write forward-only migrations (Flyway doesn't support rollback)
- Never modify committed migrations
- Document schema changes
- Monitor history table
- Disable JPA `ddl-auto` (use `validate` only)

❌ **Don't:**
- Edit V1 after it's applied to production
- Allow `clean-disabled: false` in production
- Mix Flyway with JPA auto DDL
- Modify schema manually in production

---

## Spring Boot Migration (3 → 4)

### Key Changes

```mermaid
graph TD
    A["Spring Boot 3.3.0"] -->|Upgrade to| B["Spring Boot 4.0.6"]
    
    B --> C["Java Version"]
    C --> C1["17 → 21+<br/>required"]
    
    B --> D["Package Names"]
    D --> D1["javax.* → jakarta.*<br/>across ALL imports"]
    
    B --> E["Security Config"]
    E --> E1["Lambda DSL<br/>New API"]
    
    B --> F["Dependencies"]
    F --> F1["Lombok 1.18.30+<br/>JJWT 0.12.3+<br/>etc."]
    
    style A fill:#87CEEB
    style B fill:#FFD700
    style C1 fill:#FF6B6B
    style D1 fill:#FF6B6B
    style E1 fill:#FFA500
    style F1 fill:#FFA500
```

### Migration Process

```mermaid
graph LR
    A["1. Install Java 21"]
    B["2. Update pom.xml<br/>parent + java.version"]
    C["3. Replace javax →<br/>jakarta imports"]
    D["4. Update Security<br/>configuration"]
    E["5. Update dependencies"]
    F["6. Compile & test"]
    G["7. Deploy"]
    
    A --> B --> C --> D --> E --> F --> G
    
    style C fill:#FF6B6B
    style D fill:#FFA500
    style F fill:#90EE90
```

### Critical: javax → jakarta Migration

```mermaid
graph TD
    A["Find all javax imports"]
    B["grep -r 'import javax.' src/"]
    C["Replace with jakarta"]
    D["find . -exec sed -i 's/javax/jakarta/g'"]
    E["Verify cleanup"]
    F["grep -r 'import javax.' src/ && echo ERROR"]
    
    A --> B --> C --> D --> E --> F
    
    style B fill:#87CEEB
    style D fill:#90EE90
    style F fill:#87CEEB
```

### Pre-Migration Checklist

```bash
☐ Current state: java -version (show Java 17)
☐ Current state: mvn --version
☐ Current state: git status (clean)
☐ Create branch: git checkout -b upgrade/sb3-to-sb4
```

### Migration Checklist

```bash
# Java & Build
☐ Install Java 21
☐ Configure IDE (IntelliJ/VS Code)
☐ Update pom.xml parent (4.0.6)
☐ Update pom.xml java.version (21)

# Code Changes
☐ Replace javax → jakarta imports
☐ Update Spring Security configuration
☐ Update dependencies (Lombok, JJWT, etc.)

# Testing
☐ mvn clean compile -DskipTests
☐ mvn test
☐ mvn verify
☐ Local smoke test

# Infrastructure
☐ Update Dockerfile (Java 21)
☐ Test Docker build
☐ Deploy to staging
☐ Monitor for 48 hours
☐ Deploy to production
```

### Timeline

| Phase | Effort | Duration |
|-------|--------|----------|
| Setup & Java | 30 min | Day 1 |
| POM + fixes | 2 hours | Day 1 |
| jakarta migration | 1 hour | Day 1 |
| Testing | 2 hours | Day 1-2 |
| Staging validation | 4 hours | Day 2-3 |
| **Total** | **~10 hours** | **3 days** |

---

## Java Version Migration (21 → 25)

### ⚠️ LTS vs Non-LTS

```mermaid
graph LR
    A["Java 21 LTS<br/>Support: Sept 2028"] -->|Yes, stable| B["Production Use<br/>✅ Recommended"]
    C["Java 25 Non-LTS<br/>Support: March 2026"] -->|No, short window| D["Production Use<br/>❌ Not Recommended"]
    
    style B fill:#90EE90
    style D fill:#FF6B6B
```

### Decision Tree

```mermaid
graph TD
    A{Need to upgrade<br/>to Java 25?}
    
    A -->|No specific<br/>feature needed| B["STAY on Java 21 LTS<br/>Support until 2028"]
    A -->|Yes, specific<br/>feature| C{Production<br/>app?}
    
    C -->|Yes| D["⚠️ Risky<br/>6-month support window<br/>Not recommended"]
    C -->|No| E["✅ OK for dev/test<br/>Go ahead"]
    
    style B fill:#90EE90
    style D fill:#FF6B6B
    style E fill:#87CEEB
```

### Migration Process (if needed)

```bash
# 1. Install Java 25
brew install openjdk@25
export JAVA_HOME=$(/usr/libexec/java_home -v 25)

# 2. Update pom.xml
# <java.version>25</java.version>

# 3. Update Dockerfile
# FROM eclipse-temurin:25-jdk-alpine

# 4. Test
mvn clean compile
mvn test && mvn verify

# 5. Deploy
docker build -t ebank:java25 .
```

### Migration Checklist

```bash
☐ Install Java 25
☐ Update pom.xml (java.version)
☐ Update dependencies
☐ Compile & test
☐ Check deprecations: mvn compile -Xlint:deprecation
☐ Update Dockerfile
☐ Test Docker image
☐ Performance benchmark
☐ Deploy to staging
☐ Monitor 48 hours
☐ Deploy to production
```

### Timeline

| Phase | Effort | Duration |
|-------|--------|----------|
| Setup | 30 min | Day 1 |
| Config | 30 min | Day 1 |
| Testing | 2 hours | Day 1-2 |
| Staging | 4 hours | Day 2-3 |
| **Total** | **~9 hours** | **3 days** |

### Recommendation

**For banking applications (ebank):** Stay on **Java 21 LTS**. Next upgrade to **Java 27 LTS** when released (Sept 2026).

---

## Cache & Database Versioning

### The Challenge

```mermaid
graph TD
    A["v1 to v2 Migration"]
    
    B["Scenario: Rolling Deployment"]
    B --> B1["Pod1,2: v1 running"]
    B --> B2["Pod3,4: v2 starting"]
    
    B1 -->|Cache key| C["account:v1:123<br/>old format"]
    B2 -->|Cache key| D["account:v2:123<br/>new format"]
    
    C -->|Data mismatch| E["⚠️ Problem:<br/>Stale cache reads<br/>Inconsistent data"]
    D -->|Missing data| E
    
    style E fill:#FF6B6B
```

### Solution: Versioned Cache Keys

```mermaid
graph LR
    A["Version 1 Deployed"]
    A -->|Cache Key| B["account:v1:123"]
    A -->|DB Schema| C["Basic fields"]
    
    D["Version 2 Deployed"]
    D -->|Cache Key| E["account:v2:123"]
    D -->|DB Schema| F["New columns added"]
    
    B -->|No collision| E
    C -->|Migration| F
    
    style B fill:#87CEEB
    style E fill:#90EE90
```

### Migration Timeline

```mermaid
graph TD
    A["Phase 1: Preparation"]
    A --> A1["Increment app.cache.version: 1→2"]
    A --> A2["Update cache keys (add version)"]
    A --> A3["Write Flyway migration"]
    A --> A4["Update DTOs"]
    
    B["Phase 2: Cache Warming"]
    B --> B1["Write CacheWarmingTask"]
    B --> B2["Pre-populate v2 cache before rollout"]
    
    C["Phase 3: Deployment"]
    C --> C1["Deploy v2 pods one by one"]
    C --> C2["Each pod runs migration + cache warming"]
    
    D["Phase 4: Monitoring"]
    D --> D1["Track cache hit rates"]
    D --> D2["Verify DB ↔ Cache consistency"]
    
    E["Phase 5: Cleanup"]
    E --> E1["Allow v1 cache keys to expire"]
    E --> E2["Document for next migration"]
    
    A --> B --> C --> D --> E
    
    style B fill:#90EE90
    style C fill:#FFD700
    style D fill:#87CEEB
```

### Cache Key Strategy

```mermaid
graph TD
    A["Old v1 Code"]
    A --> B["Cache: account:v1:123"]
    A --> C["Format: accountId, balance"]
    
    D["New v2 Code"]
    D --> E["Cache: account:v2:123"]
    D --> F["Format: accountId, balance,<br/>interestRate, lastActivity"]
    
    G["Result"]
    G --> H["✅ No collision<br/>✅ No stale reads<br/>✅ Both coexist safely"]
    
    style B fill:#87CEEB
    style E fill:#90EE90
    style H fill:#90EE90
```

### Architecture: Cache + DB Versioning

```mermaid
graph TD
    A["App v2 Starts"]
    
    B["Step 1: Run Flyway"]
    B --> B1["V5__add_cache_fields.sql"]
    B --> B2["Add interest_rate column"]
    B --> B3["Add last_activity_at column"]
    
    C["Step 2: Warm Cache"]
    C --> C1["CacheWarmingTask runs"]
    C --> C2["Read from DB (with new columns)"]
    C --> C3["Store in cache:v2:key"]
    
    D["Step 3: Handle Requests"]
    D --> D1["Read from cache:v2"]
    D --> D2["Get new fields"]
    
    E["Step 4: Invalidate"]
    E --> E1["On update, clear v1 AND v2 keys"]
    E --> E2["Prevent stale data"]
    
    A --> B --> C --> D --> E
    
    style B fill:#90EE90
    style C fill:#FFD700
    style D fill:#87CEEB
    style E fill:#FFB6C6
```

### Kubernetes Rolling Deployment

```mermaid
graph TD
    A["Rolling Deployment Strategy"]
    
    B["Pod A (v1)"]
    B --> B1["Running"]
    
    C["Pod B (v2 - NEW)"]
    C --> C1["1. Run Flyway migration"]
    C --> C2["2. Run cache warming"]
    C --> C3["3. Start serving requests"]
    
    D["Pod A (v1)"]
    D --> D1["Drain gracefully"]
    
    E["Pod C (v2 - NEW)"]
    E --> E1["Same as Pod B"]
    
    F["Result"]
    F --> F1["✅ Zero downtime"]
    F --> F2["✅ Cache pre-warmed"]
    F --> F3["✅ DB schema applied"]
    F --> F4["✅ No stale reads"]
    
    A --> B
    A --> C
    B --> D
    D --> E
    E --> F
    
    style C fill:#90EE90
    style E fill:#90EE90
    style F fill:#90EE90
```

### Monitoring Cache Consistency

```mermaid
graph LR
    A["Monitoring Endpoints"]
    
    B["/actuator/cache/consistency-check"]
    B --> B1["Total accounts"]
    B --> B2["Cached accounts"]
    B --> B3["Hit rate %"]
    
    C["/actuator/cache/verify/{id}"]
    C --> C1["Compare cache value"]
    C --> C2["vs DB value"]
    C --> C3["Alert if mismatch"]
    
    D["Alert Thresholds"]
    D --> D1["Hit rate < 70%: WARN"]
    D --> D2["Mismatch detected: ALERT"]
    D --> D3["DB ≠ Cache: Invalidate"]
    
    A --> B
    A --> C
    A --> D
    
    style B fill:#87CEEB
    style C fill:#87CEEB
    style D1 fill:#FFA500
    style D2 fill:#FF6B6B
```

### Deployment Checklist

```bash
# PHASE 1: Preparation
☐ Increment app.cache.version (1 → 2)
☐ Update cache key format: "account:v{version}:{id}"
☐ Write Flyway migration: V5__add_cache_fields.sql
☐ Update DTOs with new fields
☐ Write CacheWarmingTask

# PHASE 2: Testing
☐ Test locally: versioned cache keys work
☐ Test Flyway migration
☐ Test cache warming
☐ Simulate rolling deployment

# PHASE 3: Staging
☐ Deploy to staging
☐ Monitor cache hit rates
☐ Verify DB ↔ Cache consistency
☐ Monitor 48 hours

# PHASE 4: Production
☐ Backup Redis + PostgreSQL
☐ Deploy with rolling strategy
☐ Monitor cache consistency
☐ Monitor error rates
☐ Alert if cache hit < 70%

# PHASE 5: Cleanup
☐ Allow v1 cache to expire
☐ Daily cleanup script
☐ Document for next migration
```

### Key Principles

| Principle | Why |
|-----------|-----|
| **Versioned Cache Keys** | v1 and v2 don't collide, no stale reads |
| **DB Migration First** | Flyway applies before cache uses new columns |
| **Cache Warming Before** | Pre-populate v2 cache to avoid misses during rollout |
| **Both Versions in Deploy** | Invalidate both v1 and v2 keys on updates |
| **Monitor Consistency** | Track hits/misses, alert on mismatches |
| **Rolling Deployment** | Gradual v2 rollout, coexist with v1 safely |

---

## Summary

### Key Practices

| Area | Practice | Benefit |
|------|----------|---------|
| **Exceptions** | Centralized handler + traceId | Secure, observable, debuggable |
| **Environments** | Profile-based config + Vault | Secrets-safe, reproducible |
| **API Versioning** | Multi-version with shared service | Gradual migration, no breaking changes |
| **Migrations** | Flyway version control | Consistent, auditable, reproducible |
| **Upgrades** | Systematic process with testing | Safe, zero-downtime deployments |
| **Cache + DB** | Versioned keys + warming | Consistent data during rolling updates |

### Quick Reference

```mermaid
graph TD
    A["Production-Ready ebank"]
    
    A --> B["Error Handling"]
    B --> B1["@RestControllerAdvice<br/>Centralized + ELK logging"]
    
    A --> C["Configuration"]
    C --> C1["Profiles + Vault<br/>No hardcoded secrets"]
    
    A --> D["APIs"]
    D --> D1["v1 + v2 simultaneous<br/>Gradual migration"]
    
    A --> E["Database"]
    E --> E1["Flyway migrations<br/>Version-controlled schema"]
    
    A --> F["Cache"]
    F --> F1["Versioned keys<br/>Safe migrations"]
    
    A --> G["Security"]
    G --> G1["jakarta.*, Spring Security Lambda DSL<br/>Latest frameworks"]
    
    style A fill:#FFD700
    style B fill:#87CEEB
    style C fill:#87CEEB
    style D fill:#87CEEB
    style E fill:#87CEEB
    style F fill:#87CEEB
    style G fill:#87CEEB
```

---

**All practices prioritize production-readiness, security, observability, and reliability for a banking application.** ✅
