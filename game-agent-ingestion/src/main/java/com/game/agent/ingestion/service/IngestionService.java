package com.game.agent.ingestion.service;

import com.game.agent.common.metadata.DocumentMetadata;
import com.game.agent.common.metadata.SourceType;
import com.game.agent.ingestion.collector.LocalFileCollector;
import com.game.agent.ingestion.collector.RawContent;
import com.game.agent.ingestion.model.IngestStatus;
import com.game.agent.ingestion.model.IngestTask;
import com.game.agent.ingestion.pipeline.IngestionPipeline;
import com.game.agent.ingestion.pipeline.IngestionProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库入库服务，提供三种内容来源的统一编排：
 * - ingestUrl：从网页 URL 抓取并入库
 * - ingestDocument：上传文件（MultipartFile）入库
 * - ingestLocalFile：本地磁盘文件入库
 * <p>
 * 三种方式最终都走 IngestionPipeline（分块 → embedding → 写入 ES VectorStore）。
 * 每个入库请求创建一个 IngestTask，用于追踪处理进度。
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestionPipeline pipeline;
    private final IngestionTaskManager taskManager;
    private final LocalFileCollector localFileCollector;

    public IngestionService(IngestionPipeline pipeline, IngestionTaskManager taskManager,
                            LocalFileCollector localFileCollector) {
        this.pipeline = pipeline;
        this.taskManager = taskManager;
        this.localFileCollector = localFileCollector;
    }

    /**
     * 导入本地磁盘文件（支持 .md 和 .txt）。
     * 自动读取文件内容 → IngestionPipeline 入库。
     */
    public IngestTask ingestLocalFile(String filePath, SourceType sourceType, String tags) {
        var raw = localFileCollector.collect(filePath);
        if (raw.isEmpty()) {
            String taskId = UUID.randomUUID().toString().replace("-", "");
            IngestTask task = taskManager.create(taskId, sourceType.value(), filePath, filePath);
            task = task.withError("文件读取失败或格式不支持: " + filePath);
            taskManager.update(task);
            return task;
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        String fileName = Path.of(filePath).getFileName().toString();
        IngestTask task = taskManager.create(taskId, sourceType.value(), fileName, filePath);

        pipeline.execute(
                raw.get(),
                buildMetadata(sourceType, null, fileName, tags),
                progress -> taskManager.update(task.withProgress(progress))
        ).whenComplete((documents, ex) -> {
            if (ex != null) {
                log.error("Ingestion failed for file: {}", filePath, ex);
                taskManager.update(task.withError(ex.getMessage()));
            } else {
                log.info("Ingestion completed for file: {} ({} chunks)", filePath, documents.size());
                taskManager.update(task.withStatus(IngestStatus.SUCCESS));
            }
        });

        return task;
    }

    /**
     * 从 URL 抓取网页内容并入库。
     * 使用 WebPageCollector 下载 HTML，Jsoup 清洗后入库。
     */
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

    /**
     * 通过 MultipartFile 上传文件并入库。
     */
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

    /**
     * 构建 Document metadata。
     * 这些 metadata 会随 Document 一起存入 ES，后续检索时作为过滤条件和 Citation 来源。
     */
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
