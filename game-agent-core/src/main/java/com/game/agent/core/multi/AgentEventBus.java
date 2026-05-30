package com.game.agent.core.multi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AgentEventBus {

    private static final Logger log = LoggerFactory.getLogger(AgentEventBus.class);

    private final ApplicationEventPublisher publisher;

    public AgentEventBus(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(AgentEvent event) {
        log.debug("AgentEventBus publish: type={}, source={}, correlationId={}",
                event.getType(), event.getSourceAgent(), event.getCorrelationId());
        publisher.publishEvent(event);
    }
}
