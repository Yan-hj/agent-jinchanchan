package com.game.agent.knowledge.model;

public record Citation(
        String source,
        String sourceType,
        String title,
        String url,
        String snippet,
        double confidenceScore
) {}
