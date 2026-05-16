package com.ebank.accounts;

import com.ebank.accounts.api.AccountRepository;
import com.ebank.accounts.config.TestRedisConfig;
import com.ebank.accounts.dto.AccountRequest;
import com.ebank.accounts.dto.AccountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AccountControllerIT {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        accountRepository.deleteAll().block();
    }

    // ---- helpers ----

    private AccountRequest buildRequest(String accountNumber, String email, String holderName) {
        AccountRequest req = new AccountRequest();
        req.setAccountNumber(accountNumber);
        req.setAccountHolderName(holderName);
        req.setEmail(email);
        req.setPhoneNumber("1234567890");
        req.setAccountType("SAVINGS");
        req.setBalance(BigDecimal.valueOf(1000.00));
        req.setAddress("123 Main St");
        req.setStatus("ACTIVE");
        return req;
    }

    private AccountResponse createAndExtract(AccountRequest request) {
        return webTestClient.post().uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AccountResponse.class)
                .returnResult()
                .getResponseBody();
    }

    @Nested
    @DisplayName("GET /api/accounts")
    class GetAllAccountsTests {

        @Test
        @DisplayName("Should return empty list when no accounts exist")
        void getAllAccounts_WhenNoAccountsExist_ShouldReturnEmptyList() {
            webTestClient.get().uri("/api/accounts")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(AccountResponse.class)
                    .hasSize(0);
        }

        @Test
        @DisplayName("Should return all accounts when accounts exist")
        void getAllAccounts_WhenAccountsExist_ShouldReturnList() {
            createAndExtract(buildRequest("ACC1000000001", "alice@example.com", "Alice Smith"));
            createAndExtract(buildRequest("ACC2000000002", "bob@example.com", "Bob Jones"));

            webTestClient.get().uri("/api/accounts")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(AccountResponse.class)
                    .hasSize(2);
        }

        @Test
        @DisplayName("Should return correct content type")
        void getAllAccounts_ShouldReturnCorrectContentType() {
            createAndExtract(buildRequest("ACC3000000003", "carol@example.com", "Carol White"));

            webTestClient.get().uri("/api/accounts")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON);
        }
    }

    @Nested
    @DisplayName("GET /api/accounts/{id}")
    class GetAccountByIdTests {

        @Test
        @DisplayName("Should return account when it exists")
        void getAccountById_ShouldReturnAccount_WhenExists() {
            AccountResponse created = createAndExtract(
                    buildRequest("ACC4000000004", "dave@example.com", "Dave Brown"));

            webTestClient.get().uri("/api/accounts/{id}", created.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(AccountResponse.class)
                    .value(response -> {
                        assert response.getId().equals(created.getId());
                        assert response.getAccountNumber().equals("ACC4000000004");
                        assert response.getEmail().equals("dave@example.com");
                    });
        }

        @Test
        @DisplayName("Should return 404 when account does not exist")
        void getAccountById_ShouldReturn404_WhenNotFound() {
            webTestClient.get().uri("/api/accounts/99999")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("POST /api/accounts")
    class CreateAccountTests {

        @Test
        @DisplayName("Should create account and return 201 with body")
        void createAccount_WithValidRequest_ShouldReturn201() {
            AccountRequest request = buildRequest("ACC5000000005", "eve@example.com", "Eve Taylor");

            webTestClient.post().uri("/api/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(AccountResponse.class)
                    .value(response -> {
                        assert response.getId() != null;
                        assert response.getAccountNumber().equals("ACC5000000005");
                        assert response.getEmail().equals("eve@example.com");
                        assert response.getAccountHolderName().equals("Eve Taylor");
                    });
        }

        @Test
        @DisplayName("Should return 400 when accountNumber is blank")
        void createAccount_WithBlankAccountNumber_ShouldReturn400() {
            AccountRequest request = buildRequest("ACC6000000006", "frank@example.com", "Frank Lee");
            request.setAccountNumber(null);

            webTestClient.post().uri("/api/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Should return 400 when balance is negative")
        void createAccount_WithNegativeBalance_ShouldReturn400() {
            AccountRequest request = buildRequest("ACC7000000007", "grace@example.com", "Grace Kim");
            request.setBalance(BigDecimal.valueOf(-50.00));

            webTestClient.post().uri("/api/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Should return 409 when accountNumber already exists")
        void createAccount_WithDuplicateAccountNumber_ShouldReturn409() {
            AccountRequest first = buildRequest("ACC8000000008", "henry@example.com", "Henry Ford");
            createAndExtract(first);

            AccountRequest duplicate = buildRequest("ACC8000000008", "ivan@example.com", "Ivan Cruz");

            webTestClient.post().uri("/api/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(duplicate)
                    .exchange()
                    .expectStatus().isEqualTo(409);
        }

        @Test
        @DisplayName("Should return 409 when email already exists")
        void createAccount_WithDuplicateEmail_ShouldReturn409() {
            AccountRequest first = buildRequest("ACC9000000009", "shared@example.com", "Jane Doe");
            createAndExtract(first);

            AccountRequest duplicate = buildRequest("ACC9000000010", "shared@example.com", "John Smith");

            webTestClient.post().uri("/api/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(duplicate)
                    .exchange()
                    .expectStatus().isEqualTo(409);
        }
    }

    @Nested
    @DisplayName("PUT /api/accounts/{id}")
    class UpdateAccountTests {

        @Test
        @DisplayName("Should update account and return updated response")
        void updateAccount_WithValidRequest_ShouldReturnUpdated() {
            AccountResponse created = createAndExtract(
                    buildRequest("ACC1100000011", "kate@example.com", "Kate Old"));

            AccountRequest update = buildRequest("ACC1100000011", "kate@example.com", "Kate New");
            update.setBalance(BigDecimal.valueOf(2000.00));

            webTestClient.put().uri("/api/accounts/{id}", created.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(update)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(AccountResponse.class)
                    .value(response -> {
                        assert response.getAccountHolderName().equals("Kate New");
                        assert response.getBalance().compareTo(BigDecimal.valueOf(2000.00)) == 0;
                    });
        }

        @Test
        @DisplayName("Should return 404 when updating non-existent account")
        void updateAccount_WhenNotFound_ShouldReturn404() {
            AccountRequest update = buildRequest("ACC1200000012", "liam@example.com", "Liam Neeson");

            webTestClient.put().uri("/api/accounts/99999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(update)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("DELETE /api/accounts/{id}")
    class DeleteAccountTests {

        @Test
        @DisplayName("Should delete account and return 204")
        void deleteAccount_WhenExists_ShouldReturn204() {
            AccountResponse created = createAndExtract(
                    buildRequest("ACC1300000013", "mia@example.com", "Mia Wong"));

            webTestClient.delete().uri("/api/accounts/{id}", created.getId())
                    .exchange()
                    .expectStatus().isNoContent();

            // Verify it is gone
            webTestClient.get().uri("/api/accounts/{id}", created.getId())
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent account")
        void deleteAccount_WhenNotFound_ShouldReturn404() {
            webTestClient.delete().uri("/api/accounts/99999")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("Combined flow tests")
    class CombinedFlowTests {

        @Test
        @DisplayName("Should create account then retrieve it in GET all")
        void createThenGetAll_ShouldIncludeNewAccount() {
            AccountRequest request = buildRequest("ACC1400000014", "nina@example.com", "Nina Simone");
            createAndExtract(request);

            webTestClient.get().uri("/api/accounts")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(AccountResponse.class)
                    .hasSize(1)
                    .value(list -> {
                        assert list.get(0).getAccountNumber().equals("ACC1400000014");
                        assert list.get(0).getEmail().equals("nina@example.com");
                    });
        }

        @Test
        @DisplayName("Should create multiple accounts and verify count")
        void createMultipleAccounts_ShouldIncreaseCount() {
            createAndExtract(buildRequest("ACC1500000015", "omar@example.com", "Omar Sharif"));
            createAndExtract(buildRequest("ACC1600000016", "pam@example.com", "Pam Anderson"));

            webTestClient.get().uri("/api/accounts")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(AccountResponse.class)
                    .hasSize(2);
        }
    }
}
