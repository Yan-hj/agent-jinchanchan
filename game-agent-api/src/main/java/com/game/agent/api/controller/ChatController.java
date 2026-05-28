package com.game.agent.api.controller;

import com.game.agent.api.model.*;
import com.game.agent.api.service.ChatService;
import com.game.agent.common.ApiResponse;
import com.game.agent.common.PagedResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Chat request: sessionId={}, message={}", request.sessionId(), request.message());
        ChatResponse response = chatService.chat(
                request.message(), request.sessionId(), request.seasonTag());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public reactor.core.publisher.Flux<String> chatStream(
            @RequestParam("message") String message,
            @RequestParam(name = "session_id", required = false) String sessionId,
            @RequestParam(name = "season_tag", required = false) String seasonTag) {
        log.info("Chat stream request: sessionId={}", sessionId);
        return chatService.chatStream(message, sessionId, seasonTag)
                .map(text -> "event: message\ndata: " + text + "\n\n");
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<PagedResponse<SessionSummary>>> listSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<SessionSummary> sessions = chatService.listSessions(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(sessions, page, size, sessions.size())));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<SessionSummary>> getSession(@PathVariable String sessionId) {
        return chatService.getSession(sessionId)
                .map(s -> ResponseEntity.ok(ApiResponse.success(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<MessageHistory>>> getMessages(
            @PathVariable String sessionId) {
        List<MessageHistory> messages = chatService.getMessages(sessionId);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }
}
