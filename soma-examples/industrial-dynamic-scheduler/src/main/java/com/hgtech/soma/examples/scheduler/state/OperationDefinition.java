package com.hgtech.soma.examples.scheduler.state;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaUnique;
import java.util.List;

@SomaTable(name = "operation_definitions", defaultCapacity = 8192)
@SomaUnique(name = "by_job_sequence", fields = {
    "operationKey.jobId.value", "sequenceNo"})
public final class OperationDefinition {
  @SomaKey public OperationKey operationKey;
  @SomaField public int sequenceNo;
  @SomaField public SetupFamilyId setupFamily;
  @SomaField public ResourceId requiredResource;
  @SomaField public int requiredResourceUnits;
  @SomaChild(initialCapacity = 8)
  public List<EligibleMachine> eligibleMachines;
}
