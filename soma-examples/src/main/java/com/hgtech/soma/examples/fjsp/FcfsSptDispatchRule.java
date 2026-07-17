package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateRows;

/** FCFS + SPT，并以领域 identity 保证完全稳定的 tie-break。 */
public final class FcfsSptDispatchRule {
  MachineCandidateRows.Comparator comparator() {
    return (left, right) -> {
      int compared = Long.compare(
        left.effectiveReadyMinute(), right.effectiveReadyMinute());
      if (compared != 0) return compared;
      compared = Long.compare(left.fcfsValue(), right.fcfsValue());
      if (compared != 0) return compared;
      compared = Long.compare(left.sptValue(), right.sptValue());
      if (compared != 0) return compared;
      compared = Long.compare(left.candidateKeyOperationKeyJobIdValue(),
        right.candidateKeyOperationKeyJobIdValue());
      if (compared != 0) return compared;
      compared = Long.compare(
        left.candidateKeyOperationKeyOperationIdValue(),
        right.candidateKeyOperationKeyOperationIdValue());
      if (compared != 0) return compared;
      return Long.compare(left.candidateKeyMachineIdValue(),
        right.candidateKeyMachineIdValue());
    };
  }
}
