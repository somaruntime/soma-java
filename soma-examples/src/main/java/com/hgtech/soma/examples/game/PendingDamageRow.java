package com.hgtech.soma.examples.game;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "pending_damage_rows", defaultCapacity = 1024)
@SomaOrder(name = "by_resolution_order", by = {
        @SomaSort("resolutionOrder"), @SomaSort("targetUnit.value")})
public final class PendingDamageRow {
    @SomaField public long resolutionOrder;
    @SomaField public UnitId sourceUnit;
    @SomaField public UnitId targetUnit;
    @SomaField public int damage;
}
