#!/bin/sh
# Vault initialization script for the local docker-compose vault overlay.
# Seeds the E2 (dev/testing) path with values that work against the local
# postgres container so the 'dev' Spring profile can start end-to-end.
#
# For a real shared E2 environment, load vault/seeds/E2.json instead
# (after filling in the correct DB host and credentials):
#   vault kv put secret/e-bank/monolith/E2/config @vault/seeds/E2.json
#
# VAULT_ADDR and VAULT_TOKEN are injected by docker-compose.vault.yml.
set -e

echo "[vault-init] Starting Vault initialization..."

# Vault dev mode pre-mounts 'secret/' as KV v1.
# Replace with KV v2 so Spring Cloud Vault's backend-version: 2 works correctly.
echo "[vault-init] Mounting KV v2 at secret/..."
vault secrets disable secret 2>/dev/null || true
vault secrets enable -version=2 -path=secret kv

# ── Seed E2 with local-stack-compatible values ────────────────────────────────
# Datasource points to the 'postgres' docker-compose service.
# These override the CHANGE_ME placeholders in vault/seeds/E2.json for
# local testing only.
echo "[vault-init] Writing E2 config → secret/e-bank/monolith/E2/config"
vault kv put secret/e-bank/monolith/E2/config \
  "spring.datasource.url=jdbc:postgresql://postgres:5432/ebank_dev" \
  "spring.datasource.username=ebank_user" \
  "spring.datasource.password=ebank_password" \
  "spring.datasource.hikari.maximum-pool-size=10" \
  "spring.datasource.hikari.minimum-idle=3" \
  "spring.datasource.hikari.connection-timeout=3000" \
  "spring.datasource.hikari.idle-timeout=300000" \
  "spring.datasource.hikari.max-lifetime=900000" \
  "spring.datasource.hikari.leak-detection-threshold=5000" \
  "spring.jpa.hibernate.ddl-auto=update" \
  "spring.jpa.show-sql=false" \
  "jwt.expiration=86400000" \
  "admin.email=admin@ebank.com" \
  "admin.password=ChangeMe123!" \
  "rate-limiting.login.capacity=20" \
  "rate-limiting.login.refill-minutes=1" \
  "logging.level.root=INFO" \
  "logging.level.com.ebank=DEBUG" \
  "management.endpoints.web.exposure.include=health,info"

# ── Access control ────────────────────────────────────────────────────────────
echo "[vault-init] Writing ebank-monolith policy..."
vault policy write ebank-monolith /vault-config/policy/ebank-monolith.hcl

echo "[vault-init] Enabling AppRole auth method..."
vault auth enable approle 2>/dev/null || true

echo "[vault-init] Creating ebank-monolith AppRole..."
vault write auth/approle/role/ebank-monolith \
  token_policies="ebank-monolith" \
  token_ttl=1h \
  token_max_ttl=4h \
  secret_id_ttl=0

ROLE_ID=$(vault read -field=role_id auth/approle/role/ebank-monolith/role-id)
SECRET_ID=$(vault write -field=secret_id -f auth/approle/role/ebank-monolith/secret-id)

echo ""
echo "[vault-init] Initialization complete."
echo "[vault-init] Vault UI: http://localhost:8200  (token: root)"
echo ""
echo "[vault-init] Seeded: secret/e-bank/monolith/E2/config  (local postgres)"
echo ""
echo "[vault-init] AppRole credentials for real dev/prod deployments:"
echo "  VAULT_ROLE_ID=${ROLE_ID}"
echo "  VAULT_SECRET_ID=${SECRET_ID}"
echo ""
echo "[vault-init] To load real E2 config: vault kv put secret/e-bank/monolith/E2/config @vault/seeds/E2.json"
echo "[vault-init] To load E1 (prod):      vault kv put secret/e-bank/monolith/E1/config @vault/seeds/E1.json"
