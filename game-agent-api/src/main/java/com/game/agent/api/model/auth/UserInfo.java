package com.game.agent.api.model.auth;

import java.util.Set;

public record UserInfo(
        String userId,
        String role,
        Set<String> allowedSources
) {}
