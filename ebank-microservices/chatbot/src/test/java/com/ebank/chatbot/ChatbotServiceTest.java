package com.ebank.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.ebank.chatbot.service.ChatbotService;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests — no Spring context, no DB, no LLM. They keep {@code mvn test}
 * green in CI without external infrastructure.
 */
class ChatbotServiceTest {

    @Test
    void resolveSessionId_generatesOneWhenMissing() {
        String a = ChatbotService.resolveSessionId(null);
        String b = ChatbotService.resolveSessionId("  ");
        assertNotNull(a);
        assertNotNull(b);
        assertNotEquals(a, b, "each blank request should get its own session id");
    }

    @Test
    void resolveSessionId_keepsProvidedId() {
        assertEquals("sess-123", ChatbotService.resolveSessionId("sess-123"));
    }
}
