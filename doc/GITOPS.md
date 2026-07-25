# GitOps — eBank Monolith

This document explains the GitOps setup for ebank-monolith: the tools used, how they fit together, every design decision, and exactly how to operate the system.

---

## Table of Contents

1. [What is GitOps and why?](#1-what-is-gitops-and-why)
2. [Architecture Overview](#2-architecture-overview)
3. [Repository Layout](#3-repository-layout)
4. [The Promotion Flow — Step by Step](#4-the-promotion-flow--step-by-step)
5. [Argo CD Setup](#5-argo-cd-setup)
6. [GitHub Actions Workflows](#6-github-actions-workflows)
7. [Jenkins Integration](#7-jenkins-integration)
8. [Branching Strategy](#8-branching-strategy)
9. [Secrets Management in GitOps](#9-secrets-management-in-gitops)
10. [Operating the System](#10-operating-the-system)
11. [Design Decisions and Trade-offs](#11-design-decisions-and-trade-offs)

---

## 1. What is GitOps and why?

**GitOps** is an operational model where Git is the single source of truth for both application code and infrastructure state. Every change to the cluster is made by committing to Git; the cluster continuously converges towards what Git describes.

### Before GitOps (push-based CD)

```
Developer → git push → CI builds image → Jenkins kubectl apply → Cluster
                                              ↑
                               "Who ran this? When? From which machine?"
```

Problems:
- **Auditability**: cluster changes happen outside Git — no traceable history
- **Drift**: someone `kubectl edit`s a deployment; the cluster no longer matches CI expectations
- **Recovery**: restoring a cluster requires re-running CI pipelines, not just `git checkout`
- **Access creep**: every CI agent needs cluster credentials with write access

### After GitOps (pull-based CD)

```
Developer → git push → CI builds image → commits image tag to Git
                                                        ↓
                                          Argo CD detects Git change
                                                        ↓
                                          Argo CD pulls + applies → Cluster
```

Benefits:
- **Auditability**: every cluster change has a Git commit with author, timestamp, and message
- **Drift correction**: Argo CD continuously reconciles the cluster to the Git state (selfHeal)
- **One-command recovery**: `helm upgrade` or `git revert` + Argo CD syncs
- **Least-privilege CI**: CI only writes to Git (not the cluster); only Argo CD has cluster access

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           GitHub Repository                                 │
│                                                                             │
│  ┌──────────────┐    PR      ┌──────────────┐                               │
│  │ feature/*    │ ────────→  │   develop    │  (dev environment)            │
│  └──────────────┘            └──────┬───────┘                               │
│                                     │  PR + review                          │
│                                     ↓                                       │
│                              ┌──────────────┐  (prod environment)           │
│                              │     main     │                               │
│                              └──────────────┘                               │
│                                                                             │
│  GitHub Actions workflows:                                                  │
│    ci.yml              → runs on every PR (test, lint, scan)                │
│    gitops-promote.yml  → runs on push to main/develop (build + tag update)  │
└─────────────────────────────────────────────────────────────────────────────┘
         │ commit image tag                    │ commit image tag
         ↓ to environments/dev/values.yaml     ↓ to environments/prod/values.yaml
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Argo CD (running in Kubernetes)                          │
│                                                                             │
│   ┌─────────────────────────┐     ┌─────────────────────────┐              │
│   │  ebank-monolith-dev     │     │  ebank-monolith-prod    │              │
│   │  namespace: ebank-dev   │     │  namespace: ebank-prod  │              │
│   │  branch:    develop     │     │  branch:    main        │              │
│   │  sync: automated        │     │  sync: automated        │              │
│   │  prune: true            │     │  prune: false           │              │
│   └──────────┬──────────────┘     └──────────┬──────────────┘              │
│              │                               │                              │
│              ↓                               ↓                              │
│     Helm chart rendered               Helm chart rendered                  │
│     with values-dev.yaml             with values-prod.yaml                 │
│     + environments/dev/              + environments/prod/                  │
└─────────────────────────────────────────────────────────────────────────────┘
         │                                     │
         ↓                                     ↓
┌──────────────────┐                  ┌──────────────────┐
│  ebank-dev (K8s) │                  │ ebank-prod (K8s) │
│  Spring profile: │                  │ Spring profile:  │
│    dev           │                  │   prod           │
│  Vault path: E2  │                  │ Vault path: E1   │
└──────────────────┘                  └──────────────────┘
```

---

## 3. Repository Layout

```
ebank-app/
├── .github/
│   └── workflows/
│       ├── ci.yml                    # PR gate: test, helm lint, docker build, trivy
│       └── gitops-promote.yml        # On push to main/develop: build + update tag
│
└── monolith/
    ├── helm/                         # Helm chart (static, committed to Git)
    │   ├── Chart.yaml
    │   ├── values.yaml               # chart defaults
    │   ├── values-dev.yaml           # static dev config (Vault host, replicas…)
    │   ├── values-prod.yaml          # static prod config (Vault host, TLS, replicas…)
    │   ├── environments/             # DYNAMIC — written by CI on every deploy
    │   │   ├── dev/
    │   │   │   └── values.yaml       # { image: { tag: abc1234 } }
    │   │   └── prod/
    │   │       └── values.yaml       # { image: { tag: def5678 } }
    │   └── templates/                # Helm templates (deployment, svc, ingress…)
    │
    └── gitops/
        └── argocd/
            ├── project.yaml          # AppProject: access control, sync windows, RBAC
            ├── bootstrap.yaml        # App of Apps: apply this once to seed everything
            ├── applicationset.yaml   # Alternative: one manifest manages all envs
            └── apps/
                ├── ebank-monolith-dev.yaml   # Argo CD Application for dev
                └── ebank-monolith-prod.yaml  # Argo CD Application for prod
```

### The key design: separate static from dynamic values

| File | Who writes it | Content |
|---|---|---|
| `helm/values.yaml` | Developers (PR) | Chart defaults |
| `helm/values-dev.yaml` | Developers (PR) | Static dev config |
| `helm/values-prod.yaml` | Developers (PR) | Static prod config |
| `helm/environments/dev/values.yaml` | CI automatically | `image.tag` only |
| `helm/environments/prod/values.yaml` | CI automatically | `image.tag` only |

CI **only touches the `environments/` files**. Everything else is a human PR. This means Argo CD always deploys exactly the tag that CI built and tested — no manual intervention, no drift.

---

## 4. The Promotion Flow — Step by Step

### Dev promotion (develop branch)

```
1. Developer opens PR → feature/my-feature → develop
2. ci.yml runs:
   - Maven tests (H2 in-memory, no infrastructure needed)
   - Helm lint (dev + prod values)
   - Docker build check (no push)
   - Trivy security scan
   If any check fails → PR is blocked.

3. PR is reviewed and merged to develop.

4. gitops-promote.yml triggers:
   a. Runs tests again (safety net)
   b. Builds Docker image
   c. Pushes to Docker Hub with tags:
      - yourdockerhub/ebank-monolith:<sha>
      - yourdockerhub/ebank-monolith:develop-<run_number>
   d. Updates helm/environments/dev/values.yaml:
      image:
        tag: <sha>
   e. Commits and pushes: "gitops(dev): ebank-monolith → <sha> [skip ci]"

5. Argo CD detects the commit on develop branch.
   Polls every 3 minutes (default) or via webhook (recommended).

6. Argo CD syncs ebank-monolith-dev:
   - Renders Helm chart with values.yaml + values-dev.yaml + environments/dev/values.yaml
   - Applies Deployment, Service, Ingress, HPA, PDB, NetworkPolicy
   - Waits for pods to become Ready

7. Dev environment is running the new version.
```

### Prod promotion (main branch)

```
1. PR: develop → main (requires code review)
2-4. Same as dev but:
   - Docker Hub also gets :latest tag
   - environments/prod/values.yaml is updated
   - Commit: "gitops(prod): ebank-monolith → <sha> [skip ci]"
   - GitHub Environment 'prod' can require manual approval before step 4d

5. Argo CD detects commit on main branch.

6. Argo CD syncs ebank-monolith-prod:
   - Renders Helm chart with values-prod.yaml + environments/prod/values.yaml
   - Applies with rolling update (maxSurge=1, maxUnavailable=0)
   - Waits for pods to become Ready

7. If rollout fails → Helm --atomic auto-rolls back.
   If sync fails → Argo CD marks Application as Degraded.
   Alert fires via Argo CD Notifications (email/Slack/Telegram).
```

---

## 5. Argo CD Setup

### Installation

```bash
# Install Argo CD into its own namespace
kubectl create namespace argocd
kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for all pods to be Ready
kubectl wait --for=condition=Ready pods --all -n argocd --timeout=300s

# Get the initial admin password
kubectl get secret argocd-initial-admin-secret \
  -n argocd -o jsonpath='{.data.password}' | base64 -d

# Access the UI (port-forward)
kubectl port-forward svc/argocd-server -n argocd 8080:443
# Open: https://localhost:8080  (user: admin, password: from above)
```

### Connect the GitHub repository

If the repository is private, add a deploy key or repository credentials:

```bash
# Using Argo CD CLI (install: https://argo-cd.readthedocs.io/en/stable/cli_installation/)
argocd login localhost:8080 --username admin --insecure

argocd repo add https://github.com/samlogy/ebank-app \
  --username <github-user> \
  --password <github-pat>   # needs repo:read scope
```

Or via the UI: Settings → Repositories → Connect Repo.

### Bootstrap everything with a single command

```bash
# 1. Create the K8s Secrets for Vault AppRole (one per namespace)
for NS in ebank-dev ebank-prod; do
  kubectl create namespace $NS 2>/dev/null || true
  kubectl create secret generic ebank-vault-approle \
    --from-literal=VAULT_ROLE_ID=<role-id> \
    --from-literal=VAULT_SECRET_ID=<secret-id> \
    --namespace $NS \
    --dry-run=client -o yaml | kubectl apply -f -
done

# 2. Apply the App of Apps bootstrap
kubectl apply -f monolith/gitops/argocd/bootstrap.yaml

# Argo CD will:
#   - Create the 'ebank' AppProject
#   - Create ebank-monolith-dev and ebank-monolith-prod Applications
#   - Sync both Applications (deploy the workloads)
```

### Verify the setup

```bash
# Check all Applications
argocd app list

# Sync status
argocd app get ebank-monolith-dev
argocd app get ebank-monolith-prod

# Force sync if needed
argocd app sync ebank-monolith-dev
argocd app sync ebank-monolith-prod --prune   # only when you intentionally want to delete resources
```

### Enable GitHub webhook (recommended)

Instead of polling every 3 minutes, configure a webhook so Argo CD syncs immediately on push:

1. In GitHub: Settings → Webhooks → Add webhook
   - Payload URL: `https://<argocd-host>/api/webhook`
   - Content type: `application/json`
   - Secret: (set and store in Argo CD's webhook config)
   - Events: `Push`

2. In Argo CD:
   ```bash
   kubectl edit configmap argocd-cm -n argocd
   # Add:
   # data:
   #   webhook.github.secret: <your-webhook-secret>
   ```

---

## 6. GitHub Actions Workflows

### `ci.yml` — Pull Request gate

Triggers on every PR targeting `main` or `develop` when application code or Helm chart changes.

| Job | What it checks |
|---|---|
| `test` | Maven unit + integration tests (H2, no infra needed) |
| `helm-lint` | `helm lint` with dev + prod values; `helm template` dry-run |
| `docker-build` | Full Docker multi-stage build (not pushed) with layer cache |
| `trivy-scan` | HIGH/CRITICAL CVE scan; results uploaded to GitHub Security tab |

All jobs must pass before a PR can be merged (enforce via branch protection rules).

### `gitops-promote.yml` — Image build and tag promotion

Triggers on push to `main` or `develop` (after PR merge), and can be triggered manually.

```
build-push job:
  1. Run tests (regression safety net)
  2. Build Docker image
  3. Push to Docker Hub with deterministic SHA tag
  4. Output: image-tag, environment

promote job (needs build-push):
  1. Uses GitHub Environment ('prod' can require approval)
  2. Updates helm/environments/{env}/values.yaml with new tag
  3. Commits with [skip ci] to avoid infinite loop
  4. Pushes to the same branch
  5. Posts a summary to the GitHub Actions run summary
```

### Required GitHub secrets and variables

| Secret / Variable | Where | Description |
|---|---|---|
| `DOCKER_HUB_USERNAME` | Repository secret | Docker Hub username |
| `DOCKER_HUB_TOKEN` | Repository secret | Docker Hub access token (not password) |
| `GITOPS_TOKEN` | Repository secret | GitHub PAT: `contents: write` scope |

### GitHub Environment setup (for prod approval gate)

1. Go to: Repository → Settings → Environments → New environment → `prod`
2. Add required reviewers (e.g. team leads)
3. The `gitops-promote.yml` workflow pauses at the `promote` job and waits for approval before updating `environments/prod/values.yaml`

---

## 7. Jenkins Integration

The Jenkins pipeline has an additional stage `GitOps — Update image tag` (CI-11) that runs after `Push Image` when the branch is `main` or `develop`.

It uses the same logic as the GitHub Actions workflow: updates `helm/environments/{env}/values.yaml` and commits back to the branch.

### New Jenkins credential required

| Credential ID | Kind | Description |
|---|---|---|
| `github-gitops-token` | Username+Password | GitHub PAT with `contents: write` scope |

### Tool requirement

`yq` (the Go version by Mike Farah) must be installed on the Jenkins agent:

```bash
# On Debian/Ubuntu
wget https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 \
  -O /usr/local/bin/yq && chmod +x /usr/local/bin/yq
```

---

## 8. Branching Strategy

```
main     ─────────────────────────────────── prod (Argo CD watches)
              ↑ PR + review
develop  ──────────────────────────────────  dev  (Argo CD watches)
              ↑ PR
feature/ ──────────────────────────────────  (CI runs, no deploy)
```

| Branch | CI | Deploy | Argo CD target |
|---|---|---|---|
| `feature/*` | Tests + lint | No | — |
| `develop` | Full CI | Dev auto | `ebank-monolith-dev` |
| `main` | Full CI | Prod auto (+ optional approval) | `ebank-monolith-prod` |

---

## 9. Secrets Management in GitOps

The only secret the cluster needs is the Vault AppRole credential (`ebank-vault-approle`). Everything else (DB password, JWT config, admin credentials) is fetched from Vault by the application at startup.

### The `ebank-vault-approle` K8s Secret is NOT managed by Argo CD

Argo CD manages all Helm-rendered resources. The AppRole secret is pre-provisioned by the Jenkins pipeline / operator before Argo CD syncs, and Argo CD ignores it (it's not in the Helm chart templates).

If you want Argo CD to manage it too, options are:
- **Sealed Secrets**: encrypt the secret value and commit the SealedSecret CR to Git
- **External Secrets Operator**: pull from Vault dynamically and create the K8s Secret

### Secret rotation

```bash
# Generate a new secret-id
NEW_SECRET_ID=$(vault write -field=secret_id -f \
  auth/approle/role/ebank-monolith/secret-id)

# Update the K8s Secret in each namespace
for NS in ebank-dev ebank-prod; do
  kubectl create secret generic ebank-vault-approle \
    --from-literal=VAULT_ROLE_ID=$(vault read -field=role_id \
      auth/approle/role/ebank-monolith/role-id) \
    --from-literal=VAULT_SECRET_ID=${NEW_SECRET_ID} \
    --namespace $NS \
    --dry-run=client -o yaml | kubectl apply -f -
done

# Trigger a rolling restart to pick up the new credentials
kubectl rollout restart deployment/ebank-monolith -n ebank-dev
kubectl rollout restart deployment/ebank-monolith -n ebank-prod
```

---

## 10. Operating the System

### Checking sync status

```bash
# All Applications at a glance
argocd app list

# Detailed status including conditions
argocd app get ebank-monolith-prod

# Diff between Git and cluster
argocd app diff ebank-monolith-prod
```

### Manual sync (prod)

```bash
# Sync without pruning (safe — won't delete anything)
argocd app sync ebank-monolith-prod

# Sync with prune (deletes resources removed from Git)
argocd app sync ebank-monolith-prod --prune
```

### Rollback

```bash
# List release history
argocd app history ebank-monolith-prod

# Roll back to a specific revision
argocd app rollback ebank-monolith-prod <revision-number>
```

### Promoting a specific image tag manually

```bash
# Update the values file directly and commit
yq -i '.image.tag = "abc1234"' \
  monolith/helm/environments/prod/values.yaml

git add monolith/helm/environments/prod/values.yaml
git commit -m "gitops(prod): manual promotion to abc1234"
git push origin main

# Argo CD detects the commit and syncs
```

### Pausing automated sync (maintenance window)

```bash
# Disable auto-sync temporarily
argocd app set ebank-monolith-prod --sync-policy none

# Re-enable after maintenance
argocd app set ebank-monolith-prod --sync-policy automated
```

### Debugging a failed sync

```bash
# View sync events and conditions
argocd app get ebank-monolith-prod --show-operation

# View Argo CD controller logs
kubectl logs -n argocd -l app.kubernetes.io/name=argocd-application-controller --tail=100
```

---

## 11. Design Decisions and Trade-offs

### Decision 1: App of Apps vs ApplicationSet

| | App of Apps | ApplicationSet (chosen as alternative) |
|---|---|---|
| Readability | Each env is a separate, readable YAML | Single manifest; less obvious |
| Templating | No — each app is explicitly written | Yes — list generator |
| Bootstrapping | Apply bootstrap.yaml → rest is managed | Apply applicationset.yaml → apps generated |
| Adding an environment | New file in `apps/` | New entry in the `elements` list |
| Argo CD version requirement | v2.0+ | v2.3+ (ApplicationSet merged into core) |

**Decision**: Both are included. `apps/*.yaml` are the primary approach (readable, self-documenting). `applicationset.yaml` is provided as an alternative for teams that prefer it.

### Decision 2: Same repo (monorepo) vs separate GitOps repo

| | Monorepo (chosen) | Separate GitOps repo |
|---|---|---|
| Complexity | Lower — one repo to manage | Higher — two repos, cross-repo PRs |
| Separation of concerns | Weaker — app code + infra config together | Stronger — infra team controls the GitOps repo |
| Access control | All developers see all GitOps config | GitOps repo can be access-restricted |
| Argo CD setup | One credential | One credential per repo |

**Decision**: Monorepo for this portfolio project. In a larger team, a separate GitOps repo would be better (separate PR workflows, different access controls for infra vs app teams).

### Decision 3: Automated sync in prod

Prod uses `automated.selfHeal: true` (drift correction) but `automated.prune: false` (no auto-delete). The AppProject adds a sync window that blocks automated syncs during business hours Mon–Fri 08:00–18:00.

Trade-off: automated sync makes the system converge to Git continuously (no manual operator action needed) but a bad commit can reach prod quickly. Mitigations:
- The PR review process is the gate
- `revisionHistoryLimit: 20` gives 20 rollback points
- The AppProject sync window limits blast radius to off-hours

### Decision 4: GitHub Actions + Jenkins

Both CI systems are used:
- **GitHub Actions**: native to GitHub, runs on PRs without any Jenkins infrastructure
- **Jenkins**: existing investment, handles the heavy lifting (OWASP, SonarQube, DAST, ZAP, etc.)

The GitOps update stage exists in both (`gitops-promote.yml` and `Jenkinsfile CI-11`). Choose one depending on your CI system. Using both creates a race condition — pick one as the source of truth for image tag commits.
