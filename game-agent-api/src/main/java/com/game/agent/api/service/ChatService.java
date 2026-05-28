package com.game.agent.api.service;

import com.game.agent.api.model.*;
import com.game.agent.core.GameAgent;
import com.game.agent.core.model.AgentResponse;
import com.game.agent.knowledge.model.Citation;
import com.game.agent.memory.model.Message;
import com.game.agent.memory.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final GameAgent gameAgent;
    private final MemoryService memoryService;

    public ChatService(GameAgent gameAgent, MemoryService memoryService) {
        this.gameAgent = gameAgent;
        this.memoryService = memoryService;
    }

    public ChatResponse chat(String message, String sessionId, String seasonTag) {
        sessionId = resolveSessionId(sessionId);
        memoryService.addUserMessage(sessionId, message);

        var history = memoryService.getHistory(sessionId);
        AgentResponse result = history.isEmpty()
                ? gameAgent.chat(message, seasonTag)
                : gameAgent.chatWithHistory(message, seasonTag, history);

        List<CitationResponse> citations = toCitationResponse(result.citations());
        Message saved = memoryService.addAssistantMessage(sessionId, result.content(),
                result.citations().stream().map(this::toMemoryCitation).toList());

        return new ChatResponse(saved.id(), sessionId, result.content(), citations, null);
    }

    public Flux<String> chatStream(String message, String sessionId, String seasonTag) {
        final String sid = resolveSessionId(sessionId);
        memoryService.addUserMessage(sid, message);

        return gameAgent.chatStream(message, seasonTag)
                .doOnComplete(() -> log.debug("Stream completed for session: {}", sid));
    }

    public List<SessionSummary> listSessions(int page, int size) {
        return memoryService.listSessions(page, size).stream()
                .map(ChatService::toSessionSummary)
                .toList();
    }

    public Optional<SessionSummary> getSession(String sessionId) {
        return memoryService.getSession(sessionId).map(ChatService::toSessionSummary);
    }

    public List<MessageHistory> getMessages(String sessionId) {
        return memoryService.getHistory(sessionId).stream()
                .map(m -> {
                    String role = switch (m.getMessageType()) {
                        case USER -> "user";
                        case ASSISTANT -> "assistant";
                        default -> "system";
                    };
                    return new MessageHistory(role, m.getText(), null, null);
                })
                .toList();
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId != null && memoryService.getSession(sessionId).isPresent()) {
            return sessionId;
        }
        return memoryService.createSession(null);
    }

    private List<CitationResponse> toCitationResponse(List<Citation> citations) {
        return citations.stream()
                .map(c -> new CitationResponse(c.source(), c.sourceType(), c.title(),
                        c.url(), c.snippet(), c.confidenceScore()))
                .toList();
    }

    private com.game.agent.memory.model.Citation toMemoryCitation(Citation c) {
        return new com.game.agent.memory.model.Citation(
                c.source(), c.sourceType(), c.title(), c.url(), c.snippet(), c.confidenceScore());
    }

    private static SessionSummary toSessionSummary(com.game.agent.memory.model.Session s) {
        return new SessionSummary(s.id(), s.title(), s.messageCount(),
                s.seasonTag(), s.lastMessageAt(), s.createdAt());
    }
}
