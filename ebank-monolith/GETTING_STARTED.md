# eBank Monolith — Complete Getting Started Guide

> **Goal**: From zero to working Spring Boot monolith in 4 steps.

---

## Phase 1: Project Setup (30 mins)

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

## Phase 2: Building Core Modules (90 mins)

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

### Step 2.4: Build Account Module

**2.4.1 - Account & AccountType Enum**

**Location**: `src/main/java/com/ebank/account/entity/Account.java`

```java
package com.ebank.account.entity;

import com.ebank.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_accounts_user_id", columnList = "user_id"),
    @Index(name = "idx_accounts_account_number", columnList = "account_number")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account extends BaseEntity {
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false, unique = true, length = 20)
    private String accountNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;
    
    public enum AccountType {
        CHECKING, SAVINGS, INVESTMENT
    }
    
    public enum AccountStatus {
        ACTIVE, FROZEN, CLOSED
    }
}
```

**2.4.2 - Account DTOs**

**Location**: `src/main/java/com/ebank/account/dto/AccountDTOs.java`

```java
package com.ebank.account.dto;

import com.ebank.account.entity.Account.AccountStatus;
import com.ebank.account.entity.Account.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {
    private AccountType accountType;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    private Long id;
    private String accountNumber;
    private AccountType accountType;
    private BigDecimal balance;
    private AccountStatus status;
    private LocalDateTime createdAt;
}
```

**2.4.3 - Account Repository & Service**

**Location**: `src/main/java/com/ebank/account/repository/AccountRepository.java`

```java
package com.ebank.account.repository;

import com.ebank.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByUserId(Long userId);
    Optional<Account> findByAccountNumber(String accountNumber);
    Optional<Account> findByIdAndUserId(Long id, Long userId);
}
```

**Location**: `src/main/java/com/ebank/account/service/AccountService.java`

```java
package com.ebank.account.service;

import com.ebank.account.dto.AccountResponse;
import com.ebank.account.dto.CreateAccountRequest;
import com.ebank.account.entity.Account;
import com.ebank.account.repository.AccountRepository;
import com.ebank.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {
    
    private final AccountRepository accountRepository;
    
    @Transactional
    public AccountResponse createAccount(Long userId, CreateAccountRequest request) {
        // Generate unique account number
        String accountNumber = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        Account account = Account.builder()
            .userId(userId)
            .accountNumber(accountNumber)
            .accountType(request.getAccountType())
            .status(Account.AccountStatus.ACTIVE)
            .build();
        
        Account savedAccount = accountRepository.save(account);
        return mapToResponse(savedAccount);
    }
    
    @Transactional(readOnly = true)
    public List<AccountResponse> getUserAccounts(Long userId) {
        return accountRepository.findByUserId(userId)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId, Long userId) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return mapToResponse(account);
    }
    
    private AccountResponse mapToResponse(Account account) {
        return AccountResponse.builder()
            .id(account.getId())
            .accountNumber(account.getAccountNumber())
            .accountType(account.getAccountType())
            .balance(account.getBalance())
            .status(account.getStatus())
            .createdAt(account.getCreatedAt())
            .build();
    }
}
```

**2.4.4 - Account Controller**

**Location**: `src/main/java/com/ebank/account/controller/AccountController.java`

```java
package com.ebank.account.controller;

import com.ebank.account.dto.AccountResponse;
import com.ebank.account.dto.CreateAccountRequest;
import com.ebank.account.service.AccountService;
import com.ebank.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    
    private final AccountService accountService;
    
    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @RequestBody CreateAccountRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        AccountResponse response = accountService.createAccount(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Account created successfully"));
    }
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getUserAccounts(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<AccountResponse> accounts = accountService.getUserAccounts(userId);
        return ResponseEntity.ok(ApiResponse.success(accounts, "Accounts retrieved"));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        AccountResponse account = accountService.getAccount(id, userId);
        return ResponseEntity.ok(ApiResponse.success(account, "Account retrieved"));
    }
}
```

### Step 2.5: Build Transaction Module (Simplified MVP)

**2.5.1 - Transaction Entity**

**Location**: `src/main/java/com/ebank/transaction/entity/Transaction.java`

