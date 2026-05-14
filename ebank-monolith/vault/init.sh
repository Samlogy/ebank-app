#!/bin/sh
# Vault initialization script for ebank-monolith local development.
# Executed once by the vault-init container on first stack start.
# VAULT_ADDR and VAULT_TOKEN are injected by docker-compose.vault.yml.
set -e

echo "[vault-init] Starting Vault initialization for ebank-monolith..."

# Vault dev mode pre-mounts 'secret/' as KV v1.
# Replace it with KV v2 so Spring Cloud Vault's backend-version: 2 works correctly.
echo "[vault-init] Mounting KV v2 at secret/..."
vault secrets disable secret 2>/dev/null || true
vault secrets enable -version=2 -path=secret kv

# ── Seed environment configs ──────────────────────────────────────────────────

echo "[vault-init] Writing local config  → secret/e-bank/monolith/local/config"
vault kv put secret/e-bank/monolith/local/config \
  @/vault-config/seeds/local.json

echo "[vault-init] Writing E2 config     → secret/e-bank/monolith/E2/config"
vault kv put secret/e-bank/monolith/E2/config \
  @/vault-config/seeds/E2.json

# E1 (prod) is intentionally NOT seeded here — it holds real credentials and
# must be loaded manually by the ops team on the production Vault cluster:
#   vault kv put secret/e-bank/monolith/E1/config @vault/seeds/E1.json
echo "[vault-init] Skipping E1 (prod) — load manually on the production Vault."

# ── Access control ────────────────────────────────────────────────────────────

echo "[vault-init] Writing ebank-monolith policy..."
vault policy write ebank-monolith /vault-config/policy/ebank-monolith.hcl

# AppRole auth — used by the app in non-dev environments (E1 prod, E2 testing)
echo "[vault-init] Enabling AppRole auth method..."
vault auth enable approle 2>/dev/null || true

echo "[vault-init] Creating ebank-monolith AppRole..."
vault write auth/approle/role/ebank-monolith \
  token_policies="ebank-monolith" \
  token_ttl=1h \
  token_max_ttl=4h \
  secret_id_ttl=0

# Print AppRole credentials so they can be used to seed VAULT_ROLE_ID / VAULT_SECRET_ID
ROLE_ID=$(vault read -field=role_id auth/approle/role/ebank-monolith/role-id)
SECRET_ID=$(vault write -field=secret_id -f auth/approle/role/ebank-monolith/secret-id)

echo ""
echo "[vault-init] Initialization complete."
echo "[vault-init] Vault UI: http://localhost:8200  (token: root)"
echo ""
echo "[vault-init] Seeded paths:"
echo "  secret/e-bank/monolith/local/config  (local dev)"
echo "  secret/e-bank/monolith/E2/config     (testing — fill CHANGE_ME values for a real E2)"
echo ""
echo "[vault-init] AppRole credentials for non-dev deployments:"
echo "  VAULT_ROLE_ID=${ROLE_ID}"
echo "  VAULT_SECRET_ID=${SECRET_ID}"
echo ""
echo "[vault-init] To load prod config (E1) on the prod Vault cluster:"
echo "  vault kv put secret/e-bank/monolith/E1/config @vault/seeds/E1.json"
