package com.game.agent.core.multi;

import com.game.agent.knowledge.model.RetrievalResult;
import com.game.agent.knowledge.service.KnowledgeRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class SearchAgent {

    private static final Logger log = LoggerFactory.getLogger(SearchAgent.class);

    private final KnowledgeRetrievalService retrievalService;
    private final AgentEventBus eventBus;

    public SearchAgent(KnowledgeRetrievalService retrievalService, AgentEventBus eventBus) {
        this.retrievalService = retrievalService;
        this.eventBus = eventBus;
    }

    @Async
    @EventListener
    public void handleSearchRequest(SearchEvent event) {
        if (event.getType() != AgentEvent.AgentEventType.SEARCH_REQUEST) return;

        log.info("SearchAgent received: correlationId={}, query={}",
                event.getCorrelationId(), event.getQuery());

        String seasonFilter = event.getSeasonTag() != null
                ? "version_tag == '" + event.getSeasonTag() + "'"
                : null;

        RetrievalResult result = retrievalService.retrieve(event.getQuery(), seasonFilter);

        eventBus.publish(new SearchEvent(
                event.getCorrelationId(), AgentRole.SEARCH, result));

        log.info("SearchAgent completed: correlationId={}, citations={}",
                event.getCorrelationId(), result.citations().size());
    }
}
