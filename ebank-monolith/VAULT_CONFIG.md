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
11. [Kubernetes Deployment (Helm)](#11-kubernetes-deployment-helm)
12. [Adding a New Environment](#12-adding-a-new-environment)
13. [Technical Trade-offs](#13-technical-trade-offs)
14. [Troubleshooting](#14-troubleshooting)

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

After (`dev` and `prod` profiles):

```
docker-compose.yml / pipeline
  └── env vars → Vault connection only (VAULT_HOST, VAULT_ROLE_ID, VAULT_SECRET_ID, VAULT_ENV_ID)
                    ↓
                 Vault KV
                    └── secret/e-bank/monolith/{ENV_ID}/config
                            ↓ (Spring Cloud Vault reads at startup)
                         app gets ALL config from Vault
```

`local` profile stays simple — pure YAML, no Vault, no extra moving parts:

```
application-local.yaml
  └── datasource, JWT, logging, etc. — all inline, suitable for developer machines only
```

For `dev` and `prod`, the only environment variables the container needs are:
- Where is Vault (`VAULT_HOST`, `VAULT_PORT`, `VAULT_SCHEME`)
- How to authenticate (`VAULT_ROLE_ID` + `VAULT_SECRET_ID` — AppRole for both)
- Which environment path to use (`VAULT_ENV_ID`: `E2` for dev, `E1` for prod)

Everything else — datasource URL, credentials, JWT expiration, logging, actuator, rate limits — comes from Vault.

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

## 5. Environments: local, dev (E2), prod (E1)

Three Spring profiles, clear separation of concerns:

### local — individual developer machine

- **Spring profile**: `local`
- **Config source**: `application-local.yaml` — self-contained, no external dependencies.
- **Vault**: **not used**. The profile does not include `spring.config.import: vault://` at all.
- **Database**: PostgreSQL on `localhost:5432` (start with `docker compose up -d postgres`).
- **Key behaviours**: `show-sql: true`, `ddl-auto: update`, DEBUG logging, both actuator endpoints.
- **Trade-off**: credentials are committed to the repo (intentionally weak dev values). This is acceptable because this profile is only ever used on a developer's own machine.

### dev (E2) — shared testing / staging environment

- **Spring profile**: `dev`
- **Config source**: Vault KV at `secret/e-bank/monolith/${VAULT_ENV_ID:E2}/config`.
- **Vault auth**: AppRole. The CI/CD pipeline injects `VAULT_ROLE_ID` and `VAULT_SECRET_ID`.
- **Seed**: `vault/seeds/E2.json` is the template — fill `CHANGE_ME_*` values with real test DB credentials.
- **Key differences from E1**: `ddl-auto: update` (schema can evolve freely in testing), rate limit is looser (20/min) so automated test suites don't hit it, both health and info actuator endpoints exposed for visibility.

### prod (E1) — production

- **Spring profile**: `prod`
- **Config source**: Vault KV at `secret/e-bank/monolith/${VAULT_ENV_ID:E1}/config`.
- **Vault auth**: AppRole. Credentials are rotated at each deployment.
- **Seed**: `vault/seeds/E1.json` is the template — load once and update with `vault kv put`.
- **Key differences**: `ddl-auto: validate` (schema must be managed externally; wrong schema = hard failure at startup), `show-sql: false`, WARN-level root logging, only health actuator exposed.

---

## 6. Authentication Strategies

Spring Cloud Vault supports many auth methods. This project uses one in production (AppRole) and overrides to Token only when running the local docker-compose vault overlay for testing.

### AppRole auth (dev profile / E2, prod profile / E1)

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

**Why AppRole for both dev and prod?**

Consistency: the `dev` and `prod` profiles use identical authentication mechanisms. A pipeline that deploys to E2 works the same way as one that deploys to E1 — only `VAULT_ENV_ID` and the Vault address differ. This eliminates a whole class of "works in staging, breaks in prod" auth bugs.

| | Long-lived token | AppRole (chosen) |
|---|---|---|
| Rotation | Manual, disruptive | `secret-id` rotated independently |
| Blast radius if leaked | Full access until manually revoked | Token expires in ≤1h |
| CI/CD integration | Secrets must be stored somewhere | `role-id` is safe to store; `secret-id` is injected at deploy time |

### Token auth (local vault overlay only)

The `docker-compose.vault.yml` overlay overrides the `dev` profile's AppRole settings with Token auth via `SPRING_CLOUD_VAULT_*` environment variables:

```yaml
SPRING_CLOUD_VAULT_AUTHENTICATION: TOKEN
SPRING_CLOUD_VAULT_TOKEN: root
```

This solves a docker-compose chicken-and-egg problem: AppRole `secret-id` values are generated by Vault at runtime and cannot be coordinated between containers without extra tooling. Token auth removes that dependency for local testing only. Real `dev` and `prod` deployments always use AppRole.

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

### Option A — `local` profile (simplest, no Vault)

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

All config comes from `application-local.yaml`. Nothing else needed.

### Option B — `dev` profile with Vault (tests the real Vault integration)

```bash
docker compose -f docker-compose.yml -f docker-compose.vault.yml up -d --build
```

What happens in order:

```
1. postgres starts and passes its healthcheck.
2. vault starts in dev mode (root token = "root"). Passes healthcheck.
3. vault-init runs init.sh:
     - Mounts KV v2 at secret/
     - Writes E2 config (local postgres values) → secret/e-bank/monolith/E2/config
     - Writes the ebank-monolith policy
     - Enables AppRole, creates the role, prints VAULT_ROLE_ID + VAULT_SECRET_ID
   vault-init exits with code 0.
4. app starts only after vault-init completes (condition: service_completed_successfully).
   Spring Boot activates the 'dev' profile → application-dev.yaml is loaded.
   SPRING_CLOUD_VAULT_AUTHENTICATION=TOKEN overrides AppRole auth for this local stack.
   Spring Cloud Vault connects to vault:8200 with token "root".
   Reads secret/e-bank/monolith/E2/config.
   All config properties load into the Spring Environment.
   DataSource is created (postgres:5432/ebank_dev).
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

The Jenkins pipeline (`jenkins/Jenkinsfile`) deploys via Helm. Vault credentials are injected into a Kubernetes Secret before every release — the cluster never stores any other application secret.

### Flow for every deployment

```
Pipeline: Push Image → Deploy Kubernetes
  1. Jenkins reads VAULT_ROLE_ID + VAULT_SECRET_ID from its credentials store.
  2. kubectl creates / updates the K8s Secret ebank-vault-approle in the
     target namespace. This is the ONLY secret the cluster holds.
  3. helm upgrade --install selects values-dev.yaml (non-main branches)
     or values-prod.yaml (main branch) and deploys / upgrades the release.
     --atomic: if pods do not become Ready within 5 min, Helm auto-rolls back.
  4. Pod starts; Spring Boot activates the chosen profile:
       SPRING_PROFILES_ACTIVE = dev | prod
       VAULT_HOST              = from values file
       VAULT_ENV_ID            = E2 | E1
       VAULT_ROLE_ID           = from K8s Secret ebank-vault-approle
       VAULT_SECRET_ID         = from K8s Secret ebank-vault-approle
  5. Spring Cloud Vault authenticates with AppRole, gets a 1h token, reads
       secret/e-bank/monolith/{E2 or E1}/config
  6. All runtime config is now in the Spring Environment. App starts.
  7. Readiness probe passes → pod joins the Service endpoints → traffic flows.
```

### Jenkins credentials required

| Credential ID | Kind | Value |
|---|---|---|
| `dockerhub-credentials` | Username+Password | Docker Hub login |
| `kubeconfig` | Secret file | `~/.kube/config` for the target cluster |
| `vault-role-id` | Secret text | Vault AppRole `role_id` |
| `vault-secret-id` | Secret text | Vault AppRole `secret_id` (rotate each deploy) |
| `sonarqube-token` | Secret text | SonarQube analysis token |
| `telegram-bot-token` | Secret text | Telegram bot token |
| `telegram-chat-id` | Secret text | Telegram target chat ID |

### Secret-id rotation

The `secret-id` is rotated on every pipeline run by generating a fresh one in Vault before the deploy step:

```bash
# Run this inside the Jenkins Deploy stage before passing it to kubectl
VAULT_SECRET_ID=$(vault write -field=secret_id -f \
  auth/approle/role/ebank-monolith/secret-id)
```

Jenkins then creates / replaces the K8s Secret:

```bash
kubectl create secret generic ebank-vault-approle \
  --from-literal=VAULT_ROLE_ID=${VAULT_ROLE_ID} \
  --from-literal=VAULT_SECRET_ID=${VAULT_SECRET_ID} \
  --namespace ebank \
  --dry-run=client -o yaml | kubectl apply -f -
```

If a `secret-id` is ever leaked, its blast radius is bounded: it expires after 4 hours (`token_max_ttl` on the AppRole role), and the next deploy automatically replaces it.

---

## 11. Kubernetes Deployment (Helm)

### Why Helm?

Raw `kubectl apply` works for simple cases but becomes fragile as the application grows:

| | Raw kubectl manifests | Helm chart (chosen) |
|---|---|---|
| Environment differences | `envsubst` + multiple YAML copies | Single chart, multiple values files |
| Rollback | Manual `kubectl rollout undo` | `helm rollback <release>` (atomic on deploy) |
| Release history | None | `helm history <release>` |
| Templating | None (`sed`, `envsubst`) | Full Go template engine |
| Dry-run | `kubectl apply --dry-run` | `helm upgrade --dry-run` |
| Dependency management | Manual | `helm dependency update` |

### Helm chart structure

```
ebank-monolith/helm/
├── Chart.yaml                 # chart metadata and version
├── .helmignore
├── values.yaml                # defaults — override with environment files
├── values-dev.yaml            # E2 overrides (1 replica, dev Vault host, no TLS)
├── values-prod.yaml           # E1 overrides (3 replicas, TLS, strict anti-affinity)
└── templates/
    ├── _helpers.tpl            # shared label and name helpers
    ├── NOTES.txt               # post-install summary printed by Helm
    ├── serviceaccount.yaml     # dedicated SA, no auto-mount of token
    ├── deployment.yaml         # Vault AppRole env vars, all security hardening
    ├── service.yaml            # ClusterIP (Ingress handles external traffic)
    ├── ingress.yaml            # optional, configurable TLS
    ├── hpa.yaml                # CPU + memory autoscaling with stabilisation windows
    ├── pdb.yaml                # PodDisruptionBudget — survives node drains
    └── networkpolicy.yaml      # deny-all + allow Vault, PostgreSQL, DNS, ingress
```

### What the chart supplies vs what Vault supplies

| Config item | Source |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Helm values (`spring.profile`) |
| `VAULT_HOST`, `VAULT_PORT`, `VAULT_SCHEME` | Helm values (`vault.host/port/scheme`) |
| `VAULT_ENV_ID` | Helm values (`vault.envId`) |
| `VAULT_ROLE_ID` | K8s Secret `ebank-vault-approle` |
| `VAULT_SECRET_ID` | K8s Secret `ebank-vault-approle` |
| `spring.datasource.*` | Vault KV |
| `jwt.expiration` | Vault KV |
| `admin.*` | Vault KV |
| `rate-limiting.*` | Vault KV |
| `logging.level.*` | Vault KV |
| `management.endpoints.*` | Vault KV |

### First-time setup

```bash
# 1. Seed Vault (once per environment — update later with vault kv patch)
VAULT_ADDR=https://vault.example.com VAULT_TOKEN=<admin-token> \
  vault kv put secret/e-bank/monolith/E2/config @vault/seeds/E2.json   # dev
  vault kv put secret/e-bank/monolith/E1/config @vault/seeds/E1.json   # prod

# 2. Get the AppRole credentials (created by vault/init.sh)
VAULT_ROLE_ID=$(vault read -field=role_id \
  auth/approle/role/ebank-monolith/role-id)
VAULT_SECRET_ID=$(vault write -field=secret_id -f \
  auth/approle/role/ebank-monolith/secret-id)

# 3. Create the K8s Secret in the target namespace
kubectl create namespace ebank
kubectl create secret generic ebank-vault-approle \
  --from-literal=VAULT_ROLE_ID=${VAULT_ROLE_ID} \
  --from-literal=VAULT_SECRET_ID=${VAULT_SECRET_ID} \
  --namespace ebank

# 4. Deploy with Helm
helm upgrade --install ebank-monolith ebank-monolith/helm \
  --namespace ebank \
  --create-namespace \
  -f ebank-monolith/helm/values-dev.yaml \
  --set image.repository=yourdockerhub/ebank-monolith \
  --set image.tag=main-a3f9c12-42 \
  --atomic \
  --timeout 5m \
  --wait
```

### Rollback

```bash
# Helm keeps a history of all releases
helm history ebank-monolith -n ebank

# Roll back to the previous revision
helm rollback ebank-monolith -n ebank --wait

# Roll back to a specific revision
helm rollback ebank-monolith 3 -n ebank --wait
```

### Security hardening in the chart

The chart enforces production-grade security by default:

| Feature | Setting |
|---|---|
| Non-root user | `runAsUser: 1000`, `runAsNonRoot: true` |
| Read-only filesystem | `readOnlyRootFilesystem: true` (writable `/tmp` via `emptyDir`) |
| Capability drop | `capabilities.drop: [ALL]` |
| Seccomp | `seccompProfile.type: RuntimeDefault` |
| No SA token mount | `automountServiceAccountToken: false` |
| Graceful shutdown | `terminationGracePeriodSeconds: 60` + `preStop` sleep |
| Pod anti-affinity | Soft in dev, hard in prod (pods on different nodes) |
| NetworkPolicy | Ingress from controller only; egress to DNS, Vault, PostgreSQL only |
| PodDisruptionBudget | `minAvailable: 1` (dev off), `minAvailable: 2` (prod) |

### Adding Vault Kubernetes auth (future evolution)

The current setup passes AppRole credentials via a K8s Secret. A more cloud-native approach is **Vault Kubernetes auth**: Vault validates the pod's service account token directly against the K8s API, so no long-lived credentials need to be stored at all.

To enable it (no application code changes needed):

```bash
# On Vault:
vault auth enable kubernetes
vault write auth/kubernetes/config \
  kubernetes_host=https://${K8S_API_HOST}:6443

vault write auth/kubernetes/role/ebank-monolith \
  bound_service_account_names=ebank-monolith \
  bound_service_account_namespaces=ebank \
  policies=ebank-monolith \
  ttl=1h

# In the Helm chart: switch authentication in values.yaml
vault:
  authentication: KUBERNETES   # instead of APPROLE
  kubernetes:
    role: ebank-monolith
```

The `ebank-monolith` Vault policy (`vault/policy/ebank-monolith.hcl`) already grants the right permissions — no policy changes needed.

---

## 12. Adding a New Environment

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
  -e SPRING_PROFILES_ACTIVE=dev \   # or prod — both use AppRole + Vault
  -e VAULT_HOST=vault.uat.example.com \
  -e VAULT_ENV_ID=E3 \
  -e VAULT_ROLE_ID=<role-id> \
  -e VAULT_SECRET_ID=<secret-id> \
  your-registry/ebank-monolith:1.0.0
```

No code changes, no YAML changes, no image rebuild.

---

## 13. Technical Trade-offs

### Trade-off 1: Spring Cloud Vault vs Vault Agent Sidecar

| | Spring Cloud Vault (chosen) | Vault Agent Sidecar |
|---|---|---|
| How it works | App connects to Vault directly at startup | Vault Agent runs alongside the app, writes secrets to files or env vars |
| App awareness | App knows about Vault | App is unaware of Vault — reads env vars or files |
| K8s dependency | Works anywhere | Requires K8s or explicit sidecar setup |
| Secret rotation | Requires app restart | Agent can push updates without restart |
| Complexity | Low (just a Spring dependency) | Higher (sidecar lifecycle, file watching) |

**Decision**: Spring Cloud Vault was chosen because the stack currently uses Docker Compose, not K8s. When the project moves to K8s, adding Vault Agent Injector alongside Spring Cloud Vault is straightforward — both can coexist.

### Trade-off 2: All config in Vault (dev/prod) vs secrets only

| | Secrets only in Vault | All config in Vault (chosen for dev/prod) |
|---|---|---|
| Vault reads per startup | 1 (small payload) | 1 (larger payload, same round trip) |
| Where to look for env differences | Vault + YAML files | Vault only |
| Risk of YAML/Vault drift | High | None |
| Non-secret changes require Git commit | Yes | No |

**Decision**: all config in Vault for `dev` and `prod`. The `local` profile is the deliberate exception — it keeps credentials in YAML because local dev should have zero infrastructure dependencies beyond a PostgreSQL container. A developer cloning the repo and running `docker compose up -d postgres && mvnw spring-boot:run -Dspring-boot.run.profiles=local` should work immediately with no Vault setup.

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

## 14. Troubleshooting

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
