package com.ebank.accounts.application.port.out;

import com.ebank.accounts.domain.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepositoryPort {
    Optional<Account> findById(Long id);
    List<Account> findAll();
    Account save(Account account);
    void deleteById(Long id);
    boolean existsByAccountNumber(String accountNumber);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);
}
