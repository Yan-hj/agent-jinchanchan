package com.game.agent.memory.repository;

import com.game.agent.memory.model.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SessionRepository extends MongoRepository<Session, String> {

    Page<Session> findByOrderByLastMessageAtDesc(Pageable pageable);
}
