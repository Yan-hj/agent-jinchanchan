package com.game.agent.api.model;

import java.time.LocalDateTime;

public record SessionSummary(
        String sessionId,
        String title,
        int messageCount,
        String seasonTag,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt
) {}
