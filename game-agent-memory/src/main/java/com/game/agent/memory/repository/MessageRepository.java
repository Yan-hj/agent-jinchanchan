package com.game.agent.memory.repository;

import com.game.agent.memory.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {

    List<Message> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    int countBySessionId(String sessionId);

    List<Message> findBySessionIdAndDistilledOrderByCreatedAtAsc(String sessionId, boolean distilled);
}
