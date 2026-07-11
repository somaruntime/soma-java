package com.hgtech.soma.examples.simulation;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSemantic;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "trace_sample_rows", defaultCapacity = 65536)
@SomaOrder(name = "by_time_entity", by = {
        @SomaSort("sampleTimeMillis"), @SomaSort("entityKind"), @SomaSort("entityId")})
public final class TraceSampleRow {
    @SomaField(semantic = SomaSemantic.DATE_TIME) public long sampleTimeMillis;
    @SomaField public SimEntityKind entityKind;
    @SomaField public long entityId;
    @SomaField public SimVariableKind variableKind;
    @SomaField public double value;
}
