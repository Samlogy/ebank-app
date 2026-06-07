# HashiCorp Vault: Secrets Management Guide for ebank

## Table of Contents

1. [What is Vault?](#what-is-vault)
2. [How Vault Works](#how-vault-works)
3. [Pros vs Cons](#pros-vs-cons)
4. [Vault Alternatives Comparison](#vault-alternatives-comparison)
5. [Integration in New Projects](#integration-in-new-projects)
6. [Integration in Existing Projects](#integration-in-existing-projects)
7. [Multi-Environment Setup](#multi-environment-setup)
8. [Kubernetes Integration](#kubernetes-integration)
9. [Best Practices](#best-practices)
10. [Disaster Recovery & Backup](#disaster-recovery--backup)

---

## What is Vault?

### Definition

**HashiCorp Vault** = Secure secrets management and encryption platform.

Centralized solution to:
- Store secrets (passwords, API keys, tokens, certificates)
- Rotate secrets automatically
- Audit all access
- Control who can access what
- Encrypt data at rest and in transit

### Secret Types Vault Manages

```mermaid
graph TD
    A["Vault Secrets"]
    
    B["Static Secrets"]
    B --> B1["Database passwords"]
    B --> B2["API keys"]
    B --> B3["OAuth tokens"]
    B --> B4["TLS certificates"]
    
    C["Dynamic Secrets"]
    C --> C1["Short-lived DB credentials<br/>auto-generated"]
    C --> C2["Auto-rotated API keys"]
    C --> C3["Temporary cloud credentials<br/>AWS, Azure"]
    
    D["Encryption"]
    D --> D1["Encrypt/decrypt data"]
    D --> D2["HSM support"]
    D --> D3["Key management"]
    
    A --> B
    A --> C
    A --> D
    
    style A fill:#FFD700
    style B1 fill:#87CEEB
    style C1 fill:#FFA500
    style D1 fill:#FF6B6B
```

### Vault vs Hardcoding Secrets

```mermaid
graph TD
    A["Application Needs Secrets"]
    
    B["❌ WRONG: Hardcoded"]
    B --> B1["DB password in code"]
    B --> B2["API key in Git"]
    B --> B3["OAuth token in config"]
    B --> B4["Problems:<br/>- Git history leaked<br/>- Anyone with code has secrets<br/>- Can't rotate<br/>- Compliance violation"]
    
    C["⚠️ PROBLEMATIC: Environment Variables"]
    C --> C1["Set in .env file"]
    C --> C2["Docker: ENV vars"]
    C --> C3["K8s: ConfigMap"]
    C --> C4["Problems:<br/>- No encryption<br/>- No audit<br/>- No rotation<br/>- Visible in logs/process env"]
    
    D["✅ CORRECT: Vault"]
    D --> D1["Request secret at runtime"]
    D --> D2["Vault returns encrypted secret"]
    D --> D3["Benefits:<br/>- Centralized<br/>- Encrypted<br/>- Audit trail<br/>- Auto-rotation<br/>- Fine-grained access"]
    
    style B4 fill:#FF6B6B
    style C4 fill:#FFA500
    style D3 fill:#90EE90
```

---

## How Vault Works

### Architecture

```mermaid
graph TD
    A["Vault Server"]
    A --> A1["Vault Core"]
    A --> A2["Secret Engines"]
    A --> A3["Auth Methods"]
    A --> A4["Audit Logs"]
    A --> A5["Storage Backend"]
    
    A1 --> A1a["Encryption/Decryption<br/>Barrier"]
    A1 --> A1b["Request routing"]
    A1 --> A1c["Policy enforcement"]
    
    A2 --> A2a["KV (Key-Value)"]
    A2 --> A2b["Database"]
    A2 --> A2c["AWS/Azure/GCP"]
    A2 --> A2d["PKI<br/>Certificates"]
    
    A3 --> A3a["Kubernetes auth"]
    A3 --> A3b["AppRole"]
    A3 --> A3c["LDAP/OAuth"]
    A3 --> A3d["Token auth"]
    
    A4 --> A4a["Who accessed?"]
    A4 --> A4b["When?"]
    A4 --> A4c["What did they access?"]
    
    A5 --> A5a["PostgreSQL"]
    A5 --> A5b["Consul"]
    A5 --> A5c["S3"]
    A5 --> A5d["File system"]
    
    style A1 fill:#FFD700
    style A2 fill:#87CEEB
    style A3 fill:#87CEEB
    style A4 fill:#FFA500
    style A5 fill:#90EE90
```

### Request Flow

```mermaid
graph TD
    A["App Starts<br/>K8s Pod"]
    
    B["1. Authenticate"]
    B --> B1["Pod has K8s Service Account"]
    B --> B2["K8s auth: pod name + namespace"]
    B --> B3["Vault verifies with K8s API"]
    
    C["2. Receive Token"]
    C --> C1["Vault issues JWT token"]
    C --> C2["Token has TTL<br/>default: 1 hour"]
    
    D["3. Request Secret"]
    D --> D1["GET /v1/secret/data/ebank/db-password"]
    D --> D2["Include token in header"]
    
    E["4. Policy Check"]
    E --> E1["Vault checks policy"]
    E --> E2["Can this K8s service account<br/>access this secret?"]
    
    F["5. Return Secret"]
    F --> F1["Vault returns encrypted secret"]
    F --> F2["Audit log: who accessed when"]
    
    G["6. App Uses Secret"]
    G --> G1["Decrypt with Vault response"]
    G --> G2["Connect to database"]
    
    A --> B --> C --> D --> E --> F --> G
    
    style B1 fill:#87CEEB
    style C1 fill:#FFD700
    style D1 fill:#87CEEB
    style E2 fill:#FFA500
    style F2 fill:#90EE90
```

### Secret Storage

```mermaid
graph TD
    A["Secret Storage in Vault"]
    
    B["At Rest"]
    B --> B1["Encrypted with master key"]
    B --> B2["Master key in Vault core"]
    B --> B3["Can use HSM for key protection"]
    
    C["In Transit"]
    C --> C1["TLS encryption"]
    C --> C2["HTTPS only<br/>no plaintext"]
    
    D["Access Control"]
    D --> D1["Fine-grained policies"]
    D --> D2["Who can read/write"]
    D --> D3["Time-based access"]
    
    E["Audit Trail"]
    E --> E1["Every access logged"]
    E --> E2["Can't be disabled"]
    E --> E3["Compliance requirements"]
    
    style B3 fill:#FF6B6B
    style C1 fill:#90EE90
    style D1 fill:#FFD700
    style E3 fill:#FFA500
```

---

## Pros vs Cons

### Detailed Comparison

```mermaid
graph TD
    A["Vault Analysis"]
    
    B["PROS ✅"]
    B --> B1["Centralized<br/>Single source of truth"]
    B --> B2["Encryption<br/>At rest + in transit"]
    B --> B3["Audit Trail<br/>Every access logged"]
    B --> B4["Auto-Rotation<br/>Secrets rotated automatically"]
    B --> B5["Dynamic Secrets<br/>Short-lived credentials"]
    B --> B6["Multi-Cloud<br/>AWS, Azure, GCP support"]
    B --> B7["Fine-Grained Access<br/>Policy-based control"]
    
    C["CONS ❌"]
    C --> C1["Operational Complexity<br/>Setup, maintenance, HA"]
    C --> C2["Single Point of Failure<br/>If Vault down, no secrets"]
    C --> C3["Learning Curve<br/>New tool, new concepts"]
    C --> C4["Cost<br/>Enterprise version $$"]
    C --> C5["Integration Work<br/>Code changes needed"]
    C --> C6["Performance<br/>Extra HTTP calls for secrets"]
    
    A --> B
    A --> C
    
    style B1 fill:#90EE90
    style B4 fill:#90EE90
    style B6 fill:#90EE90
    style C1 fill:#FF6B6B
    style C2 fill:#FF6B6B
    style C6 fill:#FFA500
```

### When to Use Vault

| Scenario | Vault? | Why |
|----------|--------|-----|
| **Startup project** | ⚠️ Later | Overhead > benefit initially |
| **Multiple environments** | ✅ Yes | Easy env-specific secrets |
| **Compliance requirements** | ✅ Yes | Audit trail mandatory |
| **Cloud-native K8s** | ✅ Yes | Native K8s auth |
| **High security needs** | ✅ Yes | Banking, healthcare, etc. |
| **Small team, few secrets** | ❌ No | Environment vars sufficient |
| **Monolithic app** | ⚠️ Maybe | Depends on secret volume |

---

## Vault Alternatives Comparison

### Feature Comparison Matrix

```mermaid
graph TD
    A["Secrets Management Solutions"]
    
    B["HashiCorp Vault"]
    B --> B1["Open source + Enterprise"]
    B --> B2["Full-featured, complex"]
    B --> B3["Best for: Large teams, compliance"]
    
    C["AWS Secrets Manager"]
    C --> C1["AWS native"]
    C --> C2["Easier to use"]
    C --> C3["Best for: AWS-only shops"]
    
    D["Azure Key Vault"]
    D --> D1["Azure native"]
    D --> D2["Azure ecosystem"]
    D --> D3["Best for: Azure-only shops"]
    
    E["Sealed Secrets (K8s)"]
    E --> E1["Kubernetes native"]
    E --> E2["Simpler, less powerful"]
    E --> E3["Best for: K8s-only, GitOps"]
    
    F["Environment Variables"]
    F --> F1["Simplest"]
    F --> F2["No encryption"]
    F --> F3["Best for: Dev only"]
    
    A --> B
    A --> C
    A --> D
    A --> E
    A --> F
    
    style B fill:#FFD700
    style C fill:#87CEEB
    style D fill:#87CEEB
    style E fill:#87CEEB
    style F fill:#FF6B6B
```

### Detailed Comparison

| Feature | Vault | AWS SM | Azure KV | Sealed Secrets | .env |
|---------|-------|--------|----------|---|---|
| **Multi-cloud** | ✅ Yes | ❌ No | ❌ No | ✅ Yes | ✅ Yes |
| **Encryption** | ✅ Full | ✅ Full | ✅ Full | ✅ Full | ❌ No |
| **Audit trail** | ✅ Full | ✅ Full | ✅ Full | ⚠️ Limited | ❌ No |
| **Auto-rotation** | ✅ Yes | ✅ Yes | ✅ Yes | ❌ No | ❌ No |
| **Dynamic secrets** | ✅ Yes | ⚠️ Limited | ⚠️ Limited | ❌ No | ❌ No |
| **Learning curve** | ❌ High | ✅ Low | ✅ Low | ⚠️ Medium | ✅ Low |
| **Operational overhead** | ❌ High | ✅ Low | ✅ Low | ⚠️ Medium | ✅ Low |
| **Cost** | ⚠️ Medium | ⚠️ Medium | ⚠️ Medium | ✅ Free | ✅ Free |
| **Best for** | **Large scale** | **AWS only** | **Azure only** | **K8s only** | **Dev only** |

### Recommendation for ebank

```
✅ Recommended: HashiCorp Vault

Reasons:
- Multi-cloud (AWS + possible future expansion)
- Banking requires strict compliance (audit trail)
- Microservices architecture (multiple secrets)
- K8s native authentication
- Auto-rotation for banking credentials
- Future-proof investment
```

---

## Integration in New Projects

### Step 1: Vault Infrastructure Setup

```mermaid
graph TD
    A["New ebank Project"]
    
    B["Dev Environment"]
    B --> B1["Vault Dev Mode<br/>Single pod<br/>In-memory storage<br/>Auto-unsealed"]
    B --> B2["Purpose: Learning<br/>Not production"]
    
    C["Staging Environment"]
    C --> C1["Vault HA Setup<br/>3+ pods<br/>PostgreSQL backend<br/>Auto-unsealing"]
    C --> C2["Purpose: Pre-prod testing"]
    
    D["Production Environment"]
    D --> D1["Vault HA Cluster<br/>5+ pods<br/>Consul backend<br/>HSM key management"]
    D --> D2["Purpose: Production"]
    
    E["All Environments"]
    E --> E1["Helm chart deployment"]
    E --> E2["K8s auth method"]
    E --> E3["Service account per app"]
    
    A --> B
    A --> C
    A --> D
    A --> E
    
    style B1 fill:#87CEEB
    style C1 fill:#FFA500
    style D1 fill:#FF6B6B
```

### Step 2: Spring Boot Integration (New App)

**Add dependency:**
```
spring-cloud-starter-vault-config
↓
@RestController needs DB password
↓
Spring loads secret from Vault on startup
↓
App runs with injected secrets
```

**Application structure:**
```
src/main/java/com/ebank/
├── config/
│   └── VaultConfiguration.java      # Vault client config
├── secrets/
│   └── SecretProvider.java          # Read secrets from Vault
└── ... (rest of app)

application.yml
├── spring.cloud.vault:
│   ├─ host: vault-server
│   ├─ auth-method: kubernetes
│   ├─ kubernetes-path: /auth/kubernetes
│   └─ kubernetes-role: ebank-app
```

### Step 3: Secret Storage Structure

```mermaid
graph TD
    A["Vault Secret Hierarchy"]
    
    B["secret/ebank/<br/>Main app path"]
    
    C["secret/ebank/local/"]
    C --> C1["db-password: local-dev-pass"]
    C --> C2["jwt-secret: local-secret-key"]
    C --> C3["redis-url: redis://localhost"]
    
    D["secret/ebank/staging/"]
    D --> D1["db-password: *****"]
    D --> D2["jwt-secret: *****"]
    D --> D3["redis-url: redis-staging"]
    
    E["secret/ebank/production/"]
    E --> E1["db-password: *****"]
    E --> E2["jwt-secret: *****"]
    E --> E3["redis-url: redis-prod"]
    
    B --> C
    B --> D
    B --> E
    
    style C fill:#87CEEB
    style D fill:#FFA500
    style E fill:#FF6B6B
```

### Step 4: Initialization Script

```
Vault setup for new project:

1. Create app role:
   vault auth enable approle
   vault write auth/approle/role/ebank-app \
     token_ttl=1h token_policies="ebank-app"

2. Create secret engine:
   vault secrets enable -path=secret kv-v2

3. Store initial secrets:
   vault kv put secret/ebank/production \
     db-password="***" \
     jwt-secret="***"

4. Create policy:
   vault policy write ebank-app - <<EOF
     path "secret/data/ebank/production/*" {
       capabilities = ["read", "list"]
     }
   EOF

5. Deploy app:
   App requests secret → Vault validates K8s SA → Returns secret
```

---

## Integration in Existing Projects

### Migration Path

```mermaid
graph TD
    A["Existing Project<br/>Using .env or hardcoded"]
    
    B["Phase 1: Preparation<br/>2 weeks"]
    B --> B1["Audit current secrets"]
    B --> B2["Categorize by env"]
    B --> B3["Plan Vault structure"]
    
    C["Phase 2: Vault Setup<br/>1 week"]
    C --> C1["Deploy Vault cluster"]
    C --> C2["Configure K8s auth"]
    C --> C3["Create policies"]
    
    D["Phase 3: Code Changes<br/>2 weeks"]
    D --> D1["Add spring-cloud-vault"]
    D --> D2["Create SecretProvider"]
    D --> D3["Update @Configuration"]
    D --> D4["Remove hardcoded secrets"]
    
    E["Phase 4: Migration<br/>1 week"]
    E --> E1["Copy secrets to Vault"]
    E --> E2["Test in dev/staging"]
    E --> E3["Gradually switch to Vault"]
    
    F["Phase 5: Cleanup<br/>1 week"]
    F --> F1["Remove old secret files"]
    F --> F2["Update .gitignore"]
    F --> F3["Audit trail verification"]
    
    A --> B --> C --> D --> E --> F
    
    style F fill:#90EE90
```

### Minimal Code Changes

**Before (Hardcoded/Env Vars):**
```
application.yml:
spring:
  datasource:
    password: ${DB_PASSWORD}  # From environment
    
Environment:
export DB_PASSWORD=my-secret
```

**After (Vault):**
```
application.yml:
spring:
  datasource:
    password: ${db.password}  # From Vault
  cloud:
    vault:
      host: vault.example.com
      auth-method: kubernetes
      
Code:
@Configuration
public class DatabaseConfig {
  @Value("${db.password}")  // Spring injects from Vault
  private String password;
}
```

### Rollback Strategy

```mermaid
graph TD
    A["Vault Migration"]
    
    B["Safe rollback window<br/>2 weeks"]
    B --> B1["Keep .env files with same values"]
    B --> B2["Keep environment var export scripts"]
    B --> B3["Monitor Vault reliability"]
    
    C{Issues found?}
    C -->|No| D["✅ Commit to Vault<br/>Delete .env files"]
    C -->|Yes| E["❌ Rollback<br/>Use .env again"]
    
    D --> F["Remove Vault integration<br/>from code"]
    E --> G["Fix issues in Vault"]
    
    style D fill:#90EE90
    style E fill:#FF6B6B
```

---

## Multi-Environment Setup

### Secret Structure by Environment

```mermaid
graph TD
    A["Vault Secrets Organization"]
    
    B["Per-Environment Secrets"]
    B --> B1["secret/ebank/dev/<br/>- db-password<br/>- jwt-secret<br/>- redis-url"]
    B --> B2["secret/ebank/staging/<br/>- db-password (different)<br/>- jwt-secret (different)<br/>- redis-url (different)"]
    B --> B3["secret/ebank/production/<br/>- db-password (prod)<br/>- jwt-secret (prod)<br/>- redis-url (prod)"]
    
    C["Shared Secrets<br/>Across Envs"]
    C --> C1["secret/ebank/shared/<br/>- service-certs<br/>- public-keys"]
    
    D["Sensitive Secrets<br/>Prod Only"]
    D --> D1["secret/ebank/production-sensitive/<br/>- hsm-pin<br/>- disaster-recovery-key"]
    
    A --> B
    A --> C
    A --> D
    
    style B1 fill:#87CEEB
    style B2 fill:#FFA500
    style B3 fill:#FF6B6B
    style C fill:#FFD700
    style D fill:#FF6B6B
```

### Environment-Specific Configuration

```yaml
# application.yml (Shared)
spring:
  cloud:
    vault:
      host: ${VAULT_HOST:vault.example.com}
      auth-method: kubernetes
      kubernetes-path: auth/kubernetes

---
# application-local.yml (Dev)
spring:
  cloud:
    vault:
      host: vault-dev.internal
      kubernetes-role: ebank-dev
      kv:
        engine-version: 2
        backend-path: secret

---
# application-ref.yml (Staging)
spring:
  cloud:
    vault:
      host: vault-staging.internal
      kubernetes-role: ebank-staging
      kv:
        engine-version: 2
        backend-path: secret

---
# application-prod.yml (Production)
spring:
  cloud:
    vault:
      host: vault-prod.internal
      kubernetes-role: ebank-production
      tls:
        enabled: true
        cert-auth-path: auth/cert
        key-store: /etc/vault/certs/key.jks
      kv:
        engine-version: 2
        backend-path: secret
      
      # Enhanced security for production
      namespace: ebank
      timeout: 5000
      read-timeout: 5000
```

### Policy per Environment

```
VAULT POLICIES:

policy "ebank-dev":
  path "secret/data/ebank/dev/*" {
    capabilities = ["read", "list"]
  }
  path "secret/data/ebank/shared/*" {
    capabilities = ["read"]
  }

policy "ebank-staging":
  path "secret/data/ebank/staging/*" {
    capabilities = ["read", "list"]
  }
  path "secret/data/ebank/shared/*" {
    capabilities = ["read"]
  }

policy "ebank-production":
  path "secret/data/ebank/production/*" {
    capabilities = ["read", "list"]
  }
  path "secret/data/ebank/shared/*" {
    capabilities = ["read"]
  }
  # Deny: sensitive production secrets in separate policy
```

---

## Kubernetes Integration

### Architecture

```mermaid
graph TD
    A["Kubernetes Cluster"]
    
    B["Vault Namespace<br/>3-5 pods"]
    B --> B1["Vault StatefulSet"]
    B --> B2["HA Cluster<br/>Consul backend"]
    B --> B3["Service: vault.vault.svc"]
    
    C["ebank Namespace"]
    C --> C1["Pod 1: App A"]
    C --> C1a["SA: ebank-app"]
    C --> C1b["Requests secret"]
    C --> C1c["K8s auth verified"]
    
    C --> C2["Pod 2: App B"]
    C --> C2a["SA: transaction-service"]
    C --> C2b["Requests secret"]
    
    D["Auth Flow"]
    D --> D1["K8s validates SA"]
    D --> D2["Vault checks policy"]
    D --> D3["Returns secret"]
    
    A --> B
    A --> C
    A --> D
    
    style B1 fill:#FFD700
    style C1 fill:#87CEEB
    style C2 fill:#87CEEB
    style D1 fill:#FFA500
```

### Step 1: Deploy Vault with Helm

```bash
# Add Vault Helm repo
helm repo add hashicorp https://helm.releases.hashicorp.com

# Install Vault (HA mode)
helm install vault hashicorp/vault \
  --namespace vault \
  --create-namespace \
  --values vault-values.yaml

# Values file (vault-values.yaml):
# - server.ha.enabled: true
# - server.dataStorage.size: 10Gi
# - server.authMethod: kubernetes
```

### Step 2: Configure Kubernetes Auth

```yaml
# Vault Kubernetes Auth Configuration
vault auth enable kubernetes

vault write auth/kubernetes/config \
  token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token \
  kubernetes_host="https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT" \
  kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt

# Create role for ebank-app
vault write auth/kubernetes/role/ebank-app \
  bound_service_account_names=ebank-app \
  bound_service_account_namespaces=ebank \
  policies=ebank-app \
  ttl=1h
```

### Step 3: Create Service Account & Policy

```yaml
# K8s ServiceAccount
apiVersion: v1
kind: ServiceAccount
metadata:
  name: ebank-app
  namespace: ebank

---
# Vault Policy
vault policy write ebank-app -<<EOF
path "secret/data/ebank/production/*" {
  capabilities = ["read", "list"]
}
EOF
```

### Step 4: Deploy App with Vault Integration

```yaml
# Kubernetes Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ebank-app
  namespace: ebank
spec:
  template:
    spec:
      serviceAccountName: ebank-app  # ← Links to SA
      
      containers:
      - name: app
        image: ebank:latest
        
        env:
        # Vault connection
        - name: VAULT_ADDR
          value: "https://vault.vault.svc:8200"
        
        # K8s auth
        - name: VAULT_AUTH_METHOD
          value: "kubernetes"
        
        # This secret path will be auto-read by Spring Cloud Vault
        - name: SPRING_CLOUD_VAULT_KV_VERSION
          value: "2"
        
        volumeMounts:
        # K8s gives us the auth token
        - name: vault-token
          mountPath: /var/run/secrets/tokens
      
      volumes:
      - name: vault-token
        projected:
          sources:
          - serviceAccountToken:
              path: vault
              expirationSeconds: 3600
```

### Step 5: App Configuration (Spring Boot)

```yaml
# application-prod.yml (in K8s)
spring:
  cloud:
    vault:
      host: vault.vault.svc  # ← K8s DNS
      port: 8200
      scheme: https
      
      # Kubernetes auth
      authentication: KUBERNETES
      kubernetes-path: auth/kubernetes
      kubernetes-role: ebank-app
      
      # Service account token location
      kubernetes-service-account-token-file: \
        /var/run/secrets/tokens/vault
      
      # Secrets location
      kv:
        enabled: true
        version: 2
        backend-path: secret
```

### Full K8s Deployment Flow

```mermaid
graph TD
    A["kubectl apply deployment.yaml"]
    
    B["K8s creates Pod<br/>ebank-app namespace"]
    B --> B1["Injects service account token"]
    
    C["Spring Boot starts"]
    C --> C1["@EnableVaultConfiguration"]
    C --> C2["Initialize VaultTemplate"]
    
    D["Vault Auth"]
    D --> D1["Use K8s token to auth"]
    D --> D2["POST /auth/kubernetes/login"]
    D --> D3["Service account: ebank-app"]
    
    E["Vault Response"]
    E --> E1["Returns JWT token"]
    E --> E2["Token TTL: 1 hour"]
    E --> E3["Token auto-renewed"]
    
    F["Get Secrets"]
    F --> F1["GET /secret/data/ebank/production/db-password"]
    F --> F2["Vault checks policy"]
    F --> F3["Returns encrypted secret"]
    
    G["App Running"]
    G --> G1["DB password injected"]
    G --> G2["Ready for requests"]
    
    A --> B --> C --> D --> E --> F --> G
    
    style D1 fill:#FFD700
    style E1 fill:#90EE90
    style G2 fill:#87CEEB
```

---

## Best Practices

### 1. Secret Rotation

```mermaid
graph TD
    A["Secret Rotation Strategy"]
    
    B["Automatic Rotation<br/>Vault feature"]
    B --> B1["Database passwords"]
    B --> B2["API keys"]
    B --> B3["Certificates"]
    B --> B4["Interval: 30-90 days"]
    
    C["Manual Rotation<br/>Critical secrets"]
    C --> C1["Master encryption key"]
    C --> C2["Root tokens"]
    C --> C3["Disaster recovery key"]
    
    D["Process"]
    D --> D1["1. Vault generates new secret"]
    D --> D2["2. Rotate in target system"]
    D --> D3["3. Apps get new secret"]
    D --> D4["4. Old secret revoked"]
    
    A --> B
    A --> C
    A --> D
    
    style B1 fill:#90EE90
    style C1 fill:#FF6B6B
```

### 2. Principle of Least Privilege

```
POLICY DESIGN:

❌ WRONG:
vault write auth/kubernetes/role/ebank-app \
  policies=admin  # Too much access!

✅ CORRECT:
vault write auth/kubernetes/role/ebank-app \
  policies=ebank-app  # Minimal access to needed path only

Policy ebank-app:
path "secret/data/ebank/production/db-*" {
  capabilities = ["read"]  # Read-only
}

path "secret/data/ebank/production/jwt" {
  capabilities = ["read"]  # Read-only
}
```

### 3. Audit & Logging

```mermaid
graph TD
    A["Vault Audit Trail"]
    
    B["All Access Logged"]
    B --> B1["Who: Service account"]
    B --> B2["When: Timestamp"]
    B --> B3["What: Secret path"]
    B --> B4["Result: Success/Failure"]
    
    C["Log Destinations"]
    C --> C1["File backend"]
    C --> C2["Syslog"]
    C --> C3["Splunk/ELK"]
    
    D["Example Log Entry"]
    D --> D1["2026-05-24T10:15:30Z"]
    D --> D2["Service: ebank-app"]
    D --> D3["Action: READ"]
    D --> D4["Path: secret/ebank/production/db-password"]
    D --> D5["Result: SUCCESS"]
    
    A --> B
    A --> C
    A --> D
    
    style B1 fill:#87CEEB
    style C1 fill:#FFD700
```

### 4. High Availability

```mermaid
graph TD
    A["Vault HA Setup"]
    
    B["3-5 Vault Pods"]
    B --> B1["Pod 1: Leader<br/>Processes all requests"]
    B --> B2["Pod 2-5: Standby<br/>Ready to take over"]
    
    C["Shared Storage"]
    C --> C1["Consul<br/>Distributed consensus<br/>Decides leader"]
    
    D["Auto-Failover"]
    D --> D1["If leader fails"]
    D --> D2["Consul elects new leader"]
    D --> D3["< 1 second switchover"]
    
    E["Client-Side"]
    E --> E1["App retries failed requests"]
    E --> E2["Connects to new leader"]
    E --> E3["Transparent failover"]
    
    A --> B
    A --> C
    A --> D
    A --> E
    
    style B1 fill:#FF6B6B
    style B2 fill:#87CEEB
    style D3 fill:#90EE90
```

### 5. Backup & Disaster Recovery

```mermaid
graph TD
    A["Vault Backup Strategy"]
    
    B["What to Backup"]
    B --> B1["Storage backend data"]
    B --> B2["Unseal keys<br/>keep separately"]
    B --> B3["Root token<br/>keep offline"]
    B --> B4["Audit logs"]
    
    C["Backup Location"]
    C --> C1["Off-cluster<br/>Different region"]
    C --> C2["Encrypted"]
    C --> C3["Secure access"]
    
    D["Restore Process"]
    D --> D1["1. Restore storage"]
    D --> D2["2. Start Vault"]
    D --> D3["3. Unseal with stored keys"]
    D --> D4["4. Verify integrity"]
    
    E["Testing"]
    E --> E1["Test restore quarterly"]
    E --> E2["Document procedure"]
    E --> E3["Train team"]
    
    A --> B
    A --> C
    A --> D
    A --> E
    
    style B2 fill:#FF6B6B
    style C1 fill:#FFD700
    style E1 fill:#FFA500
```

### 6. Token Management

```
TOKEN LIFECYCLE:

Creation:
- Service account authenticates
- Vault returns JWT token

TTL (Time To Live):
- Default: 1 hour
- Apps refresh token before expiry
- Spring Cloud Vault: automatic refresh

Renewal:
- App calls: POST /auth/token/renew
- Extends token TTL

Revocation:
- Explicit: DELETE /auth/token
- On app termination
- On K8s pod deletion

Best Practice:
- Short TTL: 1-2 hours (production)
- Auto-renewal: enabled (Spring Cloud Vault)
- Revoke on shutdown: implement graceful shutdown hook
```

### 7. Secret Naming Convention

```
VAULT SECRET PATHS:

Pattern:
secret/{app}/{environment}/{secret-name}

Examples:
secret/ebank/production/db-password
secret/ebank/production/jwt-secret
secret/ebank/staging/api-key
secret/ebank/local/test-token

Benefits:
✅ Clear organization
✅ Easy to find
✅ Policy structure matches path
✅ Environment separation
```

---

## Disaster Recovery & Backup

### Vault Disaster Recovery Plan

```mermaid
graph TD
    A["Disaster Scenarios"]
    
    B["Scenario 1: Leader Pod Crashed"]
    B --> B1["Impact: < 1 second"]
    B --> B2["Recovery: Auto-failover"]
    B --> B3["Action: None, automatic"]
    
    C["Scenario 2: Entire Cluster Down"]
    C --> C1["Impact: Apps can't get secrets"]
    C --> C2["Recovery: 5-10 minutes"]
    C --> C3["Action: Restore from backup"]
    
    D["Scenario 3: Storage Corruption"]
    D --> D1["Impact: Data loss risk"]
    D --> D2["Recovery: 30 minutes"]
    D --> D3["Action: Point-in-time restore"]
    
    E["Scenario 4: Unseal Keys Lost"]
    E --> E1["Impact: Can't start Vault"]
    E --> E2["Recovery: Major incident"]
    E --> E3["Action: Restore from backup"]
    
    F["Prevention"]
    F --> F1["HA setup (scenarios 1, 4)"]
    F --> F2["Automated backups (scenarios 2, 3)"]
    F --> F3["Regular restore tests"]
    
    A --> B
    A --> C
    A --> D
    A --> E
    A --> F
    
    style F1 fill:#90EE90
    style F2 fill:#90EE90
    style F3 fill:#90EE90
```

### Backup Retention & Testing

```mermaid
timeline
    title Vault Backup & Testing Schedule

    Daily : Automated backups to S3
    
    Weekly : Verify backup integrity
    
    Monthly : Test restore procedure : Practice recovery
    
    Quarterly : Full DR drill : Simulate complete failure
    
    Annually : Audit backup security : Review access logs
```

---

## Implementation Checklist for ebank

### Phase 1: Planning
```bash
☐ Audit current secrets (in code, .env, K8s ConfigMaps)
☐ Categorize by environment (dev, staging, prod)
☐ Design Vault secret hierarchy
☐ Define access policies per app
☐ Choose deployment mode (HA, storage backend)
```

### Phase 2: Infrastructure
```bash
☐ Deploy Vault cluster (Helm chart)
☐ Configure Kubernetes auth method
☐ Setup auto-unseal (if using KMS)
☐ Configure audit logging (to Splunk/ELK)
☐ Setup backup strategy (S3, cross-region)
```

### Phase 3: Secrets Migration
```bash
☐ Store secrets in Vault per environment
☐ Create roles and policies
☐ Bind service accounts to policies
☐ Document secret naming scheme
☐ Setup rotation policies
```

### Phase 4: Application Integration
```bash
☐ Add spring-cloud-starter-vault-config
☐ Configure application.yml per environment
☐ Create SecretProvider class
☐ Update @Configuration classes
☐ Remove hardcoded secrets from code
```

### Phase 5: Testing & Migration
```bash
☐ Test locally with Vault dev mode
☐ Test in staging with HA Vault
☐ Verify auto-renewal works
☐ Perform failover test
☐ Gradual migration to Vault in prod
```

### Phase 6: Operations & Monitoring
```bash
☐ Setup alerts for Vault health
☐ Monitor token TTL and renewals
☐ Review audit logs regularly
☐ Test backup/restore monthly
☐ Document runbooks for team
```

---

## Summary

### Vault for ebank: Should You Use It?

```mermaid
graph TD
    A["ebank Decision"]
    
    B["Current State"]
    B --> B1["Microservices architecture"]
    B --> B2["Multiple environments"]
    B --> B3["Kubernetes deployment"]
    B --> B4["Banking (compliance required)"]
    
    C["Vault Fit?"]
    C --> C1["✅ Multi-cloud ready"]
    C --> C2["✅ K8s native auth"]
    C --> C3["✅ Audit trail for compliance"]
    C --> C4["✅ Auto-rotation for banking"]
    C --> C5["✅ Centralized secrets"]
    
    D["RECOMMENDATION"]
    D --> D1["✅ YES, implement Vault"]
    D --> D1a["Phase 1: K8s auth + staging"]
    D --> D1b["Phase 2: Prod migration"]
    D --> D1c["Phase 3: Full automation"]
    
    A --> B
    A --> C
    A --> D
    
    style D1a fill:#90EE90
    style D1b fill:#FFD700
    style D1c fill:#FFA500
```

### Key Takeaways

| Aspect | Recommendation |
|--------|---|
| **Use Vault?** | ✅ Yes, for production |
| **When?** | Before production deployment |
| **Setup complexity** | Medium (1-2 weeks) |
| **Operational overhead** | Medium (HA required) |
| **ROI** | High (compliance + security) |
| **K8s integration** | Native, excellent support |
| **Multi-environment** | Best tool for the job |

---

**HashiCorp Vault is the best-in-class solution for ebank's secrets management needs.** 🔐
