package com.game.agent.ingestion.repository;

import com.game.agent.ingestion.model.IngestStatus;
import com.game.agent.ingestion.model.IngestTask;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngestTaskRepository extends MongoRepository<IngestTask, String> {

    List<IngestTask> findByStatus(IngestStatus status);
}
