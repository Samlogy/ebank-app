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
        RegisterRequest request = new RegisterRequest("user@example.com", "password123", "John");
        User savedUser = User.builder()
            .email("user@example.com")
            .fullName("John")
            .role("USER")
            .build();
        savedUser.setId(1L);

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtProvider.generateToken(1L, "user@example.com", "USER")).thenReturn("jwt-token");
        
        var response = authService.register(request);
        
        assertNotNull(response);
        assertEquals("user@example.com", response.getEmail());
        assertEquals("jwt-token", response.getToken());
    }
    
    @Test
    void testRegister_EmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("user@example.com", "password123", "John");
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
        
        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }
}
