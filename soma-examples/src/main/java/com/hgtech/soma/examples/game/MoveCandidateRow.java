package com.hgtech.soma.examples.game;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "move_candidate_rows", defaultCapacity = 2048)
@SomaOrder(name = "by_total_cost", by = {
        @SomaSort("totalCost"), @SomaSort("position.y"), @SomaSort("position.x")})
public final class MoveCandidateRow {
    @SomaField public UnitId unitId;
    @SomaField public GridPosition position;
    @SomaField public int totalCost;
    @SomaField public int remainingActionPoints;
}
