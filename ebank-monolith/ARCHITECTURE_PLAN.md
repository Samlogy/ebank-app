# eBank Monolith — Complete Architectural Plan

> **Goal**: Build a production-ready Spring Boot modular monolith that works, then iterate to improve it.

---

## 1. Project Structure

```
ebank-monolith/
├── .mvn/wrapper/                           # Maven wrapper
├── src/
│   ├── main/
│   │   ├── java/com/ebank/
│   │   │   ├── common/                     # Shared infrastructure
│   │   │   │   ├── config/                 # Spring config, security
│   │   │   │   ├── exception/              # Global exception handling
│   │   │   │   ├── security/               # JWT filter, auth provider
│   │   │   │   ├── dto/                    # Common response DTOs
│   │   │   │   └── util/                   # Enums, helpers
│   │   │   │
│   │   │   ├── auth/                       # Authentication & User Management
│   │   │   │   ├── controller/             # @RestController
│   │   │   │   ├── service/                # Business logic
│   │   │   │   ├── repository/             # Spring Data JPA
│   │   │   │   ├── entity/                 # JPA entities
│   │   │   │   └── dto/                    # Request/Response DTOs
│   │   │   │
│   │   │   ├── account/                    # Account Management
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── repository/
│   │   │   │   ├── entity/
│   │   │   │   └── dto/
│   │   │   │
│   │   │   ├── transaction/                # Transaction Processing
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── repository/
│   │   │   │   ├── entity/
│   │   │   │   └── dto/
│   │   │   │
│   │   │   └── EbankMonolithApplication.java
│   │   │
│   │   └── resources/
│   │       ├── application.yaml            # Spring Boot config
│   │       ├── application-dev.yaml        # Dev overrides
│   │       ├── application-prod.yaml       # Production config
│   │       └── db/
│   │           └── migration/              # Future: Flyway scripts
│   │
│   └── test/
│       └── java/com/ebank/
│           ├── auth/
│           │   ├── controller/AuthControllerTest.java
│           │   └── service/AuthServiceTest.java
│           ├── account/
│           │   ├── controller/AccountControllerTest.java
│           │   └── service/AccountServiceTest.java
│           └── transaction/
│               └── service/TransactionServiceTest.java
│
├── docker/
│   └── Dockerfile
├── docker-compose.yml
├── pom.xml                                 # Maven dependencies
├── mvnw & mvnw.cmd                        # Maven wrapper
├── README.md                               # Getting started
└── GETTING_STARTED.md                      # Testing & running guide
```

---

## 2. Layered Architecture (Per Module)

```
┌──────────────────────────────────────┐
│    REST Controller (HTTP)            │
│ - Request validation                 │
│ - Response mapping                   │
└─────────────────┬────────────────────┘
                  │
┌─────────────────▼────────────────────┐
│    Service Layer (Business Logic)    │
│ - Domain orchestration               │
│ - Validation & rules                 │
│ - Cross-module coordination          │
└─────────────────┬────────────────────┘
                  │
┌─────────────────▼────────────────────┐
│  Repository Layer (Data Access)      │
│ - Spring Data JPA queries            │
│ - Custom query methods               │
└─────────────────┬────────────────────┘
                  │
┌─────────────────▼────────────────────┐
│      PostgreSQL Database             │
│ - Tables, indexes, constraints       │
└──────────────────────────────────────┘
```

---

## 3. Module Responsibilities

### **3.1 Common Module** (Foundation)
Shared infrastructure used by all modules.

| Component | Purpose |
|-----------|---------|
| `SecurityConfig` | Spring Security setup, CORS, filter chains |
| `JwtFilter` | JWT token extraction & validation |
| `JwtProvider` | Token generation & parsing (secret in env var) |
| `GlobalExceptionHandler` | @RestControllerAdvice for all exceptions |
| `ApiResponse<T>` | Standard response wrapper (success/error) |
| `PagedResponse<T>` | Pagination wrapper |
| `BaseEntity` | Abstract entity with `id`, `createdAt`, `updatedAt` |

**Key Files**:
```java
// Example: Common response structure
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private List<String> errors;
}

// Example: JWT filter
@Component
public class JwtFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest req, 
                                    HttpServletResponse res, 
                                    FilterChain chain) {
        // Extract token from header
        // Validate signature
        // Set Authentication in SecurityContext
    }
}
```

---

### **3.2 Auth Module** (Entry Point)
User registration, login, JWT issuance.

**Entities**:
- `User` (id, email, password_hash, full_name, created_at)

