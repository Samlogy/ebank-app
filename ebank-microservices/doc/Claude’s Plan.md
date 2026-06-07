Claude’s Plan
Plan : eBank — Corrections finales + Frontend React 19
Context
Le projet est quasi-complet (8 microservices créés). Il reste 2 tâches bloquantes :

Bug critique — GatewayConfig.java a un .build() en double qui empêche la compilation
Frontend React — non encore créé
État actuel du projet
✅ Microservices créés et corrects
Service	Port	Status
Auth Service	8081	✅ complet (JWT + Redis blacklist + Flyway)
Accounts Service	8082	✅ refactorisé WebFlux + R2DBC + CQRS
Transactions Service	8083	✅ complet (WebFlux + MongoDB + Kafka)
Analytics Service	8084	✅ complet (MVC + MongoDB + Kafka consumer)
Cards Service	8085	✅ complet (WebFlux + R2DBC)
Chatbot Service	3001	✅ complet (Node.js + LangChain mock + WebSocket + SSE)
Notifications Service	3002	✅ complet (Node.js + KafkaJS + email/SMS/push mocks)
API Gateway	8080	❌ bug compilation (voir ci-dessous)
✅ Migrations SQL corrigées
accounts : colonne version BIGINT ajoutée (requis par @Version sur l'entité)
cards : CREATE TYPE PostgreSQL supprimés → VARCHAR pur (requis par R2DBC)
❌ Bug GatewayConfig.java (compilation error)
Fichier : gateway/src/main/java/com/ebank/gateway/config/GatewayConfig.java

Le fichier contient deux .build() consécutifs avec du code orphelin entre eux. Le premier .build() ferme la chaîne de routes, puis des .route(...) supplémentaires apparaissent après — Java refuse de compiler ce code.

Fix : Supprimer l'ancien .route("chatbot-route"...) + premier .build(), garder uniquement les nouvelles routes chatbot-ws-route et chatbot-http-route avec un seul .build() final.

❌ Frontend React — non créé
Doit être un projet React 19 + Vite + TypeScript simple qui consomme le backend via le Gateway (port 8080).

Plan de correction + frontend
Étape 1 — Fix GatewayConfig.java
Remplacer le corps de gatewayRoutes() pour avoir un seul .build() avec toutes les routes :


.route("auth-route",           /api/auth/**)           → auth-service (public)
.route("accounts-route",       /api/accounts/**)       → accounts-service (JWT)
.route("transactions-route",   /api/transactions/**)   → transaction-service (JWT)
.route("analytics-route",      /api/analytics/**)      → analytics-service (JWT)
.route("cards-route",          /api/cards/**)          → card-service (JWT)
.route("chatbot-ws-route",     /ws/chat/**)            → chatbot-service (public)
.route("chatbot-http-route",   /api/chat/**)           → chatbot-service (public)
.build()
Étape 2 — Créer frontend/ React 19 + Vite
Stack : React 19, TypeScript, Vite 6, React Router v7, TanStack Query v5, Tailwind CSS v4

Structure :


frontend/
├── Dockerfile
├── package.json
├── vite.config.ts           (proxy /api → http://localhost:8080)
├── tailwind.config.ts
└── src/
    ├── main.tsx
    ├── App.tsx              (Router + Layout)
    ├── api/                 (clients HTTP centralisés)
    │   ├── auth.ts          (register, login, logout, me)
    │   ├── accounts.ts      (CRUD accounts)
    │   ├── transactions.ts  (list, create)
    │   └── cards.ts         (list, block/unblock)
    ├── context/
    │   └── AuthContext.tsx  (token JWT en mémoire, user courant)
    ├── components/
    │   ├── Layout.tsx       (sidebar nav + header)
    │   ├── ProtectedRoute.tsx
    │   └── ChatWidget.tsx   (WebSocket chatbot, messages streaming SSE)
    └── pages/
        ├── LoginPage.tsx    (form login + register)
        ├── DashboardPage.tsx (solde + stats rapides)
        ├── AccountsPage.tsx  (liste + CRUD)
        ├── TransactionsPage.tsx (liste + formulaire virement)
        ├── CardsPage.tsx     (liste cartes + block/unblock)
        └── AnalyticsPage.tsx (résumés + métriques top comptes)
Fonctionnalités clés :

Auth JWT (login/register/logout, token en mémoire + localStorage pour refresh)
Dashboard avec solde du compte et transactions récentes
CRUD comptes, liste transactions, gestion cartes (block/unblock)
Widget chatbot (WebSocket → envoie message, reçoit réponse)
Proxy Vite vers Gateway 8080 (évite CORS en dev)
Endpoints disponibles (récapitulatif complet)
via Gateway (port 8080)

POST /api/auth/register          → inscription
POST /api/auth/login             → connexion → JWT
POST /api/auth/refresh           → refresh token
POST /api/auth/logout            → révocation token
GET  /api/auth/me                → utilisateur courant

GET  /api/accounts               → liste comptes
POST /api/accounts               → créer compte
GET  /api/accounts/{id}          → détail compte
PUT  /api/accounts/{id}          → modifier compte
DELETE /api/accounts/{id}        → supprimer compte

GET  /api/transactions           → liste transactions
POST /api/transactions           → créer transaction
GET  /api/transactions/{id}      → détail transaction
GET  /api/transactions/account/{accountId} → transactions d'un compte

GET  /api/cards                  → liste cartes
POST /api/cards                  → créer carte
GET  /api/cards/{id}             → détail carte
PUT  /api/cards/{id}             → modifier limites
POST /api/cards/{id}/block       → bloquer carte
POST /api/cards/{id}/unblock     → débloquer carte
DELETE /api/cards/{id}           → supprimer carte

GET  /api/analytics/summary      → résumé global
GET  /api/analytics/summaries    → tous les résumés temporels
GET  /api/analytics/summaries/{type}/{period} → résumé par période
GET  /api/analytics/accounts/top → top comptes par volume

WS   ws://localhost:8080/ws/chat → chatbot WebSocket
Commandes de lancement

# 1. Lancer toute l'infra + services
cd /home/sam/Desktop/ebank
docker-compose up -d

# 2. Attendre ~60s que tout démarre, puis vérifier la santé
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8081/actuator/health   # Auth
curl http://localhost:8082/actuator/health   # Accounts
curl http://localhost:8083/actuator/health   # Transactions
curl http://localhost:8084/actuator/health   # Analytics
curl http://localhost:8085/actuator/health   # Cards
curl http://localhost:3001/health            # Chatbot
curl http://localhost:3002/health            # Notifications

# 3. Test flux complet via Gateway
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@ebank.com","password":"Password123!"}'

# Récupérer le JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@ebank.com","password":"Password123!"}' | jq -r '.accessToken')

# Créer un compte bancaire
curl -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"ACC0000000001","accountHolderName":"Alice","email":"alice@bank.com","accountType":"SAVINGS","balance":1000.00,"status":"ACTIVE"}'

# 4. Lancer le frontend en dev
cd frontend && npm install && npm run dev   # → http://localhost:5173

# 5. Dashboards utiles
open http://localhost:8090    # Kafka UI
open http://localhost:8025    # MailHog (emails de notification)
Fichiers à modifier/créer
Fichier	Action
gateway/src/.../GatewayConfig.java	✏️ Fix double .build()
frontend/ (tout le dossier)	➕ Créer projet React 19
Architecture cible
Service	Tech	Port	Base de données
API Gateway	Spring Cloud Gateway (WebFlux)	8080	—
Auth Service	Spring Boot + Security + JWT	8081	PostgreSQL (auth_db)
Account Service (refactorisé)	Spring WebFlux + R2DBC	8082	PostgreSQL (accounts_db)
Transaction Service	Spring WebFlux + MongoDB	8083	MongoDB
Analytics Service	Spring Boot MVC	8084	MongoDB
Card Service	Spring WebFlux + R2DBC	8085	PostgreSQL (cards_db)
Chatbot Service	Node.js + LangChain (mock)	3001	pgvector (PostgreSQL)
Notification Service	Node.js + KafkaJS	3002	—
Infrastructure : PostgreSQL, MongoDB, Redis, Kafka + Zookeeper, Kafka UI

Décisions techniques justifiées
1. Spring Boot 4.0.3 + Java 21
✅ Virtual threads (Project Loom) pour concurrence légère
✅ Pattern matching, records, sealed classes
✅ LTS, support long terme
❌ Certaines libs tierces pas encore 100% compatibles (ex : JavaMelody retiré des services WebFlux)
2. Spring Cloud Gateway (WebFlux) pour l'API Gateway
✅ Non-bloquant, réactif, faible consommation de threads
✅ Intégration native Spring (Circuit Breaker, JWT Filter, Rate Limiting via Redis)
✅ Configuration déclarative YAML + programmatique Java
❌ Plus complexe qu'un Nginx ou Kong pour les équipes non-Spring
❌ Pas de dashboard visuel natif (besoin de Spring Admin ou Grafana)
3. Spring WebFlux + R2DBC (Account, Transaction, Card Services)
✅ Modèle non-bloquant → traite des milliers de requêtes avec peu de threads
✅ Backpressure naturelle (Reactor Flux/Mono)
✅ Cohérence du stack réactif end-to-end (Gateway → Service → DB)
❌ Courbe d'apprentissage steep (reactive streams, operators)
❌ Debugging complexe (stack traces réactives)
❌ R2DBC moins mature que JPA (pas de relations automatiques, pas de lazy loading)
4. CQRS pour Account Service
✅ Séparation claire lecture/écriture → scalabilité indépendante
✅ Lisibilité et testabilité du code (command handlers séparés des query handlers)
✅ Base pour Event Sourcing futur
❌ Complexité accrue pour un CRUD simple
❌ Risque de sur-ingénierie pour les petits volumes
5. JWT + Redis pour Auth
✅ Stateless → scalabilité horizontale facile
✅ Révocation possible via Redis blacklist (logout, sécurité compromise)
✅ Refresh token pattern évite les re-auth fréquentes
❌ JWT payload visible en Base64 (ne jamais y mettre de secrets)
❌ Dépendance Redis pour la révocation
6. MongoDB pour Transactions et Analytics
✅ Schéma flexible (metadata de transaction variable)
✅ Aggregation Pipeline puissant pour analytics
✅ Volume élevé d'écritures (transactions bancaires)
❌ Transactions ACID multi-documents plus complexes (MongoDB 4+ le supporte)
❌ Jointures moins naturelles qu'en SQL
7. Apache Kafka pour l'async
✅ Garantie de livraison (at-least-once / exactly-once)
✅ Event replay pour audit trail
✅ Découplage total entre services (Transaction → Notification, Transaction → Analytics)
❌ Complexité opérationnelle (Zookeeper, partitions, consumer groups)
❌ Overhead pour faibles volumes (overkill par rapport à RabbitMQ)
8. Node.js pour Chatbot et Notification
✅ LangChain.js natif et mature pour le chatbot
✅ Streaming SSE naturel à l'event-loop
✅ KafkaJS léger pour le consumer de notifications
✅ Partage TypeScript avec le frontend Angular
❌ Moins de types stricts qu'en Java
❌ Écosystème enterprise moins mature
9. pgvector pour le RAG du Chatbot
✅ Extension PostgreSQL → pas de service supplémentaire
✅ Recherche par similarité vectorielle (cosine, L2)
❌ Performances inférieures à Pinecone/Weaviate à grande échelle
❌ Limite de dimensions (max 2000 par défaut)
10. Polyglot Persistence
✅ Outil adapté au problème (PostgreSQL ACID pour comptes, MongoDB pour logs)
✅ Performances optimales par use case
❌ Complexité opérationnelle accrue (plusieurs DBs à maintenir)
❌ Cohérence distribuée à gérer manuellement
Fichiers critiques à créer/modifier
A. docker-compose.yml (MODIFIER)
Décommenter Redis, Kafka, Zookeeper. Ajouter :

gateway-service (8080)
auth-service (8081) + auth-db (PostgreSQL avec 3 databases via init script)
accounts-service refactorisé (8082) — port change 8081→8082
transaction-service (8083) + mongodb (27017)
analytics-service (8084)
card-service (8085)
chatbot-service (3001)
notification-service (3002)
kafka-ui (8090)
B. gateway/ (NOUVEAU)

gateway/
├── Dockerfile
├── pom.xml                          (spring-cloud-starter-gateway, resilience4j, redis)
└── src/main/java/com/ebank/gateway/
    ├── GatewayApplication.java
    ├── config/
    │   ├── GatewayRoutesConfig.java  (routes vers tous les services)
    │   └── SecurityConfig.java       (filtre JWT global)
    ├── filter/
    │   ├── AuthenticationFilter.java (valide JWT sur routes protégées)
    │   └── LoggingFilter.java        (log request/response + traceId)
    └── util/JwtUtil.java             (parsing JWT sans Spring Security)
└── src/main/resources/application.yml
C. auth/ (NOUVEAU)

auth/
├── Dockerfile
├── pom.xml                          (web, security, jpa, jjwt, postgresql, flyway, redis)
└── src/main/java/com/ebank/auth/
    ├── AuthApplication.java
    ├── api/
    │   ├── User.java                 (@Entity: id, username, email, password, roles, active)
    │   ├── UserRepository.java
    │   ├── AuthController.java       (/api/auth/register, /login, /refresh, /logout)
    │   └── AuthService.java
    ├── config/
    │   ├── SecurityConfig.java       (PasswordEncoder, AuthManager, CORS)
    │   └── RedisConfig.java          (pour blacklist tokens)
    ├── dto/
    │   ├── RegisterRequest.java, LoginRequest.java
    │   ├── AuthResponse.java         (accessToken, refreshToken, expiresIn)
    │   └── RefreshTokenRequest.java
    ├── security/
    │   ├── JwtTokenProvider.java     (generate/validate JWT, jjwt 0.12.x)
    │   ├── JwtAuthFilter.java
    │   └── UserDetailsServiceImpl.java
    └── exception/GlobalExceptionHandler.java
└── src/main/resources/
    ├── application.yml               (port 8081, jwt.secret, jwt.expiration)
    └── db/migration/V1.0.0__init.sql (users table avec roles)
D. accounts/ (REFACTORISER)
pom.xml — remplacer spring-boot-starter-webmvc + spring-boot-starter-data-jpa par :

spring-boot-starter-webflux
spring-boot-starter-data-r2dbc
org.postgresql:r2dbc-postgresql (driver R2DBC)
Garder postgresql + flyway (migrations restent en JDBC)
Switcher SpringDoc → springdoc-openapi-starter-webflux-ui
Fichiers à modifier/créer :

Account.java — retirer annotations JPA, ajouter @Table, @Id Spring Data R2DBC
AccountRepository.java — ReactiveCrudRepository<Account, Long> + méthodes Mono<Boolean>
AccountsController.java — retours Flux<> / Mono<>, reactive chain
AccountService.java — retours Flux<> / Mono<>, opérateurs Reactor
config/R2dbcConfig.java — configuration connexion R2DBC
Nouveau command/ — CreateAccountCommand.java, UpdateAccountCommand.java, DeleteAccountCommand.java (Java records)
Nouveau command/handler/AccountCommandHandler.java
Nouveau query/GetAccountByIdQuery.java, GetAllAccountsQuery.java
Nouveau query/handler/AccountQueryHandler.java
application.yml — port 8082, spring.r2dbc.url
E. transactions/ (NOUVEAU)

transactions/
├── Dockerfile
├── pom.xml                          (webflux, data-mongodb-reactive, kafka, actuator, lombok)
└── src/main/java/com/ebank/transactions/
    ├── TransactionApplication.java
    ├── api/
    │   ├── Transaction.java          (@Document: id, fromAccount, toAccount, amount, type, status, createdAt)
    │   ├── TransactionRepository.java (ReactiveMongoRepository)
    │   ├── TransactionController.java (CRUD + /transfer endpoint)
    │   └── TransactionService.java
    ├── dto/TransactionRequest.java, TransactionResponse.java
    ├── event/
    │   ├── TransactionEvent.java     (record: eventType, transactionId, amount, accounts)
    │   ├── TransactionEventPublisher.java (KafkaTemplate → topic transaction-events)
    │   └── TransactionEventConsumer.java (consomme account-events)
    ├── saga/TransferSaga.java        (réserve + exécute + confirme/annule)
    └── config/KafkaConfig.java
└── src/main/resources/application.yml (port 8083, mongodb uri, kafka bootstrap)
F. cards/ (NOUVEAU)

cards/
├── Dockerfile
├── pom.xml                          (webflux, data-r2dbc, r2dbc-postgresql, flyway, actuator)
└── src/main/java/com/ebank/cards/
    ├── CardApplication.java
    ├── api/
    │   ├── Card.java                 (id, cardNumber, accountId, type, status, creditLimit, expiryDate)
    │   ├── CardRepository.java       (R2dbcRepository)
    │   ├── CardController.java       (CRUD + /block, /unblock endpoints)
    │   └── CardService.java
    ├── dto/CardRequest.java, CardResponse.java
    └── exception/GlobalExceptionHandler.java
└── src/main/resources/
    ├── application.yml               (port 8085, r2dbc url)
    └── db/migration/V1.0.0__init.sql
G. analytics/ (NOUVEAU)

analytics/
├── Dockerfile
├── pom.xml                          (web, data-mongodb, kafka, actuator, lombok)
└── src/main/java/com/ebank/analytics/
    ├── AnalyticsApplication.java
    ├── model/
    │   ├── TransactionSummary.java   (@Document: period, totalAmount, count, byType)
    │   └── AccountMetrics.java
    ├── repository/AnalyticsRepository.java (MongoRepository)
    ├── api/
    │   ├── AnalyticsController.java  (/api/analytics/summary, /by-period, /top-accounts)
    │   └── AnalyticsService.java     (aggregation pipeline MongoDB)
    └── kafka/TransactionEventConsumer.java (consomme → agrège en temps réel)
└── src/main/resources/application.yml (port 8084)
H. notifications/ (NOUVEAU — Node.js)

notifications/
├── Dockerfile
├── package.json                     (express, kafkajs, nodemailer, dotenv, typescript)
├── tsconfig.json
└── src/
    ├── index.ts                     (Express health endpoint + démarrage consumer)
    ├── consumer/kafka.consumer.ts   (consomme notification-events, transaction-events)
    ├── services/
    │   ├── email.service.ts         (nodemailer, mock SMTP)
    │   ├── sms.service.ts           (mock — log console)
    │   └── push.service.ts          (mock — log console)
    ├── types/events.ts              (interfaces TypeScript des événements Kafka)
    └── config/index.ts              (variables d'env)
I. chatbot/ (NOUVEAU — Node.js)

chatbot/
├── Dockerfile
├── package.json                     (express, ws, @langchain/core, langchain, axios, typescript)
├── tsconfig.json
└── src/
    ├── index.ts                     (Express + WebSocket server)
    ├── websocket/server.ts          (gestion connexions WS, dispatch messages)
    ├── sse/stream.ts                (helper SSE pour streaming réponses)
    ├── langchain/
    │   ├── chain.ts                 (pipeline LangChain: input → tool calling → output)
    │   ├── tools.ts                 (getAccountBalance, getRecentTransactions, getCardStatus, blockCard)
    │   └── mock-llm.ts              (Mock LLM — réponses déterministes sans API key)
    ├── services/api.client.ts       (axios → appels vers Account/Transaction/Card services)
    └── config/index.ts
J. ARCHITECTURE.md (NOUVEAU — racine du projet)
Document expliquant l'architecture complète, chaque décision technique avec avantages/inconvénients, flux de données, et guide de démarrage.

Ordre d'implémentation
docker-compose.yml — infrastructure complète (PostgreSQL multi-db, MongoDB, Redis, Kafka)
ARCHITECTURE.md — documentation technique
gateway/ — point d'entrée unique
auth/ — authentication avant tout
accounts/ — refactoriser MVC→WebFlux + CQRS
transactions/ — avec Kafka producer
cards/ — similaire à accounts
analytics/ — Kafka consumer + aggregation
notifications/ — Node.js Kafka consumer
chatbot/ — Node.js WebSocket + LangChain mock
Vérification end-to-end

# 1. Démarrer toute l'infra
docker-compose up -d

# 2. Vérifier santé des services
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8081/actuator/health   # Auth
curl http://localhost:8082/actuator/health   # Accounts
# ...

# 3. Tester le flux complet
# Register → Login → JWT → Appel API via Gateway → Vérification dans DB

# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@ebank.com","password":"Password123!"}'

# Login → récupère JWT
curl -X POST http://localhost:8080/api/auth/login \
  -d '{"email":"test@ebank.com","password":"Password123!"}'

# Créer un compte via Gateway (avec JWT)
curl -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer <token>" \
  -d '{...}'

# 4. Tester Chatbot WebSocket
wscat -c ws://localhost:3001/chat

# 5. Vérifier Kafka UI
open http://localhost:8090

# 6. Vérifier Spring Boot Admin (embarqué dans gateway)
open http://localhost:8080/admin
Tests unitaires par service :

Java : ./mvnw test dans chaque dossier service
Node.js : npm test (Jest) dans chatbot/ et notifications/

Frontend React 19 créé — 28 fichiers
Stack : React 19 · Vite 6 · TypeScript · React Router v7 · TanStack Query v5 · Tailwind CSS v3

Pages :

/login — Login + Register (tabs), JWT stocké en localStorage
/dashboard — Stats (comptes, solde total, transactions, cartes actives) + dernières 5 transactions
/accounts — CRUD complet avec modal (create/edit/delete)
/transactions — Liste + création de transaction
/cards — Grille de cartes masquées (**** **** **** 1234) + block/unblock
/analytics — Résumé global + top comptes par volume
Chatbot Widget — bouton flottant bas-droite, WebSocket vers ws://localhost:8080/ws/chat, historique de messages, affichage des toolsUsed

Lancer le projet

# Backend complet
cd /home/sam/Desktop/ebank
docker-compose up -d
# Attendre ~60s le démarrage de tous les services

# Frontend (dev)
cd frontend
npm install
npm run dev
# → http://localhost:3000
Endpoints par microservice (via Gateway :8080)
Service	Endpoints clés
Auth	POST /api/auth/register · /login · /refresh · /logout · GET /me
Accounts	GET/POST /api/accounts · GET/PUT/DELETE /api/accounts/{id}
Transactions	GET/POST /api/transactions · GET /api/transactions/account/{id}
Cards	GET/POST /api/cards · POST /api/cards/{id}/block · /unblock · DELETE
Analytics	GET /api/analytics/summary · /summaries · /accounts/top
Chatbot	WS ws://localhost:8080/ws/chat
Kafka UI	http://localhost:8090
MailHog	http://localhost:8025 (emails de notification)
