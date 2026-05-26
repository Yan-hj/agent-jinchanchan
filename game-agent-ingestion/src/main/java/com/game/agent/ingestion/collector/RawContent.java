package com.game.agent.ingestion.collector;

import java.time.Instant;

public record RawContent(
        String title,
        String body,
        String url,
        Instant publishedAt,
        String author
) {}
