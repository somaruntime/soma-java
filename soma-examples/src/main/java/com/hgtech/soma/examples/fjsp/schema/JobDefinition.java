package com.hgtech.soma.examples.fjsp.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "job_definitions", defaultCapacity = 1024)
public final class JobDefinition {
  @SomaKey public JobId jobId;
  @SomaField public long inputOrder;
  @SomaField public long dueMinute;
  @SomaField public int operationCount;
}
