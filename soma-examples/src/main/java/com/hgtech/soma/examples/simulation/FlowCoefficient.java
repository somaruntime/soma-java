package com.hgtech.soma.examples.simulation;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;
@SomaTable(name = "flow_coefficients", defaultCapacity = 1024)
public final class FlowCoefficient {
    @SomaKey public ValveMaterialKey valveMaterialKey;
    @SomaField public double coefficient;
}
