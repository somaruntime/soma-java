package com.hgtech.soma.examples.scheduler.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaUnique;

@SomaTable(name = "operation_definitions", defaultCapacity = 8192)
@SomaUnique(name = "by_job_sequence", fields = {
    "operationKey.jobId.value", "sequenceNo"})
public final class OperationDefinition {
  @SomaKey public OperationKey operationKey;
  @SomaField public int sequenceNo;
  @SomaField public SetupFamilyId setupFamily;
  @SomaField public ResourceId requiredResource;
  @SomaField public int requiredResourceUnits;
}
