package com.ebank.chatbot.config;

import com.ebank.chatbot.tools.BankTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central assembly of the {@link ChatClient} — the single entry point through which
 * every user message flows.
 *
 * <p>Three capabilities are wired as defaults:
 * <ol>
 *   <li><b>System prompt</b> — persona + guard-rails for a banking assistant.</li>
 *   <li><b>Tool calling</b> — {@link BankTools} lets the model fetch live account /
 *       transaction data.</li>
 *   <li><b>RAG</b> — {@link QuestionAnswerAdvisor} retrieves the most relevant chunks
 *       from the procedures knowledge base (pgvector) and grounds the answer in them.</li>
 *   <li><b>Memory</b> — {@link MessageChatMemoryAdvisor} keeps a rolling window of the
 *       conversation per session id, enabling natural multi-turn dialogue.</li>
 * </ol>
 */
@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
            You are eBank Assistant, the virtual banking assistant of eBank.

            Guidelines:
            - Answer in the SAME language the user writes in (French or English).
            - For anything about a customer's own balance, account or transactions,
              ALWAYS call the provided tools to get live data. Never invent figures.
            - For "how do I…" questions about banking procedures (international transfers,
              disputing a transaction, blocking a card, opening an account, KYC…),
              rely on the retrieved knowledge-base context and cite the concrete steps.
            - If the retrieved context does not cover the question, say so honestly and
              suggest contacting a human advisor — do not fabricate a procedure.
            - Be concise, clear and friendly. Use short bullet points for step-by-step
              procedures. Never reveal secrets, tokens or internal system details.
            """;

    /** Rolling per-session conversation memory (kept in-memory; swap for a JDBC/Redis
     *  ChatMemory implementation for multi-instance deployments). */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 BankTools bankTools,
                                 VectorStore vectorStore,
                                 ChatMemory chatMemory) {

        // RAG retrieval tuning: only inject context above a similarity floor, top-4 chunks.
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(4)
                .similarityThreshold(0.5)
                .build();

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(bankTools)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(searchRequest)
                                .build())
                .build();
    }
}
