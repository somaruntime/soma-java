package com.hgtech.soma.examples.game;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "units", defaultCapacity = 1024)
@SomaIndex(name = "by_player", fields = {"playerId.value"})
@SomaIndex(name = "by_state", fields = {"state"})
@SomaOrder(name = "by_turn_order", by = {
        @SomaSort("initiative"), @SomaSort("unitId.value")})
public final class GameUnit {
    @SomaKey public UnitId unitId;
    @SomaField public PlayerId playerId;
    @SomaField public UnitClassId unitClassId;
    @SomaField public GridPosition position;
    @SomaField public int hp;
    @SomaField public int actionPoints;
    @SomaField public int initiative;
    @SomaField @SomaDefault("READY") public UnitState state;
    @SomaField @SomaOptional public UnitId targetUnit;
}
