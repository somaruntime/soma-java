package com.hgtech.soma.examples.fjsp.schema;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;
import java.util.List;

@SomaTable(name = "operation_definitions", defaultCapacity = 4096)
@SomaIndex(name = "by_job_sequence", fields = {
    "operationKey.jobId.value", "sequenceNo"})
public final class OperationDefinition {
  @SomaKey public OperationKey operationKey;
  @SomaField public int sequenceNo;
  @SomaField public long releaseMinute;
  @SomaField public SetupFamilyId setupFamily;
  @SomaChild(initialCapacity = 8)
  public List<CandidateMachineDefinition> candidateMachines;
}
