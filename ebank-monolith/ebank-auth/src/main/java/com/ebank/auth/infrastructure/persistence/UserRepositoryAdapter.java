package com.ebank.auth.infrastructure.persistence;

import com.ebank.auth.application.port.out.UserRepositoryPort;
import com.ebank.auth.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
class UserRepositoryAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository delegate;

    @Override
    public Optional<User> findByEmail(String email) {
        return delegate.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return delegate.existsByEmail(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return delegate.existsByUsername(username);
    }

    @Override
    public User save(User user) {
        return delegate.save(user);
    }
}