**API Endpoints**:
```
POST   /api/v1/auth/register       Register new user
POST   /api/v1/auth/login          Get JWT token
GET    /api/auth/me                Current user info (auth required)
```

**Service Flow**:
```
Register Request
  → Validate email unique
  → Hash password (BCrypt)
  → Create User entity
  → Return JWT

Login Request
  → Find user by email
  → Verify password
  → Generate JWT (exp: 24h)
  → Return AccessToken
```

---

### **3.3 Account Module** (Core Domain)
User accounts, balances, account types.

**Entities**:
- `Account` (id, userId, account_number, account_type, balance, status, created_at)
- `AccountType` enum: CHECKING, SAVINGS, INVESTMENT

**API Endpoints**:
```
POST   /api/v1/accounts            Create account (auth required)
GET    /api/v1/accounts            List user accounts (auth required)
GET    /api/v1/accounts/{id}       Get account details
PUT    /api/v1/accounts/{id}       Update account (status, etc.)
GET    /api/v1/accounts/{id}/balance  Get current balance
```

**Service Logic**:
- Validate account ownership (user can only access own accounts)
- Generate unique account numbers
- Track balance changes

---

### **3.4 Transaction Module** (Business Logic)
Send money, receive payment, transfer between accounts.

**Entities**:
- `Transaction` (id, from_account_id, to_account_id, amount, type, status, reference, created_at)
- `TransactionType` enum: TRANSFER, DEPOSIT, WITHDRAWAL
- `TransactionStatus` enum: PENDING, COMPLETED, FAILED, REVERSED

**API Endpoints**:
```
POST   /api/v1/transactions                 Initiate transfer
GET    /api/v1/transactions                 List user transactions (paginated)
GET    /api/v1/accounts/{id}/transactions   Transactions for account
GET    /api/v1/transactions/{id}            Transaction details
GET    /api/v1/accounts/{id}/balance        Updated balance
```

**Service Logic**:
```
Transfer Flow:
  → Validate sender owns account
  → Check balance sufficient
  → Lock tables in DB (avoid double-spend)
  → Debit sender account
  → Credit receiver account
  → Create transaction record
  → Release locks
  → Return receipt
```

---

## 4. Data Model (PostgreSQL)

```sql
-- USERS TABLE (Auth Module)
CREATE TABLE users (
  id SERIAL PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(255),
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- ACCOUNTS TABLE (Account Module)
CREATE TABLE accounts (
  id SERIAL PRIMARY KEY,
  user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
  account_number VARCHAR(20) UNIQUE NOT NULL,
  account_type VARCHAR(50) NOT NULL, -- CHECKING, SAVINGS, INVESTMENT
  balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, FROZEN, CLOSED
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- TRANSACTIONS TABLE (Transaction Module)
CREATE TABLE transactions (
  id SERIAL PRIMARY KEY,
  from_account_id INTEGER REFERENCES accounts(id),
  to_account_id INTEGER REFERENCES accounts(id),
  amount DECIMAL(15,2) NOT NULL,
  transaction_type VARCHAR(50) NOT NULL, -- TRANSFER, DEPOSIT, WITHDRAWAL
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, FAILED, REVERSED
  reference VARCHAR(255) UNIQUE,
  description TEXT,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- INDEXES (Performance)
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_accounts_account_number ON accounts(account_number);
CREATE INDEX idx_transactions_from_account ON transactions(from_account_id);
CREATE INDEX idx_transactions_to_account ON transactions(to_account_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
```

---

## 5. Technology Stack

| Layer | Technology | Why |
|-------|-----------|-----|
| **Language** | Java 17 | LTS, modern records & sealed classes |
| **Framework** | Spring Boot 3.x | Production-ready, large ecosystem |
| **Security** | Spring Security + JWT | Stateless auth, scalable |
| **Data** | Spring Data JPA + PostgreSQL | Type-safe, relational integrity |
| **Validation** | Hibernate Validator | Bean validation, clean errors |
| **Documentation** | Springdoc OpenAPI (Swagger) | Auto-generated API docs |
| **Build** | Maven + Maven Wrapper | Reproducible builds, no version mismatch |
| **Testing** | JUnit 5, Mockito, MockMvc | Comprehensive test coverage |
| **Containerization** | Docker + Docker Compose | Dev ≈ prod |
| **Logging** | SLF4J + Logback | Structured, fast |

---

## 6. Development Workflow

