package com.game.agent.api.model;

import java.time.LocalDateTime;
import java.util.List;

public record MessageHistory(
        String role,
        String text,
        List<CitationResponse> citations,
        LocalDateTime createdAt
) {}
