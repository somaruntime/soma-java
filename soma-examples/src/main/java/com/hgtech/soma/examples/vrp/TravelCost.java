package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "travel_costs", defaultCapacity = 65536)
public final class TravelCost {
    @SomaKey public LocationPairKey locationPair;
    @SomaField public long distanceMeters;
    @SomaField public long travelSeconds;
}
