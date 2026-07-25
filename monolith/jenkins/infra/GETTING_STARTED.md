# Guide de Prise en Main — Infrastructure CI/CD ebank-monolith

> **Auteur :** Mike (Jenkins Expert)
> **Temps estimé :** 45–90 min pour un premier lancement complet
> **Niveau :** Débutant-Intermédiaire en DevOps

---

## Table des matières

1. [Ma recommandation : Docker vs Installation native](#1-ma-recommandation--docker-vs-installation-native)
2. [Vue d'ensemble de l'architecture locale](#2-vue-densemble-de-larchitecture-locale)
3. [Prérequis système](#3-prérequis-système)
4. [Étape 1 — Configurer le système hôte](#étape-1--configurer-le-système-hôte)
5. [Étape 2 — Lancer la stack CI/CD (Jenkins + SonarQube)](#étape-2--lancer-la-stack-cicd-jenkins--sonarqube)
6. [Étape 3 — Configuration initiale Jenkins](#étape-3--configuration-initiale-jenkins)
7. [Étape 4 — Configuration initiale SonarQube](#étape-4--configuration-initiale-sonarqube)
8. [Étape 5 — Lancer Minikube](#étape-5--lancer-minikube)
9. [Étape 6 — Préparer les Credentials Jenkins](#étape-6--préparer-les-credentials-jenkins)
10. [Étape 7 — Connecter Jenkins à SonarQube](#étape-7--connecter-jenkins-à-sonarqube)
11. [Étape 8 — Créer le Job Pipeline](#étape-8--créer-le-job-pipeline)
12. [Étape 9 — Préparer le cluster Kubernetes](#étape-9--préparer-le-cluster-kubernetes)
13. [Étape 10 — Lancer et tester le pipeline](#étape-10--lancer-et-tester-le-pipeline)
14. [Vérifier que chaque étape a bien fonctionné](#vérifier-que-chaque-étape-a-bien-fonctionné)
15. [Commandes de maintenance courantes](#commandes-de-maintenance-courantes)
16. [Troubleshooting](#troubleshooting)

---

## 1. Ma recommandation : Docker vs Installation native

En tant qu'expert Jenkins en production, voici mon avis tranché sur chaque technologie :

### Jenkins — Docker ✅ (recommandé)

```
❌ Installation native (apt/rpm)
   → Pollue le système hôte
   → Difficile à mettre à jour sans casser l'environnement
   → "Fonctionne sur ma machine" garanti

✅ Docker (notre approche)
   → Image versionnée avec TOUS les outils pré-installés (Trivy, Hadolint, Newman...)
   → Reset en 30 secondes : docker compose down -v && docker compose up -d --build
   → Reproductible à l'identique sur n'importe quelle machine Linux/Mac
   → En production réelle : Jenkins sur Kubernetes (Helm chart officiel)
```

### SonarQube — Docker ✅ (recommandé)

```
❌ SonarCloud (SaaS)
   → Gratuit uniquement pour les dépôts publics
   → Données de code envoyées chez un tiers (problématique pour du code bancaire)

❌ Installation native
   → Java + Elasticsearch + PostgreSQL à gérer manuellement
   → Complexe à maintenir

✅ Docker avec PostgreSQL (notre approche)
   → sonarqube:lts-community = version officielle gratuite
   → PostgreSQL requis pour la persistance (H2 = dev only, pas de production)
   → Données restent en local (volumes Docker)
```

### Minikube — Installation native ✅ (recommandé)

```
✅ Installation native avec driver Docker
   → Minikube utilise Docker comme hyperviseur → pas de VM lourde
   → kubectl installé séparément → réutilisable pour vrais clusters
   → Addons (ingress, metrics-server) faciles à activer

❌ Kind (Kubernetes IN Docker)
   → Alternative valide mais LoadBalancer + Ingress plus complexes à configurer

❌ k3d/k3s
   → Excellents mais moins documentés pour les débutants
```

### Outils CLI (Trivy, Hadolint, Checkov, Newman, Pa11y)

```
✅ Installés dans l'image Jenkins (notre approche)
   → Zéro installation sur la machine hôte
   → Version figée dans le Dockerfile → reproductible
   → Un seul endroit à maintenir

❌ Installés sur le système hôte
   → Versions différentes entre développeurs
   → Jenkins dépend du système hôte → fragilité
```

**Résumé :**

| Technologie | Recommandation | Pourquoi |
|-------------|---------------|---------|
| Jenkins | Docker (image custom) | Reproductible, outils inclus |
| SonarQube | Docker + PostgreSQL | Gratuit, données locales |
| Minikube | Native (driver=docker) | Simple, addons natifs |
| Trivy/Hadolint/Checkov | Dans image Jenkins | Zéro pollution hôte |
| Newman/Pa11y | Dans image Jenkins | Idem |

---

## 2. Vue d'ensemble de l'architecture locale

```
┌─── Machine Hôte (votre laptop) ─────────────────────────────────────────┐
│                                                                          │
│  ┌─── Docker Engine ──────────────────────────────────────────────────┐ │
│  │                                                                    │ │
│  │  ┌─────────────────────────────────────────────────────────────┐  │ │
│  │  │  docker-compose.infra.yml (réseau: cicd-net)                │  │ │
│  │  │                                                             │  │ │
│  │  │  ┌─────────────────┐    ┌─────────────────────────────┐    │  │ │
│  │  │  │ ebank-jenkins   │    │ ebank-sonarqube             │    │  │ │
│  │  │  │ :8090           │───▶│ :9000                       │    │  │ │
│  │  │  │ (image custom)  │    │ (sonarqube:lts-community)   │    │  │ │
│  │  │  └────────┬────────┘    └──────────────┬──────────────┘    │  │ │
│  │  │           │                            │                   │  │ │
│  │  │           │ socket Docker              ▼                   │  │ │
│  │  │           │ /var/run/docker.sock  ┌────────────────────┐   │  │ │
│  │  │           │                      │ ebank-sonarqube-db  │   │  │ │
│  │  │           │                      │ (postgres:15-alpine)│   │  │ │
│  │  │           │                      └────────────────────┘   │  │ │
│  │  └───────────┼─────────────────────────────────────────────── ┘  │ │
│  │              │                                                    │ │
│  │              ▼ (docker build, docker push, docker run ZAP)        │ │
│  │  ┌─────────────────────────────────────────────────────────────┐  │ │
│  │  │  Conteneurs éphémères (créés par Jenkins pendant le build)  │  │ │
│  │  │  → OWASP ZAP (DAST)                                        │  │ │
│  │  │  → Image ebank-monolith buildée et pushée sur Docker Hub   │  │ │
│  │  └─────────────────────────────────────────────────────────────┘  │ │
│  │                                                                    │ │
│  │  ┌─────────────────────────────────────────────────────────────┐  │ │
│  │  │  Minikube (driver=docker)                                   │  │ │
│  │  │  → Cluster Kubernetes local                                 │  │ │
│  │  │  → Namespace: ebank                                         │  │ │
│  │  │  → Ingress: http://ebank.local                              │  │ │
│  │  └─────────────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  Ports exposés :                                                         │
│    http://localhost:8090  → Jenkins UI                                   │
│    http://localhost:9000  → SonarQube UI                                 │
│    http://ebank.local     → Application déployée (via Minikube Ingress)  │
└──────────────────────────────────────────────────────────────────────────┘

 ☁️  Externe : Docker Hub (push des images buildées)
```

---

## 3. Prérequis système

### Matériel minimum

| Ressource | Minimum | Recommandé |
|-----------|---------|-----------|
| RAM | 8 GB | 16 GB |
| CPU | 4 cœurs | 6+ cœurs |
| Disque | 30 GB libres | 50 GB libres |
| OS | Linux (Ubuntu 22.04+) | Linux |

> **Note RAM :** Jenkins (~1 GB) + SonarQube (~2.5 GB) + Minikube (~2 GB) + l'app (~512 MB) = ~6 GB minimum.

### Logiciels à installer sur l'hôte

#### Docker Engine (obligatoire)

```bash
# Ubuntu/Debian
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker          # ou se déconnecter/reconnecter

# Vérification
docker --version        # Docker version 24+
docker compose version  # Docker Compose version v2+
```

#### Minikube (obligatoire)

```bash
# Téléchargement et installation
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
rm minikube-linux-amd64

# Vérification
minikube version
```

#### kubectl (obligatoire)

```bash
# Via snap (le plus simple)
sudo snap install kubectl --classic

# Ou via curl
KUBECTL_VERSION=$(curl -sSL https://dl.k8s.io/release/stable.txt)
curl -sSLO "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl"
sudo install -m 0755 kubectl /usr/local/bin/kubectl
rm kubectl

# Vérification
kubectl version --client
```

#### Git (généralement déjà installé)

```bash
sudo apt install git
git --version
```

#### Compte Docker Hub (obligatoire pour le push d'images)

Si vous n'en avez pas : [hub.docker.com](https://hub.docker.com) → Sign Up (gratuit)

---

## Étape 1 — Configurer le système hôte

Ces réglages système sont **requis par SonarQube** (qui utilise Elasticsearch en interne).
Sans eux, SonarQube démarre mais crashe immédiatement.

```bash
# ── Réglages temporaires (perdus au reboot) ──────────────────────────────
sudo sysctl -w vm.max_map_count=524288
sudo sysctl -w fs.file-max=131072

# ── Réglages permanents (survivent au reboot) ────────────────────────────
echo "vm.max_map_count=524288" | sudo tee -a /etc/sysctl.d/99-sonarqube.conf
echo "fs.file-max=131072"     | sudo tee -a /etc/sysctl.d/99-sonarqube.conf
sudo sysctl --system    # appliquer sans reboot

# ── Vérification ─────────────────────────────────────────────────────────
sysctl vm.max_map_count    # doit afficher 524288
```

---

## Étape 2 — Lancer la stack CI/CD (Jenkins + SonarQube)

```bash
# ── 1. Se placer dans le bon répertoire ──────────────────────────────────
cd monolith/jenkins/infra

# ── 2. Créer le fichier de configuration ─────────────────────────────────
cp .env.example .env
# Optionnel : modifier le mot de passe SonarQube DB dans .env
# (pour un usage local uniquement, les valeurs par défaut fonctionnent)

# ── 3. Builder l'image Jenkins personnalisée et démarrer la stack ─────────
# Cette étape prend 5-15 min au premier lancement (téléchargement des outils)
docker compose -f docker-compose.infra.yml up -d --build

# ── 4. Suivre les logs de démarrage ──────────────────────────────────────
docker compose -f docker-compose.infra.yml logs -f
# Ctrl+C pour arrêter de suivre les logs (les conteneurs continuent)
```

### Attendre que tout soit prêt

```bash
# Jenkins : attendre le message "Jenkins is fully up and running"
docker logs ebank-jenkins -f 2>&1 | grep -m1 "fully up"

# SonarQube : attendre le status "UP"
watch -n 5 'curl -sf http://localhost:9000/api/system/status 2>/dev/null | python3 -m json.tool'
# Attendre {"status":"UP"} — peut prendre 2-3 minutes

# Vérification rapide des 3 conteneurs
docker compose -f docker-compose.infra.yml ps
# Tous les STATUS doivent être "running (healthy)"
```

### Résultat attendu

```
NAME                    STATUS                    PORTS
ebank-jenkins           running (healthy)         0.0.0.0:8090->8080/tcp
ebank-sonarqube         running (healthy)         0.0.0.0:9000->9000/tcp
ebank-sonarqube-db      running (healthy)         5432/tcp
```

### Vérifier les outils installés dans Jenkins

```bash
docker exec ebank-jenkins bash -c "
    echo '── Java ──'          && java -version 2>&1 | head -1
    echo '── Docker CLI ──'    && docker --version
    echo '── kubectl ──'       && kubectl version --client --short 2>/dev/null | head -1
    echo '── Trivy ──'         && trivy --version | head -1
    echo '── Hadolint ──'      && hadolint --version
    echo '── Checkov ──'       && checkov --version
    echo '── Newman ──'        && newman --version
    echo '── Pa11y ──'         && pa11y --version
    echo '── envsubst ──'      && envsubst --version 2>&1 | head -1
"
```

---

## Étape 3 — Configuration initiale Jenkins

### 3.1 Récupérer le mot de passe administrateur initial

```bash
docker exec ebank-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
# Copier la chaîne affichée (ex: a3f9c12b45d67890...)
```

### 3.2 Accéder à Jenkins

Ouvrir : **http://localhost:8090**

1. Coller le mot de passe récupéré → **Continue**
2. Sélectionner **"Install suggested plugins"** → attendre l'installation (~5 min)
   > Les plugins supplémentaires (SonarQube, HTML Publisher, etc.) sont déjà pré-installés via `plugins.txt`
3. **Créer le premier administrateur :**
   ```
   Username : admin
   Password : choisir un mot de passe fort
   Full name : Admin
   Email     : admin@ebank.local
   ```
4. Instance Configuration : laisser `http://localhost:8090/` → **Save and Finish**
5. **Start using Jenkins**

### 3.3 Vérifier les plugins installés

**Manage Jenkins → Plugins → Installed plugins**

Rechercher et vérifier la présence de :
- `Pipeline` (workflow-aggregator)
- `SonarQube Scanner`
- `HTML Publisher`
- `Warnings Next Generation`
- `Docker Pipeline`

---

## Étape 4 — Configuration initiale SonarQube

### 4.1 Accéder à SonarQube

Ouvrir : **http://localhost:9000**

Credentials par défaut :
```
Login    : admin
Password : admin
```

Au premier login, SonarQube demande de **changer le mot de passe** :
```
Nouveau mot de passe : Admin_Sonar_2024!   ← à adapter
```

### 4.2 Créer le projet

1. **Projects → Create project → Create a local project**
2. Remplir :
   ```
   Project display name : ebank-monolith
   Project key          : ebank-monolith     ← doit correspondre à SONAR_PROJECT_KEY dans le Jenkinsfile
   ```
3. Branch principal : `main` → **Next**
4. **Use the global setting** → **Create project**

### 4.3 Générer un token d'authentification

Ce token sera utilisé par Jenkins pour envoyer les métriques à SonarQube.

1. Cliquer sur votre avatar (en haut à droite) → **My Account → Security**
2. **Generate Tokens** :
   ```
   Name   : jenkins-ebank
   Type   : Global Analysis Token
   Expires: No expiration
   ```
3. Cliquer **Generate** → **Copier le token immédiatement** (il n'est affiché qu'une fois)
   ```
   Exemple : sqa_abc123def456ghi789jkl012mno345pqr678
   ```

---

## Étape 5 — Lancer Minikube

```bash
# ── Démarrer Minikube (driver Docker = pas de VM lourde) ─────────────────
minikube start \
    --driver=docker \
    --cpus=4 \
    --memory=4096 \
    --disk-size=20g

# ── Activer les addons nécessaires ───────────────────────────────────────
minikube addons enable ingress          # Nginx Ingress Controller (DAST, Pa11y)
minikube addons enable metrics-server   # kubectl top (Green IT, HPA)

# ── Configurer l'accès DNS local ─────────────────────────────────────────
echo "$(minikube ip)  ebank.local" | sudo tee -a /etc/hosts
# Vérifier que l'IP a bien été ajoutée
grep "ebank.local" /etc/hosts

# ── Vérification ─────────────────────────────────────────────────────────
minikube status
kubectl get nodes
kubectl get pods -n ingress-nginx   # L'ingress controller doit être Running
```

### Résultat attendu

```
minikube
type: Control Plane
host: Running
kubelet: Running
apiserver: Running
kubeconfig: Configured
```

### Exposer le kubeconfig à Jenkins

Le pipeline Jenkins a besoin d'accéder à Minikube depuis le conteneur Jenkins.
Minikube utilise l'IP de la machine hôte — Jenkins (dans Docker) doit pouvoir l'atteindre.

```bash
# ── Récupérer l'IP hôte depuis le réseau Docker ───────────────────────────
DOCKER_HOST_IP=$(docker network inspect bridge --format='{{range .IPAM.Config}}{{.Gateway}}{{end}}')
echo "IP hôte Docker : $DOCKER_HOST_IP"

# ── Générer un kubeconfig adapté pour Jenkins ─────────────────────────────
# Remplace "127.0.0.1" par l'IP hôte Docker dans le kubeconfig
minikube kubectl -- config view --flatten \
    | sed "s|https://127.0.0.1|https://${DOCKER_HOST_IP}|g" \
    > /tmp/kubeconfig-for-jenkins

# Vérifier que le fichier est correct
cat /tmp/kubeconfig-for-jenkins | grep "server:"

# Tester depuis Jenkins (le conteneur doit pouvoir joindre Minikube)
docker exec ebank-jenkins kubectl --kubeconfig=/dev/stdin get nodes \
    < /tmp/kubeconfig-for-jenkins
# Doit afficher : NAME       STATUS   ROLES
#                 minikube   Ready    control-plane
```

---

## Étape 6 — Préparer les Credentials Jenkins

Aller dans : **Jenkins → Manage Jenkins → Credentials → System → Global credentials → Add Credentials**

### Credential 1 : Docker Hub

```
Kind              : Username with password
Scope             : Global
Username          : <votre username Docker Hub>
Password          : <votre password Docker Hub ou Access Token>
ID                : dockerhub-credentials       ← exactement cet ID (utilisé dans Jenkinsfile)
Description       : Docker Hub — ebank push
```

> **Conseil :** Utiliser un **Access Token** Docker Hub plutôt que votre mot de passe :
> Docker Hub → Account Settings → Security → New Access Token → `ebank-jenkins` → Read, Write, Delete

### Credential 2 : Kubeconfig Minikube

```
Kind              : Secret file
Scope             : Global
File              : [uploader le fichier /tmp/kubeconfig-for-jenkins]
ID                : kubeconfig                   ← exactement cet ID
Description       : Minikube kubeconfig (ebank local)
```

### Credential 3 : Token SonarQube

```
Kind              : Secret text
Scope             : Global
Secret            : sqa_abc123def456...          ← token généré à l'étape 4.3
ID                : sonarqube-token              ← exactement cet ID
Description       : SonarQube — ebank-monolith project token
```

### Vérification des 3 credentials

**Manage Jenkins → Credentials → System → Global credentials**

```
dockerhub-credentials   | Username with password | Docker Hub — ebank push
kubeconfig              | Secret file            | Minikube kubeconfig
sonarqube-token         | Secret text            | SonarQube token
```

---

## Étape 7 — Connecter Jenkins à SonarQube

### 7.1 Configurer le serveur SonarQube dans Jenkins

**Manage Jenkins → Configure System → SonarQube servers**

Cocher **"Enable injection of SonarQube server configuration as build environment variables"**

Cliquer **Add SonarQube** :
```
Name                          : SonarQube           ← doit correspondre à withSonarQubeEnv('SonarQube')
Server URL                    : http://sonarqube:9000   ← nom du service Docker, pas localhost !
Server authentication token   : sonarqube-token (sélectionner le credential)
```

Cliquer **Save**.

> **Pourquoi `http://sonarqube:9000` et non `http://localhost:9000` ?**
> Jenkins s'exécute dans un conteneur Docker. `localhost` dans ce contexte = le conteneur Jenkins, pas votre machine.
> `sonarqube` est le nom du service Docker Compose → résolution DNS automatique sur le réseau `cicd-net`.

### 7.2 Installer le SonarQube Scanner (outil)

**Manage Jenkins → Tools → SonarQube Scanner installations → Add SonarQube Scanner**
```
Name    : SonarScanner
Version : Installer automatiquement depuis Maven Central (laisser la case cochée)
```

---

## Étape 8 — Créer le Job Pipeline

### 8.1 Éditer le Jenkinsfile

Avant de créer le job, adapter le Jenkinsfile à votre Docker Hub username :

```bash
# Ouvrir le Jenkinsfile
nano monolith/jenkins/Jenkinsfile

# Ligne 18 — remplacer "yourdockerhub" par votre username Docker Hub
DOCKER_HUB_USER = "votre-username-docker-hub"
```

Sauvegarder et commiter :

```bash
git add monolith/jenkins/Jenkinsfile
git commit -m "chore: configure Docker Hub username in Jenkinsfile"
git push
```

### 8.2 Créer le job dans Jenkins

1. Jenkins → **New Item**
2. Name : `ebank-monolith-pipeline`
3. Type : **Pipeline** → OK

### 8.3 Configurer le job

**General :**
```
✅ Do not allow concurrent builds
✅ Discard old builds → Max builds to keep: 10
```

**Build Triggers :**
```
✅ Poll SCM → Schedule: H/5 * * * *
(Optionnel : configurer un webhook GitHub pour des builds immédiats)
```

**Pipeline :**
```
Definition : Pipeline script from SCM
SCM        : Git
Repository URL : https://github.com/votre-user/votre-repo.git
   (ou l'URL de votre dépôt local : file:///home/sam/Desktop/ebank)
Branch     : */main
Script Path: monolith/jenkins/Jenkinsfile
```

Cliquer **Save**.

---

## Étape 9 — Préparer le cluster Kubernetes

Avant le premier déploiement, créer le namespace et les secrets K8s :

```bash
# ── Namespace ─────────────────────────────────────────────────────────────
kubectl apply -f monolith/jenkins/k8s/namespace.yaml

# ── Secret (credentials de l'app) ────────────────────────────────────────
# Remplacer les valeurs par vos vraies valeurs !
kubectl create secret generic ebank-monolith-secret \
    --from-literal=SPRING_DATASOURCE_USERNAME=ebank_user \
    --from-literal=SPRING_DATASOURCE_PASSWORD=Changeme_DB_2024! \
    --from-literal=JWT_SECRET=MonSuperSecretJWTPourEbankTresLongAuMoins64Caracteres!! \
    --from-literal=ADMIN_EMAIL=admin@ebank.local \
    --from-literal=ADMIN_PASSWORD=Admin_Ebank_2024! \
    -n ebank

# Vérifier
kubectl get secret ebank-monolith-secret -n ebank

# ── Déployer PostgreSQL dans K8s (nécessaire pour l'app) ─────────────────
kubectl run postgres \
    --image=postgres:15-alpine \
    --env="POSTGRES_DB=ebank_dev" \
    --env="POSTGRES_USER=ebank_user" \
    --env="POSTGRES_PASSWORD=Changeme_DB_2024!" \
    --port=5432 \
    -n ebank

kubectl expose pod postgres \
    --name=postgres-service \
    --port=5432 \
    -n ebank

# Attendre que Postgres soit prêt
kubectl wait --for=condition=Ready pod/postgres -n ebank --timeout=60s
kubectl get pods -n ebank
```

---

## Étape 10 — Lancer et tester le pipeline

### 10.1 Premier lancement manuel

Dans Jenkins → `ebank-monolith-pipeline` → **Build Now**

Cliquer sur le numéro de build (#1) → **Console Output** pour suivre les logs en temps réel.

### 10.2 Suivre le pipeline dans Blue Ocean

Jenkins → **Open Blue Ocean** → `ebank-monolith-pipeline`

Blue Ocean affiche un graphe visuel du pipeline avec le statut de chaque stage.

### 10.3 Durée estimée par stage

| Stage | Durée estimée | Première fois |
|-------|--------------|---------------|
| Checkout | 10-30s | 10-30s |
| Validation Conformité | 30-60s | 1-2 min |
| Build | 1-3 min | 5-10 min (téléchargement deps) |
| Tests Unitaires | 20-40s | 20-40s |
| Tests Intégration | 30-60s | 30-60s |
| Analyse Dépendances (OWASP) | **5-15 min** | **10-30 min** (téléchargement NVD DB) |
| Analyse SBOM | 30s | 30s |
| SonarQube | 1-3 min | 1-3 min |
| Quality Gate | 10-30s | 10-30s |
| Docker Build | 2-5 min | 10-20 min (pull images) |
| Analyse Image (x3 parallèle) | 1-3 min | 3-5 min |
| Push Image | 30s-2 min | 1-5 min |
| Deploy K8s | 1-3 min | 1-3 min |
| Smoke Test | 10-60s | 10-60s |
| DAST (ZAP) | 5-15 min | 10-20 min (pull image ZAP) |
| Tests E2E (Newman) | 30-60s | 30-60s |
| Accessibilité (Pa11y) | 30-60s | 30-60s |
| Green IT | 30-60s | 30-60s |
| **TOTAL** | **~30-45 min** | **~60-90 min** |

> **Note :** Le premier lancement est toujours le plus long (téléchargements). Les suivants sont significativement plus rapides grâce aux caches Docker et Maven.

---

## Vérifier que chaque étape a bien fonctionné

### ✅ Validation Conformité
```
Logs Jenkins : "[INFO] There are no violations." (Checkstyle)
Logs Jenkins : "BUILD SUCCESS" (Enforcer)
Jenkins UI   : Onglet "Checkstyle Warnings" visible sur le build
```

### ✅ Tests (Unitaires + Intégration)
```
Jenkins UI : Onglet "Tests" → Tests passés / échoués
Console    : "Tests run: X, Failures: 0, Errors: 0"
```

### ✅ OWASP Dependency-Check
```
Jenkins UI : "OWASP Dependency Report" dans les Build Artifacts
Console    : "No dependencies were identified that had known CVEs" (ou liste des CVE avec CVSS < 7)
```
Accéder au rapport : **Build → OWASP Dependency Report**

### ✅ SBOM CycloneDX
```
Jenkins UI : Artifacts → bom.json et bom.xml
Console    : "[INFO] BOM generated"
```

### ✅ SonarQube
```
Console Jenkins : "ANALYSIS SUCCESSFUL"
SonarQube UI    : http://localhost:9000 → Projects → ebank-monolith
                  Voir : Bugs, Vulnerabilities, Code Smells, Coverage
```

### ✅ Quality Gate
```
Console Jenkins : "PASSED"
SonarQube UI    : Badge vert "Passed" sur le projet
```

### ✅ Docker Build + Push
```
Console Jenkins : "Successfully built <sha>"
Console Jenkins : "Successfully tagged yourdockerhub/ebank-monolith:main-..."
Docker Hub      : https://hub.docker.com/r/votre-user/ebank-monolith → voir le nouveau tag
```

### ✅ Analyse Image IaCS
```
Trivy   : "Total: 0 (HIGH: 0, CRITICAL: 0)" → ou liste des CVE trouvés
Hadolint: "No issues" → ou liste de warnings
Checkov : Rapport JSON dans les Artifacts
```

### ✅ Deploy Kubernetes
```bash
kubectl get pods -n ebank
# NAME                             READY   STATUS    RESTARTS   AGE
# ebank-monolith-xxxxxxxxx-yyyyy   1/1     Running   0          2m
# ebank-monolith-xxxxxxxxx-zzzzz   1/1     Running   0          1m
# postgres                         1/1     Running   0          10m

kubectl get deployment ebank-monolith -n ebank
# NAME             READY   UP-TO-DATE   AVAILABLE
# ebank-monolith   2/2     2            2

kubectl get ingress -n ebank
# NAME                      HOSTS        ADDRESS         PORTS
# ebank-monolith-ingress    ebank.local  <minikube-ip>   80
```

### ✅ Smoke Test
```
Console Jenkins : "==> App is UP after Xs. Health: {"status":"UP",...}"
```

### ✅ DAST (OWASP ZAP)
```
Jenkins UI : "DAST — OWASP ZAP" → rapport HTML
Console    : "WARN: X, FAIL: 0" (mode passif)
```
Accéder au rapport : **Build → DAST — OWASP ZAP**

### ✅ Tests E2E (Newman)
```
Jenkins UI : Onglet "Tests" → Newman E2E Tests
Console    : "4 passing (Xs)"
```

### ✅ Accessibilité
```
Jenkins UI : "Accessibilité — RGAA/WCAG 2.1 AA" → rapport HTML
Console    : nombre de violations détectées avec leur niveau WCAG
```

### ✅ Green IT
```
Jenkins Artifacts : ecoindex-report.json (note A-G), k8s-resource-usage.txt
Console Jenkins   : Consommation CPU/RAM des pods
```

### ✅ Application accessible
```bash
curl http://ebank.local/actuator/health
# {"status":"UP","components":{...}}

curl http://ebank.local/swagger-ui/index.html
# → page HTML de la documentation Swagger
```

---

## Commandes de maintenance courantes

### Stack CI/CD

```bash
# Arrêter la stack (données conservées)
docker compose -f docker-compose.infra.yml down

# Démarrer la stack
docker compose -f docker-compose.infra.yml up -d

# Voir les logs en temps réel
docker compose -f docker-compose.infra.yml logs -f jenkins
docker compose -f docker-compose.infra.yml logs -f sonarqube

# Reset COMPLET (⚠️ efface tous les jobs Jenkins et données SonarQube)
docker compose -f docker-compose.infra.yml down -v
docker compose -f docker-compose.infra.yml up -d --build

# Rebuild uniquement l'image Jenkins (après modification du Dockerfile ou plugins.txt)
docker compose -f docker-compose.infra.yml build jenkins
docker compose -f docker-compose.infra.yml up -d jenkins
```

### Minikube

```bash
# Arrêter Minikube (garde l'état)
minikube stop

# Démarrer Minikube
minikube start

# Reset COMPLET du cluster K8s
minikube delete
minikube start --driver=docker --cpus=4 --memory=4096

# Dashboard K8s dans le navigateur
minikube dashboard

# URL de l'app (si Ingress non configuré)
minikube service ebank-monolith-service -n ebank --url
```

### Application déployée

```bash
# Voir les pods
kubectl get pods -n ebank -w   # -w = watch (mise à jour automatique)

# Logs de l'application
kubectl logs -l app=ebank-monolith -n ebank --tail=100 -f

# Décrire un pod (events, erreurs)
kubectl describe pod <nom-du-pod> -n ebank

# Rollback manuel
bash monolith/jenkins/scripts/rollback.sh ebank-monolith ebank

# Supprimer tout dans le namespace
kubectl delete all --all -n ebank
```

### Jenkins

```bash
# Obtenir le mot de passe admin (si oublié)
docker exec ebank-jenkins cat /var/jenkins_home/secrets/initialAdminPassword

# Redémarrer Jenkins sans redémarrer le conteneur
curl -X POST http://admin:VOTRE_MOT_DE_PASSE@localhost:8090/safeRestart

# Exécuter une commande dans le conteneur Jenkins
docker exec -it ebank-jenkins bash
```

---

## Troubleshooting

### ❌ SonarQube ne démarre pas

**Symptôme :** `ebank-sonarqube` reste en `starting` ou crash immédiatement.

```bash
# Vérifier les logs
docker logs ebank-sonarqube 2>&1 | tail -30
# Si : "max virtual memory areas vm.max_map_count [65530] is too low"

# Solution :
sudo sysctl -w vm.max_map_count=524288
docker compose -f docker-compose.infra.yml restart sonarqube
```

---

### ❌ Jenkins ne peut pas accéder à Docker (permission denied)

**Symptôme :** `Got permission denied while trying to connect to the Docker daemon socket`

```bash
# Trouver le GID du socket Docker sur l'hôte
stat -c '%g' /var/run/docker.sock
# Exemple : 998

# Reconstruire l'image en passant le GID
docker compose -f docker-compose.infra.yml build \
    --build-arg DOCKER_GID=$(stat -c '%g' /var/run/docker.sock) jenkins
docker compose -f docker-compose.infra.yml up -d jenkins
```

Alternativement :
```bash
docker exec -u root ebank-jenkins bash -c \
    "groupmod -g $(stat -c '%g' /var/run/docker.sock) docker"
docker restart ebank-jenkins
```

---

### ❌ Jenkins ne peut pas atteindre SonarQube

**Symptôme :** `Connection refused to http://sonarqube:9000`

```bash
# Vérifier que les deux conteneurs sont sur le même réseau
docker network inspect ebank_cicd-net \
    | grep -A 3 '"Name"'

# Tester la connectivité depuis Jenkins
docker exec ebank-jenkins curl -s http://sonarqube:9000/api/system/status

# Si SonarQube n'est pas encore UP → attendre et réessayer
docker logs ebank-sonarqube 2>&1 | grep -i "sonarqube is up"
```

---

### ❌ kubectl échoue dans Jenkins ("Unable to connect to server")

**Symptôme :** Le stage "Deploy Kubernetes" échoue avec une erreur de connexion.

```bash
# Vérifier l'IP de l'hôte depuis Docker
DOCKER_HOST_IP=$(docker network inspect bridge --format='{{range .IPAM.Config}}{{.Gateway}}{{end}}')
echo $DOCKER_HOST_IP   # typiquement 172.17.0.1

# Vérifier que le kubeconfig utilise cette IP
cat /tmp/kubeconfig-for-jenkins | grep "server:"
# Doit afficher : server: https://172.17.0.1:PORT

# Tester depuis Jenkins
docker exec ebank-jenkins kubectl --kubeconfig=/dev/stdin get nodes \
    < /tmp/kubeconfig-for-jenkins

# Si Minikube a redémarré → régénérer le kubeconfig
minikube kubectl -- config view --flatten \
    | sed "s|https://127.0.0.1|https://${DOCKER_HOST_IP}|g" \
    > /tmp/kubeconfig-for-jenkins
# Puis re-uploader dans Jenkins → Credentials → kubeconfig
```

---

### ❌ OWASP Dependency-Check très lent (> 30 min)

**Symptôme :** Le stage "Analyse Dépendances" tourne pendant très longtemps.

```bash
# Cause : téléchargement de la base NVD (~200 MB) lors du premier run
# Solution : utiliser une clé API NVD pour lever le rate-limiting

# 1. Créer une clé API gratuite : https://nvd.nist.gov/developers/request-an-api-key
# 2. Ajouter dans Jenkins → Credentials : Secret text → ID: nvd-api-key
# 3. Dans le Jenkinsfile, ajouter dans le stage OWASP :
#    -DnvdApiKey=${NVD_API_KEY}

# Pour accélérer en attendant : désactiver le check en CI (passer -DskipDependencyCheck=true)
# et le garder uniquement sur la branche main
```

---

### ❌ Pa11y échoue ("Unable to load page")

**Symptôme :** Pa11y ne peut pas accéder à `http://ebank.local`

```bash
# Vérifier que l'Ingress fonctionne depuis l'hôte
curl -v http://ebank.local/actuator/health

# Vérifier /etc/hosts
grep "ebank.local" /etc/hosts

# Si le pod Pa11y tourne dans Jenkins (conteneur Docker), il n'a pas accès à /etc/hosts de l'hôte
# Solution : utiliser l'IP Minikube directement
MINIKUBE_IP=$(minikube ip)
NODE_PORT=$(kubectl get svc ebank-monolith-service -n ebank -o jsonpath='{.spec.ports[0].nodePort}')
# Dans le Jenkinsfile, remplacer APP_BASE_URL par http://${MINIKUBE_IP}:${NODE_PORT}
```

---

### ❌ Newman : tests E2E échouent tous (401 Unauthorized)

**Symptôme :** Toutes les requêtes retournent 401 après `/login`

```bash
# Vérifier que l'app tourne avec le bon profil Spring
kubectl exec -n ebank deployment/ebank-monolith -- \
    env | grep SPRING_PROFILES_ACTIVE
# Doit afficher : SPRING_PROFILES_ACTIVE=prod

# Vérifier les logs de l'app pour des erreurs JWT
kubectl logs -l app=ebank-monolith -n ebank --tail=50 | grep -i "jwt\|token\|auth"

# Vérifier que le secret JWT est correctement monté
kubectl exec -n ebank deployment/ebank-monolith -- \
    env | grep JWT_SECRET
```

---

## Structure des fichiers d'infrastructure

```
jenkins/infra/
├── docker-compose.infra.yml     ← Stack CI/CD (Jenkins + SonarQube + PostgreSQL)
├── .env.example                 ← Template de configuration (copier en .env)
├── .env                         ← Configuration locale (ne pas committer)
├── jenkins/
│   ├── Dockerfile               ← Image Jenkins custom (Java 17 + tous les outils CI/CD)
│   └── plugins.txt              ← Plugins Jenkins pré-installés automatiquement
└── GETTING_STARTED.md           ← Ce fichier
```

---

## Checklist de démarrage rapide

Copier-coller cette checklist pour un démarrage de zéro :

```
□ Docker Engine installé et fonctionnel (docker --version)
□ Minikube installé (minikube version)
□ kubectl installé (kubectl version --client)
□ Compte Docker Hub créé et Access Token généré
□ Réglages système appliqués (vm.max_map_count=524288)
□ Stack CI/CD lancée (docker compose up -d --build)  [5-15 min]
□ Jenkins accessible sur http://localhost:8090
□ SonarQube accessible sur http://localhost:9000 et status: UP
□ Outils Jenkins vérifiés (docker exec ebank-jenkins trivy --version)
□ Minikube démarré avec addons ingress + metrics-server
□ /etc/hosts configuré avec $(minikube ip)  ebank.local
□ kubeconfig-for-jenkins généré et testé
□ Credentials Jenkins configurés (dockerhub, kubeconfig, sonarqube-token)
□ SonarQube : projet créé avec clé "ebank-monolith"
□ Jenkins : SonarQube server configuré (URL: http://sonarqube:9000)
□ Jenkinsfile édité avec votre username Docker Hub
□ Code commité et pusché sur le dépôt Git
□ Namespace K8s créé + secrets créés + PostgreSQL déployé
□ Job Pipeline créé dans Jenkins
□ Build Now → pipeline vert ✅
```
