package com.game.agent.api.model;

public record CitationResponse(
        String source,
        String sourceType,
        String title,
        String url,
        String snippet,
        double confidenceScore
) {}
