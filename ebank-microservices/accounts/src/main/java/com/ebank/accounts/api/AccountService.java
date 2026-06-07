package com.ebank.accounts.api;

import com.ebank.accounts.command.CreateAccountCommand;
import com.ebank.accounts.command.DeleteAccountCommand;
import com.ebank.accounts.command.UpdateAccountCommand;
import com.ebank.accounts.command.handler.AccountCommandHandler;
import com.ebank.accounts.dto.AccountRequest;
import com.ebank.accounts.dto.AccountResponse;
import com.ebank.accounts.query.GetAccountByIdQuery;
import com.ebank.accounts.query.GetAllAccountsQuery;
import com.ebank.accounts.query.handler.AccountQueryHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountCommandHandler commandHandler;
    private final AccountQueryHandler queryHandler;

    public Flux<AccountResponse> getAllAccounts() {
        log.debug("Fetching all accounts");
        return queryHandler.handle(new GetAllAccountsQuery())
                .map(this::mapToResponse);
    }

    public Mono<AccountResponse> getAccountById(Long id) {
        log.debug("Fetching account by id: {}", id);
        return queryHandler.handle(new GetAccountByIdQuery(id))
                .map(this::mapToResponse);
    }

    public Mono<AccountResponse> createAccount(AccountRequest request) {
        log.info("Creating account with accountNumber: {}, email: {}", request.getAccountNumber(), request.getEmail());
        CreateAccountCommand cmd = new CreateAccountCommand(
                request.getAccountNumber(),
                request.getAccountHolderName(),
                request.getEmail(),
                request.getPhoneNumber(),
                request.getAccountType(),
                request.getBalance(),
                request.getAddress(),
                request.getStatus()
        );
        return commandHandler.handle(cmd)
                .map(this::mapToResponse);
    }

    public Mono<AccountResponse> updateAccount(Long id, AccountRequest request) {
        log.info("Updating account with id: {}", id);
        UpdateAccountCommand cmd = new UpdateAccountCommand(
                id,
                request.getAccountNumber(),
                request.getAccountHolderName(),
                request.getEmail(),
                request.getPhoneNumber(),
                request.getAccountType(),
                request.getBalance(),
                request.getAddress(),
                request.getStatus()
        );
        return commandHandler.handle(cmd)
                .map(this::mapToResponse);
    }

    public Mono<Void> deleteAccount(Long id) {
        log.info("Deleting account with id: {}", id);
        return commandHandler.handle(new DeleteAccountCommand(id));
    }

    private AccountResponse mapToResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setEmail(account.getEmail());
        response.setPhoneNumber(account.getPhoneNumber());
        response.setAccountType(account.getAccountType());
        response.setBalance(account.getBalance());
        response.setAddress(account.getAddress());
        response.setStatus(account.getStatus());
        response.setCreatedAt(account.getCreatedAt());
        response.setUpdatedAt(account.getUpdatedAt());
        return response;
    }
}
