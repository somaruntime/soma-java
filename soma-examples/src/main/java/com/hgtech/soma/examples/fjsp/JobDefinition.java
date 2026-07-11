package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "job_definitions", defaultCapacity = 1024)
@SomaOrder(name = "by_dispatch_order", by = {
        @SomaSort("inputOrder"), @SomaSort("jobId.value")})
public final class JobDefinition {
    @SomaKey public JobId jobId;
    @SomaField public long inputOrder;
    @SomaField public long dueMinute;
    @SomaField public int operationCount;
}
