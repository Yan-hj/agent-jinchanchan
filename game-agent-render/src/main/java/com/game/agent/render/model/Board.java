package com.game.agent.render.model;

import java.util.List;

public record Board(
        int level,
        String experience,
        int gold,
        List<Champion> champions,
        List<Champion> bench,
        List<ActiveSynergy> activeSynergies,
        ItemPool itemPool
) {}
