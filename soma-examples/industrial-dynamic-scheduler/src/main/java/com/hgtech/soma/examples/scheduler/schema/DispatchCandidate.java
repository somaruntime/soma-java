package com.hgtech.soma.examples.scheduler.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "dispatch_candidates", defaultCapacity = 32768)
@SomaIndex(name = "by_machine", fields = {"candidateKey.machineId.value"})
@SomaIndex(name = "by_operation", fields = {
    "candidateKey.operationKey.jobId.value",
    "candidateKey.operationKey.operationId.value"})
public final class DispatchCandidate {
  @SomaKey public DispatchCandidateKey candidateKey;
  @SomaField public SetupFamilyId targetSetupFamily;
  @SomaField public ResourceId requiredResource;
  @SomaField public int requiredResourceUnits;
  @SomaField public long baseReadyMinute;
  @SomaField public long processingMinutes;
  @SomaField public long setupMinutes;
  @SomaField public long transportMinutes;
  @SomaField public long effectiveStartMinute;
  @SomaField public long completionMinute;
  @SomaField public long dueMinute;
  @SomaField public int priority;
  @SomaField public long operationVersion;
  @SomaField public long machineVersion;
  @SomaField public long resourceVersion;
  @SomaField public boolean ready;
}
