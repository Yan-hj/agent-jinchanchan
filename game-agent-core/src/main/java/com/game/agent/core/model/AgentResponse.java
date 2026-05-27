package com.game.agent.core.model;

import com.game.agent.knowledge.model.Citation;

import java.util.List;

public record AgentResponse(
        String content,
        List<Citation> citations
) {}
