package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "vehicles", defaultCapacity = 512)
public final class Vehicle {
    @SomaKey public VehicleId vehicleId;
    @SomaField public int capacity;
    @SomaField public LocationId startLocation;
    @SomaField public LocationId endLocation;
    @SomaField @SomaDefault("0") public long availableFromMinute;
}
