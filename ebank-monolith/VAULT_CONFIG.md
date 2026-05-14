# Vault Configuration — eBank Monolith

This document explains why HashiCorp Vault was added, exactly how it works in this project, every technical decision that was made, and the trade-offs involved. Read this top-to-bottom once and you will understand everything.

---

## Table of Contents

1. [Why Vault?](#1-why-vault)
2. [Mental Model — What Vault Replaces](#2-mental-model--what-vault-replaces)
3. [Vault Path Structure](#3-vault-path-structure)
4. [What Lives in Vault vs YAML](#4-what-lives-in-vault-vs-yaml)
5. [Environments: local, E2, E1](#5-environments-local-e2-e1)
6. [Authentication Strategies](#6-authentication-strategies)
7. [Spring Cloud Vault Integration](#7-spring-cloud-vault-integration)
8. [Property Priority — How Spring Merges YAML and Vault](#8-property-priority--how-spring-merges-yaml-and-vault)
9. [Local Dev Workflow](#9-local-dev-workflow)
10. [CI/CD Pipeline Integration](#10-cicd-pipeline-integration)
11. [Adding a New Environment](#11-adding-a-new-environment)
12. [Technical Trade-offs](#12-technical-trade-offs)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Why Vault?

### The problem with environment variables and `.env` files

Before Vault the app was configured via environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_PASSWORD=secret
JWT_SECRET=very-long-key
```

This works, but creates several operational problems:

| Problem | Impact |
|---|---|
| Secrets travel as plain text in shell environments, CI logs, and `.env` files | Any team member with server access can read them |
| No audit trail — you cannot tell who read or changed a credential | Compliance and incident response are hard |
| Rotating a password requires redeploying every service that uses it | Rotation is painful, so it rarely happens |
| Config is scattered — different files for different environments | Drift between environments is invisible |
| All config is "flat" — no versioning, no rollback | A bad value causes an outage with no easy undo |

### What Vault provides

- **Single source of truth**: every environment's full config lives in one place.
- **Access control**: the app gets a token that can _only read_ its own paths. It cannot write, cannot read other services' secrets, cannot even list what else exists.
- **Audit log**: Vault records every read with a timestamp and client identity.
- **Versioning**: every `vault kv put` creates a new version. Rolling back a bad value is one command.
- **Dynamic rotation**: credentials can be rotated in Vault without touching the app. The app picks up new values on next restart.
- **Centralised non-secret config**: Vault stores _all_ per-environment config (logging levels, pool sizes, rate limits), not just secrets. This eliminates environment-specific YAML files as a config source.

---

## 2. Mental Model — What Vault Replaces

Before:

```
docker-compose.yml
  └── env vars → app reads SPRING_DATASOURCE_URL, JWT_SECRET, etc.
```

After:

```
docker-compose.yml
  └── env vars → Vault connection only (VAULT_HOST, VAULT_ROLE_ID, VAULT_ENV_ID)
                    ↓
                 Vault KV
                    └── secret/e-bank/monolith/{ENV_ID}/config
                            ↓ (Spring Cloud Vault reads at startup)
                         app gets ALL config from Vault
```

The only environment variables the container now needs are:
- Where is Vault (`VAULT_HOST`, `VAULT_PORT`, `VAULT_SCHEME`)
- How to authenticate (`VAULT_TOKEN` for dev / `VAULT_ROLE_ID` + `VAULT_SECRET_ID` for prod)
- Which environment path to use (`VAULT_ENV_ID`)

Everything else — datasource, JWT expiration, logging, actuator, rate limits — comes from Vault.

---

## 3. Vault Path Structure

The project uses **KV version 2** (key-value secrets engine with versioning).

### Mount point

```
secret/          ← KV v2 mount (default Vault mount name)
```

### Environment paths

```
secret/
  e-bank/
    monolith/
      local/
        config     ← local development
      E2/
        config     ← testing / staging
      E1/
        config     ← production
```

### Path naming conventions

| Segment | Value | Meaning |
|---|---|---|
| `e-bank` | fixed | project / organisation namespace |
| `monolith` | fixed | which sub-project (monolith vs microservices) |
| `local` / `E1` / `E2` | `VAULT_ENV_ID` env var | selects the environment |
| `config` | fixed | the secret name within that environment |

The `+` wildcard in the Vault policy (`secret/data/e-bank/monolith/+/config`) covers all environments without listing each one.

### Why this hierarchy?

- **Namespace isolation**: if you ever run multiple projects in the same Vault cluster, `e-bank/` keeps them separate.
- **Service isolation**: `monolith/` separates the monolith from the microservices, which have their own paths.
- **Environment isolation**: `E1/`, `E2/`, `local/` each hold a completely independent config snapshot. Changing E2 has zero effect on E1.
- **Single key per env**: one `config` key per environment keeps the data flat and easy to inspect (`vault kv get secret/e-bank/monolith/E1/config`).

### KV v2 vs v1 — why v2?

KV v2 adds versioning. Every `vault kv put` creates a new numbered version instead of overwriting. You can:

```bash
# See the full history
vault kv metadata get secret/e-bank/monolith/E1/config

# Roll back to the previous version
vault kv rollback -version=2 secret/e-bank/monolith/E1/config
```

This is critical in production: if a misconfigured value causes an outage, rollback takes seconds.

---

## 4. What Lives in Vault vs YAML

The rule: **YAML holds framework constants; Vault holds everything that differs between environments.**

### In `application.yaml` (never changes, committed to Git)

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver   # same everywhere
  jpa:
    properties:
      hibernate:
        format_sql: true                       # framework behaviour
        jdbc.batch_size: 20                    # tuning constant
        jdbc.fetch_size: 50
  cloud:
    vault:
      enabled: false                           # profiles override this
```

### In Vault (per-environment, never in Git)

| Property | local | E2 (testing) | E1 (prod) |
|---|---|---|---|
| `spring.datasource.url` | postgres container | test DB host | prod DB host |
| `spring.datasource.username` | `ebank_user` | test user | prod user |
| `spring.datasource.password` | `ebank_password` | secret | secret |
| `spring.datasource.hikari.maximum-pool-size` | 10 | 10 | 20 |
| `spring.jpa.hibernate.ddl-auto` | `update` | `update` | `validate` |
| `spring.jpa.show-sql` | `true` | `false` | `false` |
| `jwt.expiration` | 86400000 | 86400000 | 86400000 |
| `admin.email` | dev admin | test admin | prod admin |
| `admin.password` | simple | secret | secret |
| `rate-limiting.login.capacity` | 10 | 20 | 10 |
| `logging.level.root` | INFO | INFO | WARN |
| `logging.level.com.ebank` | DEBUG | DEBUG | INFO |
| `management.endpoints.web.exposure.include` | health,info | health,info | health |

**Important**: `jwt.secret` is _not_ stored in Vault for this monolith because the app generates a fresh RSA-2048 key pair in memory on every startup (`JwtKeyStore.java`). There is no shared symmetric secret to protect.

### Why store non-secrets (logging levels, pool sizes) in Vault?

The user requirement was "Vault will contain all config for each environment, not just secrets." There are real reasons this is a good pattern:

1. **One place to look**: operators don't have to check YAML files _and_ Vault to understand why an environment behaves differently.
2. **Change without redeployment**: tuning a pool size or log level in Vault takes effect on next app restart without touching the image or YAML.
3. **Audit trail for config changes**: every change is logged with who made it and when.
4. **Environment parity is visible**: comparing `vault kv get E1/config` vs `vault kv get E2/config` immediately shows every difference.

---

## 5. Environments: local, E2, E1

### local — development

- **Purpose**: individual developer machine.
- **Vault mode**: dev server (`hashicorp/vault:1.17` in dev mode). Data is ephemeral — lost when the container is removed. That is intentional: local Vault is always re-seeded on startup via `vault-init`.
- **Auth**: root token (`root`). No authentication ceremony needed.
- **Seed**: `vault/seeds/local.json` is loaded automatically by `vault-init`.
- **Key differences**: `show-sql: true`, `ddl-auto: update`, verbose logging.

### E2 — testing / staging

- **Purpose**: shared environment where QA runs tests, the CI pipeline verifies releases before production, and integration tests run against real infrastructure.
- **Vault mode**: persistent Vault server (production-grade, not dev mode).
- **Auth**: AppRole (same as prod). The pipeline injects `VAULT_ROLE_ID` and `VAULT_SECRET_ID`.
- **Seed**: `vault/seeds/E2.json` is the template. Replace all `CHANGE_ME_*` values before loading.
- **Key differences from E1**: `ddl-auto: update` (schema can evolve), rate limit is looser (20/min) so automated tests don't hit limits, both health and info actuator endpoints exposed.

### E1 — production

- **Purpose**: live environment serving real users.
- **Vault mode**: persistent Vault cluster (HA recommended).
- **Auth**: AppRole. Credentials are rotated regularly.
- **Seed**: `vault/seeds/E1.json` is the template. Load once; use `vault kv put` for updates.
- **Key differences**: `ddl-auto: validate` (schema must be managed by migrations), `show-sql: false`, WARN-level root logging, only health actuator exposed.

---

## 6. Authentication Strategies

Spring Cloud Vault supports many auth methods. This project uses two.

### Token auth (local only)

```yaml
spring:
  cloud:
    vault:
      authentication: TOKEN
      token: ${VAULT_TOKEN:root}
```

The app presents the token directly to Vault. Simple, but:
- Tokens do not expire automatically in dev mode.
- You must protect the token just as carefully as any password.
- **Only used in local dev**, where the Vault server is ephemeral and the token is public (`root`).

### AppRole auth (E2, E1)

```yaml
spring:
  cloud:
    vault:
      authentication: APPROLE
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
```

AppRole is designed for machine-to-machine authentication:

1. The app presents a `role-id` (semi-public, identifies _what_ is authenticating) and a `secret-id` (rotatable, proves the app is legitimate).
2. Vault validates both and issues a short-lived token (TTL 1 hour, max 4 hours).
3. Spring Cloud Vault uses this token for all subsequent reads and automatically renews it before expiry.

**Why AppRole over a long-lived token?**

| | Long-lived token | AppRole |
|---|---|---|
| Rotation | Manual, disruptive | `secret-id` rotated independently |
| Blast radius if leaked | Full access until manually revoked | Token expires in ≤1h |
| CI/CD integration | Secrets must be stored somewhere | `role-id` is safe to store; `secret-id` is injected at deploy time |

### Future: Kubernetes auth

For K8s deployments, Vault's Kubernetes auth method is the preferred approach. The pod's service account token is presented to Vault, which verifies it against the K8s API. No long-lived credentials need to be injected at all. This is already prepared for by the `ebank-monolith` AppRole policy — the K8s auth method would be wired to the same `ebank-monolith` policy.

---

## 7. Spring Cloud Vault Integration

### Dependency

```xml
<!-- pom.xml -->
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>2023.0.3</version>   <!-- compatible with Spring Boot 3.3.x -->
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
```

### How it wires in

Spring Cloud Vault uses the **Config Data API** (Spring Boot 2.4+). There is no `bootstrap.yml` — integration happens via `spring.config.import`.

When `application-local.yaml` (or `application-prod.yaml`) is loaded:

```yaml
spring:
  config:
    import: "vault://"          # trigger the Vault import
  cloud:
    vault:
      enabled: true
      kv:
        default-context: "e-bank/monolith/${VAULT_ENV_ID:local}/config"
        backend-version: 2
```

Spring sees `vault://` in `spring.config.import` and:
1. Reads `spring.cloud.vault.*` from the current file to know where and how to connect.
2. Authenticates using the configured auth method.
3. Reads the KV secret at `secret/data/e-bank/monolith/{ENV_ID}/config`.
4. Adds all key-value pairs from that secret into the Spring `Environment` as a high-priority `PropertySource`.

### How secrets are stored in Vault KV

Vault KV stores secrets as a JSON object where each key is a string and each value is a string. Spring Cloud Vault maps the Vault keys directly to Spring property names:

```
Vault key                                   → Spring property
────────────────────────────────────────────────────────────────
spring.datasource.url                       → spring.datasource.url
spring.datasource.hikari.maximum-pool-size  → spring.datasource.hikari.maximum-pool-size
logging.level.com.ebank                     → logging.level.com.ebank
```

This is why the seed files (`vault/seeds/*.json`) use dot-separated Spring property paths as keys — they map directly with no transformation.

### `VAULT_ENV_ID` variable

This is the key design mechanism. It is an **OS environment variable** that selects which Vault path the app reads. It is completely separate from `SPRING_PROFILES_ACTIVE`:

| Variable | Purpose | Set by |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Selects Spring profile (`local` / `prod`), which controls the Vault _auth method_ and other Spring behaviours | Docker Compose or pipeline |
| `VAULT_ENV_ID` | Selects the Vault _path_ to read config from (`local`, `E1`, `E2`, …) | Docker Compose or pipeline |

This separation is intentional. It lets you run the `prod` Spring profile but point at the `E2` Vault path — or run `local` profile but point at a shared dev Vault path. The two axes are independent.

---

## 8. Property Priority — How Spring Merges YAML and Vault

Spring Cloud Vault property sources rank **higher** than `application.yaml` or `application-{profile}.yaml` files. The full order from highest to lowest priority:

```
1. OS environment variables (SPRING_DATASOURCE_URL, etc.)   ← highest
2. Vault KV properties (via spring.config.import)
3. application-{profile}.yaml
4. application.yaml                                         ← lowest
```

Practical consequences:

- If `SPRING_DATASOURCE_URL` is set as an OS env var, it overrides the Vault value. This is intentional — it lets operators do emergency overrides without touching Vault.
- `application.yaml` retains fallback values for the no-profile case (unit tests). Tests never activate the `local` or `prod` profile, so Vault is never contacted. They use the H2 config from `src/test/resources/application.yaml`.
- Profile YAML files (`application-local.yaml`, `application-prod.yaml`) hold only Vault connection config. They do not re-declare datasource or JWT properties — those come exclusively from Vault.

---

## 9. Local Dev Workflow

### First start

```bash
docker compose -f docker-compose.yml -f docker-compose.vault.yml up -d --build
```

What happens in order:

```
1. postgres starts and passes its healthcheck.
2. vault starts in dev mode (root token = "root"). Passes healthcheck.
3. vault-init runs init.sh:
     - Mounts KV v2 at secret/
     - Writes local.json → secret/e-bank/monolith/local/config
     - Writes E2.json   → secret/e-bank/monolith/E2/config
     - Writes the ebank-monolith policy
     - Enables AppRole, creates the role, prints VAULT_ROLE_ID + VAULT_SECRET_ID
   vault-init exits with code 0.
4. app starts only after vault-init completes (condition: service_completed_successfully).
   Spring Boot activates the 'local' profile.
   Spring Cloud Vault connects to vault:8200 with token "root".
   Reads secret/e-bank/monolith/local/config.
   All 19 config properties are loaded into the Spring Environment.
   DataSource is created with the correct URL (postgres:5432/ebank_dev).
   App passes its healthcheck.
```

### Subsequent starts

On the second `docker compose up`, the `vault-init` container runs again because its state is ephemeral (Vault dev mode resets on restart). All config is re-seeded in a few seconds.

If you want to persist Vault data across restarts, add a volume to the Vault service:
```yaml
vault:
  volumes:
    - vault_data:/vault/data
  environment:
    VAULT_DEV_ROOT_TOKEN_ID: root
```
But for local dev, ephemeral is usually preferable — it guarantees a clean, reproducible state.

### Inspect / modify local config

```bash
# View current local config
vault kv get secret/e-bank/monolith/local/config

# Update a single value (creates a new version, old version preserved)
vault kv patch secret/e-bank/monolith/local/config \
  "logging.level.com.ebank=TRACE"

# Restore previous version
vault kv rollback -version=1 secret/e-bank/monolith/local/config

# List all versions
vault kv metadata get secret/e-bank/monolith/local/config
```

To apply changes without restarting the full stack, restart only the app:
```bash
docker compose restart app
```

---

## 10. CI/CD Pipeline Integration

The Jenkins pipeline (see `jenkins/Jenkinsfile`) needs to supply Vault credentials so the deployed app can read its config at runtime. Here is the flow for each environment.

### E2 (testing) deployment

```
Pipeline stage: Deploy to E2
  1. Pipeline reads VAULT_ROLE_ID and VAULT_SECRET_ID from Jenkins credentials store.
  2. These are passed as container environment variables — never written to disk.
  3. App container starts with:
       SPRING_PROFILES_ACTIVE=prod
       VAULT_HOST=vault.test.example.com
       VAULT_ENV_ID=E2
       VAULT_ROLE_ID=<from Jenkins>
       VAULT_SECRET_ID=<from Jenkins>
  4. Spring Boot activates the prod profile → application-prod.yaml configures AppRole auth.
  5. Spring Cloud Vault authenticates, gets a 1h token, reads:
       secret/e-bank/monolith/E2/config
  6. App starts with test-environment config.
```

### E1 (production) deployment

Identical to E2, except `VAULT_ENV_ID=E1` and the Vault address points to the production cluster.

### Jenkins credentials setup

In Jenkins, store the following as **Secret Text** credentials:

| Credential ID | Value |
|---|---|
| `VAULT_HOST_E1` | Vault hostname for prod |
| `VAULT_HOST_E2` | Vault hostname for testing |
| `VAULT_ROLE_ID` | AppRole role-id (same for all envs if using one role) |
| `VAULT_SECRET_ID_E1` | AppRole secret-id for prod |
| `VAULT_SECRET_ID_E2` | AppRole secret-id for testing |

In `Jenkinsfile`:
```groovy
environment {
    VAULT_ROLE_ID    = credentials('VAULT_ROLE_ID')
    VAULT_SECRET_ID  = credentials("VAULT_SECRET_ID_${ENV_ID}")
    VAULT_HOST       = credentials("VAULT_HOST_${ENV_ID}")
}

stage('Deploy') {
    steps {
        sh """
          docker run -d \
            -e SPRING_PROFILES_ACTIVE=prod \
            -e VAULT_HOST=${VAULT_HOST} \
            -e VAULT_ENV_ID=${ENV_ID} \
            -e VAULT_ROLE_ID=${VAULT_ROLE_ID} \
            -e VAULT_SECRET_ID=${VAULT_SECRET_ID} \
            ${APP_IMAGE}
        """
    }
}
```

### Secret-id rotation

`secret-id` tokens should be rotated regularly. The recommended pipeline pattern:

1. Before each deployment, generate a new `secret-id`:
   ```bash
   vault write -field=secret_id -f auth/approle/role/ebank-monolith/secret-id
   ```
2. Pass the new `secret-id` to the container.
3. The old `secret-id` is automatically invalidated (or expires, depending on TTL).

This way, even if a previous deployment's `secret-id` is leaked, it is no longer valid.

---

## 11. Adding a New Environment

Example: adding `E3` as a UAT environment.

### Step 1 — Create the seed file

```bash
cp vault/seeds/E2.json vault/seeds/E3.json
```

Edit `E3.json` with the appropriate values (DB host, credentials, etc.).

### Step 2 — Load into Vault

On the Vault cluster that serves E3:

```bash
VAULT_ADDR=https://vault.uat.example.com VAULT_TOKEN=<admin-token> \
  vault kv put secret/e-bank/monolith/E3/config @vault/seeds/E3.json
```

The existing policy already covers E3 — the `+` wildcard matches any single path segment including `E3`. No policy changes needed.

### Step 3 — Deploy

```bash
docker run -d \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e VAULT_HOST=vault.uat.example.com \
  -e VAULT_ENV_ID=E3 \
  -e VAULT_ROLE_ID=<role-id> \
  -e VAULT_SECRET_ID=<secret-id> \
  your-registry/ebank-monolith:1.0.0
```

No code changes, no YAML changes, no image rebuild.

---

## 12. Technical Trade-offs

### Trade-off 1: Spring Cloud Vault vs Vault Agent Sidecar

| | Spring Cloud Vault (chosen) | Vault Agent Sidecar |
|---|---|---|
| How it works | App connects to Vault directly at startup | Vault Agent runs alongside the app, writes secrets to files or env vars |
| App awareness | App knows about Vault | App is unaware of Vault — reads env vars or files |
| K8s dependency | Works anywhere | Requires K8s or explicit sidecar setup |
| Secret rotation | Requires app restart | Agent can push updates without restart |
| Complexity | Low (just a Spring dependency) | Higher (sidecar lifecycle, file watching) |

**Decision**: Spring Cloud Vault was chosen because the stack currently uses Docker Compose, not K8s. When the project moves to K8s, adding Vault Agent Injector alongside Spring Cloud Vault is straightforward — both can coexist.

### Trade-off 2: All config in Vault vs secrets only

| | Secrets only in Vault | All config in Vault (chosen) |
|---|---|---|
| Vault reads per startup | 1 (small payload) | 1 (larger payload, same round trip) |
| Where to look for env differences | Vault + YAML files | Vault only |
| Risk of YAML/Vault drift | High | None |
| Non-secret changes require Git commit | Yes | No |

**Decision**: all config in Vault. The operational simplicity of a single source of truth outweighs any downsides.

### Trade-off 3: VAULT_ENV_ID vs mapping Spring profile to Vault path

Alternative approach: map `prod` Spring profile directly to `E1` Vault path (no separate `VAULT_ENV_ID`).

| | Profile = Vault path | VAULT_ENV_ID (chosen) |
|---|---|---|
| Flexibility | One Vault path per Spring profile | Many Vault paths, any profile |
| Complexity | Simpler | Slightly more env vars |
| Blue/green deploys | Hard (both are `prod` profile) | Easy (E1 and E1-canary are different VAULT_ENV_IDs) |
| Multiple prod regions | Hard | Easy (E1-us, E1-eu) |

**Decision**: `VAULT_ENV_ID` gives far more flexibility for blue/green deployments and multi-region setups.

### Trade-off 4: Ephemeral local Vault vs persistent local Vault

| | Ephemeral (chosen) | Persistent |
|---|---|---|
| State after restart | Fresh, always re-seeded | Preserved |
| Reproducibility | Guaranteed | Depends on what was changed |
| Onboarding friction | Zero — just `docker compose up` | Must explain Vault state management |
| Local customisation | Survives only until next restart | Persists |

**Decision**: ephemeral is better for local development. If a developer needs persistent config, they can comment out the `vault-init` depends_on block after first seed.

### Trade-off 5: Vault dev mode vs full Vault for local dev

| | Dev mode (chosen) | Full Vault locally |
|---|---|---|
| Setup | Zero — single container | Requires initialisation, unsealing |
| TLS | None (HTTP) | Optional |
| HA / clustering | No | Configurable |
| Data persistence | None | File or database backend |
| Root token | Fixed (`root`) | Generated at init |

**Decision**: dev mode is the right choice for local development. The trade-off (no persistence, no TLS, fixed token) is explicitly acceptable for a non-production environment.

---

## 13. Troubleshooting

### App fails to start: `Could not resolve placeholder 'VAULT_HOST'`

The `prod` profile is active but `VAULT_HOST` was not passed to the container. All required env vars for the prod profile:

```
VAULT_HOST       (required, no default)
VAULT_ROLE_ID    (required, no default)
VAULT_SECRET_ID  (required, no default)
VAULT_ENV_ID     (defaults to E1 if not set)
```

### App fails to start: `Connection refused to vault:8200`

The `local` profile is active but Vault is not running. Start the stack with the Vault overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose.vault.yml up -d
```

### App starts but datasource config is wrong (using fallback defaults)

The profile may not be active. Verify:
```bash
docker exec ebank_app env | grep SPRING_PROFILES_ACTIVE
```

If empty, set `SPRING_PROFILES_ACTIVE=local` (or `prod`) so the correct profile-YAML is loaded and the Vault import is triggered.

### Vault returns 403 Forbidden

The AppRole does not have the `ebank-monolith` policy, or the policy path does not match. Verify:

```bash
vault token capabilities secret/data/e-bank/monolith/E1/config
# Expected: read
```

### Read a specific environment's config for debugging

```bash
# From your local machine (needs Vault CLI and VAULT_ADDR/VAULT_TOKEN set)
vault kv get -format=json secret/e-bank/monolith/E2/config | jq '.data.data'
```

### Seed a config update without restarting the stack

```bash
vault kv patch secret/e-bank/monolith/local/config \
  "logging.level.com.ebank=TRACE"

docker compose restart app   # picks up the new value
```

### Roll back a bad config value

```bash
# See versions
vault kv metadata get secret/e-bank/monolith/E1/config

# Roll back to version 3
vault kv rollback -version=3 secret/e-bank/monolith/E1/config

# Restart the app to apply
docker compose restart app
```
