# eBank Monolith - Production-Ready Spring Boot Application

A **modular monolithic Spring Boot application** designed for learning production-ready backend architecture. This project demonstrates clean architecture, domain-driven design, and containerization best practices.

## Quick Start

```bash
# 1. Build the project
./mvnw clean package

# 2. Start with Docker Compose
docker-compose up --build

# 3. Access the API
curl http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "fullName": "John Doe"
  }'

# 4. View API docs
http://localhost:8080/swagger-ui.html
```

## Project Structure

```
ebank-monolith/
├── src/
│   ├── main/java/com/ebank/
│   │   ├── common/          # Shared infrastructure (JWT, security, exceptions)
│   │   ├── auth/            # User authentication & registration
│   │   ├── account/         # Account management
│   │   ├── transaction/     # Transaction processing
│   │   └── EbankMonolithApplication.java
│   ├── test/java/com/ebank/ # Unit & integration tests
│   └── resources/
│       ├── application.yaml # Development config
│       └── application-test.yaml
├── Dockerfile
├── docker-compose.yml
├── pom.xml                  # Maven dependencies
└── README.md
```

## Modules

| Module | Responsibility |
|--------|-----------------|
| **common** | Security, JWT, exceptions, shared DTOs |
| **auth** | Register, login, JWT generation |
| **account** | Create/list accounts, balance tracking|
| **transaction** | Transfer money, transaction history |

## API Endpoints

### Authentication
```
POST   /api/v1/auth/register      - Register new user
POST   /api/v1/auth/login         - Login & get JWT token
GET    /api/v1/auth/me            - Get current user (auth required)
```

### Accounts
```
POST   /api/v1/accounts            - Create account (auth required)
GET    /api/v1/accounts            - List user accounts (auth required)
GET    /api/v1/accounts/{id}       - Get account details (auth required)
```

### Transactions
```
POST   /api/v1/transactions/accounts/{id}/transfer - Transfer money (auth required)
GET    /api/v1/transactions/accounts/{id}/history  - Transaction history (auth required)
```

## Tech Stack

- **Java 17** - Modern JVM language
- **Spring Boot 3.3** - Production framework
- **Spring Security + JWT** - Stateless authentication
- **Spring Data JPA** - Database access
- **PostgreSQL** - Relational database
- **Docker** - Containerization
- **JUnit 5, Mockito** - Testing

## Testing

```bash
# Run unit tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=AuthControllerTest

# Run with coverage
./mvnw clean test jacoco:report
```

## Development

```bash
# Start development with local PostgreSQL
docker-compose up postgres  # Start only the database
./mvnw spring-boot:run      # Run the app

# View live API docs
http://localhost:8080/swagger-ui.html
```

## Production

For production deployment:

1. Update `application-prod.yaml` with production settings
2. Use environment variables for sensitive data:
   - `JWT_SECRET` - JWT signing key
   - `SPRING_DATASOURCE_URL` - Database URL
   - `SPRING_DATASOURCE_USERNAME` - DB username
   - `SPRING_DATASOURCE_PASSWORD` - DB password

3. Deploy the Docker image:
```bash
docker build -t ebank-monolith:latest .
docker push your-registry/ebank-monolith:latest
```

## Learning Resources

- See `ARCHITECTURE_PLAN.md` for complete system design
- See `GETTING_STARTED.md` for detailed implementation guide

## Author

Built as a learning project for production-ready Spring Boot architecture.