```java
package com.ebank.transaction.entity;

import com.ebank.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_from_account", columnList = "from_account_id"),
    @Index(name = "idx_transactions_to_account", columnList = "to_account_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction extends BaseEntity {
    
    @Column(nullable = false)
    private Long fromAccountId;
    
    @Column(nullable = false)
    private Long toAccountId;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status = TransactionStatus.PENDING;
    
    @Column(unique = true)
    private String reference;
    
    private String description;
    
    public enum TransactionType {
        TRANSFER, DEPOSIT, WITHDRAWAL
    }
    
    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, REVERSED
    }
}
```

**2.5.2 - Transaction DTOs & Repository**

**Location**: `src/main/java/com/ebank/transaction/dto/TransactionDTOs.java`

```java
package com.ebank.transaction.dto;

import com.ebank.transaction.entity.Transaction.TransactionStatus;
import com.ebank.transaction.entity.Transaction.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    private Long toAccountId;
    private BigDecimal amount;
    private String description;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private Long id;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private TransactionType transactionType;
    private TransactionStatus status;
    private String reference;
    private LocalDateTime createdAt;
}
```

**Location**: `src/main/java/com/ebank/transaction/repository/TransactionRepository.java`

```java
package com.ebank.transaction.repository;

import com.ebank.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByFromAccountId(Long fromAccountId);
    List<Transaction> findByToAccountId(Long toAccountId);
}
```

**2.5.3 - Transaction Service**

**Location**: `src/main/java/com/ebank/transaction/service/TransactionService.java`

```java
package com.ebank.transaction.service;

import com.ebank.account.entity.Account;
import com.ebank.account.repository.AccountRepository;
import com.ebank.common.exception.ResourceNotFoundException;
import com.ebank.transaction.dto.TransactionResponse;
import com.ebank.transaction.dto.TransferRequest;
import com.ebank.transaction.entity.Transaction;
import com.ebank.transaction.entity.Transaction.TransactionStatus;
import com.ebank.transaction.entity.Transaction.TransactionType;
import com.ebank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {
    
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    
    @Transactional
    public TransactionResponse transfer(Long fromAccountId, TransferRequest request, Long userId) {
        // Fetch and validate sender's account
        Account fromAccount = accountRepository.findByIdAndUserId(fromAccountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Sender account not found"));
        
        // Fetch receiver's account
        Account toAccount = accountRepository.findById(request.getToAccountId())
            .orElseThrow(() -> new ResourceNotFoundException("Receiver account not found"));
        
        // Validate Balance
        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        
        // Perform transfer (ACID transaction)
        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));
        
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
        
        // Record transaction
        Transaction transaction = Transaction.builder()
            .fromAccountId(fromAccountId)
            .toAccountId(request.getToAccountId())
            .amount(request.getAmount())
            .transactionType(TransactionType.TRANSFER)
            .status(TransactionStatus.COMPLETED)
            .reference("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .description(request.getDescription())
            .build();
        
        Transaction savedTransaction = transactionRepository.save(transaction);
        return mapToResponse(savedTransaction);
    }
    
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionHistory(Long accountId, Long userId) {
        // Verify ownership
        accountRepository.findByIdAndUserId(accountId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        
        return transactionRepository.findByFromAccountId(accountId)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
            .id(transaction.getId())
            .fromAccountId(transaction.getFromAccountId())
            .toAccountId(transaction.getToAccountId())
            .amount(transaction.getAmount())
            .transactionType(transaction.getTransactionType())
            .status(transaction.getStatus())
            .reference(transaction.getReference())
            .createdAt(transaction.getCreatedAt())
            .build();
    }
}
```

**2.5.4 - Transaction Controller**

**Location**: `src/main/java/com/ebank/transaction/controller/TransactionController.java`

```java
package com.ebank.transaction.controller;

import com.ebank.common.dto.ApiResponse;
import com.ebank.transaction.dto.TransactionResponse;
import com.ebank.transaction.dto.TransferRequest;
import com.ebank.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {
    
    private final TransactionService transactionService;
    
    @PostMapping("/accounts/{fromAccountId}/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @PathVariable Long fromAccountId,
            @RequestBody TransferRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        TransactionResponse response = transactionService.transfer(fromAccountId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Transfer completed successfully"));
    }
    
    @GetMapping("/accounts/{accountId}/history")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactionHistory(
            @PathVariable Long accountId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<TransactionResponse> transactions = transactionService.getTransactionHistory(accountId, userId);
        return ResponseEntity.ok(ApiResponse.success(transactions, "Transaction history retrieved"));
    }
}
```

