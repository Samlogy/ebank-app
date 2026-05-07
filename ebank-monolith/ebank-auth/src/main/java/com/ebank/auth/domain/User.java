package com.ebank.auth.domain;

import com.ebank.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean active;

    @Builder
    User(String username, String email, String password, UserRole role) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username required");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email required");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password required");
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role != null ? role : UserRole.ROLE_USER;
        this.active = true;
    }

    void deactivate() {
        this.active = false;
    }

    void changePassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) throw new IllegalArgumentException("Password required");
        this.password = encodedPassword;
    }
}
