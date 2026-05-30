package com.game.agent.api.model.auth;

import java.util.Set;

public record LoginResponse(
        String token,
        String userId,
        String role,
        Set<String> allowedSources
) {}
