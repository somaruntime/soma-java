package com.hgtech.soma.examples.simulation;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

/** Import 后只读的 valve topology/parameter facts。 */
@SomaTable(name = "valve_definitions", defaultCapacity = 512)
@SomaIndex(name = "by_from_tank", fields = {"fromTank.value"})
@SomaIndex(name = "by_to_tank", fields = {"toTank.value"})
public final class ValveDefinition {
  @SomaKey public ValveId valveId;
  @SomaField public TankId fromTank;
  @SomaField public TankId toTank;
  @SomaField public double maxFlowLitersPerSecond;
  @SomaField @SomaDefault("true") public boolean enabled;
}
