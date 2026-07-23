# Guide de démarrage complet — eBank (microservices)

> Guide pas-à-pas pour démarrer la plateforme **eBank** en **Docker Compose** et en
> **Minikube**, la tester de bout en bout, et utiliser **Spring Admin**, la
> **caching** (Redis) et le stack d'**observabilité** (Prometheus, Grafana, Tempo,
> Loki, ELK).
>
> Toutes les commandes sont à lancer depuis le dossier `microservices/` sauf
> indication contraire.

---

## Sommaire

1. [Architecture & ports](#1-architecture--ports)
2. [Prérequis](#2-prérequis)
3. [Démarrage en Docker Compose](#3-démarrage-en-docker-compose)
4. [Démarrage en Minikube](#4-démarrage-en-minikube)
5. [Tester l'application](#5-tester-lapplication)
6. [Caching (Redis)](#6-caching-redis)
7. [Observabilité (Prometheus / Grafana / Tempo / Loki / ELK)](#7-observabilité)
8. [Spring Admin](#8-spring-admin)
9. [Dépannage (troubleshooting)](#9-dépannage)
10. [Annexe — validations & corrections apportées](#10-annexe--validations--corrections)

---

## 1. Architecture & ports

La plateforme est composée de **6 microservices applicatifs**, d'un socle
d'**infrastructure** (bases, bus d'événements, secrets) et d'un stack
d'**observabilité**.

| Composant | Techno | Port (host) | Rôle |
|---|---|---|---|
| **gateway-service** | Spring Cloud Gateway (WebFlux) | `8080` | Point d'entrée unique, auth JWT, rate-limiting, routage |
| **auth-service** | Spring Boot + Security + JWT | `8081` | Inscription, login, refresh, blacklist JWT (Redis) |
| **accounts-service** | Spring WebFlux + R2DBC (réactif) | `8082` | Comptes bancaires (CQRS, cache-aside) |
| **transaction-service** | Spring WebFlux + MongoDB + Kafka | `8083` | Transactions, événements (saga) |
| **chatbot-service** | Spring Boot + Spring AI (RAG/pgvector) | `3001` | Chatbot, tool-calling, RAG |
| **notification-service** | Node.js + KafkaJS | `3002` | Emails/SMS déclenchés par Kafka |
| **admin** | Spring Boot Admin (`de.codecentric`) | `8091` (→ 8090) | UI de supervision des 5 services Spring |
| postgres (pgvector) | PostgreSQL 17 + pgvector | `5432` | `auth_db`, `accounts_db`, `chatbot_db` |
| mongodb | MongoDB 7 | `27017` | `transactions_db` |
| redis | Redis 7 | `6379` | Cache, sessions, rate-limit, blacklist JWT |
| kafka / zookeeper | Confluent 7.6 | `9092`/`29092`, `2181` | Bus d'événements |
| kafka-ui | provectus kafka-ui | `8090` | Dashboard des topics Kafka |
| mailhog | MailHog | `1025` (SMTP) / `8025` (UI) | SMTP de dev (capture des emails) |
| vault | HashiCorp Vault (dev) | `8200` | Secrets & configuration des services |
| **prometheus** | Prometheus | `9090` | Métriques (scrape `/actuator/prometheus`) |
| **grafana** | Grafana | `3000` | UI unifiée (métriques + logs + traces) |
| **tempo** | Grafana Tempo | `3200`, `4317` (gRPC), `4318` (HTTP) | Traces distribuées (OTLP) |
| **loki** | Grafana Loki | `3100` | Agrégation de logs |
| *(overlay ELK)* elasticsearch | Elasticsearch 8.13 | `9200` | Index de logs full-text |
| *(overlay ELK)* kibana | Kibana 8.13 | `5601` | Recherche full-text des logs |
| *(overlay ELK)* logstash | Logstash 8.13 | `5044` | Pipeline d'ingestion (Filebeat → ES) |

**Flux de configuration :** il n'y a **pas de Config Server**. Au démarrage, le
conteneur `vault-init` injecte toute la configuration de chaque service dans
**Vault** (chemins `secret/ebank/<service>/docker`). Chaque service lit sa config
depuis Vault via Spring Cloud Vault (`SPRING_PROFILES_ACTIVE=docker`,
`VAULT_URI`, `VAULT_TOKEN=root`).

> ℹ️ **Port 8090 :** `kafka-ui` occupe `8090` sur l'hôte. Le serveur **Spring Boot
> Admin** écoute sur `8090` **dans le conteneur** mais est publié sur l'hôte en
> **`8091`** pour éviter le conflit (voir §8).

---

## 2. Prérequis

| Outil | Version conseillée | Vérifier |
|---|---|---|
| Docker + Compose v2 | Docker ≥ 24, Compose ≥ 2.20 | `docker version && docker compose version` |
| Minikube | ≥ 1.32 | `minikube version` |
| kubectl | ≥ 1.28 | `kubectl version --client` |
| Helm | ≥ 3.14 | `helm version` |
| (build local optionnel) JDK 21 + Maven 3.9, Node 20+ | — | `java -version`, `mvn -v`, `node -v` |

**Ressources recommandées :** au moins **6 CPU / 10 Go RAM** libres pour faire
tourner tout le stack (services + observabilité). L'overlay ELK ajoute ~1,5 Go.

**Clé LLM (optionnelle) pour le chatbot :** le `chatbot-service` démarre sans clé
(placeholder), mais les appels au modèle échoueront tant que vous n'aurez pas
fourni une vraie clé. Exportez-la avant le `up` :

```bash
export OPENAI_API_KEY=sk-...            # OpenAI, Groq, ...
# ou pointez vers un backend compatible (Ollama, etc.) :
export OPENAI_BASE_URL=http://host.docker.internal:11434
export CHATBOT_MODEL=llama3.1
```

---

## 3. Démarrage en Docker Compose

### 3.1 Stack de base (services + observabilité)

```bash
cd microservices

# (optionnel) valider la composition avant de lancer
docker compose config -q && echo "compose OK"

# Construire et démarrer TOUT : 6 services + Postgres/Mongo/Redis/Kafka/Vault
# + Prometheus/Grafana/Tempo/Loki
docker compose up -d --build
```

Le démarrage est **ordonné** par des `healthcheck` + `depends_on` :
`vault` → `vault-init` (injecte la config) → bases saines → services applicatifs.
Comptez **2 à 4 minutes** au premier lancement (build des images + migrations Flyway).

**Suivre la montée en charge :**

```bash
docker compose ps                      # statut + (healthy)
docker compose logs -f gateway-service # logs d'un service
watch -n 3 'docker compose ps --format "table {{.Name}}\t{{.Status}}"'
```

Attendre que gateway/auth/accounts/transaction/chatbot/notification soient
`(healthy)`.

### 3.2 Overlay ELK (recherche full-text des logs) — optionnel

Le stack de base logue déjà dans **Loki**. Pour ajouter **Elasticsearch +
Logstash + Kibana + Filebeat** (Filebeat lit le `stdout` des conteneurs
`*_service`, aucun changement de code) :

```bash
docker compose -f docker-compose.yml -f docker-compose.elk.yml up -d
# Kibana : http://localhost:5601   (index : ebank-microservices-*)
```

### 3.3 Vérification rapide

```bash
# Santé de chaque service
for p in 8080 8081 8082 8083 3001; do
  echo "port $p:"; curl -s http://localhost:$p/actuator/health | head -c 120; echo
done
curl -s http://localhost:3002/health          # notification (Node)
curl -s -u admin:admin http://localhost:8091/actuator/health  # Spring Boot Admin

# Vault a bien seedé la config ?
curl -s -H "X-Vault-Token: root" \
  http://localhost:8200/v1/secret/data/ebank/auth-service/docker | head -c 200; echo
```

Réponse attendue pour la santé : `{"status":"UP", ...}`.

### 3.4 Arrêt

```bash
docker compose down                 # stoppe et supprime les conteneurs
docker compose down -v              # + supprime les volumes (reset total des données)
docker compose -f docker-compose.yml -f docker-compose.elk.yml down  # si ELK lancé
```

---

## 4. Démarrage en Minikube

> L'ordre des étapes est important : `kube-prometheus-stack` doit exister
> **avant** le chart de l'app (il fournit la CRD `ServiceMonitor`).

### 4.1 Démarrer le cluster

```bash
minikube start --driver=docker --cpus=6 --memory=10240 --disk-size=40g
minikube addons enable ingress
minikube addons enable metrics-server

# Dépôts Helm pour l'observabilité
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
```

### 4.2 Construire les images DANS le démon Docker de Minikube

```bash
# CRITIQUE : à refaire dans CHAQUE terminal qui build
eval $(minikube docker-env)

docker build -t ebank-auth:local          ./auth
docker build -t ebank-gateway:local       ./gateway
docker build -t ebank-accounts:local      ./accounts
docker build -t ebank-transactions:local  ./transactions
docker build -t ebank-chatbot:local       ./chatbot
docker build -t ebank-notification:local  ./notifications
# (optionnel) frontend :
# docker build -t ebank-frontend:local ../front-react

docker images | grep ebank          # vérifier qu'elles sont présentes
```

### 4.3 Déployer Vault, les bases et Tempo

```bash
kubectl create namespace ebank-local

kubectl apply -f k8s/vault-dev.yaml   -n ebank-local
kubectl wait --for=condition=ready pod -l app=vault -n ebank-local --timeout=60s

kubectl apply -f k8s/infra-local.yaml -n ebank-local   # postgres, mongo, redis, kafka
kubectl apply -f k8s/tempo-local.yaml -n ebank-local
kubectl wait --for=condition=ready pod -l tier=infra -n ebank-local --timeout=180s
```

### 4.4 Déployer l'observabilité

```bash
# Prometheus + CRD ServiceMonitor (namespace monitoring)
kubectl create namespace monitoring
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring

# Loki + Grafana (sidecars → auto-chargement datasources/dashboards)
helm upgrade --install loki grafana/loki-stack \
  -n ebank-local \
  --set grafana.enabled=true,prometheus.enabled=false
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=grafana \
  -n ebank-local --timeout=180s

# Câbler Prometheus + Loki + Tempo dans Grafana + charger les dashboards
kubectl apply -f k8s/grafana-datasources.yaml -n ebank-local
kubectl apply -f k8s/grafana-dashboards.yaml  -n ebank-local
kubectl apply -f k8s/observability-ingress.yaml -n ebank-local   # optionnel (Ingress)
```

### 4.5 Déployer l'application (Helm)

```bash
helm upgrade --install ebank ./helm/ebank \
  -f helm/ebank/values.yaml \
  -f helm/ebank/values-local.yaml \
  --namespace ebank-local --create-namespace

# Suivre
kubectl get pods -n ebank-local -w
kubectl get all  -n ebank-local
```

Le chart déploie : les 6 services + leurs `ServiceMonitor`, un `Job` `vault-init`
(seed de la config, y compris l'endpoint OTLP Tempo), un `Job` de migration DB, le
frontend, et les Ingress. (`values-local.yaml` fournit le token Vault de dev
`root` ; en prod ce token est requis via secret/`--set`, sinon le rendu Helm
échoue volontairement.)

### 4.6 Accéder aux services

```bash
# API via le gateway
kubectl port-forward svc/ebank-ebank-gateway -n ebank-local 8080:8080 &

# Grafana
kubectl port-forward svc/loki-grafana -n ebank-local 3000:80 &
kubectl get secret loki-grafana -n ebank-local \
  -o jsonpath='{.data.admin-password}' | base64 -d; echo    # mot de passe admin

# Frontend via Ingress
minikube ip
echo "$(minikube ip) ebank.local grafana.ebank.local" | sudo tee -a /etc/hosts
# → http://ebank.local , http://grafana.ebank.local
```

### 4.7 Scaler / nettoyer

```bash
kubectl scale deployment ebank-ebank-auth -n ebank-local --replicas=3
helm uninstall ebank -n ebank-local
minikube delete
```

---

## 5. Tester l'application

Tout passe par le **gateway (`:8080`)**. Routes exposées :
`/api/auth/**`, `/api/accounts/**`, `/api/transactions/**`, `/api/chat/**`,
`/ws/chat/**`.

### 5.1 Parcours fonctionnel complet (auth → compte → transaction)

```bash
# 1) Inscription
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@bank.com","password":"Test1234!","firstName":"Alice","lastName":"Smith"}'

# 2) Login → récupérer le JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@bank.com","password":"Test1234!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
echo "TOKEN=$TOKEN"

# 3) Créer un compte (route protégée → header Authorization requis)
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"accountNumber":"ACCT00000001","accountHolderName":"Alice Smith","email":"alice@bank.com","phoneNumber":"0601020304","accountType":"SAVINGS","balance":500.00,"status":"ACTIVE"}'

# 4) Lister les comptes
curl -s http://localhost:8080/api/accounts -H "Authorization: Bearer $TOKEN" | jq .

# 5) Créer une transaction (dépôt)
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"toAccountId":"ACCT00000001","amount":150.00,"type":"DEPOSIT","description":"Dépôt initial"}'
```

**Attendu :** `register` → 200 + tokens ; `login` → 200 + `accessToken` ;
sans `Authorization` sur `/api/accounts` → **401**.

### 5.2 Notifications (Kafka → email)

Une transaction publie un événement Kafka consommé par `notification-service`,
qui envoie un email intercepté par **MailHog**.

```bash
# Vérifier le consumer
curl -s http://localhost:3002/health

# Produire un événement à la main via Kafka UI (http://localhost:8090) :
#   Topics → notification-events → Produce message :
#   {"type":"EMAIL","recipient":"test@example.com","subject":"Test","body":"Hello","occurredAt":"2026-01-01T12:00:00Z"}

# Voir l'email arriver :
open http://localhost:8025          # UI MailHog
docker compose logs -f notification-service
```

### 5.3 Chatbot (nécessite une clé LLM — cf. §2)

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"message":"Quel est le solde de mon compte ?"}'
```

### 5.4 Documentation d'API (Swagger)

Chaque service Spring expose sa spec OpenAPI, ex. :
`http://localhost:8081/auth-api-ui`, `http://localhost:8082/accounts-api-ui`,
`http://localhost:8083/transactions-api-ui`.

---

## 6. Caching (Redis)

Redis (`:6379`, mot de passe `redispass`) sous-tend tous les cas de cache :

| Service | Ce qui est mis en cache | Pattern | TTL |
|---|---|---|---|
| accounts | compte-par-id, liste des comptes | cache-aside | 10 min / 5 min |
| transactions | transaction-par-id, par-compte | cache-aside | 10 min / 5 min |
| auth | refresh tokens, blacklist de révocation | source de vérité Redis | TTL refresh / expiration token |
| gateway | résultat de validation JWT (username+role) | cache-aside devant auth-service | ≤ 60 s |
| notifications | déduplication de redelivery Kafka | idempotence | 24 h |

**Vérifier le cache en action :**

```bash
# Se connecter à Redis
docker exec -it ebank_redis redis-cli -a redispass

# Dans le CLI Redis :
KEYS *                         # voir les clés (accounts::, jwt:validation:, blacklist:, ...)
TTL <clé>                      # TTL restant
MONITOR                        # observer les GET/SET en temps réel pendant les requêtes
```

Un 2ᵉ appel identique (ex. `GET /api/accounts/{id}`) doit être servi depuis le
cache : latence plus faible et, côté métriques,
`cache_gets_total{result="hit"}` qui augmente (voir §7).

---

## 7. Observabilité

Quatre signaux, une porte d'entrée : **Grafana**.

| Outil | URL (compose) | Usage |
|---|---|---|
| **Grafana** | http://localhost:3000 | Métriques + logs + traces corrélés (accès anonyme Admin en dev) |
| **Prometheus** | http://localhost:9090 | Stockage des métriques, PromQL, alerting |
| **Tempo** | via Grafana (API `:3200`) | Traces distribuées |
| **Loki** | via Grafana (`:3100`) | Logs (LogQL) |
| **Kibana** (overlay ELK) | http://localhost:5601 | Recherche full-text des logs |

### 7.1 Métriques (Prometheus + Grafana)

- Prometheus scrape `/actuator/prometheus` de **tous** les services Spring
  (gateway, auth, accounts, transaction, **chatbot**) + `/metrics` du service Node.
- Vérifier les cibles : http://localhost:9090/targets (tout doit être **UP**).
- Dans Grafana → **Explore** → source *Prometheus*. Requêtes utiles :

```promql
# Débit de requêtes par service
sum(rate(http_server_requests_seconds_count[1m])) by (service)
# Latence P99 par route
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))
# Taux d'erreurs 5xx
sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) by (service)
# Taux de hit du cache
sum(rate(cache_gets_total{result="hit"}[1m])) / sum(rate(cache_gets_total[1m]))
# Connexions DB actives (Hikari)
hikaricp_connections_active
```

### 7.2 Traces (Tempo)

- Les services poussent leurs spans en **OTLP** vers Tempo (`:4318`).
- Grafana → **Explore** → source *Tempo* → onglet **Search** →
  `service.name = auth-service` (ou TraceQL `{ status = error }`).
- Tempo génère aussi les **span-metrics** et le **service graph**
  (Grafana → Explore → Tempo → *Service Graph*).

### 7.3 Logs (Loki) & corrélation

- Grafana → **Explore** → source *Loki* :

```logql
{service="auth-service"} |= "ERROR"
{service="gateway"} | logfmt | status >= 400
```

- **Corrélation trace ↔ logs ↔ métriques** (câblée dans le provisioning Grafana) :
  - dans une **trace** Tempo, un clic ouvre les **logs** Loki du même `service`,
    et les **métriques** Prometheus correspondantes ;
  - dans une **ligne de log** Loki, le champ `traceId` est cliquable → ouvre la
    trace dans Tempo.
  > Ces liens reposent sur des `uid` de datasource stables (`loki`, `prometheus`,
  > `tempo`) — désormais fixés dans le provisioning (cf. §10).

### 7.4 Logs full-text (ELK / Kibana) — si overlay lancé

1. http://localhost:5601 → **Discover**.
2. Créer l'index pattern `ebank-microservices-*` (champ temps `@timestamp`).
3. Filtrer par `service`, `log_level`, `trace_id`, etc. Filebeat capture le
   `stdout` des conteneurs `*_service` ; Logstash normalise les deux formats
   (JSON pino côté Node, logfmt logback côté Java).

---

## 8. Spring Admin

Le stack microservices embarque désormais un **serveur Spring Boot Admin**
dédié (module `microservices/admin`, service `admin` dans `docker-compose.yml`).

- **UI :** http://localhost:8091 &nbsp;(login **`admin` / `admin`**, surchargeable
  via `ADMIN_UI_USER` / `ADMIN_UI_PASSWORD`).
- **Port :** exposé sur l'hôte en **`8091`** (le `8090` interne est remappé car
  `kafka-ui` occupe déjà `8090`).
- **Découverte :** le serveur monitore les **5 services Spring** (gateway, auth,
  accounts, transaction, chatbot) via une **liste statique**
  (`SimpleDiscoveryClient`, cf. `admin/src/main/resources/application.yml`). Les
  services n'embarquent **aucune dépendance client** — SBA interroge simplement
  leurs endpoints `/actuator/**` déjà exposés.
  (`notification-service` est en Node.js : pas d'API Actuator Spring, donc non
  monitoré ici — voir §7 pour ses métriques/logs.)

```bash
# Une fois `docker compose up -d` terminé :
open http://localhost:8091            # → liste des 5 services, statut UP en vert
# health du serveur Admin lui-même :
curl -s -u admin:admin http://localhost:8091/actuator/health
```

**Ce que Spring Admin offre (panneaux clés), pour chaque service :**

| Panneau | Source (`/actuator/…`) | Ce que vous pouvez faire |
|---|---|---|
| Health | `health` | État DB, Redis, disque en un coup d'œil |
| JVM / Metrics | `metrics` | Heap/non-heap, GC, threads, HTTP en direct |
| Loggers | `loggers` | Passer `com.ebank` en DEBUG **sans redémarrer** |
| Caches | `caches` | Caches Redis (accounts/transactions) |
| Environment | `env` | Propriétés actives (valeurs masquées) |
| Thread dump | `threaddump` | Snapshot des threads |
| Mappings | `mappings` | Tous les endpoints HTTP enregistrés |

> Ces panneaux fonctionnent parce que l'exposition Actuator des services a été
> élargie à `loggers,threaddump,caches,mappings` (seed Vault + `application.yml`
> du chatbot, cf. §10).

**En Minikube :** le chart Helm déploie aussi le serveur Admin
(`admin.enabled: true`). Y accéder :

```bash
kubectl port-forward svc/ebank-ebank-admin -n ebank-local 8091:8090 &
# → http://localhost:8091   (admin / admin)
```

**Équivalent « à la main » (sans SBA) :** chaque service reste interrogeable
directement, utile pour un script ou un check ponctuel :

```bash
curl -s http://localhost:8081/actuator/health | jq .
# Changer un niveau de log à chaud (ce que fait le panneau Loggers de SBA) :
curl -X POST http://localhost:8081/actuator/loggers/com.ebank \
  -H "Content-Type: application/json" -d '{"configuredLevel":"DEBUG"}'
# Recharger la config depuis Vault sans redémarrage :
curl -X POST http://localhost:8081/actuator/refresh
```

---

## 9. Dépannage

| Symptôme | Cause probable | Action |
|---|---|---|
| Un service reste `unhealthy` | Vault/DB pas prêt au démarrage | `docker compose logs <service>` ; `docker compose restart <service>` |
| `401` sur `/api/accounts` | JWT manquant/expiré | Refaire un login, renvoyer `Authorization: Bearer $TOKEN` |
| Chatbot répond en erreur LLM | `OPENAI_API_KEY` placeholder | Exporter une vraie clé et `docker compose up -d chatbot-service` |
| Grafana « No data » | Cibles Prometheus DOWN | http://localhost:9090/targets ; vérifier réseau `ebank-network` |
| Traces↔logs non cliquables | uid datasource | Corrigé (uid `loki`/`prometheus`/`tempo`) ; sinon vérifier le provisioning |
| Emails absents | notification-service down / topic vide | `curl :3002/health` ; Kafka UI → topic `notification-events` |
| Minikube : `helm install ebank` échoue sur `ServiceMonitor` | kube-prometheus-stack absent | Installer `monitoring` **avant** le chart app (§4.4) |
| Minikube : image `ImagePullBackOff` | images pas dans le démon minikube | `eval $(minikube docker-env)` puis rebuild (§4.2) |

Reset complet en compose : `docker compose down -v && docker compose up -d --build`.

---

## 10. Annexe — validations & corrections

### 10.1 Ce qui a été validé

- **Build applicatif complet** (Maven/npm locaux) : les 6 services compilent et
  produisent leur artefact — `auth`, `gateway`, `accounts`, `transaction`,
  `chatbot` (fat-jars) + `notification` (Node/tsc). Frontends **React et Angular** :
  build de production OK.
- **Tests unitaires** métier verts : `AuthServiceTest`, `AccountServiceTest`,
  `ChatbotServiceTest`.
- **Serveur Spring Boot Admin** (nouveau module `admin/`) : compile, et le jar
  **démarre réellement** en local (contexte chargé, UI SBA servie, découverte
  statique active).
- **Docker Compose** : `docker compose config` valide (stack de base **et**
  overlay ELK).
- **Kubernetes** : tous les manifestes `k8s/*.yaml` sont des objets K8s
  bien formés ; le **chart Helm** passe `helm lint` et `helm template`
  (profil `values-local`, 27 objets rendus, dont le serveur Admin).
- **Configs d'observabilité** relues (Prometheus, Tempo, Loki, datasources
  Grafana, pipeline Logstash/Filebeat).

### 10.2 Corrections apportées

| Fichier | Correction | Pourquoi |
|---|---|---|
| `gateway/Dockerfile`, `accounts/Dockerfile` | Build via image `maven:3.9-eclipse-temurin-21-alpine` + `mvn` (au lieu de `./mvnw`) | `.mvn/wrapper/maven-wrapper.properties` est gitignoré → `./mvnw` (et `COPY .mvn`) faisait échouer le `docker build`. Aligné sur `auth`/`transactions`. |
| `notifications/package-lock.json` | Lockfile régénéré | Désynchronisé avec `package.json` → `npm ci` (utilisé par le Dockerfile) échouait. |
| `front-angular/package-lock.json` | Lockfile régénéré | Même désynchronisation → `docker build ../front-angular` (`npm ci`) échouait. |
| `infra/prometheus/prometheus.yml` | Ajout du job de scrape `chatbot-service:3001` | Les métriques du chatbot n'étaient pas collectées. |
| `infra/grafana/.../loki.yml`, `prometheus.yml` | Ajout d'`uid: loki` / `uid: prometheus` | Sans uid stable, les corrélations Tempo→Logs/Métriques ne se résolvaient pas. |
| `auth/.../AuthServiceTest.java` | Injection d'un vrai `SimpleMeterRegistry` | Le test ne fournissait pas de `MeterRegistry` → `NullPointerException` (5 tests en erreur). |

### 10.2bis Ajout — Spring Boot Admin (objectif « spring admin »)

| Ajout | Détail |
|---|---|
| Module `admin/` | Nouveau serveur Spring Boot Admin (`de.codecentric` 4.0.4, Spring Boot 4.0.3). Découverte statique des 5 services Spring via `SimpleDiscoveryClient` → **aucune modification des services existants**. |
| Service `admin` dans `docker-compose.yml` | Publié sur l'hôte `8091` (interne 8090), login `admin`/`admin`. |
| Templates Helm `admin-deployment.yaml` / `admin-service.yaml` + `admin` dans `values.yaml` | Déploiement du serveur Admin en Minikube (parité docker-compose ↔ k8s). |
| Exposition Actuator élargie (`loggers,threaddump,caches,mappings`) | Seed Vault (auth/accounts/transaction/gateway) + `application.yml` du chatbot — pour que les panneaux SBA (log-level runtime, thread dump, caches) soient pleinement fonctionnels. |

### 10.3 Limite d'environnement (transparence)

La **validation runtime** en direct (`docker compose up` / `minikube start`)
n'a **pas pu être exécutée dans cet environnement** : la politique réseau de
sortie bloque le CDN des blobs Docker Hub
(`production.cloudfront.docker.com` → **403**), donc aucune image de base ne peut
être *pull*. La validation a donc porté sur : compilation et tests du code,
validation des compositions/manifestes/chart, et relecture des configs. Les
commandes de ce guide sont prêtes à l'emploi sur un poste disposant d'un accès
Docker Hub normal.
