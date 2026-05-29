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

/**
 * 社媒内容采集服务。目前实现 B 站 UP 主视频内容的批量采集入库。
 * <p>
 * 工作流程：
 * 1. 启动时从配置注册 UP 主列表（红莲、神超等）
 * 2. fetchAllCreators() 遍历所有 UP 主，调用 BilibiliContentSource 爬取空间页视频列表
 * 3. URL 去重（knownUrls），新视频提交到 IngestionPipeline 入库
 * <p>
 * 社媒内容的 confidence_score 较低（默认 0.5），表示可信度不如官方内容（1.0）。
 * 这会影响 Agent 在检索排序时的权重。
 */
@Service
public class SocialIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SocialIngestionService.class);

    private final BilibiliContentSource bilibiliSource;
    private final IngestionPipeline pipeline;
    private final IngestionTaskManager taskManager;
    private final List<CreatorConfig> creators;
    private final Set<String> knownUrls = new HashSet<>();  // URL 去重集合
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

    /** 注册一个 B 站 UP 主 */
    public void registerCreator(String name, String uid, String spaceUrl) {
        creators.add(new CreatorConfig(name, uid, spaceUrl));
        log.info("注册B站UP主: name={}, uid={}", name, uid);
    }

    public List<CreatorConfig> getCreators() {
        return List.copyOf(creators);
    }

    /** 抓取所有已注册 UP 主的最新视频 */
    public List<IngestTask> fetchAllCreators() {
        List<IngestTask> tasks = new ArrayList<>();
        for (CreatorConfig creator : creators) {
            tasks.addAll(fetchCreator(creator));
        }
        return tasks;
    }

    /** 按名称或 UID 抓取指定 UP 主 */
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

    /** 抓取单个 UP 主的空间页，去重后入库 */
    private List<IngestTask> fetchCreator(CreatorConfig creator) {
        List<RawContent> contents = bilibiliSource.collectFromSpacePage(creator.spaceUrl(), creator.name());
        if (contents.isEmpty()) {
            log.info("UP主 {} 暂无新视频", creator.name());
            return List.of();
        }

        List<IngestTask> tasks = new ArrayList<>();
        for (RawContent raw : contents) {
            if (raw.url() != null && !knownUrls.add(raw.url())) {
                continue; // 已抓取过的 URL 跳过
            }
            tasks.add(submitToPipeline(raw, creator.name()));
        }
        log.info("UP主 {} 本次入库 {} 个视频 (总去重数: {})", creator.name(), tasks.size(), knownUrls.size());
        return tasks;
    }

    /** 抓取单个 B 站视频链接并入库 */
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

    /** 提交到 IngestionPipeline 异步入库 */
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

    /**
     * 社媒 metadata 的特点：
     * - source_type = social
     * - 包含 author（UP主名）
     * - confidence_score 较低（0.5），因为社媒内容可靠性不如官方公告
     * - 标记 platform = bilibili
     */
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

    /** UP 主配置：name（显示名）、uid（B站UID）、spaceUrl（空间页URL） */
    public record CreatorConfig(String name, String uid, String spaceUrl) {}
}
