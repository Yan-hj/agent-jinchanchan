package com.game.agent.api.security;

public enum SourcePermission {
    READ_OFFICIAL("official"),
    READ_SOCIAL("social"),
    READ_SKILL("skill");

    private final String sourceType;

    SourcePermission(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public static SourcePermission fromSourceType(String sourceType) {
        for (SourcePermission p : values()) {
            if (p.sourceType.equals(sourceType)) {
                return p;
            }
        }
        return null;
    }
}
