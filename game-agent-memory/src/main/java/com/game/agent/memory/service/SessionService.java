package com.game.agent.memory.service;

import com.game.agent.memory.model.Session;
import com.game.agent.memory.repository.SessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SessionService {

    private final SessionRepository repository;

    public SessionService(SessionRepository repository) {
        this.repository = repository;
    }

    public Session createSession(String seasonTag) {
        var now = LocalDateTime.now();
        Session session = new Session(null, null, 0, seasonTag, now, now, now);
        return repository.save(session);
    }

    public Optional<Session> getSession(String sessionId) {
        return repository.findById(sessionId);
    }

    public Page<Session> listSessions(Pageable pageable) {
        return repository.findByOrderByLastMessageAtDesc(pageable);
    }

    public void deleteSession(String sessionId) {
        repository.deleteById(sessionId);
    }

    public Session updateTitle(String sessionId, String title) {
        return repository.findById(sessionId).map(s -> {
            var now = LocalDateTime.now();
            return repository.save(s.withTitle(title).withUpdatedAt(now));
        }).orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    public Session touchSession(String sessionId) {
        return repository.findById(sessionId).map(s -> {
            var now = LocalDateTime.now();
            return repository.save(s
                    .withLastMessageAt(now)
                    .withUpdatedAt(now)
                    .withMessageCount(s.messageCount() + 1));
        }).orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }
}
