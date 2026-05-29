package com.game.agent.render.model;

import java.util.List;

public record ItemPool(
        List<String> components,
        List<String> completed
) {}
