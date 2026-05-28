package com.game.agent.api.model;

import java.util.List;

public record ChatResponse(
        String messageId,
        String sessionId,
        String textAdvice,
        List<CitationResponse> citations,
        String voiceText
) {}
