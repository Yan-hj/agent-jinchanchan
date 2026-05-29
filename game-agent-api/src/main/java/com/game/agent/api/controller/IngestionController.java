package com.game.agent.api.controller;

import com.game.agent.common.ApiResponse;
import com.game.agent.common.ErrorCode;
import com.game.agent.common.metadata.SourceType;
import com.game.agent.ingestion.collector.FileCollector;
import com.game.agent.ingestion.collector.WebPageCollector;
import com.game.agent.ingestion.model.IngestStatus;
import com.game.agent.ingestion.model.IngestTask;
import com.game.agent.ingestion.service.IngestionService;
import com.game.agent.ingestion.service.SocialIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

    private final IngestionService ingestionService;
    private final SocialIngestionService socialIngestionService;
    private final WebPageCollector webPageCollector;
    private final FileCollector fileCollector;

    public IngestionController(IngestionService ingestionService,
                                SocialIngestionService socialIngestionService,
                                WebPageCollector webPageCollector,
                                FileCollector fileCollector) {
        this.ingestionService = ingestionService;
        this.socialIngestionService = socialIngestionService;
        this.webPageCollector = webPageCollector;
        this.fileCollector = fileCollector;
    }

    @PostMapping("/document")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "source_type", required = false) String sourceType,
            @RequestParam(name = "tags", required = false) String tags) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.PARAM_INVALID, "File is empty"));
        }

        var raw = fileCollector.collectFile(file);
        if (raw.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.INGEST_FORMAT_UNSUPPORTED,
                            "Unsupported file format. Only .md and .txt are supported."));
        }

        SourceType type = resolveSourceType(sourceType);
        IngestTask task = ingestionService.ingestDocument(
                file.getOriginalFilename(), raw.get().body(), type, tags);

        log.info("Document upload submitted: file={}, taskId={}", file.getOriginalFilename(), task.taskId());

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "task_id", task.taskId(),
                "status", task.status().name(),
                "document_name", file.getOriginalFilename()
        )));
    }

    @PostMapping("/url")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importUrl(
            @RequestBody UrlImportRequest request) {

        if (!webPageCollector.supportsUrl(request.url())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.INGEST_URL_UNREACHABLE, "Invalid or unsupported URL"));
        }

        SourceType type = resolveSourceType(request.sourceType());
        IngestTask task = ingestionService.ingestUrl(request.url(), type, request.tags());

        log.info("URL import submitted: url={}, taskId={}", request.url(), task.taskId());

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "task_id", task.taskId(),
                "status", task.status().name(),
                "url", request.url()
        )));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<ApiResponse<IngestTask>> getTask(@PathVariable String taskId) {
        IngestTask task = ingestionService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "Task not found: " + taskId));
        }
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<List<IngestTask>>> listTasks(
            @RequestParam(name = "status", required = false) String status) {

        IngestStatus filter = (status != null) ? IngestStatus.valueOf(status.toUpperCase()) : null;
        return ResponseEntity.ok(ApiResponse.success(ingestionService.listTasks(filter)));
    }

    @PostMapping("/trigger")
    public ResponseEntity<ApiResponse<Map<String, String>>> triggerIngestion(
            @RequestBody TriggerRequest request) {

        log.info("Manual trigger received: source={}, priority={}", request.source(), request.priority());

        return ResponseEntity.ok(ApiResponse.error("NOT_IMPLEMENTED", "Manual trigger not yet implemented"));
    }

    private SourceType resolveSourceType(String value) {
        if (value == null || value.isBlank()) return SourceType.OFFICIAL;
        try {
            return SourceType.fromValue(value);
        } catch (IllegalArgumentException e) {
            return SourceType.OFFICIAL;
        }
    }

    @PostMapping("/local-file")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ingestLocalFile(
            @RequestBody LocalFileRequest request) {

        Path filePath = Path.of(request.filePath());
        if (!filePath.toFile().exists()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.PARAM_INVALID, "File not found: " + request.filePath()));
        }

        SourceType type = resolveSourceType(request.sourceType());
        IngestTask task = ingestionService.ingestLocalFile(request.filePath(), type, request.tags());

        log.info("Local file ingestion submitted: path={}, taskId={}", request.filePath(), task.taskId());

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "task_id", task.taskId(),
                "status", task.status().name(),
                "file_path", request.filePath()
        )));
    }

    // ─── 社媒采集 ───

    @PostMapping("/social/fetch-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fetchAllSocial() {
        var tasks = socialIngestionService.fetchAllCreators();
        log.info("社媒全量抓取触发，生成 {} 个采集任务", tasks.size());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "task_count", tasks.size(),
                "task_ids", tasks.stream().map(IngestTask::taskId).toList()
        )));
    }

    @PostMapping("/social/fetch/{creator}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fetchSocialCreator(
            @PathVariable String creator) {
        var tasks = socialIngestionService.fetchCreator(creator);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "creator", creator,
                "task_count", tasks.size(),
                "task_ids", tasks.stream().map(IngestTask::taskId).toList()
        )));
    }

    @PostMapping("/social/url")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ingestSocialUrl(
            @RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.PARAM_INVALID, "url is required"));
        }
        var task = socialIngestionService.ingestSingleUrl(url);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "task_id", task.taskId(),
                "status", task.status().name(),
                "url", url
        )));
    }

    @GetMapping("/social/creators")
    public ResponseEntity<ApiResponse<List<SocialIngestionService.CreatorConfig>>> listCreators() {
        return ResponseEntity.ok(ApiResponse.success(socialIngestionService.getCreators()));
    }

    public record UrlImportRequest(String url, String sourceType, String tags) {}
    public record LocalFileRequest(String filePath, String sourceType, String tags) {}
    public record TriggerRequest(String source, String priority) {}
}
