package com.game.agent.memory.model;

public record Citation(
        String source,
        String sourceType,
        String title,
        String url,
        String snippet,
        double confidenceScore
) {}
