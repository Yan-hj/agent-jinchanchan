package com.game.agent.ingestion.event;

import java.time.Instant;

public record PatchDetectedEvent(
        String source,
        String sourceUrl,
        String versionTag,
        Instant detectedAt
) {
    public PatchDetectedEvent(String source, String sourceUrl, String versionTag) {
        this(source, sourceUrl, versionTag, Instant.now());
    }
}
