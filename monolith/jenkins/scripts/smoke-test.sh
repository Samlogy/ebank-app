#!/usr/bin/env bash
# smoke-test.sh — post-deploy health check
#
# Usage: bash smoke-test.sh <deployment-name> <namespace>
# Example: bash smoke-test.sh ebank-monolith ebank
#
# Waits up to 60 seconds for the Spring Actuator health endpoint to return
# {"status":"UP"}. Exits 0 on success, 1 on timeout or unexpected status.

set -euo pipefail

DEPLOYMENT="${1:-ebank-monolith}"
NAMESPACE="${2:-ebank}"
MAX_WAIT=60
INTERVAL=5

# ── Resolve the NodePort URL ───────────────────────────────────────────────
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
NODE_PORT=$(kubectl get svc "${DEPLOYMENT}-service" -n "${NAMESPACE}" \
    -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || echo "30080")

HEALTH_URL="http://${NODE_IP}:${NODE_PORT}/actuator/health"

echo "==> Smoke test: ${HEALTH_URL}"
echo "==> Waiting up to ${MAX_WAIT}s for app to become healthy..."

elapsed=0
while [ "$elapsed" -lt "$MAX_WAIT" ]; do
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 "${HEALTH_URL}" || echo "000")

    if [ "$HTTP_STATUS" = "200" ]; then
        BODY=$(curl -s --connect-timeout 3 "${HEALTH_URL}")
        if echo "$BODY" | grep -q '"status":"UP"'; then
            echo "==> App is UP after ${elapsed}s. Health: ${BODY}"
            exit 0
        fi
    fi

    echo "    Waiting... (${elapsed}s elapsed, HTTP ${HTTP_STATUS})"
    sleep "$INTERVAL"
    elapsed=$((elapsed + INTERVAL))
done

echo "==> ERROR: App did not become healthy within ${MAX_WAIT}s."
echo "==> Check pod logs: kubectl logs -l app=${DEPLOYMENT} -n ${NAMESPACE} --tail=50"
exit 1
