# ebank-monolith Vault policy
#
# Grants the application read-only access to its own config paths.
# The '+' wildcard matches a single path segment (local, E1, E2, …),
# so one policy covers all environments without listing each one.
#
# KV v2 separates data (secret/data/…) from metadata (secret/metadata/…).

path "secret/data/e-bank/monolith/+/config" {
  capabilities = ["read"]
}

path "secret/metadata/e-bank/monolith/+/config" {
  capabilities = ["read", "list"]
}
