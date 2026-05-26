package com.game.agent.common.metadata;

public enum SourceType {
    OFFICIAL("official"),
    SOCIAL("social"),
    SKILL("skill");

    private final String value;

    SourceType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static SourceType fromValue(String value) {
        for (SourceType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown source type: " + value);
    }
}
