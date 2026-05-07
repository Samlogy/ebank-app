package com.ebank.auth.api;

import com.ebank.auth.api.dto.*;
import com.ebank.auth.application.command.LoginCommand;
import com.ebank.auth.application.command.RegisterCommand;
import com.ebank.auth.application.dto.AuthTokenDto;
import com.ebank.auth.application.dto.TokenClaimsDto;
import com.ebank.auth.application.port.in.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "JWT authentication endpoints")
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        AuthTokenDto dto = registerUseCase.register(
                new RegisterCommand(request.username(), request.email(), request.password()));
        return toResponse(dto);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthTokenDto dto = loginUseCase.login(new LoginCommand(request.email(), request.password()));
        return toResponse(dto);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthTokenDto dto = refreshTokenUseCase.refresh(request.refreshToken());
        return toResponse(dto);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Logout and revoke tokens")
    public void logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        logoutUseCase.logout(token);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate JWT token (used by gateway / internal)")
    public ResponseEntity<Map<String, String>> validate(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        TokenClaimsDto claims = validateTokenUseCase.validate(token);
        return ResponseEntity.ok(Map.of(
                "email", claims.email(),
                "role", claims.role(),
                "userId", String.valueOf(claims.userId())
        ));
    }

    private AuthResponse toResponse(AuthTokenDto dto) {
        return new AuthResponse(dto.accessToken(), dto.refreshToken(), dto.expiresIn(),
                dto.username(), dto.email(), dto.role());
    }
}
