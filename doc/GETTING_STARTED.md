# eBank Monolith — Complete Getting Started Guide

> **Goal**: From zero to working Spring Boot monolith in 4 steps.

---

## Phase 1: Project Setup

### Step 1.1: Initialize Maven Project

```bash
# Navigate to project directory
cd /home/sam/Desktop/ebank
cd ebank-monolith

# Initialize Maven project structure manually
# OR use Spring Boot CLI (if you have it):
# spring boot new --type=maven-project ebank-monolith

# Manually create structure:
mkdir -p src/{main,test}/{java/com/ebank,resources}
mkdir -p src/main/java/com/ebank/{common,auth,account,transaction}/{config,service,repository,controller,entity,dto}
```

### Step 1.2: Create pom.xml

The main Maven file that defines dependencies and build config.

**Location**: `/home/sam/Desktop/ebank/ebank-monolith/pom.xml`

Key dependencies:
- Spring Boot 3.3.x (latest LTS)
- Spring Security
- Spring Data JPA
- PostgreSQL driver
- JWT (jjwt)
- Testing (JUnit 5, Mockito)
- Swagger/OpenAPI

### Step 1.3: Create Maven Wrapper

This allows others to run `./mvnw` without Maven installed.

```bash
# Download Maven wrapper files
mvn -N io.takari:maven:wrapper -Dmaven=3.9.6
```

### Step 1.4: Create application.yaml

Development config file.

**Location**: `src/main/resources/application.yaml`

```yaml
spring:
  application:
    name: ebank-monolith
  
  datasource:
    url: jdbc:postgresql://localhost:5432/ebank_dev
    username: ebank_user
    password: ebank_password
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: true
  
  security:
    user:
      name: admin
      password: admin123

server:
  port: 8080
  servlet:
    context-path: /

jwt:
  secret: dev-secret-key-never-use-in-production-change-this
  expiration: 86400000  # 24 hours in milliseconds

logging:
  level:
    root: INFO
    com.ebank: DEBUG
```

---

## Phase 2: Building Core Modules

### Step 2.1: Create Application Entry Point

**Location**: `src/main/java/com/ebank/EbankMonolithApplication.java`

```java
package com.ebank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.ebank.*")
public class EbankMonolithApplication {
    public static void main(String[] args) {
        SpringApplication.run(EbankMonolithApplication.class, args);
    }
}
```

### Step 2.2: Build Common Module (Shared Infrastructure)

**2.2.1 - API Response Wrapper**

**Location**: `src/main/java/com/ebank/common/dto/ApiResponse.java`

```java
package com.ebank.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private List<String> errors;
    private LocalDateTime timestamp;
    
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> ApiResponse<T> error(String message, List<String> errors) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .errors(errors)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
```

**2.2.2 - Base Entity**

**Location**: `src/main/java/com/ebank/common/entity/BaseEntity.java`

```java
package com.ebank.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

@Data
@MappedSuperclass
@ToString
public class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

**2.2.3 - Global Exception Handler**

**Location**: `src/main/java/com/ebank/common/exception/GlobalExceptionHandler.java`

```java
package com.ebank.common.exception;

import com.ebank.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage(), null));
    }
    
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<?>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(ex.getMessage(), null));
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
            MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors()
            .stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.toList());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Validation failed", errors));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("Internal server error", null));
    }
}
```

**2.2.4 - Custom Exceptions**

**Location**: `src/main/java/com/ebank/common/exception/CustomExceptions.java`

```java
package com.ebank.common.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
```

### Step 2.3: Build Auth Module

**2.3.1 - User Entity**

**Location**: `src/main/java/com/ebank/auth/entity/User.java`

```java
package com.ebank.auth.entity;

import com.ebank.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", uniqueConstraints = {@UniqueConstraint(columnNames = "email")})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String passwordHash;
    
    @Column(name = "full_name")
    private String fullName;
    
    @Column(nullable = false)
    private String role = "USER";  // USER, ADMIN
}
```

**2.3.2 - Auth DTOs**

**Location**: `src/main/java/com/ebank/auth/dto/AuthDTOs.java`

```java
package com.ebank.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    private String email;
    private String password;
    private String fullName;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String email;
    private String password;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String fullName;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String fullName;
}
```

**2.3.3 - User Repository**

**Location**: `src/main/java/com/ebank/auth/repository/UserRepository.java`

```java
package com.ebank.auth.repository;

