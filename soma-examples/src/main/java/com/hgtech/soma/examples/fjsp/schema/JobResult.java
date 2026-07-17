package com.hgtech.soma.examples.fjsp.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "job_results", defaultCapacity = 1024)
public final class JobResult {
  @SomaKey public JobId jobId;
  @SomaField public long completedMinute;
  @SomaField public long tardinessMinutes;
}
