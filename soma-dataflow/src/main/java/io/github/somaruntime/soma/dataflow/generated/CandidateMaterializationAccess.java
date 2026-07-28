package io.github.somaruntime.soma.dataflow.generated;

import io.github.somaruntime.soma.runtime.MaterializationBudget;

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
