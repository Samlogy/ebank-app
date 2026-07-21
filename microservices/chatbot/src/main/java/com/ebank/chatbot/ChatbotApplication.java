package com.ebank.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * eBank AI Chatbot microservice.
 *
 * <p>Built on Spring AI, it combines two capabilities behind a single ChatClient:
 * <ul>
 *   <li><b>Tool Calling</b> — the LLM calls typed Java functions ({@code BankTools})
 *       that fetch live data from the accounts &amp; transactions services.</li>
 *   <li><b>RAG</b> — questions about complex banking <i>procedures</i> are answered
 *       from an internal knowledge base (markdown documents) indexed in a
 *       pgvector store and injected via a {@code QuestionAnswerAdvisor}.</li>
 * </ul>
 */
@SpringBootApplication
public class ChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }
}
