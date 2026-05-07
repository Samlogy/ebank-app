# eBank — Modular Monolith (Spring Boot + Hexagonal Architecture)

> Branch: `monolith-modular` | Stack: Spring Boot 4.0.3 · Java 21 · PostgreSQL · Redis · Kafka (optionnel)

---

## Table des matières

1. [Analyse du repo de référence (execution-os-backend)](#1-analyse-du-repo-de-référence)
2. [Critiques et améliorations](#2-critiques-et-améliorations)
3. [Architecture du monolithe modulaire](#3-architecture)
4. [Best practices implémentées](#4-best-practices)
5. [Structure du projet](#5-structure-du-projet)
6. [Démarrage rapide (local)](#6-démarrage-rapide-local)
7. [Démarrage avec Docker Compose](#7-docker-compose)
8. [API Reference](#8-api-reference)
9. [Tests](#9-tests)
10. [Observabilité](#10-observabilité)
11. [Ajouter un nouveau module](#11-ajouter-un-nouveau-module)
12. [Choix techniques expliqués](#12-choix-techniques)

---

## 1. Analyse du repo de référence

[execution-os-backend](https://github.com/abinash-backend/execution-os-backend) est un Spring Boot 3.5 / Java 17 **REST API de suivi de tâches personnelles**. Organisation par packages :

```
com.executionos/
├── auth/       — UserController + UserService + UserRepository + DTOs
├── task/       — TaskController + TaskService + TaskRepository + DTOs
├── execution/  — ExecutionController + ExecutionService + ExecutionRepository + DTOs
├── common/     — SecurityConfig + GlobalExceptionHandler
└── system/     — HealthController
```

**Points forts** : JWT stateless, BCrypt, DDL auto, SpringDoc, Docker multi-stage, test unitaires Mockito.

---

## 2. Critiques et améliorations

| # | Problème | Impact | Correction appliquée ici |
|---|----------|--------|--------------------------|
| 1 | Secret JWT hardcodé dans le source | Critique | `@Value("${jwt.secret}")` externalizable + Vault |
| 2 | Pas d'isolation inter-modules | Architecture | Maven multi-module, classes package-private |
| 3 | Pas de contrats explicites | Architecture | Interfaces `UseCase` + `Port` publiques |
| 4 | N+1 dans le leaderboard | Performance | Requêtes SQL avec `@Query` JPQL |
| 5 | `@Transactional` absent | Correction | Au niveau use-case, pas repository |
| 6 | Pas de `@Valid` | Sécurité | `@Valid` + annotations JSR-303 sur tous les DTOs |
| 7 | Pas d'audit | Observabilité | `BaseEntity` avec `@CreatedBy` / `@LastModifiedDate` |
| 8 | Pas de rate limiting | Sécurité | (Bucket4j — à ajouter en prod) |
| 9 | Package plat | Maintenabilité | `domain/application/infrastructure/api` par module |
| 10 | Logique métier dans le service | DDD | Méthodes d'agrégat : `account.withdraw()`, `account.close()` |

---

## 3. Architecture

### Vue globale

```
┌─────────────────────────────────────────────────────────┐
│                  ebank-app (Bootstrap)                  │
│  EBankApplication · AuditConfig · OpenApiConfig         │
│  Flyway migrations · application.yml                    │
└───────────────────────────────┬─────────────────────────┘
                                │ depends on
       ┌────────────────────────┼────────────────────────┐
       ▼                        ▼                        ▼
 ┌───────────┐          ┌─────────────────┐    ┌──────────────────┐
 │ ebank-auth│          │  ebank-accounts │    │ebank-transactions│
 └───────────┘          └─────────────────┘    └──────────────────┘
       │                        │ DebitAccountUseCase          │
       │                        │ CreditAccountUseCase         │
       └────────────────────────┴──────────────────────────────┘
                                │
                        ┌───────┴──────────┐
                        │  ebank-core       │
                        │  BaseEntity, Money│
                        │  Exceptions       │
                        │  GlobalExHandler  │
                        └──────────────────┘
```

### Hexagonal Architecture (par module)

```
┌─ ebank-accounts ────────────────────────────────────────────────┐
│                                                                   │
│  api/                    ←── HTTP Request                        │
│    AccountController                                              │
│    dto/ AccountRequest · AccountResponse                         │
│         │                                                         │
│         ▼ (CreateAccountCommand)                                  │
│  application/                                                     │
│    port/in/  CreateAccountUseCase  ←── interface publique        │
│              DebitAccountUseCase   ←── interface publique        │
│    port/out/ AccountRepositoryPort ←── interface interne         │
│              AccountCachePort      ←── interface interne         │
│    service/  AccountApplicationService (implémente tous les cas) │
│         │                                                         │
│         ▼                                                         │
│  domain/                                                          │
│    Account (aggregate root) — deposit(), withdraw(), close()     │
│    AccountType · AccountStatus (enums)                            │
│         │                                                         │
│         ▼                                                         │
│  infrastructure/                                                  │
│    persistence/ SpringDataAccountRepository (JPA)                │
│                 AccountRepositoryAdapter                          │
│    cache/       RedisAccountCacheAdapter                          │
└───────────────────────────────────────────────────────────────────┘
```

### Flux transactionnel atomique (le gain principal)

```
POST /api/v1/transactions
  → TransactionController
  → CreateTransactionUseCase.create()          ← @Transactional
      ├─ DebitAccountUseCase.debit()           ← REQUIRED (join tx)
      ├─ CreditAccountUseCase.credit()         ← REQUIRED (join tx)
      ├─ Transaction.create() + save()
      └─ ApplicationEventPublisher.publish()   ← AFTER_COMMIT → Kafka
```

Si `debit` ou `credit` échoue → tout rollback (débit + crédit + transaction).

---

## 4. Best Practices

### Domain-Driven Design

```java
// ✅ Invariant dans l'agrégat (pas dans le service)
public void withdraw(BigDecimal amount) {
    requireActive();
    if (this.balance.compareTo(amount) < 0)
        throw new BusinessRuleViolationException("Insufficient funds");
    this.balance = this.balance.subtract(amount);
}

// ❌ Mauvaise pratique (référence repo)
account.setBalance(account.getBalance().subtract(amount)); // dans le service
```

### Ports & Adapters

```java
// Port (interface publique, dans application/)
public interface DebitAccountUseCase {
    void debit(DebitAccountCommand command);
}

// Adaptateur (implémentation privée, dans infrastructure/)
// → Transactions utilise l'interface, jamais l'implémentation
```

### @Transactional au bon niveau

```java
// ✅ Use-case level : une opération métier = une transaction
@Transactional
public TransactionDto create(CreateTransactionCommand cmd) { ... }

// ❌ Repository level : trop granulaire, perd la cohérence
```

### Tests en 3 couches

```
Domain tests     → AccountDomainTest.java (0 Spring, pur Java)
Application tests→ AccountApplicationServiceTest.java (Mockito)
Integration tests→ AccountControllerIT.java (@SpringBootTest + MockMvc)
Architecture     → ArchitectureTest.java (ArchUnit)
```

---

## 5. Structure du projet

```
ebank-monolith/
├── pom.xml                          # Parent POM (<packaging>pom</packaging>)
│
├── ebank-core/                      # Shared kernel — zéro dépendance métier
│   └── src/main/java/com/ebank/core/
│       ├── domain/BaseEntity.java   # id + version + createdAt + createdBy
│       ├── domain/Money.java        # Value Object immuable
│       ├── exception/               # DomainException + sous-classes
│       └── web/GlobalExceptionHandler.java
│
├── ebank-auth/                      # Module authentification
│   └── src/main/java/com/ebank/auth/
│       ├── domain/User.java         # Entité package-private
│       ├── application/port/in/     # RegisterUseCase, LoginUseCase…
│       ├── application/port/out/    # UserRepositoryPort, JwtPort…
│       ├── application/service/     # AuthApplicationService
│       ├── infrastructure/security/ # JwtTokenService, SecurityConfig, JwtAuthFilter
│       ├── infrastructure/redis/    # TokenBlacklist, RefreshToken adapters
│       └── api/                     # AuthController + DTOs
│
├── ebank-accounts/                  # Module comptes
│   └── src/main/java/com/ebank/accounts/
│       ├── domain/Account.java      # Aggregate avec deposit/withdraw/close
│       ├── application/port/in/     # CreateAccountUseCase, DebitAccountUseCase…
│       ├── application/service/     # AccountApplicationService
│       ├── infrastructure/          # JPA + Redis adapters
│       └── api/                     # AccountController + DTOs
│
├── ebank-transactions/              # Module transactions
│   └── src/main/java/com/ebank/transactions/
│       ├── domain/Transaction.java  # Aggregate + TransactionCreatedEvent
│       ├── application/service/     # Atomic debit+credit+save
│       ├── infrastructure/kafka/    # KafkaTransactionEventPublisher (AFTER_COMMIT)
│       └── api/                     # TransactionController
│
├── ebank-notifications/             # Module notifications
│   └── src/main/java/com/ebank/notifications/
│       ├── application/service/     # @TransactionalEventListener(AFTER_COMMIT)
│       └── infrastructure/email/    # SpringMailEmailAdapter (MailHog en local)
│
├── ebank-app/                       # Bootstrap — seul module qui produit le JAR
│   ├── src/main/java/com/ebank/app/
│   │   ├── EBankApplication.java
│   │   └── config/AuditConfig.java, OpenApiConfig.java
│   └── src/main/resources/
│       ├── application.yml          # Config commune
│       ├── application-local.yml    # Dev local
│       ├── application-docker.yml   # Docker Compose
│       └── db/migration/            # V1.0.0 → V1.3.0 (Flyway)
│
├── docker-compose.yml               # postgres + redis + kafka + mailhog + observabilité
├── Dockerfile                       # Multi-stage Maven → JRE
└── infra/                           # prometheus.yml, tempo.yaml, grafana datasources
```

---

## 6. Démarrage rapide (local)

### Prérequis

- Java 21
- Maven 3.9+
- PostgreSQL 16 en cours d'exécution
- Redis en cours d'exécution

### 1. Créer la base de données

```sql
CREATE DATABASE ebank_db;
CREATE USER ebank WITH PASSWORD 'ebank';
GRANT ALL PRIVILEGES ON DATABASE ebank_db TO ebank;
```

### 2. Lancer l'application

```bash
cd ebank-monolith
mvn spring-boot:run -pl ebank-app -am -Dspring.profiles.active=local
```

L'option `-am` (also-make) compile tous les modules nécessaires avant de lancer `ebank-app`.

### 3. Vérifier

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

# Swagger UI
open http://localhost:8080/swagger-ui.html
```

---

## 7. Docker Compose

```bash
cd ebank-monolith

# Démarrage complet (postgres + redis + kafka + mailhog + observabilité + app)
docker compose up --build

# En arrière-plan
docker compose up --build -d

# Arrêt
docker compose down -v   # -v supprime les volumes postgres
```

**Services démarrés :**

| Service      | URL / Port          | Description                        |
|--------------|--------------------|------------------------------------|
| App          | http://localhost:8080 | eBank API                        |
| Swagger UI   | http://localhost:8080/swagger-ui.html | Documentation API   |
| MailHog      | http://localhost:8025 | Visualiser les emails envoyés    |
| Grafana      | http://localhost:3000 | Dashboards (Prometheus + Tempo + Loki) |
| Prometheus   | http://localhost:9090 | Métriques brutes                 |
| Kafka        | localhost:9092        | Broker (KRaft mode)              |

---

## 8. API Reference

Toutes les routes sont documentées dans Swagger UI. Résumé rapide :

### Auth (`/api/v1/auth`)

```bash
# Inscription
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"secret123"}'

# Connexion
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"secret123"}'
# → {"accessToken":"eyJ...","refreshToken":"eyJ...","expiresIn":900000}

# Refresh
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"eyJ..."}'

# Logout
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer eyJ..."
```

### Accounts (`/api/v1/accounts`) — JWT requis

```bash
TOKEN="eyJ..."

# Créer un compte
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"ACC-001","accountHolderName":"Alice","email":"alice@bank.com","accountType":"SAVINGS","balance":1000.00}'

# Lister
curl http://localhost:8080/api/v1/accounts -H "Authorization: Bearer $TOKEN"

# Détail
curl http://localhost:8080/api/v1/accounts/1 -H "Authorization: Bearer $TOKEN"
```

### Transactions (`/api/v1/transactions`) — JWT requis

```bash
# Virement (débit compte 1, crédit compte 2 — atomique)
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":200.00,"type":"TRANSFER","description":"Virement"}'

# Dépôt (crédit seul)
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"toAccountId":1,"amount":500.00,"type":"DEPOSIT"}'

# Historique par compte
curl http://localhost:8080/api/v1/transactions/account/1 \
  -H "Authorization: Bearer $TOKEN"
```

---

## 9. Tests

### Lancer tous les tests

```bash
cd ebank-monolith
mvn verify
```

### Tests unitaires seulement (rapide, pas de Spring context)

```bash
mvn test -pl ebank-accounts,ebank-auth,ebank-transactions
```

### Tests d'architecture ArchUnit

```bash
mvn test -pl ebank-accounts -Dtest=ArchitectureTest
mvn test -pl ebank-auth     -Dtest=ArchitectureTest
mvn test -pl ebank-transactions -Dtest=ArchitectureTest
```

Les règles vérifiées :
- `domain` n'a pas d'annotations Spring (`@Service`, `@Component`, `@Repository`)
- `application` ne dépend pas de `infrastructure`
- `accounts` ne dépend pas de `transactions`

### Tests de domaine purs

```bash
mvn test -pl ebank-accounts -Dtest=AccountDomainTest
```

Ces tests vérifient les invariants du domaine sans Spring (Account.withdraw, Account.close…).

---

## 10. Observabilité

### Métriques (Prometheus + Grafana)

Endpoint : `GET /actuator/prometheus`

Métriques disponibles :
- `auth.operations{operation="login",status="success"}` — logins réussis
- `accounts.operations{operation="create",status="success"}` — comptes créés
- `transactions.operations{type="TRANSFER",status="success"}` — transactions
- `transactions.creation.duration{type="TRANSFER"}` — durée P99

### Distributed Tracing (Tempo)

Chaque requête obtient un `traceId` propagé dans les logs et envoyé à Tempo via OTLP.

Dans Grafana → Explore → Tempo : chercher par `traceId`.

### Logs structurés

Format : `%d [%thread] %-5level [%X{traceId}] %logger - %msg%n`

En production avec Loki : configurer `logback-spring.xml` avec l'appender Loki4j.

### Health check

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/info
```

---

## 11. Ajouter un nouveau module

Exemple : ajouter un module `ebank-loans`.

### 1. Créer la structure

```bash
mkdir -p ebank-loans/src/{main,test}/java/com/ebank/loans/{domain,application/{port/{in,out},command,dto,service},infrastructure/{persistence,cache},api/dto}
```

### 2. Créer `ebank-loans/pom.xml`

```xml
<parent>
  <groupId>com.ebank</groupId>
  <artifactId>ebank-monolith</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</parent>
<artifactId>ebank-loans</artifactId>
<dependencies>
  <dependency>
    <groupId>com.ebank</groupId>
    <artifactId>ebank-core</artifactId>
  </dependency>
  <dependency>
    <groupId>com.ebank</groupId>
    <artifactId>ebank-accounts</artifactId>  <!-- si le module doit accéder aux comptes -->
  </dependency>
  ...
</dependencies>
```

### 3. Ajouter dans le parent POM

```xml
<!-- ebank-monolith/pom.xml -->
<modules>
  ...
  <module>ebank-loans</module>
</modules>
```

### 4. Ajouter dans ebank-app/pom.xml

```xml
<dependency>
  <groupId>com.ebank</groupId>
  <artifactId>ebank-loans</artifactId>
</dependency>
```

### 5. Migration Flyway

```sql
-- ebank-app/src/main/resources/db/migration/V1.4.0__create_loans.sql
CREATE TABLE loans ( id BIGSERIAL PRIMARY KEY, ... );
```

### 6. Règle : ce qui est public vs privé

| Classe | Visibilité | Raison |
|--------|-----------|--------|
| `LoanUseCase` (port in) | `public` | Utilisé par `api/` et potentiellement par d'autres modules |
| `Loan` (entity) | `package-private` | Implémentation interne |
| `LoanApplicationService` | `package-private` | Implémentation interne |
| `LoanController` | `public` | Scanné par Spring |

---

## 12. Choix techniques

### Pourquoi Maven multi-module et pas un seul module ?

L'isolation physique des dépendances est **la seule façon de garantir** qu'un module ne peut pas accéder aux classes internes d'un autre. Avec un seul module, même bien organisé en packages, rien n'empêche `TransactionService` d'instancier directement `AccountRepositoryAdapter` — ce que `maven-enforcer-plugin` interdit avec une erreur de compilation.

### Pourquoi JPA synchrone et pas WebFlux/R2DBC ?

Dans un monolithe, il n'y a pas de latence réseau inter-services. Le gain de throughput de R2DBC est négligeable jusqu'à ~10 000 req/s. JPA est infiniment plus mature : tooling, migrations, transactions ACID, relations bidirectionnelles. WebFlux n'est justifié que lorsque la majorité des opérations est I/O-bound et que les connexions concurrent atteignent des milliers.

### Pourquoi une seule base PostgreSQL ?

MongoDB était utilisé pour les transactions dans les microservices. Dans le monolithe, le vrai avantage est d'avoir **une seule frontière transactionnelle ACID** : débit + crédit + enregistrement de transaction = un seul `@Transactional`, avec rollback complet si quoi que ce soit échoue. C'est impossible avec deux bases de données séparées sans saga patterns.

### Pourquoi Kafka est conditionnel (`@ConditionalOnProperty`) ?

Kafka apporte une vraie valeur pour l'intégration externe (systèmes tiers, analytics). En local ou en dev, il alourdit le setup. L'architecture domain events via `ApplicationEventPublisher` fonctionne parfaitement sans Kafka. `@TransactionalEventListener(phase = AFTER_COMMIT)` garantit la même sémantique : l'event ne part qu'après le commit DB, dans les deux cas.

### Pourquoi ArchUnit et pas seulement des conventions ?

Les conventions ne s'appliquent pas elles-mêmes. Six mois après, sous pression, quelqu'un importe directement `AccountRepositoryAdapter` depuis `TransactionService`. ArchUnit transforme la convention en **test CI qui fail** — visible, reproductible, automatique.

### Pourquoi les services d'application sont package-private ?

`AccountApplicationService` n'a pas besoin d'être publique. Spring la détecte via `@Service` et la scan. En la rendant package-private, n'importe quel code extérieur au module qui tenterait de l'injecter directement (au lieu de passer par l'interface `CreateAccountUseCase`) obtiendra une erreur de compilation. C'est la règle de la salle des machines : les contrats sont publics, les tuyaux sont cachés.
