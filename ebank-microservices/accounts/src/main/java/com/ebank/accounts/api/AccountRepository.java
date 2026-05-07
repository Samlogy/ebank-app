package com.ebank.accounts.api;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AccountRepository extends ReactiveCrudRepository<Account, Long> {
    Mono<Boolean> existsByAccountNumber(String accountNumber);
    Mono<Boolean> existsByEmail(String email);
    Mono<Account> findByAccountNumber(String accountNumber);
    Mono<Boolean> existsByEmailAndIdNot(String email, Long id);
}
