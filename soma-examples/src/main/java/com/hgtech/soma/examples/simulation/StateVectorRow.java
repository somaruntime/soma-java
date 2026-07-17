package com.hgtech.soma.examples.simulation;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "state_vector_rows", defaultCapacity = 4096)
public final class StateVectorRow {
    @SomaField public int vectorIndex;
    @SomaField public SimEntityKind entityKind;
    @SomaField public long entityId;
    @SomaField public SimVariableKind variableKind;
    @SomaField public double value;
    @SomaField public double derivative;
    @SomaField public double scale;
}
