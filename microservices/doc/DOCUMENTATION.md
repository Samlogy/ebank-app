# eBank — Plateforme Bancaire Microservices

> Plateforme bancaire moderne construite sur une architecture microservices polyglotte (Java + Node.js). Conçue à des fins pédagogiques (upskilling Tech Lead Junior), elle implémente les patterns de production : API Gateway, CQRS, Saga, Event Sourcing, gestion centralisée des secrets et de la configuration, et un chatbot IA intégré.

---

## Table des matières

1. [Tech Stack](#tech-stack)
2. [Structure du projet](#structure-du-projet)
3. [Architecture — Diagramme de flux](#architecture--diagramme-de-flux)
4. [Environnements](#environnements)
5. [Microservices](#microservices)
6. [Observabilité](#observabilité)
7. [Deployment](#deployment)
8. [CI/CD](#cicd)
9. [Troubleshooting](#troubleshooting)
10. [Prise en main](#prise-en-main)

---

## Tech Stack

### Backend (Java)

| Technologie | Version | Usage |
|-------------|---------|-------|
| Java | 21 (LTS) | Runtime services Java |
| Spring Boot | 4.0.3 | Framework applicatif |
| Spring Cloud Gateway | WebFlux | API Gateway réactive |
| Spring WebFlux + R2DBC | — | Services réactifs (Accounts, Transactions) |
| Spring Security + JJWT | 0.12.6 | Authentification JWT |
| Spring Cloud Config | — | Configuration centralisée |
| Spring Cloud Vault | — | Intégration secrets Vault |
| Flyway | — | Migrations base de données |
| Resilience4j | — | Circuit Breaker |
| Springdoc OpenAPI | 2.8.5 | Documentation API (Swagger) |
| Maven | 3.9 | Build tool |

### Backend (Node.js)

| Technologie | Version | Usage |
|-------------|---------|-------|
| Node.js | 20 | Runtime services Node |
| TypeScript | 5.3.3 | Langage typé |
| Express | 4.18.2 | Serveur HTTP |
| KafkaJS | 2.2.4 | Kafka consumer (Notifications) |
| LangChain.js | — | Orchestration LLM (Chatbot) |
| Nodemailer | 8.0.2 | Envoi d'emails |
| Twilio SDK | 5.3.0 | SMS |

### Frontend

| Technologie | Version | Usage |
|-------------|---------|-------|
| React | 19.0.0 | Framework UI |
| TypeScript | 5.0.0 | Langage typé |
| Vite | 6.0.0 | Build tool |
| React Router DOM | 7.0.0 | Routing SPA |
| TanStack Query | 5.0.0 | Data fetching / cache |
| Axios | 1.6.0 | Client HTTP |
| Tailwind CSS | 3.4.0 | Styling utility-first |

### Infrastructure

| Technologie | Version | Usage |
|-------------|---------|-------|
| PostgreSQL | 17-alpine | Auth, Accounts, Chatbot (pgvector) |
| MongoDB | 7-jammy | Transactions, Analytics |
| Redis | 7-alpine | Cache, Rate limiting, JWT blacklist |
| Apache Kafka | 7.6.0 (Confluent) | Event streaming |
| Zookeeper | 7.6.0 | Coordination cluster Kafka |
| HashiCorp Vault | 1.17 | Gestion des secrets |
| Docker / Docker Compose | — | Containerisation |
| Nginx | alpine | Reverse proxy frontend (prod) |
| MailHog | latest | Mock SMTP (dev uniquement) |

---

## Structure du projet

```
ebank/
├── auth/                        # Auth Service (Java/Spring Boot)
│   ├── src/main/
│   │   ├── java/com/ebank/auth/
│   │   └── resources/
│   │       ├── application*.yml          # Profils local/docker/recf/test/prod
│   │       └── db/migration/            # Flyway migrations
│   ├── pom.xml
│   └── Dockerfile
│
├── gateway/                     # API Gateway (Spring Cloud Gateway)
│   ├── src/main/
│   │   ├── java/com/ebank/gateway/
│   │   │   ├── config/GatewayConfig.java  # Définition des routes
│   │   │   ├── filter/AuthenticationFilter.java
│   │   │   ├── filter/LoggingFilter.java
│   │   │   └── util/JwtUtil.java
│   │   └── resources/application*.yml
│   ├── pom.xml
│   └── Dockerfile
│
├── accounts/                    # Accounts Service (Spring WebFlux + R2DBC)
│   ├── src/main/
│   │   ├── java/com/ebank/accounts/
│   │   └── resources/
│   │       ├── application*.yml
│   │       └── db/migration/
│   ├── pom.xml
│   └── Dockerfile
│
├── transactions/                # Transaction Service (Spring WebFlux + MongoDB)
│   ├── src/main/
│   │   ├── java/com/ebank/transactions/
│   │   └── resources/application*.yml
│   ├── pom.xml
│   └── Dockerfile
│
├── config-server/               # Config Server (Spring Cloud Config)
│   ├── src/main/
│   │   ├── java/com/ebank/config/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── config-repo/       # Fichiers de config par service/profil
│   ├── pom.xml
│   └── Dockerfile
│
├── chatbot/                     # Chatbot Service (Node.js + LangChain)
│   ├── src/
│   │   ├── langchain/             # chain.ts, tools.ts, mock-llm.ts
│   │   ├── services/api.client.ts
│   │   ├── sse/stream.ts
│   │   ├── websocket/server.ts
│   │   └── index.ts
│   ├── package.json
│   ├── tsconfig.json
│   └── Dockerfile
│
├── notifications/               # Notification Service (Node.js + KafkaJS)
│   ├── src/
│   │   ├── config/
│   │   ├── consumer/kafka.consumer.ts
│   │   ├── services/              # Email, SMS, Push
│   │   ├── types/
│   │   └── index.ts
│   ├── package.json
│   ├── tsconfig.json
│   └── Dockerfile
│
├── frontend/                    # Frontend React 19
│   ├── src/
│   │   ├── api/
│   │   ├── components/
│   │   ├── context/
│   │   ├── pages/
│   │   ├── types.ts
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── vite.config.ts
│   ├── tailwind.config.js
│   ├── package.json
│   └── Dockerfile
│
├── infra/
│   ├── postgres/init.sql        # Création des bases (auth_db, accounts_db, chatbot_db)
│   └── vault/vault-init.sh      # Seed des secrets Vault
│
├── k8s/                         # Manifests Kubernetes (placeholder)
├── doc/                         # Documentation
├── docker-compose.yml           # Environnement Docker (développement)
├── docker-compose.recf.yml      # Environnement Docker (staging)
└── .gitignore
```

---

## Architecture — Diagramme de flux

### Vue d'ensemble (flux de requête utilisateur)

```mermaid
flowchart TD
    Browser["🌐 Browser / Mobile\n(React Frontend :3000)"]

    subgraph GATEWAY["API Gateway :8080"]
        GW["Spring Cloud Gateway"]
        AUTH_FILTER["AuthenticationFilter\n(JWT Validation)"]
        LOG_FILTER["LoggingFilter"]
        CB["Circuit Breaker\n(Resilience4j)"]
        RL["Rate Limiter\n(Redis Sliding Window)"]
    end

    subgraph SERVICES["Microservices"]
        AUTH["Auth Service\n:8081\n(Spring Boot + Security)"]
        ACCOUNTS["Accounts Service\n:8082\n(Spring WebFlux + R2DBC)"]
        TRANS["Transaction Service\n:8083\n(Spring WebFlux + MongoDB)"]
        CHATBOT["Chatbot Service\n:3001\n(Node.js + LangChain)"]
        NOTIF["Notification Service\n:3002\n(Node.js + KafkaJS)"]
    end

    subgraph CONFIG["Configuration & Secrets"]
        CFG["Config Server\n:8888\n(Spring Cloud Config)"]
        VAULT["HashiCorp Vault\n:8200"]
    end

    subgraph DATA["Data Stores"]
        PG["PostgreSQL :5432\n(auth_db, accounts_db, chatbot_db)"]
        MONGO["MongoDB :27017\n(transactions_db, analytics_db)"]
        REDIS["Redis :6379\n(Cache, Sessions, Blacklist)"]
    end

    subgraph MESSAGING["Event Streaming"]
        KAFKA["Apache Kafka :9092"]
        ZK["Zookeeper :2181"]
    end

    subgraph DEV_TOOLS["Outils Dev"]
        MAILHOG["MailHog :8025\n(Mock SMTP)"]
        KAFKA_UI["Kafka UI :8090"]
    end

    Browser -->|"HTTP / WebSocket"| GW
    GW --> LOG_FILTER --> AUTH_FILTER --> RL --> CB

    CB -->|"/api/auth/**\n(public)"| AUTH
    CB -->|"/api/accounts/**\n(protected)"| ACCOUNTS
    CB -->|"/api/transactions/**\n(protected)"| TRANS
    CB -->|"/api/chat/** + /ws/chat/**\n(public)"| CHATBOT

    AUTH --> PG
    AUTH --> REDIS

    ACCOUNTS --> PG
    ACCOUNTS --> REDIS

    TRANS --> MONGO
    TRANS --> REDIS
    TRANS -->|"transaction-events"| KAFKA

    CHATBOT -->|"REST calls"| ACCOUNTS
    CHATBOT -->|"REST calls"| TRANS
    CHATBOT --> PG

    KAFKA -->|"Consomme topics"| NOTIF
    NOTIF -->|"Email (dev)"| MAILHOG
    NOTIF -->|"SMS"| TWILIO["Twilio API"]

    KAFKA --> ZK

    CFG -->|"Fournit la config"| AUTH
    CFG -->|"Fournit la config"| GW
    CFG -->|"Fournit la config"| ACCOUNTS
    CFG -->|"Fournit la config"| TRANS
    CFG --> VAULT

    KAFKA_UI -->|"Monitor"| KAFKA
```

### Flux d'authentification

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Gateway :8080
    participant A as Auth Service :8081
    participant R as Redis
    participant DB as PostgreSQL

    C->>GW: POST /api/auth/login {email, password}
    GW->>A: Forward (route publique)
    A->>DB: SELECT user WHERE email=?
    DB-->>A: User record
    A->>A: Verify BCrypt password
    A->>A: Generate Access Token (15min) + Refresh Token (7j)
    A->>R: SETEX refresh_token TTL=7j
    A-->>GW: {accessToken, refreshToken}
    GW-->>C: {accessToken, refreshToken}

    Note over C,GW: Requête protégée
    C->>GW: GET /api/accounts (Bearer token)
    GW->>GW: AuthenticationFilter.filter()
    GW->>R: EXISTS blacklist:{token} ?
    R-->>GW: false
    GW->>GW: Valide signature JWT + expiration
    GW->>GW: Injecte X-User-Id, X-User-Role headers
    GW->>A: Forward vers Accounts Service
```

### Flux d'une transaction (Saga Pattern)

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Gateway
    participant T as Transaction Service
    participant A as Accounts Service
    participant K as Kafka
    participant N as Notification Service

    C->>GW: POST /api/transactions {type:TRANSFER, from, to, amount}
    GW->>T: Forward
    T->>T: Créer transaction (status: PENDING)
    T->>A: Reserve amount on source account
    alt Solde insuffisant
        A-->>T: Erreur
        T->>T: Saga compensate → status: FAILED
        T-->>C: 400 Bad Request
    else Succès
        A-->>T: OK
        T->>A: Credit destination account
        T->>T: Update status: COMPLETED
        T->>K: Publish transaction-events
        K->>N: Consume transaction-events
        N->>N: Send email confirmation
        T-->>C: 201 Created
    end
```

---

## Environnements

Le projet dispose de 4 profils d'environnement :

| Profil | Fichier Compose | Sources de Config | Usage |
|--------|----------------|-------------------|-------|
| `local` | — (IDE local) | `application-local.yml` | Développement sur machine (sans Docker) |
| `docker` | `docker-compose.yml` | Vault `secret/ebank/{service}/docker` | Développement en containers |
| `recf` | `docker-compose.recf.yml` | Vault `secret/ebank/{service}/recf` | Staging / recette |
| `prod` | Kubernetes (k8s/) | Config Server (Git) + Vault | Production |

### Détail des différences

**local** — développement IDE
- Databases: `localhost:5432`, `localhost:27017`, `localhost:6379`
- Logs SQL activés
- Tous les endpoints Actuator exposés
- Niveau de log: `DEBUG`

**docker** — développement conteneurisé
- Databases: hostnames Docker internes (`postgres`, `mongodb`, `redis`)
- Secrets injectés depuis Vault (mode dev, in-memory)
- Kafka UI accessible sur `:8090`
- MailHog accessible sur `:8025`
- Réseau: `ebank-network`

**recf (staging)**
- Databases: endpoints externes configurés via variables d'environnement
- Vault en mode fichier (persistant)
- Pas de Kafka UI ni MailHog
- Logs: niveau `INFO`
- Réseau: `ebank-recf-network`

**prod (Kubernetes)**
- Config Server avec backend Git
- Vault en cluster (Raft)
- Logs: niveau `WARN`, pas de stack traces
- Endpoints Actuator restreints (`health`, `info`, `metrics`)
- TLS/SSL activé

### Structure des secrets Vault

```
secret/ebank/
├── auth-service/
│   ├── docker     → DB URL, JWT secret, Redis config
│   ├── recf       → Credentials staging
│   └── prod       → Credentials production
├── accounts-service/
│   ├── docker / recf / prod
├── transaction-service/
│   ├── docker / recf / prod
├── gateway/
│   ├── docker / recf / prod
└── notification-service/
    ├── docker / recf / prod
```

---

## Microservices

### 1. Config Server (`:8888`)

**Rôle :** Serveur de configuration centralisé. Fournit la configuration externalisée à tous les microservices selon leur profil actif.

**Technologies :**
- Spring Cloud Config Server
- Backends : Native (classpath), Vault (KV v2), Git (production)

**Endpoints clés :**
```
GET /{service-name}/{profile}      → Config résolue
GET /actuator/health               → Santé
```

**Choix techniques :**
| | |
|--|--|
| Backend `native` en dev | Config versionnée dans le code, pas de dépendance externe |
| Backend Vault | Secrets séparés du code source |
| Backend Git en prod | Configuration versionnable, auditée, déployable sans rebuild |

**Avantages :**
- Source unique de vérité pour toute la configuration
- Hot-reload via `/actuator/refresh` sans redémarrage
- Profils environnement gérés de façon homogène

**Inconvénients :**
- Single point of failure si pas haute disponibilité
- Latence au démarrage (chaque service appelle le config server)

**Améliorations possibles :**
- Déployer en cluster (multiple replicas) avec load balancer
- Activer le chiffrement de la configuration en transit (Git Crypt)
- Migrer vers un backend Git privé (GitLab, Gitea) en staging

---

### 2. Auth Service (`:8081`)

**Rôle :** Authentification et gestion des utilisateurs. Émet des tokens JWT, gère les sessions et le cycle de vie des tokens (refresh, logout/blacklist).

**Technologies :**
- Spring Boot 4.0.3, Spring Security
- JJWT 0.12.6 (génération/validation des JWT)
- PostgreSQL + JPA/Hibernate + Flyway
- Redis (blacklist des tokens révoqués)

**Endpoints :**
```
POST /api/auth/register     → Inscription
POST /api/auth/login        → Authentification → {accessToken, refreshToken}
POST /api/auth/refresh      → Renouvellement du token
POST /api/auth/logout       → Révocation du token
GET  /api/auth/me           → Profil de l'utilisateur courant
```

**Stratégie JWT :**
- Access token : durée 15 minutes
- Refresh token : durée 7 jours, stocké en Redis avec TTL
- Révocation : blacklist Redis (clé = token, TTL = expiration résiduelle)

**Choix techniques :**
| Choix | Justification |
|-------|--------------|
| Access token court (15min) | Limite la fenêtre d'exposition en cas de vol |
| Refresh token en Redis | Révocation possible immédiate |
| BCrypt | Standard industrie pour hachage mot de passe |
| Flyway | Migrations versionnées et reproductibles |

**Avantages :**
- Séparation des responsabilités : auth isolée des autres services
- Stateless pour les requêtes (JWT)
- Révocation immédiate possible (Redis)

**Inconvénients :**
- Pas d'OAuth2/OIDC standard (interopérabilité limitée)
- Gestion des rôles basique (`ROLE_USER`, `ROLE_ADMIN`)

**Améliorations possibles :**
- Migrer vers Keycloak ou Spring Authorization Server (OAuth2/OIDC)
- Ajouter MFA (TOTP, SMS)
- Rotation automatique des clés JWT (JWKS)
- Audit log des connexions

---

### 3. API Gateway (`:8080`)

**Rôle :** Point d'entrée unique de la plateforme. Gère le routage, la validation JWT, le rate limiting, le circuit breaker et le logging centralisé.

**Technologies :**
- Spring Cloud Gateway (WebFlux)
- Resilience4j Circuit Breaker
- Redis Reactive (rate limiting)
- JJWT 0.12.6

**Routes définies :**
```
/api/auth/**         → auth-service:8081       [PUBLIC]
/api/accounts/**     → accounts-service:8082   [AUTH requis]
/api/transactions/** → transaction-service:8083 [AUTH requis]
/ws/chat/**          → chatbot-service:3001    [PUBLIC WebSocket]
/api/chat/**         → chatbot-service:3001    [PUBLIC HTTP/SSE]
```

**Filters pipeline :**
1. `LoggingFilter` — trace de chaque requête/réponse avec ID de corrélation
2. `AuthenticationFilter` — validation JWT + injection headers (`X-User-Id`, `X-User-Role`)
3. `RateLimiter` — fenêtre glissante par user/IP via Redis
4. `CircuitBreaker` — fallback automatique si un service est down

**Choix techniques :**
| Choix | Justification |
|-------|--------------|
| WebFlux non-bloquant | Haute concurrence avec peu de threads |
| JWT validé au Gateway | Un seul point de validation, services déchargés |
| Redis pour rate limiting | Partagé entre instances, pas d'état local |
| Resilience4j | Évite les cascades de pannes (cascade failure) |

**Avantages :**
- Encapsulation complète des services internes
- Services downstream allégés (pas de logique auth)
- Observabilité centralisée (logs, métriques)

**Inconvénients :**
- Risque de goulot d'étranglement si non répliqué
- Couplage sur le secret JWT partagé avec Auth Service

**Améliorations possibles :**
- Validation JWT via appel à Auth Service (introspection) plutôt que secret partagé
- Ajouter OpenTelemetry pour tracing distribué
- mTLS entre Gateway et services internes

---

### 4. Accounts Service (`:8082`)

**Rôle :** Gestion des comptes bancaires (création, consultation, mise à jour, suppression). Implémente le pattern CQRS pour séparer les flux de lecture et d'écriture.

**Technologies :**
- Spring WebFlux (programmation réactive)
- R2DBC (accès base de données non-bloquant)
- PostgreSQL
- Flyway (migrations)
- Redis (cache réactif)

**Endpoints :**
```
GET    /api/accounts           → Liste des comptes
GET    /api/accounts/{id}      → Compte par ID
POST   /api/accounts           → Créer un compte
PUT    /api/accounts/{id}      → Modifier un compte
DELETE /api/accounts/{id}      → Supprimer un compte
```

**Pattern CQRS :**
- **Commands** : `CreateAccountCommand`, `UpdateAccountCommand`, `DeleteAccountCommand` → modifient l'état
- **Queries** : `GetAccountByIdQuery`, `GetAllAccountsQuery` → lectures seules
- Séparation permettant un scaling indépendant reads/writes

**Schéma base de données :**
```sql
accounts (
    id BIGSERIAL PK,
    account_number VARCHAR(20) UNIQUE,
    account_holder_name VARCHAR(100),
    email VARCHAR(100) UNIQUE,
    balance DECIMAL(15,2),
    account_type VARCHAR(20),   -- SAVINGS, CHECKING, ...
    status VARCHAR(10),         -- ACTIVE, INACTIVE
    version BIGINT,             -- Optimistic locking
    created_at, updated_at TIMESTAMP
)
```

**Choix techniques :**
| Choix | Justification |
|-------|--------------|
| WebFlux + R2DBC | Stack 100% non-bloquant, meilleure utilisation CPU/mémoire |
| CQRS | Flexibilité de scaling lecture/écriture indépendante |
| Optimistic locking (`version`) | Évite les écritures conflictuelles sans lock BD |
| Redis cache | Réduction de la charge PostgreSQL sur les lectures |

**Avantages :**
- Haute concurrence sans augmenter les threads
- Lecture cachée, écriture transactionnelle

**Inconvénients :**
- Complexité accrue (paradigme réactif moins lisible)
- R2DBC moins mature que JDBC (moins de tooling)
- CQRS peut être overkill pour un service simple

**Améliorations possibles :**
- Ajouter Event Sourcing complet (stocker chaque mutation comme événement)
- Read replicas PostgreSQL pour le query side
- Invalidation du cache par événement Kafka

---

### 5. Transaction Service (`:8083`)

**Rôle :** Enregistrement et gestion des transactions financières (dépôts, retraits, virements, paiements). Implémente le pattern Saga pour les virements distribués et publie des événements sur Kafka.

**Technologies :**
- Spring WebFlux (réactif)
- MongoDB Reactive (schéma flexible)
- Apache Kafka (producteur d'événements)
- Redis (cache)

**Endpoints :**
```
GET  /api/transactions              → Toutes les transactions
GET  /api/transactions/{id}         → Transaction par ID
GET  /api/transactions/account/{id} → Transactions d'un compte
POST /api/transactions              → Créer une transaction
```

**Types de transactions :** `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `PAYMENT`

**Pattern Saga (pour les virements) :**
1. Réserve le montant sur le compte source
2. Crédite le compte destination
3. En cas d'échec : transaction compensatrice (rollback)
4. Publish event `transaction-events` sur Kafka

**Topics Kafka produits :**
| Topic | Consommateurs |
|-------|--------------|
| `transaction-events` | Notification Service, Analytics |

**Choix techniques :**
| Choix | Justification |
|-------|--------------|
| MongoDB | Schéma flexible, historique immuable, bonne écriture |
| Saga pattern | Transactions distribuées sans 2PC (distributed lock) |
| Kafka publish | Découplage entre transaction et notification |
| WebFlux | Cohérence avec le reste du backend réactif |

**Avantages :**
- Transactions immuables (audit trail naturel)
- Découplage fort grâce aux événements Kafka
- Scalabilité horizontale de MongoDB

**Inconvénients :**
- Pas de transactions ACID native MongoDB (avant v4)
- Saga complexifie le debugging en cas d'échec partiel
- Schéma flexible peut mener à des incohérences

**Améliorations possibles :**
- Utiliser MongoDB transactions (v4+) pour la cohérence locale
- Implémenter Outbox Pattern (garantie exactly-once delivery)
- Ajouter un audit service pour la conformité réglementaire (PSD2)
- Tableau de bord analytique en temps réel (Kafka Streams)

---

### 6. Chatbot Service (`:3001`)

**Rôle :** Assistant IA bancaire. Répond aux questions des utilisateurs sur leurs comptes et transactions via HTTP, WebSocket ou Server-Sent Events. Orchestré par LangChain avec outil calling.

**Technologies :**
- Node.js 20 + TypeScript
- Express (HTTP + SSE)
- LangChain.js (orchestration LLM)
- WebSocket (`ws`)
- PostgreSQL + pgvector (embeddings RAG)

**Endpoints :**
```
POST /api/chat/message    → Chat HTTP request/response
WS   /ws/chat             → Streaming WebSocket
GET  /api/chat/stream     → Server-Sent Events
GET  /health              → Santé du service
```

**Outils LLM (Tool Calling) :**
```typescript
getAccountBalance(accountId)
  → GET /api/accounts/{id}/balance    // Appelle Accounts Service

getRecentTransactions(accountId, limit)
  → GET /api/transactions/account/{id} // Appelle Transaction Service
```

**Flux d'exécution LangChain :**
1. Construction du contexte (system prompt + historique)
2. Appel LLM → peut inclure des tool calls
3. Exécution des outils (si demandé)
4. Réponse LLM enrichie des résultats d'outils
5. Streaming token par token (SSE ou WS)

**Choix techniques :**
| Choix | Justification |
|-------|--------------|
| LangChain.js | Abstraction LLM interchangeable (mock → GPT-4 → Claude) |
| SSE + WebSocket | Deux modes de streaming selon le client |
| pgvector | RAG contextuel sur les données bancaires |
| Mock LLM en dev | Développement sans dépendance à une API LLM payante |

**Avantages :**
- LLM swappable sans changer le code métier
- Streaming natif pour UX fluide
- RAG permet des réponses contextualisées

**Inconvénients :**
- LLM mock limité (pas de vraie compréhension)
- Sécurité des tool calls (accès aux données sensibles)
- Coût en production si LLM externe (tokens)

**Améliorations possibles :**
- Intégrer un vrai LLM (Claude API, OpenAI)
- Guardrails de sécurité (PII masking, validation des outputs)
- Mémoire conversationnelle persistante
- Cache sémantique des réponses (embedding similarity)

---

### 7. Notification Service (`:3002`)

**Rôle :** Consommateur d'événements Kafka. Envoie des notifications multicanal (email, SMS, push) en réaction aux événements métier (transactions, alertes, sécurité).

**Technologies :**
- Node.js 20 + TypeScript
- KafkaJS 2.2.4 (consommateur Kafka)
- Nodemailer 8.0.2 (email)
- Twilio SDK 5.3.0 (SMS)
- MailHog (mock SMTP dev)

**Topics Kafka consommés :**
| Topic | Déclencheur | Action |
|-------|-------------|--------|
| `transaction-events` | Virement/dépôt complété | Email de confirmation |
| `notification-events` | Événement générique | Email/SMS selon type |
| `card-events` | Blocage de carte | SMS d'alerte urgente |

**Channels de notification :**
- **Email** : Nodemailer → MailHog (dev), SendGrid/SES (prod)
- **SMS** : Twilio (mock en dev via numéro test)
- **Push** : FCM (placeholder, prod)

**Choix techniques :**
| Choix | Justification |
|-------|--------------|
| Découplage via Kafka | Notification n'est jamais bloquante pour le service métier |
| KafkaJS | Client Kafka natif Node.js, sans dépendance Java |
| MailHog en dev | Intercepte tous les emails, pas de risque d'envoi accidentel |
| Graceful shutdown | Arrêt propre du consumer Kafka sur SIGTERM |

**Avantages :**
- Complètement découplé des services métier
- Multi-canal extensible facilement
- Retry automatique en cas d'échec de consommation

**Inconvénients :**
- Au moins une fois (at-least-once delivery) → risque de doublon
- Pas de dead letter queue (DLQ) configurée
- Templates d'emails non versionnés

**Améliorations possibles :**
- Idempotency key pour éviter les doublons de notifications
- Dead Letter Queue (DLQ) pour les messages en échec
- Templates d'emails versionnés (Handlebars, MJML)
- Dashboard de monitoring des envois (SendGrid analytics)

---

### 8. Frontend (`:3000` dev / `:80` prod)

**Rôle :** Interface utilisateur SPA (Single Page Application) permettant aux clients de consulter leurs comptes, effectuer des transactions et interagir avec le chatbot.

**Technologies :**
- React 19 + TypeScript
- Vite (build ultra-rapide)
- React Router DOM (routing)
- TanStack Query (fetching + cache client)
- Axios (HTTP)
- Tailwind CSS (styling)
- Nginx (serve statique en production)

**Proxy dev (vite.config.ts) :**
```
/api  → http://localhost:8080   (Gateway)
/ws   → ws://localhost:8080     (WebSocket chatbot)
```

**Choix techniques :**
| Choix | Justification |
|-------|--------------|
| React 19 | Dernière version stable, Server Components ready |
| TanStack Query | Cache client déclaratif, évite la gestion manuelle d'état async |
| Vite | Build/HMR beaucoup plus rapide que CRA/Webpack |
| Nginx multi-stage | Image finale légère (~20MB) |

**Avantages :**
- Expérience développeur excellente (HMR instant)
- Cache de données intelligent (TanStack Query)
- Build optimisé (code splitting automatique Vite)

**Inconvénients :**
- Pas de SSR (SEO limité, chargement initial)
- Gestion d'état complexe sans Redux/Zustand si l'app grandit

**Améliorations possibles :**
- Migrer vers Next.js pour SSR/SSG
- Ajouter tests E2E (Playwright)
- Internationalisation (i18n)
- PWA (mode offline)

---

## Observabilité

### Health Checks

Tous les services exposent un endpoint de santé :

| Service | URL |
|---------|-----|
| Gateway | `GET http://localhost:8080/actuator/health` |
| Auth | `GET http://localhost:8081/actuator/health` |
| Accounts | `GET http://localhost:8082/actuator/health` |
| Transactions | `GET http://localhost:8083/actuator/health` |
| Config Server | `GET http://localhost:8888/actuator/health` |
| Chatbot | `GET http://localhost:3001/health` |
| Notifications | `GET http://localhost:3002/health` (métriques : `GET http://localhost:3002/metrics`) |

### Spring Actuator

Endpoints disponibles (profil `docker`) :

```
/actuator/health     → Statut du service + dépendances (DB, Redis, Kafka)
/actuator/info       → Métadonnées (version, build)
/actuator/metrics    → Métriques JVM (heap, GC, threads, HTTP)
/actuator/env        → Toutes les propriétés résolues
/actuator/refresh    → Hot-reload de la configuration (sans redémarrage)
```

> En production, seuls `health`, `info` et `metrics` sont exposés.

### API Documentation (Swagger/OpenAPI)

| Service | API Docs | Swagger UI |
|---------|----------|------------|
| Auth | `http://localhost:8081/auth-api-docs` | `http://localhost:8081/auth-api-ui` |
| Accounts | `http://localhost:8082/accounts-api-docs` | `http://localhost:8082/accounts-api-ui` |
| Transactions | `http://localhost:8083/transactions-api-docs` | `http://localhost:8083/transactions-api-ui` |

### Kafka UI

Dashboard de monitoring des topics et consumers :

```
http://localhost:8090
```

Fonctionnalités :
- Visualisation des topics et partitions
- Consumer groups et offsets (lag)
- Produire des messages de test
- Inspecter les messages

### MailHog (Dev uniquement)

Interception des emails envoyés par le Notification Service :

```
http://localhost:8025
```

### Logging

- **Format :** JSON structuré (recommandé en production)
- **Corrélation :** Le Gateway injecte un `X-Trace-Id` dans chaque requête → propagé dans les logs de chaque service
- **Niveaux :** `DEBUG` (local) → `INFO` (docker/recf) → `WARN` (prod)

### Métriques, tracing & logs centralisés

La stack d'observabilité tourne par défaut avec `docker-compose.yml` — pas d'étape d'activation à faire :

```
Prometheus  →  Scrape /actuator/prometheus (Java) et /metrics (Node) toutes les 15s
Grafana     →  http://localhost:3000 — dashboards + datasources Prometheus/Tempo/Loki auto-provisionnés
Tempo       →  Traces distribuées OTLP (Micrometer Tracing côté Java, OpenTelemetry SDK côté Node)
Loki        →  Logs par défaut (labels bas-cardinalité, requêtable en LogQL)
```

| Service | Métriques | Traces (OTLP → Tempo `:4318`) |
|---|---|---|
| gateway / auth / accounts / transaction-service | `/actuator/prometheus` (Micrometer) | `micrometer-tracing-bridge-otel` |
| notification-service | `/metrics` (`prom-client`) | `@opentelemetry/sdk-node` + auto-instrumentations |

Chaque log de chaque service porte `traceId`/`spanId` — dans Grafana, un clic sur un span dans Tempo ouvre directement les logs corrélés dans Loki.

**ELK (recherche full-text, optionnel) :** Filebeat lit les logs stdout des 5 conteneurs applicatifs (`*_service`) et les envoie à Logstash → Elasticsearch → Kibana, en plus de Loki — aucune modification de code nécessaire (les services logguent déjà en JSON/logfmt sur stdout).

```bash
docker compose -f docker-compose.yml -f docker-compose.elk.yml up -d
# → Kibana: http://localhost:5601   (index pattern: ebank-microservices-*)
```

**Loki vs ELK :** Loki est plus léger (index uniquement les labels) et suffit pour le dev/staging corrélé aux métriques/traces. ELK apporte la recherche plein texte et les agrégations — utile pour l'audit/compliance, au prix d'un cluster Elasticsearch à opérer.

---

## Deployment

### Architecture de déploiement

```
[Dev]       docker-compose.yml         → machines locales
[Staging]   docker-compose.recf.yml    → serveur recette
[Prod]      k8s/ (manifests à écrire)  → cluster Kubernetes
```

### Docker Compose (Dev / Staging)

**Ordre de démarrage (dépendances `depends_on`) :**

```
vault
  └─→ vault-init
        └─→ config-server
              └─→ gateway, auth, accounts, transactions, chatbot, notifications
```

Parallèlement, les services d'infrastructure démarrent :
```
postgres, mongodb, redis, zookeeper
  └─→ kafka
        └─→ kafka-ui, transactions, notifications
```

**Commandes :**
```bash
# Démarrer tout
docker-compose up -d

# Démarrer avec rebuild des images
docker-compose up -d --build

# Voir les logs d'un service
docker-compose logs -f accounts

# Redémarrer un service sans toucher les autres
docker-compose restart accounts

# Stopper et supprimer les volumes
docker-compose down -v
```

### Kubernetes (Production — À implémenter)

Le répertoire `k8s/` est actuellement vide. Structure recommandée :

```
k8s/
├── namespace.yaml
├── configmaps/
├── secrets/          # Via External Secrets Operator + Vault
├── deployments/
│   ├── gateway.yaml
│   ├── auth.yaml
│   ├── accounts.yaml
│   ├── transactions.yaml
│   ├── config-server.yaml
│   ├── chatbot.yaml
│   └── notifications.yaml
├── services/
├── ingress/
│   └── nginx-ingress.yaml
└── hpa/              # Horizontal Pod Autoscaler
```

### Images Docker

Toutes les images utilisent un build **multi-stage** pour minimiser la taille finale :

| Service | Base Build | Base Runtime | Taille approx. |
|---------|-----------|-------------|---------------|
| Java services | `maven:3.9-eclipse-temurin-21-alpine` | `eclipse-temurin:21-jre-alpine` | ~150MB |
| Node.js services | `node:20-alpine` | `node:20-alpine` | ~100MB |
| Frontend | `node:20-alpine` | `nginx:alpine` | ~25MB |

---

## CI/CD

### GitHub Actions (actuel)

Le projet dispose d'un workflow GitHub Actions (`java-upgrade`) pour la mise à jour des dépendances Java.

**État actuel :** Pipeline minimal — CI complète à implémenter.

### Pipeline recommandé

```yaml
# .github/workflows/ci.yml

name: CI Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test-java:
    strategy:
      matrix:
        service: [auth, gateway, accounts, transactions, config-server]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21' }
      - run: cd ${{ matrix.service }} && ./mvnw test

  test-node:
    strategy:
      matrix:
        service: [chatbot, notifications, frontend]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: cd ${{ matrix.service }} && npm ci && npm test

  build-and-push:
    needs: [test-java, test-node]
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Build & push Docker images
        uses: docker/build-push-action@v5
        with:
          push: true
          tags: ghcr.io/org/ebank-${{ matrix.service }}:${{ github.sha }}

  deploy-staging:
    needs: build-and-push
    environment: recf
    steps:
      - run: docker-compose -f docker-compose.recf.yml up -d --pull always
```

### Stratégies de déploiement

| Environnement | Stratégie | Rollback |
|--------------|-----------|---------|
| Dev | `docker-compose up --build` | Manuel |
| Staging (recf) | Rolling update via Compose | `docker-compose up` image précédente |
| Production (k8s) | Rolling update (Kubernetes) | `kubectl rollout undo` |

---

## Troubleshooting

### Problèmes fréquents

#### Services qui ne démarrent pas

```bash
# 1. Vérifier l'ordre de démarrage — Vault doit être UP avant Config Server
docker-compose ps
docker-compose logs vault
docker-compose logs config-server

# 2. Vérifier que Vault est initialisé
curl http://localhost:8200/v1/sys/health
# Doit retourner: {"initialized": true, "sealed": false, ...}

# 3. Forcer le redémarrage dans l'ordre
docker-compose restart vault-init
docker-compose restart config-server
docker-compose restart auth accounts transactions gateway
```

#### Erreur `Failed to configure a DataSource`

```bash
# PostgreSQL pas encore prêt
docker-compose logs postgres
# Attendre que postgres affiche: "database system is ready to accept connections"

# Ou forcer le redémarrage du service applicatif
docker-compose restart auth
```

#### JWT invalide / 401 Unauthorized

```bash
# Vérifier que le secret JWT est identique dans auth-service et gateway
curl -s http://localhost:8200/v1/secret/data/ebank/auth-service/docker \
  -H "X-Vault-Token: root" | jq '.data.data["jwt.secret"]'

curl -s http://localhost:8200/v1/secret/data/ebank/gateway/docker \
  -H "X-Vault-Token: root" | jq '.data.data["jwt.secret"]'
# Les deux doivent être identiques
```

#### Kafka — Consumer lag élevé

```bash
# Vérifier le consumer group
docker exec -it ebank-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group notification-group

# Réinitialiser les offsets (ATTENTION: perd les messages non traités)
docker exec -it ebank-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group notification-group \
  --topic transaction-events \
  --reset-offsets --to-latest --execute
```

#### Redis — Erreur d'authentification

```bash
# Tester la connexion Redis
docker exec -it ebank-redis redis-cli -a redispass ping
# Doit retourner: PONG

# Vider le cache (dev uniquement)
docker exec -it ebank-redis redis-cli -a redispass FLUSHALL
```

#### Comptes / Transactions inaccessibles (403 Forbidden)

```bash
# 1. Vérifier que le token n'est pas expiré (durée 15min)
# 2. Vérifier les headers transmis par le Gateway
docker-compose logs gateway | grep "X-User-Id"

# 3. Tester directement le service (bypass Gateway)
curl http://localhost:8082/api/accounts \
  -H "X-User-Id: 1" \
  -H "X-User-Role: ROLE_USER"
```

#### Hot-reload de config échoue

```bash
# Déclencher le refresh sur un service
curl -X POST http://localhost:8081/actuator/refresh

# Vérifier que la config a bien été rechargée
curl http://localhost:8081/actuator/env | jq '.'
```

#### Frontend ne se connecte pas au backend

```bash
# Vérifier que le proxy Vite est bien configuré (vite.config.ts)
# En dev, les requêtes /api/* doivent être proxiées vers localhost:8080

# Vérifier que le Gateway est UP
curl http://localhost:8080/actuator/health

# Inspecter les erreurs CORS dans les devtools du navigateur
```

### Logs utiles

```bash
# Tous les services en temps réel
docker-compose logs -f

# Uniquement un service
docker-compose logs -f gateway

# Filtrer les erreurs
docker-compose logs gateway 2>&1 | grep -i error

# Accéder au shell d'un container
docker exec -it ebank-auth sh
docker exec -it ebank-postgres psql -U postgres -d auth_db
docker exec -it ebank-mongodb mongosh -u mongo -p mongopass
```

---

## Prise en main

### Prérequis

| Outil | Version minimale | Vérification |
|-------|-----------------|-------------|
| Docker | 24+ | `docker --version` |
| Docker Compose | 2.20+ | `docker compose version` |
| Git | 2.x | `git --version` |
| (optionnel) Java | 21 | `java -version` |
| (optionnel) Node.js | 20 | `node --version` |
| (optionnel) jq | any | `jq --version` |

### Démarrage rapide (Docker)

```bash
# 1. Cloner le projet
git clone <url-du-repo> ebank
cd ebank

# 2. Démarrer tous les services
docker-compose up -d

# 3. Attendre ~60 secondes que tous les services soient prêts
# Suivre l'avancement :
docker-compose ps

# 4. Vérifier la santé des services
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8081/actuator/health   # Auth
curl http://localhost:8082/actuator/health   # Accounts
curl http://localhost:8083/actuator/health   # Transactions
curl http://localhost:3002/health            # Notifications
```

### Créer un utilisateur et tester l'API

```bash
# 1. Inscription
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "Secret123!"
  }'

# 2. Connexion → récupérer le token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Secret123!"}' \
  | jq -r '.accessToken')

echo "Token: $TOKEN"

# 3. Créer un compte bancaire
curl -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC001",
    "accountHolderName": "Alice Martin",
    "email": "alice@example.com",
    "accountType": "SAVINGS",
    "balance": 5000.00
  }'

# 4. Consulter les comptes
curl http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN"

# 5. Créer une transaction
curl -X POST http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "DEPOSIT",
    "toAccountId": 1,
    "amount": 1000.00,
    "description": "Virement initial"
  }'
```

### Accès aux interfaces web

| Interface | URL | Credentials |
|-----------|-----|-------------|
| Frontend React | http://localhost:3000 | (créer un compte) |
| Swagger Auth | http://localhost:8081/auth-api-ui | — |
| Swagger Accounts | http://localhost:8082/accounts-api-ui | — |
| Swagger Transactions | http://localhost:8083/transactions-api-ui | — |
| Kafka UI | http://localhost:8090 | — |
| MailHog (emails) | http://localhost:8025 | — |
| Vault UI | http://localhost:8200/ui | Token: `root` |

### Développement local (sans Docker)

Pour développer un service en local tout en utilisant l'infrastructure Docker :

```bash
# 1. Démarrer uniquement l'infrastructure
docker-compose up -d postgres mongodb redis kafka zookeeper vault vault-init config-server

# 2. Lancer un service Java en local
cd auth
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 3. Ou lancer un service Node.js en local
cd notifications
npm install
npm run dev   # ou: ts-node src/index.ts
```

### Variables d'environnement pour staging (recf)

Créer un fichier `.env` (non commité) :

```env
DB_PASSWORD=votre_mot_de_passe
MONGODB_URI=mongodb://user:pass@host:27017/transactions_db
REDIS_PASSWORD=votre_redis_password
JWT_SECRET=votre_jwt_secret_min_32_chars
KAFKA_BROKERS=kafka-host:9092
VAULT_ADDR=http://vault-host:8200
VAULT_TOKEN=votre_vault_token
```

Puis :
```bash
docker-compose -f docker-compose.recf.yml up -d
```

### Résumé des ports

| Service | Port | Protocole |
|---------|------|-----------|
| Frontend | 3000 | HTTP |
| Gateway | 8080 | HTTP |
| Auth Service | 8081 | HTTP |
| Accounts Service | 8082 | HTTP |
| Transaction Service | 8083 | HTTP |
| Config Server | 8888 | HTTP |
| Chatbot Service | 3001 | HTTP + WS |
| Notification Service | 3002 | HTTP |
| PostgreSQL | 5432 | TCP |
| MongoDB | 27017 | TCP |
| Redis | 6379 | TCP |
| Kafka (interne) | 9092 | TCP |
| Kafka (externe) | 29092 | TCP |
| Zookeeper | 2181 | TCP |
| Vault | 8200 | HTTP |
| Kafka UI | 8090 | HTTP |
| MailHog SMTP | 1025 | SMTP |
| MailHog Web | 8025 | HTTP |

---

*Documentation générée automatiquement — eBank v1.0.0*
