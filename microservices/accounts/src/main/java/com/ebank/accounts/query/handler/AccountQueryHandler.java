package com.ebank.accounts.query.handler;

import com.ebank.accounts.api.Account;
import com.ebank.accounts.api.AccountRepository;
import com.ebank.accounts.cache.AccountCacheService;
import com.ebank.accounts.exception.ResourceNotFoundException;
import com.ebank.accounts.query.GetAccountByIdQuery;
import com.ebank.accounts.query.GetAllAccountsQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountQueryHandler {

    private final AccountRepository accountRepository;
    private final AccountCacheService cacheService;

    public Flux<Account> handle(GetAllAccountsQuery query) {
        log.debug("Handling GetAllAccountsQuery");
        // Try to get from cache first
        return cacheService.getCachedAll()
                .doOnNext(accounts -> log.debug("Returning {} accounts from cache", accounts.size()))
                .flatMapMany(Flux::fromIterable)
                .switchIfEmpty(
                    // Cache miss: fetch from DB and cache the result
                    accountRepository.findAll()
                            .collectList()
                            .doOnNext(accounts -> {
                                if (!accounts.isEmpty()) {
                                    log.debug("Caching {} accounts", accounts.size());
                                    cacheService.putAll(accounts).subscribe();
                                }
                            })
                            .flatMapMany(Flux::fromIterable)
                );
    }

    public Mono<Account> handle(GetAccountByIdQuery query) {
        log.debug("Handling GetAccountByIdQuery for id: {}", query.id());
        // Try to get from cache first
        return cacheService.getCachedById(query.id())
                .doOnNext(account -> log.debug("Cache hit for account id: {}", query.id()))
                .switchIfEmpty(
                    // Cache miss: fetch from DB, cache, and return
                    accountRepository.findById(query.id())
                            .doOnNext(account -> {
                                log.debug("Caching account id: {}", account.getId());
                                cacheService.putById(account).subscribe();
                            })
                            .switchIfEmpty(Mono.error(
                                    new ResourceNotFoundException("Account", "id", query.id())))
                );
    }
}
