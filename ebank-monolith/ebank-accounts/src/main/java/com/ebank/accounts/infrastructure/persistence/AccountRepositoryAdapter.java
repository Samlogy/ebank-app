package com.ebank.accounts.infrastructure.persistence;

import com.ebank.accounts.application.port.out.AccountRepositoryPort;
import com.ebank.accounts.domain.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class AccountRepositoryAdapter implements AccountRepositoryPort {

    private final SpringDataAccountRepository delegate;

    @Override
    public Optional<Account> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public List<Account> findAll() {
        return delegate.findAll();
    }

    @Override
    public Account save(Account account) {
        return delegate.save(account);
    }

    @Override
    public void deleteById(Long id) {
        delegate.deleteById(id);
    }

    @Override
    public boolean existsByAccountNumber(String accountNumber) {
        return delegate.existsByAccountNumber(accountNumber);
    }

    @Override
    public boolean existsByEmail(String email) {
        return delegate.existsByEmail(email);
    }

    @Override
    public boolean existsByEmailAndIdNot(String email, Long id) {
        return delegate.existsByEmailAndIdNot(email, id);
    }
}
