package com.ebank.chatbot.tools;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Banking "tools" exposed to the LLM (Spring AI Tool Calling — the same capability
 * an MCP server would advertise, here co-located for a self-contained service).
 *
 * <p>When the model decides a user question needs live data ("what's my balance?",
 * "show my last transactions"), Spring AI invokes the matching {@code @Tool} method,
 * feeds the JSON result back into the conversation, and lets the model phrase the
 * final natural-language answer.
 *
 * <p>Each call is wrapped so a downstream outage degrades to a friendly message
 * instead of throwing inside the LLM turn.
 */
@Component
public class BankTools {

    private static final Logger log = LoggerFactory.getLogger(BankTools.class);

    private final RestClient accountsRestClient;
    private final RestClient transactionsRestClient;

    public BankTools(RestClient accountsRestClient, RestClient transactionsRestClient) {
        this.accountsRestClient = accountsRestClient;
        this.transactionsRestClient = transactionsRestClient;
    }

    // ── Tool result shapes (serialized to JSON for the model) ────────────────────

    public record AccountInfo(String accountId, String accountNumber, Double balance,
                              String currency, String status, String note) {
    }

    public record TransactionInfo(String id, String type, Double amount,
                                  String currency, String status, String date) {
    }

    // ── Tools ────────────────────────────────────────────────────────────────────

    @Tool(description = "Get the current balance and status of a bank account by its id. "
            + "Use this whenever the user asks about their balance or account details.")
    public AccountInfo getAccountBalance(
            @ToolParam(description = "The numeric account id, e.g. 1") String accountId) {
        log.info("[tool] getAccountBalance accountId={}", accountId);
        try {
            Map<String, Object> a = accountsRestClient.get()
                    .uri("/api/accounts/{id}", accountId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (a == null) {
                return unavailableAccount(accountId);
            }
            return new AccountInfo(
                    String.valueOf(a.getOrDefault("id", accountId)),
                    asString(a.get("accountNumber")),
                    asDouble(a.get("balance")),
                    asString(a.getOrDefault("currency", "EUR")),
                    asString(a.getOrDefault("status", "ACTIVE")),
                    null);
        } catch (Exception e) {
            log.warn("[tool] getAccountBalance failed for {}: {}", accountId, e.getMessage());
            return unavailableAccount(accountId);
        }
    }

    @Tool(description = "List the recent transactions of a bank account, most recent first. "
            + "Use this when the user asks about their transaction history, payments or transfers.")
    public List<TransactionInfo> getRecentTransactions(
            @ToolParam(description = "The account id whose transactions to fetch") String accountId,
            @ToolParam(required = false, description = "Max number of transactions to return (default 5)") Integer limit) {
        int max = (limit == null || limit <= 0) ? 5 : Math.min(limit, 50);
        log.info("[tool] getRecentTransactions accountId={} limit={}", accountId, max);
        try {
            List<Map<String, Object>> txs = transactionsRestClient.get()
                    .uri("/api/transactions/account/{accountId}", accountId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (txs == null || txs.isEmpty()) {
                return List.of();
            }
            return txs.stream()
                    .limit(max)
                    .map(t -> new TransactionInfo(
                            asString(t.get("id")),
                            asString(t.getOrDefault("type", "TRANSACTION")),
                            asDouble(t.get("amount")),
                            asString(t.getOrDefault("currency", "EUR")),
                            asString(t.getOrDefault("status", "COMPLETED")),
                            asString(t.getOrDefault("createdAt", t.get("date")))))
                    .toList();
        } catch (Exception e) {
            log.warn("[tool] getRecentTransactions failed for {}: {}", accountId, e.getMessage());
            return List.of();
        }
    }

    private AccountInfo unavailableAccount(String accountId) {
        return new AccountInfo(accountId, null, null, "EUR", "UNKNOWN",
                "Account data is temporarily unavailable. Please try again shortly.");
    }

    // ── Loose JSON coercion helpers ───────────────────────────────────────────────

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Double asDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
