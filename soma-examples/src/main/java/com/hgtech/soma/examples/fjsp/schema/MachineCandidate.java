package com.hgtech.soma.examples.fjsp.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "machine_candidates", defaultCapacity = 8192)
@SomaIndex(name = "by_machine", fields = {"candidateKey.machineId.value"})
@SomaIndex(name = "by_operation", fields = {
    "candidateKey.operationKey.jobId.value",
    "candidateKey.operationKey.operationId.value"})
public final class MachineCandidate {
  @SomaKey public OperationMachineKey candidateKey;
  @SomaField public SetupFamilyId targetSetupFamily;
  @SomaField public long operationReleaseMinute;
  @SomaField public long jobReadyMinute;
  @SomaField public long materialReadyMinute;
  @SomaField public long baseReadyMinute;
  @SomaField public long processingMinutes;
  @SomaField public long setupMinutes;
  @SomaField public long effectiveReadyMinute;
  @SomaField public long fcfsValue;
  @SomaField public long sptValue;
  @SomaField public boolean indicatorReady;
}
