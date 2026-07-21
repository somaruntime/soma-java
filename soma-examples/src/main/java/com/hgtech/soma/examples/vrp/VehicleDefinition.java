package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

/** Import 后只读的 vehicle input facts。 */
@SomaTable(name = "vehicle_definitions", defaultCapacity = 512)
public final class VehicleDefinition {
  @SomaKey public VehicleId vehicleId;
  @SomaField public int capacity;
  @SomaField public LocationId startLocation;
  @SomaField public LocationId endLocation;
  @SomaField @SomaDefault("0") public long availableFromSecond;
}
