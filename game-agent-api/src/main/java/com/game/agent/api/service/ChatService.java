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

/**
 * 对话服务，串联 GameAgent（LLM 问答）和 MemoryService（会话存储）。
 * <p>
 * 职责：
 * 1. 管理会话生命周期（创建/复用 session）
 * 2. 消息持久化（用户消息 → 存库 → Agent 回答 → 存库）
 * 3. 调用 GameAgent 获取 LLM 回复
 * 4. 转换数据格式（Citation 转 DTO）
 * <p>
 * 每次 chat() 的流程：
 * resolveSessionId → saveUserMessage → getHistory → gameAgent.chat → saveAssistantMessage → 返回
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final GameAgent gameAgent;
    private final MemoryService memoryService;

    public ChatService(GameAgent gameAgent, MemoryService memoryService) {
        this.gameAgent = gameAgent;
        this.memoryService = memoryService;
    }

    /**
     * 同步问答：创建/复用会话 → 存用户消息 → 调 Agent → 存回答 → 返回。
     */
    public ChatResponse chat(String message, String sessionId, String seasonTag) {
        sessionId = resolveSessionId(sessionId);
        memoryService.addUserMessage(sessionId, message);

        List<org.springframework.ai.chat.messages.Message> history = memoryService.getHistory(sessionId);
        AgentResponse result = history.isEmpty()
                ? gameAgent.chat(message, seasonTag)
                : gameAgent.chatWithHistory(message, seasonTag, history);

        List<CitationResponse> citations = toCitationResponse(result.citations());
        Message saved = memoryService.addAssistantMessage(sessionId, result.content(),
                result.citations().stream().map(this::toMemoryCitation).toList());

        return new ChatResponse(saved.id(), sessionId, result.content(), citations, null);
    }

    /**
     * SSE 流式问答：暂不持久化助手回复（流式文本是逐 token 到达的，完整保存需要额外逻辑）。
     */
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

    /** 获取会话历史消息（转为前端 DTO） */
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

    /**
     * 解析 sessionId：如果传了有效的 sessionId 则复用，否则创建新会话。
     */
    private String resolveSessionId(String sessionId) {
        if (sessionId != null && memoryService.getSession(sessionId).isPresent()) {
            return sessionId;
        }
        return memoryService.createSession(null);
    }

    /** knowledge 模块的 Citation → API 响应 DTO */
    private List<CitationResponse> toCitationResponse(List<Citation> citations) {
        return citations.stream()
                .map(c -> new CitationResponse(c.source(), c.sourceType(), c.title(),
                        c.url(), c.snippet(), c.confidenceScore()))
                .toList();
    }

    /** knowledge 模块的 Citation → memory 模块的 Citation（用于持久化） */
    private com.game.agent.memory.model.Citation toMemoryCitation(Citation c) {
        return new com.game.agent.memory.model.Citation(
                c.source(), c.sourceType(), c.title(), c.url(), c.snippet(), c.confidenceScore());
    }

    private static SessionSummary toSessionSummary(com.game.agent.memory.model.Session s) {
        return new SessionSummary(s.id(), s.title(), s.messageCount(),
                s.seasonTag(), s.lastMessageAt(), s.createdAt());
    }
}
