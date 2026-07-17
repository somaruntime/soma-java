package com.hgtech.soma.examples.fjsp.schema;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "job_runtime_states", defaultCapacity = 1024)
public final class JobRuntimeState {
  @SomaKey public JobId jobId;
  @SomaField @SomaDefault("0") public int nextSequenceNo;
}
