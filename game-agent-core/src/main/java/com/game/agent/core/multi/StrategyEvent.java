package com.game.agent.core.multi;

import com.game.agent.knowledge.model.Citation;

import java.util.List;

public class StrategyEvent extends AgentEvent {

    private final String query;
    private final String seasonTag;
    private final List<Citation> contextCitations;
    private final String response;

    /** 请求事件 */
    public StrategyEvent(String correlationId, AgentRole source, String query, String seasonTag,
                         List<Citation> contextCitations) {
        super(correlationId, source, AgentEventType.STRATEGY_REQUEST);
        this.query = query;
        this.seasonTag = seasonTag;
        this.contextCitations = contextCitations;
        this.response = null;
    }

    /** 响应事件 */
    public StrategyEvent(String correlationId, AgentRole source, String response) {
        super(correlationId, source, AgentEventType.STRATEGY_RESPONSE);
        this.query = null;
        this.seasonTag = null;
        this.contextCitations = null;
        this.response = response;
    }

    public String getQuery() { return query; }
    public String getSeasonTag() { return seasonTag; }
    public List<Citation> getContextCitations() { return contextCitations; }
    public String getResponse() { return response; }
}
