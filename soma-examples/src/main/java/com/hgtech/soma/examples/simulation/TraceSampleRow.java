package com.hgtech.soma.examples.simulation;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "trace_sample_rows", defaultCapacity = 65536)
public final class TraceSampleRow {
    @SomaField public long sampleTimeNanos;
    @SomaField public SimEntityKind entityKind;
    @SomaField public long entityId;
    @SomaField public SimVariableKind variableKind;
    @SomaField public double value;
}
