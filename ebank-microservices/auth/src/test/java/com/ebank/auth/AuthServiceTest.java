package com.ebank.auth;

import com.ebank.auth.api.AuthService;
import com.ebank.auth.api.User;
import com.ebank.auth.api.UserRepository;
import com.ebank.auth.dto.AuthResponse;
import com.ebank.auth.dto.LoginRequest;
import com.ebank.auth.dto.RegisterRequest;
import com.ebank.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("johndoe", "john@example.com", "securePass123");
        loginRequest = new LoginRequest("john@example.com", "securePass123");

        savedUser = User.builder()
                .id(1L)
                .username("johndoe")
                .email("john@example.com")
                .password("$2a$10$encodedpassword")
                .role("ROLE_USER")
                .active(true)
                .build();
    }

    // =============================================
    // REGISTER TESTS
    // =============================================

    @Test
    @DisplayName("register - success: new user is saved and tokens are returned")
    void register_success() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("johndoe")).thenReturn(false);
        when(passwordEncoder.encode("securePass123")).thenReturn("$2a$10$encodedpassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtTokenProvider.generateAccessToken("john@example.com", "ROLE_USER"))
                .thenReturn("access-token-abc");
        when(jwtTokenProvider.generateRefreshToken("john@example.com"))
                .thenReturn("refresh-token-xyz");
        when(jwtTokenProvider.getExpiration()).thenReturn(900000L);
        when(jwtTokenProvider.getRefreshExpiration()).thenReturn(604800000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AuthResponse response = authService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token-abc");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-xyz");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.username()).isEqualTo("johndoe");
        assertThat(response.role()).isEqualTo("ROLE_USER");
        assertThat(response.expiresIn()).isEqualTo(900000L);

        verify(userRepository).existsByEmail("john@example.com");
        verify(userRepository).existsByUsername("johndoe");
        verify(userRepository).save(any(User.class));
        verify(passwordEncoder).encode("securePass123");
        verify(valueOperations).set(
                eq("refresh:john@example.com"),
                eq("refresh-token-xyz"),
                eq(604800000L),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    @DisplayName("register - failure: duplicate email throws RuntimeException")
    void register_duplicateEmail_throwsException() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email already in use");

        verify(userRepository).existsByEmail("john@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("register - failure: duplicate username throws RuntimeException")
    void register_duplicateUsername_throwsException() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("johndoe")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Username already taken");

        verify(userRepository, never()).save(any(User.class));
    }

    // =============================================
    // LOGIN TESTS
    // =============================================

    @Test
    @DisplayName("login - success: valid credentials return tokens")
    void login_success() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("john@example.com");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser));
        when(jwtTokenProvider.generateAccessToken("john@example.com", "ROLE_USER"))
                .thenReturn("access-token-login");
        when(jwtTokenProvider.generateRefreshToken("john@example.com"))
                .thenReturn("refresh-token-login");
        when(jwtTokenProvider.getExpiration()).thenReturn(900000L);
        when(jwtTokenProvider.getRefreshExpiration()).thenReturn(604800000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AuthResponse response = authService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token-login");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-login");
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.username()).isEqualTo("johndoe");
        assertThat(response.role()).isEqualTo("ROLE_USER");
        assertThat(response.tokenType()).isEqualTo("Bearer");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).findByEmail("john@example.com");
        verify(jwtTokenProvider).generateAccessToken("john@example.com", "ROLE_USER");
        verify(jwtTokenProvider).generateRefreshToken("john@example.com");
    }

    @Test
    @DisplayName("login - failure: bad credentials throws BadCredentialsException")
    void login_badCredentials_throwsException() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Bad credentials");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository, never()).findByEmail(anyString());
        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), anyString());
    }
}
