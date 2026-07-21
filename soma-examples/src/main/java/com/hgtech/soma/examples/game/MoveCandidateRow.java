package com.hgtech.soma.examples.game;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "move_candidate_rows", defaultCapacity = 2048)
public final class MoveCandidateRow {
    @SomaField public GridPosition position;
    @SomaField public int totalCost;
    @SomaField public int remainingActionPoints;
}
