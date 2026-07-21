package com.ebank.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Flux;

/**
 * Thin orchestration layer over the configured {@link ChatClient}.
 *
 * <p>Every request is bound to a {@code sessionId} so the memory advisor can keep
 * per-conversation context. Both a blocking and a streaming variant are exposed.
 */
@Service
public class ChatbotService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);

    private final ChatClient chatClient;

    public ChatbotService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /** Blocking single-shot answer. */
    public String chat(String sessionId, String message) {
        log.info("[chat] session={} message='{}'", sessionId, message);
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
    }

    /** Streaming answer — emits response tokens as they are generated (for SSE / WebSocket). */
    public Flux<String> stream(String sessionId, String message) {
        log.info("[chat-stream] session={} message='{}'", sessionId, message);
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content();
    }

    public static String resolveSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId : java.util.UUID.randomUUID().toString();
    }
}
