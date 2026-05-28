package com.game.agent.api.model;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        String sessionId,
        @NotBlank(message = "message is required") String message,
        String seasonTag,
        boolean voiceMode
) {}
