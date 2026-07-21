package com.hgtech.soma.examples.game;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "pending_damage_rows", defaultCapacity = 1024)
public final class PendingDamageRow {
    @SomaField public long resolutionOrder;
    @SomaField public long sequenceNo;
    @SomaField public UnitId sourceUnit;
    @SomaField public UnitId targetUnit;
    @SomaField public int damage;
}
