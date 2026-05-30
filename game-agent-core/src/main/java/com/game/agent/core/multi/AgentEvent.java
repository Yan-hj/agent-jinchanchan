package com.game.agent.core.multi;

import java.time.Instant;
import java.util.UUID;

public abstract class AgentEvent {

    private final String correlationId;
    private final AgentRole sourceAgent;
    private final AgentEventType type;
    private final Instant timestamp;

    public AgentEvent(AgentRole sourceAgent, AgentEventType type) {
        this(UUID.randomUUID().toString().replace("-", ""), sourceAgent, type);
    }

    public AgentEvent(String correlationId, AgentRole sourceAgent, AgentEventType type) {
        this.correlationId = correlationId;
        this.sourceAgent = sourceAgent;
        this.type = type;
        this.timestamp = Instant.now();
    }

    public String getCorrelationId() { return correlationId; }
    public AgentRole getSourceAgent() { return sourceAgent; }
    public AgentEventType getType() { return type; }
    public Instant getTimestamp() { return timestamp; }

    public enum AgentEventType {
        QUERY_REQUEST,
        SEARCH_REQUEST, SEARCH_RESPONSE,
        STRATEGY_REQUEST, STRATEGY_RESPONSE,
        LINEUP_REQUEST, LINEUP_RESPONSE
    }
}
