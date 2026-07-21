package com.hgtech.soma.examples.simulation;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

/** Import 后只读的 tank topology/parameter facts。 */
@SomaTable(name = "tank_definitions", defaultCapacity = 256)
public final class TankDefinition {
  @SomaKey public TankId tankId;
  @SomaField public MaterialId materialId;
  @SomaField public double capacityLiters;
}
