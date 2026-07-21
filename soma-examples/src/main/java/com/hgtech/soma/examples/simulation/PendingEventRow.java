package com.hgtech.soma.examples.simulation;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "pending_event_rows", defaultCapacity = 1024)
public final class PendingEventRow {
    @SomaField public long simulationTimeNanos;
    @SomaField public long sequenceNo;
    @SomaField public SimEventKind eventKind;
    @SomaField public SimEntityKind targetKind;
    @SomaField public long targetId;
    @SomaField @SomaOptional public Double numericPayload;
}
