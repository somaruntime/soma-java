package com.hgtech.soma.examples.simulation;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSemantic;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "pending_event_rows", defaultCapacity = 1024)
@SomaOrder(name = "by_event_time", by = {
        @SomaSort("eventTimeMillis"), @SomaSort("sequenceNo")})
public final class PendingEventRow {
    @SomaField(semantic = SomaSemantic.DATE_TIME) public long eventTimeMillis;
    @SomaField public long sequenceNo;
    @SomaField public SimEventKind eventKind;
    @SomaField public SimEntityKind targetKind;
    @SomaField public long targetId;
    @SomaField @SomaOptional public Double numericPayload;
}