### Phase 1: Working MVP (First Week)
- ✅ Project skeleton
- ✅ Common module + security config
- ✅ Auth (register, login)
- ✅ Account (CRUD)
- ✅ Transaction (basic transfer)
- ✅ Docker dev setup
- ✅ Basic tests

### Phase 2: Production Readiness (Second Week)
- ✅ Exception handling (all modules)
- ✅ Input validation
- ✅ Pagination & filtering
- ✅ Database transactions & locking
- ✅ Comprehensive test coverage (60%+)
- ✅ Application properties profiles (dev/prod)
- ✅ Health check endpoints

### Phase 3: Observability (Third Week+)
- ✅ Structured logging (MDC context)
- ✅ Metrics (Prometheus, Micrometer)
- ✅ Request/response tracing
- ✅ Database connection pooling
- ✅ Performance monitoring

---

## 7. Testing Strategy

| Level | Tools | Coverage |
|-------|-------|----------|
| **Unit** | JUnit 5, Mockito | Service logic |
| **Integration** | MockMvc, @SpringBootTest | Controller + Service |
| **Database** | @DataJpaTest, TestContainers | Repository queries |
| **E2E** | RestAssured (optional) | Full request flow |

**Test Example**:
```java
@SpringBootTest
@ActiveProfiles("test")
class AuthControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean AuthService authService;
    
    @Test
    void testRegister_Success() throws Exception {
        // Arrange
        RegisterRequest req = new RegisterRequest("user@example.com", "password123", "John");
        AuthResponse resp = new AuthResponse("jwt-token-123");
        when(authService.register(any())).thenReturn(resp);
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.token").exists());
    }
}
```

---

## 8. Running & Testing Checklist

### Local Development
```bash
# 1. Build project
./mvnw clean package -DskipTests

# 2. Start services (PostgreSQL + App)
docker-compose up --build

# 3. Run tests
./mvnw test

# 4. Generate test coverage
./mvnw clean test jacoco:report

# 5. Access API
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"pwd123","fullName":"John"}'

# 6. View Swagger
http://localhost:8080/swagger-ui.html

# 7. Health check
curl http://localhost:8080/actuator/health
```

---

## 9. Configuration Management

### application.yaml (Dev)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ebank_dev
    username: ebank_user
    password: dev_password
  jpa:
    hibernate:
      ddl-auto: update  # Auto-create tables in dev
    show-sql: true
  
  security:
    jwt:
      secret: dev-secret-key-change-in-prod
      expiration: 86400000  # 24h in ms

server:
  port: 8080
```

### application-prod.yaml (Production)
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate  # Never auto-migrate in prod
    show-sql: false
  
  security:
    jwt:
      secret: ${JWT_SECRET}  # From environment/vault
      expiration: ${JWT_EXPIRATION:86400000}

server:
  port: 8080
```

---

## 10. Key Implementation Patterns

### Pattern 1: Module Independence
Each module (auth, account, transaction) can be understood independently. They communicate through:
- Direct service injection (same process)
- Shared entities only in database access
- DTOs for external APIs

### Pattern 2: Stateless Authentication
- No sessions stored on server
- JWT contains user identity
- Can scale horizontally

### Pattern 3: Transaction Safety
- ACID properties via PostgreSQL
- Service-level optimistic locking
- Idempotent API (safe retries)

### Pattern 4: Error Handling Consistency
```java
// All modules throw custom exceptions
public class InsufficientBalanceException extends DomainException {
    public InsufficientBalanceException(String message) {
        super("INSUFFICIENT_BALANCE", message, HttpStatus.BAD_REQUEST);
    }
}

// Global handler catches and formats
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<?>> handleDomainException(DomainException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
            .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }
}
```

---

## 11. Success Criteria (MVP Complete)

- ✅ User can register and login (get JWT)
- ✅ Authenticated user can create accounts
- ✅ User can transfer money between their accounts
- ✅ Transaction is atomic (all-or-nothing)
- ✅ All endpoints documented in Swagger
- ✅ Docker setup works locally (docker-compose up)
- ✅ 70%+ test coverage
- ✅ README explains how to run & extend

---

## Next: Implementation Roadmap

1. **Create project skeleton** (pom.xml, Maven structure)
2. **Build common module** (config, security, exceptions)
3. **Build auth module** (register, login)
4. **Build account module** (CRUD)
5. **Build transaction module** (transfer, balance)
6. **Add tests** (unit + integration)
7. **Docker setup** (Dockerfile, docker-compose)
8. **Documentation** (README, GETTING_STARTED)

---

**Ready to start Phase 1? I'll guide you step-by-step through implementation.**
