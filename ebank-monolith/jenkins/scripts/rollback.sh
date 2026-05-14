#!/usr/bin/env bash
# rollback.sh — emergency rollback to the previous stable deployment
#
# Usage: bash rollback.sh <deployment-name> <namespace>
# Example: bash rollback.sh ebank-monolith ebank
#
# Kubernetes keeps a revision history (controlled by revisionHistoryLimit).
# This script rolls back one revision using kubectl rollout undo,
# then waits for the rollback to complete.

set -euo pipefail

DEPLOYMENT="${1:-ebank-monolith}"
NAMESPACE="${2:-ebank}"

echo "==> ROLLBACK triggered for ${DEPLOYMENT} in namespace ${NAMESPACE}"

# Show current and previous revision info
echo "==> Current rollout history:"
kubectl rollout history deployment/"${DEPLOYMENT}" -n "${NAMESPACE}" || true

# Perform the rollback
echo "==> Rolling back to previous revision..."
kubectl rollout undo deployment/"${DEPLOYMENT}" -n "${NAMESPACE}"

# Wait for rollback to stabilise
echo "==> Waiting for rollback to complete (timeout: 90s)..."
kubectl rollout status deployment/"${DEPLOYMENT}" -n "${NAMESPACE}" --timeout=90s

CURRENT_IMAGE=$(kubectl get deployment "${DEPLOYMENT}" -n "${NAMESPACE}" \
    -o jsonpath='{.spec.template.spec.containers[0].image}')

echo "==> Rollback complete. Running image: ${CURRENT_IMAGE}"
