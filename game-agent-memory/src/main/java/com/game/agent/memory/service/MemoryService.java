package com.game.agent.memory.service;

import com.game.agent.memory.model.Citation;
import com.game.agent.memory.model.Message;
import com.game.agent.memory.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final SessionService sessionService;
    private final MessageService messageService;
    private final DistillService distillService;
    private final int distillThreshold;

    public MemoryService(
            SessionService sessionService,
            MessageService messageService,
            @Autowired(required = false) DistillService distillService,
            @Value("${game-agent.memory.distill-threshold:10}") int distillThreshold) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.distillService = distillService;
        this.distillThreshold = distillThreshold;
    }

    public String createSession(String seasonTag) {
        Session session = sessionService.createSession(seasonTag);
        log.debug("Created session: {}", session.id());
        return session.id();
    }

    public Optional<Session> getSession(String sessionId) {
        return sessionService.getSession(sessionId);
    }

    public List<Session> listSessions(int page, int size) {
        return sessionService.listSessions(
                org.springframework.data.domain.PageRequest.of(page, size)).getContent();
    }

    public Message addUserMessage(String sessionId, String text) {
        int count = messageService.countBySession(sessionId);
        Message msg = messageService.addMessage(sessionId, "user", text, null);
        sessionService.touchSession(sessionId);

        if (count == 0 && text.length() > 0) {
            String title = text.length() > 30 ? text.substring(0, 30) + "..." : text;
            sessionService.updateTitle(sessionId, title);
        }

        return msg;
    }

    public Message addAssistantMessage(String sessionId, String text, List<Citation> citations) {
        Message msg = messageService.addMessage(sessionId, "assistant", text, citations);
        sessionService.touchSession(sessionId);

        int count = messageService.countBySession(sessionId);
        if (distillService != null && count > 0 && count % distillThreshold == 0) {
            try {
                distillService.distill(sessionId);
            } catch (Exception e) {
                log.warn("Distill failed for session: {}", sessionId, e);
            }
        }

        return msg;
    }

    public List<org.springframework.ai.chat.messages.Message> getHistory(String sessionId) {
        List<Message> messages = messageService.getSessionMessages(sessionId);
        List<org.springframework.ai.chat.messages.Message> result = new ArrayList<>();

        for (Message msg : messages) {
            if (msg.distilled() && msg.distillSummary() != null) {
                result.add(new SystemMessage(msg.distillSummary()));
            } else if ("user".equals(msg.role())) {
                result.add(new UserMessage(msg.text()));
            } else if ("assistant".equals(msg.role())) {
                result.add(new AssistantMessage(msg.text()));
            }
        }

        return result;
    }

    public void deleteSession(String sessionId) {
        messageService.deleteSessionMessages(sessionId);
        sessionService.deleteSession(sessionId);
    }
}
