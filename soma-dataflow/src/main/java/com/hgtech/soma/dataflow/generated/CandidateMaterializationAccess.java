package com.hgtech.soma.dataflow.generated;

import com.hgtech.soma.runtime.MaterializationBudget;

import java.util.List;

/** Generated explicit object-materialization boundary for one candidate shape. */
public interface CandidateMaterializationAccess<
        B extends DataFlowBinding, T> {
    List<T> materialize(
            B binding,
            int[] indexes,
            int count,
            MaterializationBudget budget);

    String identity();
}
