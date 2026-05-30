package com.game.agent.core.multi;

import com.game.agent.knowledge.model.Citation;
import com.game.agent.knowledge.model.RetrievalResult;

import java.util.List;

public class SearchEvent extends AgentEvent {

    private final String query;
    private final String seasonTag;
    private final String filter;
    private final RetrievalResult result;

    /** 请求事件 */
    public SearchEvent(String correlationId, AgentRole source, String query, String seasonTag) {
        super(correlationId, source, AgentEventType.SEARCH_REQUEST);
        this.query = query;
        this.seasonTag = seasonTag;
        this.filter = null;
        this.result = null;
    }

    /** 响应事件 */
    public SearchEvent(String correlationId, AgentRole source, RetrievalResult result) {
        super(correlationId, source, AgentEventType.SEARCH_RESPONSE);
        this.query = null;
        this.seasonTag = null;
        this.filter = null;
        this.result = result;
    }

    public String getQuery() { return query; }
    public String getSeasonTag() { return seasonTag; }
    public String getFilter() { return filter; }
    public RetrievalResult getResult() { return result; }
}
