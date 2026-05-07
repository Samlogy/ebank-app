package com.ebank.accounts.infrastructure.persistence;

import com.ebank.accounts.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAccountRepository extends JpaRepository<Account, Long> {
    boolean existsByAccountNumber(String accountNumber);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);
}
