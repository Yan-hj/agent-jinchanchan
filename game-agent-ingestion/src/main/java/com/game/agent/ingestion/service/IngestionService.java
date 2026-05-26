package com.game.agent.ingestion.service;

import com.game.agent.common.metadata.DocumentMetadata;
import com.game.agent.common.metadata.SourceType;
import com.game.agent.ingestion.collector.RawContent;
import com.game.agent.ingestion.model.IngestStatus;
import com.game.agent.ingestion.model.IngestTask;
import com.game.agent.ingestion.pipeline.IngestionPipeline;
import com.game.agent.ingestion.pipeline.IngestionProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestionPipeline pipeline;
    private final IngestionTaskManager taskManager;

    public IngestionService(IngestionPipeline pipeline, IngestionTaskManager taskManager) {
        this.pipeline = pipeline;
        this.taskManager = taskManager;
    }

    public IngestTask ingestUrl(String url, SourceType sourceType, String tags) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        IngestTask task = taskManager.create(taskId, sourceType.value(), url, url);

        pipeline.execute(
                createRawContent(url, sourceType, url),
                buildMetadata(sourceType, url, null, tags),
                progress -> taskManager.update(task.withProgress(progress))
        ).whenComplete((documents, ex) -> {
            if (ex != null) {
                log.error("Ingestion failed for URL: {}", url, ex);
                taskManager.update(task.withError(ex.getMessage()));
            } else {
                log.info("Ingestion completed for URL: {} ({} chunks)", url, documents.size());
                taskManager.update(task.withStatus(IngestStatus.SUCCESS));
            }
        });

        return task;
    }

    public IngestTask ingestDocument(String fileName, String content, SourceType sourceType, String tags) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        IngestTask task = taskManager.create(taskId, sourceType.value(), fileName, fileName);

        RawContent raw = new RawContent(fileName, content, null, Instant.now(), null);

        pipeline.execute(
                raw,
                buildMetadata(sourceType, null, fileName, tags),
                progress -> taskManager.update(task.withProgress(progress))
        ).whenComplete((documents, ex) -> {
            if (ex != null) {
                log.error("Ingestion failed for file: {}", fileName, ex);
                taskManager.update(task.withError(ex.getMessage()));
            } else {
                log.info("Ingestion completed for file: {} ({} chunks)", fileName, documents.size());
                taskManager.update(task.withStatus(IngestStatus.SUCCESS));
            }
        });

        return task;
    }

    private RawContent createRawContent(String title, SourceType sourceType, String url) {
        return new RawContent(title, null, url, Instant.now(), null);
    }

    private Map<String, Object> buildMetadata(SourceType sourceType, String url, String fileName, String tags) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(DocumentMetadata.SOURCE_TYPE, sourceType.value());
        metadata.put(DocumentMetadata.PUBLISHED_AT, Instant.now().toString());
        metadata.put(DocumentMetadata.CONFIDENCE_SCORE, 1.0);
        if (url != null) metadata.put(DocumentMetadata.CITATION_URL, url);
        if (fileName != null) metadata.put(DocumentMetadata.SOURCE, fileName);
        if (tags != null && !tags.isBlank()) metadata.put("tags", tags);
        return metadata;
    }

    public IngestTask getTask(String taskId) {
        return taskManager.get(taskId);
    }

    public java.util.List<IngestTask> listTasks(IngestStatus statusFilter) {
        return taskManager.list(statusFilter);
    }
}
