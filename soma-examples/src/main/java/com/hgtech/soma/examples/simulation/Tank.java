package com.hgtech.soma.examples.simulation;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaSemantic;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "tanks", defaultCapacity = 256)
public final class Tank {
    @SomaKey public TankId tankId;
    @SomaField public MaterialId materialId;
    @SomaField public double levelLiters;
    @SomaField public double capacityLiters;
    @SomaField public double temperatureCelsius;
    @SomaField(semantic = SomaSemantic.DATE_TIME) public long lastUpdateMillis;
}