import com.ebank.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

**2.3.4 - Auth Service**

**Location**: `src/main/java/com/ebank/auth/service/AuthService.java`

```java
package com.ebank.auth.service;

import com.ebank.auth.dto.AuthResponse;
import com.ebank.auth.dto.LoginRequest;
import com.ebank.auth.dto.RegisterRequest;
import com.ebank.auth.dto.UserResponse;
import com.ebank.auth.entity.User;
import com.ebank.auth.repository.UserRepository;
import com.ebank.common.exception.InvalidCredentialsException;
import com.ebank.common.exception.ResourceNotFoundException;
import com.ebank.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Validate email not taken
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        
        // Create user
        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .fullName(request.getFullName())
            .role("USER")
            .build();
        
        User savedUser = userRepository.save(user);
        
        // Generate JWT
        String token = jwtProvider.generateToken(savedUser.getId(), savedUser.getEmail());
        
        return AuthResponse.builder()
            .token(token)
            .email(savedUser.getEmail())
            .fullName(savedUser.getFullName())
            .build();
    }
    
    public AuthResponse login(LoginRequest request) {
        // Find user
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        
        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        
        // Generate JWT
        String token = jwtProvider.generateToken(user.getId(), user.getEmail());
        
        return AuthResponse.builder()
            .token(token)
            .email(user.getEmail())
            .fullName(user.getFullName())
            .build();
    }
    
    public UserResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName());
    }
}
```

**2.3.5 - JWT Provider**

**Location**: `src/main/java/com/ebank/common/security/JwtProvider.java`

```java
package com.ebank.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtProvider {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration}")
    private long jwtExpiration;
    
    public String generateToken(Long userId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);
        
        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("email", email)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS512)
            .compact();
    }
    
    public Long getUserIdFromToken(String token) {
        return Long.parseLong(
            Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject()
        );
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

**2.3.6 - JWT Filter**

**Location**: `src/main/java/com/ebank/common/security/JwtFilter.java`

```java
package com.ebank.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    
    private final JwtProvider jwtProvider;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String jwt = getJwtFromRequest(request);
            
            if (jwt != null && jwtProvider.validateToken(jwt)) {
                Long userId = jwtProvider.getUserIdFromToken(jwt);
                
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication", ex);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

**2.3.7 - Auth Controller**

**Location**: `src/main/java/com/ebank/auth/controller/AuthController.java`

```java
package com.ebank.auth.controller;

import com.ebank.auth.dto.AuthResponse;
import com.ebank.auth.dto.LoginRequest;
import com.ebank.auth.dto.RegisterRequest;
import com.ebank.auth.dto.UserResponse;
import com.ebank.auth.service.AuthService;
import com.ebank.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "User registered successfully"));
    }
    
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }
    
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserResponse response = authService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "User profile retrieved"));
    }
}
```
---

## Phase 5: Running & Testing

### Step 5.1: Build Locally

```bash
cd /home/sam/Desktop/ebank/ebank-monolith

# Build without running tests
./mvnw clean package -DskipTests

# Or build with tests
./mvnw clean package
```

### Step 5.2: Run with Docker

```bash
# Start services
docker-compose up --build

# Check if app is running
curl http://localhost:8080/actuator/health

# Stop services
docker-compose down
```

### Step 5.3: Test API Endpoints

```bash
# Register user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "fullName": "John Doe"
  }'

# Response: { "token": "eyJhbGc..." }

# Login user
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'

# Create account (requires JWT token from register/login)
TOKEN="<token-from-register>"
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "accountType": "CHECKING"
  }'

# Response: { "id": 1, "accountNumber": "ACC-ABC123", "balance": 0.00 }

# List user accounts
curl -X GET http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN"
```

### Step 5.4: View API Documentation

```
http://localhost:8080/swagger-ui.html
```

### Step 5.5: Run Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=AuthServiceTest

# Generate coverage report
./mvnw clean test jacoco:report
# Report: target/site/jacoco/index.html
```