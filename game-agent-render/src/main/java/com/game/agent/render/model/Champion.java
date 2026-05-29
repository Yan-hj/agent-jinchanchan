package com.game.agent.render.model;

import java.util.List;

public record Champion(
        String name,
        int cost,
        int star,
        int[] position, // [row, col], 0-based
        List<String> items,
        List<String> synergies
) {
    public int row() { return position != null && position.length > 0 ? position[0] : 0; }
    public int col() { return position != null && position.length > 1 ? position[1] : 0; }
}
