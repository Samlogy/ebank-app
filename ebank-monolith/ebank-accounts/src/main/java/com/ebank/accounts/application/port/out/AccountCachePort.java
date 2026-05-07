package com.ebank.accounts.application.port.out;

import com.ebank.accounts.application.dto.AccountDto;

import java.util.List;
import java.util.Optional;

public interface AccountCachePort {
    Optional<AccountDto> findById(Long id);
    void cacheById(AccountDto dto);
    void evictById(Long id);
    Optional<List<AccountDto>> findAll();
    void cacheAll(List<AccountDto> accounts);
    void evictAll();
}
