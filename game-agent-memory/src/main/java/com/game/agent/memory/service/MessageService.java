package com.game.agent.memory.service;

import com.game.agent.memory.model.Citation;
import com.game.agent.memory.model.Message;
import com.game.agent.memory.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    private final MessageRepository repository;

    public MessageService(MessageRepository repository) {
        this.repository = repository;
    }

    public Message addMessage(String sessionId, String role, String text, List<Citation> citations) {
        Message msg = new Message(null, sessionId, role, text,
                citations != null ? citations : List.of(),
                false, null, LocalDateTime.now());
        return repository.save(msg);
    }

    public List<Message> getSessionMessages(String sessionId) {
        return repository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    public int countBySession(String sessionId) {
        return repository.countBySessionId(sessionId);
    }

    public List<Message> getNonDistilledMessages(String sessionId) {
        return repository.findBySessionIdAndDistilledOrderByCreatedAtAsc(sessionId, false);
    }

    public Message markDistilled(String messageId, String summary) {
        return repository.findById(messageId).map(m ->
                repository.save(m.withDistilled(true).withDistillSummary(summary))
        ).orElse(null);
    }

    public void deleteSessionMessages(String sessionId) {
        List<Message> msgs = repository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        repository.deleteAll(msgs);
    }
}
