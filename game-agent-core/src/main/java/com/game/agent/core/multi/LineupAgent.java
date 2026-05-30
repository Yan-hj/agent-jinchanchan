package com.game.agent.core.multi;

import com.game.agent.render.model.Board;
import com.game.agent.render.model.LineupCard;
import com.game.agent.render.service.SvgLineupRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class LineupAgent {

    private static final Logger log = LoggerFactory.getLogger(LineupAgent.class);

    private final SvgLineupRenderer renderer;
    private final AgentEventBus eventBus;

    public LineupAgent(SvgLineupRenderer renderer, AgentEventBus eventBus) {
        this.renderer = renderer;
        this.eventBus = eventBus;
    }

    @Async
    @EventListener
    public void handleLineupRequest(LineupEvent event) {
        if (event.getType() != AgentEvent.AgentEventType.LINEUP_REQUEST) return;

        log.info("LineupAgent received: correlationId={}", event.getCorrelationId());

        Board board = event.getBoard();
        String svg = renderer.render(board);
        LineupCard card = new LineupCard(null, board);

        eventBus.publish(new LineupEvent(
                event.getCorrelationId(), AgentRole.LINEUP, card, svg));

        log.info("LineupAgent completed: correlationId={}", event.getCorrelationId());
    }
}
