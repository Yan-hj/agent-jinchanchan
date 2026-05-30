package com.game.agent.api.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class PermissionConfig {

    private static final Map<String, Set<String>> ROLE_SOURCES = Map.of(
            "ADMIN", Set.of("official", "social", "skill"),
            "ANALYST", Set.of("official", "social"),
            "BASIC", Set.of("official")
    );

    public boolean hasPermission(String role, String sourceType) {
        Set<String> allowed = ROLE_SOURCES.get(role);
        return allowed != null && allowed.contains(sourceType);
    }

    public Set<String> getAllowedSources(String role) {
        return ROLE_SOURCES.getOrDefault(role, Set.of());
    }

    public boolean isAdmin(String role) {
        return "ADMIN".equals(role);
    }

    public static Map<String, Set<String>> getRoleSources() {
        return ROLE_SOURCES;
    }
}
