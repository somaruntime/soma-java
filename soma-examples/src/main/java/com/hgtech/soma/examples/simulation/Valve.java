package com.hgtech.soma.examples.simulation;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "valves", defaultCapacity = 512)
@SomaIndex(name = "by_from_tank", fields = {"fromTank.value"})
@SomaIndex(name = "by_to_tank", fields = {"toTank.value"})
public final class Valve {
    @SomaKey public ValveId valveId;
    @SomaField public TankId fromTank;
    @SomaField public TankId toTank;
    @SomaField public double openingRatio;
    @SomaField public double maxFlowLitersPerSecond;
    @SomaField @SomaDefault("true") public boolean enabled;
}
