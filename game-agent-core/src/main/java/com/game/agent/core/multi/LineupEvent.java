package com.game.agent.core.multi;

import com.game.agent.render.model.Board;
import com.game.agent.render.model.LineupCard;

public class LineupEvent extends AgentEvent {

    private final Board board;
    private final LineupCard lineupCard;
    private final String svgContent;

    /** 请求事件 */
    public LineupEvent(String correlationId, AgentRole source, Board board) {
        super(correlationId, source, AgentEventType.LINEUP_REQUEST);
        this.board = board;
        this.lineupCard = null;
        this.svgContent = null;
    }

    /** 响应事件 */
    public LineupEvent(String correlationId, AgentRole source, LineupCard lineupCard, String svgContent) {
        super(correlationId, source, AgentEventType.LINEUP_RESPONSE);
        this.board = null;
        this.lineupCard = lineupCard;
        this.svgContent = svgContent;
    }

    public Board getBoard() { return board; }
    public LineupCard getLineupCard() { return lineupCard; }
    public String getSvgContent() { return svgContent; }
}