### Step 2.6: Security Configuration

**Location**: `src/main/java/com/ebank/common/config/SecurityConfig.java`

```java
package com.ebank.common.config;

import com.ebank.common.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtFilter jwtFilter;
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

---

## Phase 3: Docker & Database (30 mins)

### Step 3.1: Create Dockerfile

**Location**: `/home/sam/Desktop/ebank/ebank-monolith/Dockerfile`

```dockerfile
#1 Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Step 3.2: Create docker-compose.yml

**Location**: `/home/sam/Desktop/ebank/ebank-monolith/docker-compose.yml`

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: ebank_postgres
    environment:
      POSTGRES_USER: ebank_user
      POSTGRES_PASSWORD: ebank_password
      POSTGRES_DB: ebank_dev
    ports:
      - "5432:5432"
    volumes:
      - ebank_postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ebank_user -d ebank_dev"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - ebank-network

  app:
    build: .
    container_name: ebank_app
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/ebank_dev
      SPRING_DATASOURCE_USERNAME: ebank_user
      SPRING_DATASOURCE_PASSWORD: ebank_password
      JWT_SECRET: dev-secret-key
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - ebank-network

networks:
  ebank-network:
    driver: bridge

volumes:
  ebank_postgres_data:
```

---

## Phase 4: Testing (60 mins)

### Step 4.1: Add Test Dependencies

In `pom.xml`, add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

### Step 4.2: Write Unit Tests

**Location**: `src/test/java/com/ebank/auth/service/AuthServiceTest.java`

```java
package com.ebank.auth.service;

import com.ebank.auth.dto.RegisterRequest;
import com.ebank.auth.entity.User;
import com.ebank.auth.repository.UserRepository;
import com.ebank.common.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private JwtProvider jwtProvider;
    
    @InjectMocks
    private AuthService authService;
    
    @Test
    void testRegister_Success() {
        // Arrange
        RegisterRequest request = new RegisterRequest("user@example.com", "password123", "John");
        User savedUser = User.builder()
            .id(1L)
            .email("user@example.com")
            .fullName("John")
            .build();
        
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtProvider.generateToken(1L, "user@example.com")).thenReturn("jwt-token");
        
        // Act
        var response = authService.register(request);
        
        // Assert
        assertNotNull(response);
        assertEquals("user@example.com", response.getEmail());
        assertEquals("jwt-token", response.getToken());
    }
    
    @Test
    void testRegister_EmailAlreadyExists() {
        // Arrange
        RegisterRequest request = new RegisterRequest("user@example.com", "password123", "John");
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }
}
```

### Step 4.3: Integration Tests

**Location**: `src/test/java/com/ebank/auth/controller/AuthControllerTest.java`

```java
package com.ebank.auth.controller;

import com.ebank.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void testRegister_Success() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
            "newuser@example.com", 
            "password123", 
            "John Doe"
        );
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").exists());
    }
    
    @Test
    void testLogin_Success() throws Exception {
        // First register
        RegisterRequest registerReq = new RegisterRequest(
            "test@example.com", 
            "password123", 
            "Test User"
        );
        
        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerReq)));
        
        // Then login
        LoginRequest loginReq = new LoginRequest("test@example.com", "password123");
        
        mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").exists());
    }
}
```

---

## Phase 5: Running & Testing (20 mins)

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

---

## Success Checklist ✅

- [ ] Project builds without errors
- [ ] Docker containers start successfully
- [ ] Can register a new user
- [ ] Can login and get JWT token
- [ ] Can create accounts
- [ ] Can transfer money
- [ ] All tests pass (test coverage > 60%)
- [ ] Swagger docs are accessible
- [ ] No sensitive data in code (JWT secret in env var)

---

## Next Steps After MVP

Once Phase 1 is complete:

1. **Add Request Parameter Validation** (@Valid, custom validators)
2. **Add Pagination & Filtering** (sorting, search)
3. **Add Database Transactions** (pessimistic locking for transfers)
4. **Add Audit Logs** (who did what, when)
5. **Add Rate Limiting** (prevent abuse)
6. **Add Observability** (logging, metrics, traces)
7. **Add CI/CD Pipeline** (GitHub Actions)
8. **Production Deployment** (Kubernetes or Cloud Run)

---

**Ready to start? I'll implement this step-by-step with you.**
