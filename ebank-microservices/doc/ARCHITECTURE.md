# eBank — Architecture Microservices

> Projet pédagogique : montée en compétence Tech Lead Junior
> Stack : Java 21 · Spring Boot 4 · Node.js · Angular 20 · PostgreSQL · MongoDB · Redis · Kafka

---

## Table des matières

1. [Vue d'ensemble](#vue-densemble)
2. [Services](#services)
3. [Infrastructure](#infrastructure)
4. [Décisions techniques justifiées](#décisions-techniques-justifiées)
5. [Flux de données](#flux-de-données)
6. [Démarrage rapide](#démarrage-rapide)
7. [Variables d'environnement](#variables-denvironnement)

---

## Vue d'ensemble

```
Client (Angular 20)
       │
       ▼
┌─────────────────────────────────────────────────────┐
│  API Gateway :8080  (Spring Cloud Gateway)          │
│  JWT Auth · Rate Limiting · Circuit Breaker · CORS  │
└──────────┬──────────────────────────────────────────┘
           │
    ┌──────┴──────────────────────────────────────┐
    │                                             │
    ▼                                             ▼
Auth :8081          Accounts :8082       Transactions :8083
Spring Boot         Spring WebFlux       Spring WebFlux
JWT + Redis         R2DBC + CQRS         MongoDB + Kafka
    │                   │                     │
    ▼                   ▼                     ▼
PostgreSQL          PostgreSQL            MongoDB
(auth_db)           (accounts_db)       (transactions_db)

Cards :8085         Analytics :8084      Chatbot :3001
Spring WebFlux      Spring Boot MVC      Node.js + LangChain
R2DBC               MongoDB              WebSocket + SSE
    │                   │                     │
    ▼                   ▼                     ▼
PostgreSQL          MongoDB               pgvector
(cards_db)         (analytics_db)       (chatbot_db)

                 Notifications :3002
                 Node.js + KafkaJS
                 Email · SMS · Push

═══════════════════════════════════════════════
Infrastructure : Redis · Kafka · Zookeeper · MailHog
═══════════════════════════════════════════════
```

---

## Services

### API Gateway — Port 8080
**Technologie :** Spring Cloud Gateway (WebFlux)
**Responsabilités :**
- Routage vers tous les microservices
- Validation JWT (sans toucher Spring Security sur chaque service)
- Rate limiting via Redis (sliding window)
- Circuit Breaker (Resilience4j)
- Logging centralisé des requêtes avec TraceId
- CORS global
- SSL Termination (production)

**Routes :**
| Path | Service cible |
|------|--------------|
| `/api/auth/**` | auth-service:8081 (public) |
| `/api/accounts/**` | accounts-service:8082 (protégé) |
| `/api/transactions/**` | transaction-service:8083 (protégé) |
| `/api/analytics/**` | analytics-service:8084 (protégé) |
| `/api/cards/**` | card-service:8085 (protégé) |
| `/ws/chat` | chatbot-service:3001 (WebSocket) |

---

### Auth Service — Port 8081
**Technologie :** Spring Boot 4 · Spring Security · jjwt 0.12 · Redis · PostgreSQL
**Endpoints :**
```
POST /api/auth/register   → Inscription
POST /api/auth/login      → Connexion → {accessToken, refreshToken}
POST /api/auth/refresh    → Renouveler access token via refresh token
POST /api/auth/logout     → Blackliste le token dans Redis
GET  /api/auth/me         → Profil utilisateur connecté
```
**Stratégie JWT :**
- Access token : 15 min (stateless, validé par le Gateway)
- Refresh token : 7 jours (stocké en Redis, révocable)
- Logout : access token blacklisté dans Redis jusqu'à expiration

---

### Account Service — Port 8082
**Technologie :** Spring WebFlux · R2DBC · PostgreSQL · CQRS
**Pattern CQRS :**
- **Commands** (écriture) : `CreateAccountCommand`, `UpdateAccountCommand`, `DeleteAccountCommand`
- **Queries** (lecture) : `GetAccountByIdQuery`, `GetAllAccountsQuery`
- `AccountCommandHandler` → traite les mutations
- `AccountQueryHandler` → optimise les lectures (peut être scalé indépendamment)

**Endpoints réactifs :**
```
GET    /api/accounts        → Flux<AccountResponse>
GET    /api/accounts/{id}   → Mono<AccountResponse>
POST   /api/accounts        → Mono<AccountResponse> (201)
PUT    /api/accounts/{id}   → Mono<AccountResponse>
DELETE /api/accounts/{id}   → Mono<Void> (204)
```

---

### Transaction Service — Port 8083
**Technologie :** Spring WebFlux · MongoDB Reactive · Kafka (producer + consumer)
**Types de transactions :** DEPOSIT, WITHDRAWAL, TRANSFER, PAYMENT
**Saga Pattern (transfert) :**
1. Réserver montant sur compte source
2. Crédit compte destination
3. Confirmer ou annuler (compensation)

**Events Kafka produits :**
- `transaction-events` → topic consommé par Notification + Analytics

---

### Analytics Service — Port 8084
**Technologie :** Spring Boot MVC · MongoDB (non-réactif) · Kafka consumer
**Endpoints :**
```
GET /api/analytics/summary               → Résumé global
GET /api/analytics/transactions/by-period → Agrégation par période
GET /api/analytics/accounts/top          → Top comptes par volume
```
**Consumer Kafka :** consomme `transaction-events` → agrège en temps réel dans MongoDB

---

### Card Service — Port 8085
**Technologie :** Spring WebFlux · R2DBC · PostgreSQL
**Endpoints :**
```
GET    /api/cards              → Flux<CardResponse>
GET    /api/cards/{id}         → Mono<CardResponse>
GET    /api/cards/account/{id} → Cartes d'un compte
POST   /api/cards              → Créer une carte (virtuelle ou physique)
PUT    /api/cards/{id}         → Modifier limites
POST   /api/cards/{id}/block   → Bloquer la carte
POST   /api/cards/{id}/unblock → Débloquer la carte
DELETE /api/cards/{id}         → Supprimer
```

---

### Chatbot Service — Port 3001
**Technologie :** Spring Boot 3.5 · Java 21 · **Spring AI 1.1** · PostgreSQL/**pgvector** · SSE · WebSocket
**Deux capacités derrière un seul `ChatClient` :**
- **Tool Calling** → le LLM appelle des fonctions Java qui interrogent les API REST des autres services (données live).
- **RAG** → les questions sur les procédures complexes sont répondues à partir d'une base documentaire indexée dans pgvector (`QuestionAnswerAdvisor`).

**Flux d'une requête :**
1. Client envoie un message (REST `/api/chat`, SSE `/api/chat/stream` ou WebSocket `/ws/chat`)
2. Gateway route vers chatbot-service (routes publiques déjà configurées)
3. Le `ChatClient` applique : system prompt, mémoire de session, advisor RAG et tools
4. Selon l'intent : Tool Calling (REST) **et/ou** récupération RAG (pgvector)
5. Le LLM (endpoint OpenAI-compatible) génère la réponse (streamée via SSE/WS)

**Tools disponibles :**
| Tool | Service appelé |
|------|---------------|
| `getAccountBalance` | accounts-service |
| `getRecentTransactions` | transaction-service |

> Détails complets, diagrammes mermaid et guides de test (Docker Compose + Minikube) :
> voir [`CHATBOT_DESIGN.md`](./CHATBOT_DESIGN.md).

---

### Notification Service — Port 3002
**Technologie :** Node.js 20 · TypeScript · KafkaJS · Nodemailer
**Consumers Kafka :**
- `transaction-events` → email de confirmation de transaction
- `notification-events` → alertes génériques
- `card-events` → alerte blocage carte

**Canaux de notification :**
- Email (Nodemailer + MailHog en dev, SendGrid en prod)
- SMS (mock en dev, Twilio en prod)
- Push (mock en dev, FCM en prod)

---

## Infrastructure

### PostgreSQL 17
**Usage :** auth_db · accounts_db · cards_db · chatbot_db
**Pattern :** Database-per-Service (une instance Docker, 4 bases créées via init.sql)
**Migrations :** Flyway (versionné, tracé dans git)

### MongoDB 7
**Usage :** transactions_db · analytics_db
**Pattern :** Collections séparées par service dans la même instance Docker

### Redis 7
**Usage :**
- JWT token blacklist (logout)
- Refresh tokens
- Rate limiting (sliding window counter)
- Cache de sessions

### Apache Kafka 7.6 (Confluent)
**Topics :**
| Topic | Producer | Consumers |
|-------|----------|-----------|
| `transaction-events` | transaction-service | notification-service, analytics-service |
| `account-events` | accounts-service | analytics-service |
| `card-events` | card-service | notification-service |
| `notification-events` | tous services | notification-service |

**Kafka UI :** http://localhost:8090

### MailHog
**Usage :** Mock SMTP en développement
**Web UI :** http://localhost:8025

---

## Décisions techniques justifiées

### 1. Spring Boot 4.0.3 + Java 21
**Choix :** Dernière version Spring Boot avec support Java 21.
✅ Virtual Threads (Project Loom) → concurrence légère sans callbacks
✅ Pattern matching, records, sealed classes → code plus expressif
✅ LTS Java 21 → support long terme
❌ Certaines libs tierces pas encore 100% compatibles (ex: JavaMelody)
❌ Ecosystem encore en transition (Spring Boot 4 = breaking changes vs 3.x)

---

### 2. Spring Cloud Gateway (WebFlux)
**Choix :** API Gateway réactif plutôt que Nginx ou Kong.
✅ Stack Java homogène → même équipe maintient tout
✅ Non-bloquant → gère des milliers de connexions simultanées
✅ Circuit Breaker Resilience4j intégré nativement
✅ Rate limiting via Redis intégré
✅ Filtres custom en Java pur
❌ Plus complexe que Nginx pour des équipes ops-first
❌ Pas de dashboard natif (besoin Grafana/Zipkin)
❌ Single point of failure → mitiger avec scaling horizontal

---

### 3. Spring WebFlux + R2DBC (Account, Card Services)
**Choix :** Réactif (WebFlux/R2DBC) plutôt que MVC/JPA pour les services à fort I/O.
✅ Modèle non-bloquant → 1 thread sert des centaines de requêtes
✅ Backpressure naturelle (Reactor Flux/Mono)
✅ Cohérence end-to-end : Gateway (réactif) → Service (réactif) → DB (R2DBC réactif)
❌ Courbe d'apprentissage steep : operators (flatMap, switchIfEmpty, etc.)
❌ Debugging difficile (stack traces réactives illisibles)
❌ R2DBC: pas de lazy loading, pas de @OneToMany automatique
❌ Tooling IDE moins mature qu'avec JPA

---

### 4. CQRS (Account Service)
**Choix :** Séparer les handlers de commandes (écriture) des handlers de requêtes (lecture).
✅ Read et Write scalent indépendamment (lectures >> écritures en banking)
✅ Testabilité : tester les mutations et les lectures séparément
✅ Prépare le terrain pour Event Sourcing
✅ Clarté du code : intention explicite par le type de commande/query
❌ Complexité accrue pour un CRUD simple
❌ Duplication de code potentielle (mappers séparés)
❌ Risque de sur-ingénierie si pas de réel besoin de scalabilité différenciée

---

### 5. JWT + Redis (Auth)
**Choix :** JWT stateless avec blacklist Redis pour la révocation.
✅ Stateless → le Gateway valide le JWT sans appel réseau à Auth Service
✅ Scalabilité horizontale naturelle
✅ Révocation possible (blacklist Redis avec TTL = durée du token)
✅ Refresh token pattern → courte durée d'access sans re-auth fréquente
❌ JWT payload encodé en Base64 → lisible par n'importe qui (ne pas y mettre de secrets)
❌ Dépendance Redis pour la révocation (si Redis tombe, logout ne fonctionne plus)
❌ Taille du JWT croît avec les claims → overhead réseau

---

### 6. MongoDB pour Transactions & Analytics
**Choix :** MongoDB plutôt que PostgreSQL pour les transactions et analytics.
✅ Schéma flexible : les métadonnées d'une transaction varient selon le type
✅ Aggregation Pipeline MongoDB = SQL GROUP BY sous stéroïdes
✅ Haute performance en écriture (pas de contraintes relationnelles)
✅ Pas de migration de schéma rigide pour des données évolutives
❌ Transactions ACID multi-documents requièrent une syntaxe explicite
❌ Jointures entre collections non naturelles (lookup = équivalent JOIN)
❌ Cohérence éventuelle si pas de transactions explicites

---

### 7. Apache Kafka
**Choix :** Kafka plutôt que RabbitMQ ou API calls directs entre services.
✅ At-least-once delivery → aucun événement perdu
✅ Event replay → reconstruire l'état ou déboguer
✅ Découplage total : Transaction Service ne connaît pas Notification Service
✅ Consumer groups → scale horizontalement les consumers
✅ Audit trail naturel (tous les événements sont loggés)
❌ Complexité opérationnelle (Zookeeper, partitions, offsets, rebalancing)
❌ Overhead pour faibles volumes (RabbitMQ serait plus simple)
❌ Latence légèrement supérieure à un appel REST direct

---

### 8. Spring AI pour le Chatbot, Node.js pour les Notifications
**Choix :** le Chatbot est en **Spring Boot + Spring AI** (même stack que le cœur de la
plateforme) ; le service Notifications reste en Node.js + KafkaJS.

**Chatbot — Spring AI :**
✅ `ChatClient` unifié : Tool Calling typé + advisors RAG/mémoire + streaming
✅ **VectorStore pgvector natif** → RAG sans service vectoriel dédié
✅ Observabilité identique aux services Java (Actuator, tracing OTLP, Loki)
✅ Endpoint LLM OpenAI-compatible → OpenAI, Groq ou **Ollama** local au choix
❌ Nécessite une clé/endpoint LLM pour un chat pleinement fonctionnel
❌ Épinglé sur Spring Boot 3.5 (compatibilité GA Spring AI 1.1), service isolé

**Notifications — Node.js :**
✅ KafkaJS léger et simple pour le consumer d'événements
✅ Event-loop = envoi email/SMS/push non bloquant
✅ Partage du TypeScript avec le frontend
❌ Écosystème enterprise (tracing, health checks) moins mature qu'en Java

---

### 9. pgvector pour le RAG du Chatbot
**Choix :** Extension PostgreSQL plutôt qu'une base vectorielle dédiée (Pinecone, Weaviate).
✅ Pas de service supplémentaire → coût infra réduit
✅ Extension PostgreSQL native → SQL standard + requêtes vectorielles
✅ Recherche par similarité cosine, L2, inner product
✅ Transactions ACID sur les embeddings comme sur n'importe quelle table
❌ Performances inférieures à Pinecone/Weaviate à très grande échelle
❌ Limite pratique ~1M vecteurs pour de bonnes performances sans tuning
❌ Pas de filtrage ANN aussi optimisé que des solutions dédiées

---

### 10. Polyglot Persistence
**Choix :** Utiliser la bonne base pour le bon besoin.
| Service | Base | Raison |
|---------|------|--------|
| Auth | PostgreSQL | ACID, relations users/rôles |
| Accounts | PostgreSQL | ACID, transactions financières critiques |
| Cards | PostgreSQL | ACID, contraintes d'unicité strictes |
| Transactions | MongoDB | Schéma flexible, haute écriture |
| Analytics | MongoDB | Aggregation Pipeline puissant |
| Chatbot | pgvector | Embeddings sans service dédié |
| Cache/Sessions | Redis | Ultra-rapide, TTL natif |

✅ Outil adapté au problème → meilleures performances par use case
✅ Isolation des données par service (pas de couplage de schéma)
❌ Complexité opérationnelle (3 types de bases à maintenir)
❌ Cohérence distribuée à gérer manuellement (pas de XA transactions)
❌ Expertise multiple requise dans l'équipe

---

## Flux de données

### Flux d'authentification
```
Client → POST /api/auth/login → Gateway → Auth Service
Auth Service → valide credentials → génère JWT
Auth Service → stocke refresh token dans Redis
Auth Service → retourne {accessToken, refreshToken}
Client → stocke tokens → envoie JWT dans Authorization header
Gateway → valide JWT sur chaque requête → route vers service cible
```

### Flux d'un virement
```
Client → POST /api/transactions/transfer → Gateway (JWT check)
Gateway → Transaction Service
Transaction Service → vérifie solde (appel Account Service)
Transaction Service → Saga: réserve montant source
Transaction Service → Saga: crédite destination
Transaction Service → publie sur Kafka topic 'transaction-events'
Kafka → Notification Service → envoie email/SMS
Kafka → Analytics Service → met à jour agrégats MongoDB
```

### Flux chatbot
```
Client → WebSocket /ws/chat → Gateway → Chatbot Service
Chatbot → LangChain analyse l'intent
Chatbot → Tool: getAccountBalance → GET /api/accounts/{id} → Account Service
Chatbot → Mock LLM génère réponse
Chatbot → streame réponse via SSE → Gateway → Client
```

---

## Démarrage rapide

```bash
# Cloner le repo
git clone <repo-url> && cd ebank

# Démarrer toute l'infrastructure + services
docker-compose up -d

# Vérifier la santé des services
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # Auth
curl http://localhost:8082/actuator/health  # Accounts
curl http://localhost:8083/actuator/health  # Transactions
curl http://localhost:8084/actuator/health  # Analytics
curl http://localhost:8085/actuator/health  # Cards

# S'inscrire
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@ebank.com","password":"SecurePass123!"}'

# Se connecter → récupère le JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@ebank.com","password":"SecurePass123!"}' | jq -r '.accessToken')

# Créer un compte bancaire
curl -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"ACC0000000001","accountHolderName":"Alice","email":"alice@ebank.com","phoneNumber":"0601020304","accountType":"SAVINGS","balance":1000.00,"status":"ACTIVE"}'

# Kafka UI
open http://localhost:8090

# Emails (MailHog)
open http://localhost:8025

# API Docs Gateway
open http://localhost:8080/swagger-ui.html
```

---

## Variables d'environnement

| Variable | Service | Description |
|----------|---------|-------------|
| `JWT_SECRET` | Gateway, Auth | Clé secrète HMAC-SHA256 (min 32 chars) |
| `JWT_EXPIRATION` | Auth | Durée access token en ms (défaut: 900000 = 15min) |
| `JWT_REFRESH_EXPIRATION` | Auth | Durée refresh token en ms (défaut: 604800000 = 7j) |
| `REDIS_PASSWORD` | Gateway, Auth | Mot de passe Redis |
| `SPRING_R2DBC_URL` | Accounts, Cards | URL R2DBC PostgreSQL |
| `SPRING_FLYWAY_URL` | Accounts, Cards | URL JDBC pour migrations Flyway |
| `SPRING_DATA_MONGODB_URI` | Transactions, Analytics | URI MongoDB avec auth |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Transactions, Analytics | Adresse(s) broker(s) Kafka |
| `KAFKA_BROKERS` | Notifications | Adresse(s) broker(s) Kafka (Node.js) |
| `SMTP_HOST` / `SMTP_PORT` | Notifications | Serveur SMTP (MailHog en dev) |
| `ACCOUNTS_SERVICE_URL` | Chatbot, Gateway | URL interne Account Service |

---

## Ports exposés

| Service | Port Host | Description |
|---------|-----------|-------------|
| API Gateway | 8080 | Point d'entrée unique |
| Auth Service | 8081 | Auth directe (dev only) |
| Account Service | 8082 | Comptes (dev only) |
| Transaction Service | 8083 | Transactions (dev only) |
| Analytics Service | 8084 | Analytics (dev only) |
| Card Service | 8085 | Cartes (dev only) |
| Chatbot Service | 3001 | WebSocket + SSE |
| Notification Service | 3002 | Health check |
| PostgreSQL | 5432 | DB relationnelle |
| MongoDB | 27017 | DB documents |
| Redis | 6379 | Cache |
| Kafka | 29092 | Accès local (9092 = interne Docker) |
| Kafka UI | 8090 | Dashboard Kafka |
| MailHog Web | 8025 | Emails interceptés |
| MailHog SMTP | 1025 | Serveur SMTP mock |
