package io.github.somaruntime.soma.examples.scheduler.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;
import io.github.somaruntime.soma.annotation.SomaUnique;

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
