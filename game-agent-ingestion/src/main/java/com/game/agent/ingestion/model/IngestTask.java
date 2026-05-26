package com.game.agent.ingestion.model;

import com.game.agent.ingestion.pipeline.IngestionProgress;

import java.time.Instant;

public record IngestTask(
        String taskId,
        IngestStatus status,
        String sourceType,
        String sourceUrl,
        String documentName,
        IngestionProgress progress,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public IngestTask withStatus(IngestStatus newStatus) {
        return new IngestTask(taskId, newStatus, sourceType, sourceUrl, documentName,
                progress, errorMessage, createdAt, Instant.now());
    }

    public IngestTask withProgress(IngestionProgress newProgress) {
        return new IngestTask(taskId, status, sourceType, sourceUrl, documentName,
                newProgress, errorMessage, createdAt, Instant.now());
    }

    public IngestTask withError(String error) {
        return new IngestTask(taskId, IngestStatus.FAILED, sourceType, sourceUrl, documentName,
                progress, error, createdAt, Instant.now());
    }
}
