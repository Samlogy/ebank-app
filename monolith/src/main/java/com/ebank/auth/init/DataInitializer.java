package com.ebank.auth.init;

import com.ebank.auth.entity.User;
import com.ebank.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a single ADMIN user on first startup when admin.email and admin.password
 * are both configured. Idempotent: does nothing if the email already exists.
 * Leave admin.password empty (the default) to skip seeding entirely.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) return;
        if (userRepository.existsByEmail(adminEmail)) return;

        User admin = User.builder()
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .fullName("Admin")
                .role("ADMIN")
                .build();
        userRepository.save(admin);
        log.info("Admin user seeded: email={}", adminEmail);
    }
}
