# ebank — CLAUDE.md

## Project Overview

**ebank** is a full-stack electronic banking platform built as a learning and portfolio project targeting a **DevOps / Cloud Engineer** career transition. It ships in two independent implementations side by side:

| Sub-project | Architecture | Java | Spring Boot |
|---|---|---|---|
| `ebank-monolith/` | Modular Monolith | 17 | 3.3.0 |
| `ebank-microservices/` | Microservices | 21 | 4.0.3 |

---

## Repository Layout

```
ebank/
├── ebank-monolith/          # Modular monolith (production-ready)
│   ├── src/main/java/com/ebank/
│   │   ├── account/         # Account domain (controller/dto/entity/repo/service)
│   │   ├── auth/            # JWT auth domain
│   │   ├── transaction/     # Transaction domain
│   │   ├── admin/           # Admin endpoints
│   │   └── common/          # Cross-cutting (security, ratelimit, logging, config)
│   ├── jenkins/             # Full CI/CD pipeline
│   │   ├── Jenkinsfile      # Declarative pipeline (build → test → SAST/SCA → deploy → DAST)
│   │   ├── k8s/             # Kubernetes manifests (deployment, service, ingress, hpa, etc.)
│   │   ├── config/          # Checkstyle + OWASP suppression rules
│   │   └── e2e/             # Postman/Newman collection
│   ├── docker-compose.yml        # Local dev stack
│   ├── docker-compose.prod.yml   # Production stack
│   └── docker-compose.elk.yml    # ELK logging stack
│
├── ebank-microservices/     # Reactive microservices (Spring WebFlux + R2DBC)
│   ├── accounts/            # Accounts service
│   ├── auth/                # Auth service
│   ├── gateway/             # API Gateway
│   ├── transactions/        # Transactions service
│   ├── notifications/       # Notifications service
│   ├── frontend/            # Frontend (React)
│   ├── chatbot/             # AI chatbot
│   ├── k8s/                 # K8s manifests
│   ├── helm/                # Helm charts
│   └── docker-compose.yml   # Full stack with HashiCorp Vault
│
├── ARCHITECTURE_PLAN.md
├── ROADMAP-NEW-JOB.md
└── docker-compose.yml       # Root-level dev compose
```

---

## Tech Stack

### Monolith
- **Runtime**: Java 17, Spring Boot 3.3.0
- **API**: Spring MVC (REST)
- **Security**: Spring Security + JWT (jjwt 0.12.x) + role-based access + rate limiting
- **Persistence**: Spring Data JPA + PostgreSQL 42.7.1
- **Observability**: ELK stack (Logback + JSON structured logging)
- **Tooling**: Lombok, MapStruct (if present), Bean Validation

### Microservices
- **Runtime**: Java 21, Spring Boot 4.0.3
- **API**: Spring WebFlux (reactive / non-blocking)
- **Persistence**: R2DBC + PostgreSQL (reactive), Flyway migrations
- **Secrets**: HashiCorp Vault (spring-cloud-starter-vault-config)
- **Observability**: Micrometer + OpenTelemetry (OTLP exporter) + Actuator
- **Docs**: SpringDoc OpenAPI (WebFlux UI)

### Infrastructure / DevOps
- **Containers**: Docker + Docker Compose (local / prod / ELK)
- **Orchestration**: Kubernetes + Helm
- **CI/CD**: Jenkins Declarative Pipeline
  - Stages: Checkout → Build → Tests → OWASP SCA / CycloneDX SBOM (parallel) → SonarQube → Quality Gate → Docker Build → Trivy CVE / Hadolint / Checkov (parallel) → Push → K8s Deploy → Smoke Test → ZAP DAST / Newman E2E (parallel) → Pa11y → GreenIT
- **Security scanning**: OWASP Dependency-Check, Trivy, OWASP ZAP, Checkov
- **Code quality**: SonarQube, Checkstyle

---

## Running the Monolith Locally

```bash
cd ebank-monolith
docker compose up -d          # starts PostgreSQL + app
# or for ELK observability:
docker compose -f docker-compose.yml -f docker-compose.elk.yml up -d
```

## Running the Microservices Stack

```bash
cd ebank-microservices
docker compose up -d          # starts Vault + all services
```

---

## Key Conventions

- **Package structure**: feature-first slicing (`account/`, `auth/`, `transaction/`) not layer-first
- **DTOs**: separate request/response DTOs, no entity leakage to API layer
- **Security**: JWT is stateless; roles enforced at controller level (`@PreAuthorize`)
- **Environments**: monolith uses Spring profiles (`local`, `test`, `prod`)
- **Secrets**: microservices read all secrets from Vault at startup — never hardcode credentials
- **Migrations**: use Flyway for schema changes in microservices; JPA `ddl-auto` in monolith dev only
- **Commits**: conventional prefix format (`add:`, `fix:`, `refactor:`, etc.)

---

## Active Branch Context

- Current branch: `appmod/java-upgrade-20260509125415`
- Main branch: `main`
- The Jenkins pipeline + K8s manifests were recently added to `ebank-monolith/jenkins/`

---

## Career Goal

This project is a portfolio piece targeting **DevOps / Cloud Engineer** roles. When suggesting improvements, prioritize production-readiness, observability, security posture, and CI/CD completeness over new features.
