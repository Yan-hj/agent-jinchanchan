package com.game.agent.ingestion.event;

import com.game.agent.common.metadata.SourceType;
import com.game.agent.ingestion.service.IngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class PatchDetectionListener {

    private static final Logger log = LoggerFactory.getLogger(PatchDetectionListener.class);

    private final IngestionService ingestionService;

    public PatchDetectionListener(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Async("highPriorityIngestExecutor")
    @EventListener
    public void onPatchDetected(PatchDetectedEvent event) {
        log.info("High-priority ingestion triggered by patch: version={}, source={}",
                event.versionTag(), event.source());

        ingestionService.ingestUrl(event.sourceUrl(), SourceType.OFFICIAL, event.versionTag());
    }
}
