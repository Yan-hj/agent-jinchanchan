package com.game.agent.ingestion.service;

import com.game.agent.common.metadata.DocumentMetadata;
import com.game.agent.common.metadata.SourceType;
import com.game.agent.ingestion.collector.BilibiliContentSource;
import com.game.agent.ingestion.collector.RawContent;
import com.game.agent.ingestion.model.IngestStatus;
import com.game.agent.ingestion.model.IngestTask;
import com.game.agent.ingestion.pipeline.IngestionPipeline;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class SocialIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SocialIngestionService.class);

    private final BilibiliContentSource bilibiliSource;
    private final IngestionPipeline pipeline;
    private final IngestionTaskManager taskManager;
    private final List<CreatorConfig> creators;
    private final Set<String> knownUrls = new HashSet<>();
    private final double socialConfidence;

    public SocialIngestionService(
            BilibiliContentSource bilibiliSource,
            IngestionPipeline pipeline,
            IngestionTaskManager taskManager,
            @Value("${game-agent.ingestion.social.confidence:0.5}") double socialConfidence) {
        this.bilibiliSource = bilibiliSource;
        this.pipeline = pipeline;
        this.taskManager = taskManager;
        this.socialConfidence = socialConfidence;
        this.creators = new ArrayList<>();
    }

    @PostConstruct
    void init() {
        log.info("SocialIngestionService 已就绪，已注册 {} 个UP主", creators.size());
    }

    public void registerCreator(String name, String uid, String spaceUrl) {
        creators.add(new CreatorConfig(name, uid, spaceUrl));
        log.info("注册B站UP主: name={}, uid={}", name, uid);
    }

    public List<CreatorConfig> getCreators() {
        return List.copyOf(creators);
    }

    public List<IngestTask> fetchAllCreators() {
        List<IngestTask> tasks = new ArrayList<>();
        for (CreatorConfig creator : creators) {
            tasks.addAll(fetchCreator(creator));
        }
        return tasks;
    }

    public List<IngestTask> fetchCreator(String nameOrUid) {
        CreatorConfig creator = creators.stream()
                .filter(c -> c.name().equals(nameOrUid) || c.uid().equals(nameOrUid))
                .findFirst()
                .orElse(null);

        if (creator == null) {
            log.warn("未找到UP主: {}", nameOrUid);
            return List.of();
        }

        return fetchCreator(creator);
    }

    private List<IngestTask> fetchCreator(CreatorConfig creator) {
        List<RawContent> contents = bilibiliSource.collectFromSpacePage(creator.spaceUrl(), creator.name());
        if (contents.isEmpty()) {
            log.info("UP主 {} 暂无新视频", creator.name());
            return List.of();
        }

        List<IngestTask> tasks = new ArrayList<>();
        for (RawContent raw : contents) {
            if (raw.url() != null && !knownUrls.add(raw.url())) {
                continue; // dedup
            }
            tasks.add(submitToPipeline(raw, creator.name()));
        }
        log.info("UP主 {} 本次入库 {} 个视频 (总去重数: {})", creator.name(), tasks.size(), knownUrls.size());
        return tasks;
    }

    public IngestTask ingestSingleUrl(String url) {
        var raw = bilibiliSource.collect(url);
        if (raw.isEmpty()) {
            IngestTask task = taskManager.create(
                    UUID.randomUUID().toString().replace("-", ""),
                    SourceType.SOCIAL.value(), url, url);
            task = task.withError("B站视频页抓取失败");
            taskManager.update(task);
            return task;
        }
        return submitToPipeline(raw.get(), raw.get().author());
    }

    private IngestTask submitToPipeline(RawContent raw, String author) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        String sourceName = raw.author() != null && !raw.author().isBlank()
                ? raw.author() : author;
        IngestTask task = taskManager.create(taskId, SourceType.SOCIAL.value(), sourceName, raw.url());

        Map<String, Object> metadata = buildSocialMetadata(raw, author);
        pipeline.execute(raw, metadata,
                progress -> taskManager.update(task.withProgress(progress))
        ).whenComplete((documents, ex) -> {
            if (ex != null) {
                log.error("社媒内容入库失败: {}", raw.url(), ex);
                taskManager.update(task.withError(ex.getMessage()));
            } else {
                log.info("社媒内容入库完成: {} ({} chunks)", raw.url(), documents.size());
                taskManager.update(task.withStatus(IngestStatus.SUCCESS));
            }
        });

        return task;
    }

    private Map<String, Object> buildSocialMetadata(RawContent raw, String author) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(DocumentMetadata.SOURCE_TYPE, SourceType.SOCIAL.value());
        metadata.put(DocumentMetadata.SOURCE, author != null ? author : "unknown");
        metadata.put(DocumentMetadata.AUTHOR, author != null ? author : "unknown");
        metadata.put(DocumentMetadata.PUBLISHED_AT, raw.publishedAt().toString());
        metadata.put(DocumentMetadata.CONFIDENCE_SCORE, socialConfidence);
        if (raw.url() != null) metadata.put(DocumentMetadata.CITATION_URL, raw.url());
        metadata.put("platform", "bilibili");
        return metadata;
    }

    public record CreatorConfig(String name, String uid, String spaceUrl) {}
}
