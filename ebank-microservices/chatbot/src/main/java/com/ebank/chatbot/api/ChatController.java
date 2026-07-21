package com.ebank.chatbot.api;

import com.ebank.chatbot.api.dto.ChatRequest;
import com.ebank.chatbot.api.dto.ChatResponse;
import com.ebank.chatbot.service.ChatbotService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

/**
 * Public chatbot HTTP API. Routed by the gateway under {@code /api/chat/**}.
 *
 * <ul>
 *   <li>{@code POST /api/chat}        — blocking, returns the full answer.</li>
 *   <li>{@code POST /api/chat/stream} — Server-Sent Events, streams the answer token by token.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chatbot", description = "eBank AI assistant — tool calling + RAG")
public class ChatController {

    private final ChatbotService chatbotService;

    public ChatController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping
    @Operation(summary = "Send a message and get the full answer")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String sessionId = ChatbotService.resolveSessionId(request.sessionId());
        String answer = chatbotService.chat(sessionId, request.message());
        return ChatResponse.of(sessionId, answer);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send a message and stream the answer via Server-Sent Events")
    public Flux<String> stream(@Valid @RequestBody ChatRequest request) {
        String sessionId = ChatbotService.resolveSessionId(request.sessionId());
        return chatbotService.stream(sessionId, request.message());
    }
}
