package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.CandidateMaterializationAccess;
import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;
import io.github.somaruntime.soma.runtime.IndexSnapshot;
import io.github.somaruntime.soma.runtime.MaterializationBudget;

import java.util.List;

/**
 * Lazy zero-or-one access shape for primary-key, secondary-unique or current
 * Index access.
 */
public final class PointFlow<B extends DataFlowBinding, T> {
    private final CandidateFlow<B> candidates;
    private final CandidateMaterializationAccess<B, T> materialization;

    PointFlow(
            CandidateFlow<B> candidates,
            CandidateMaterializationAccess<B, T> materialization) {
        this.candidates = candidates;
        this.materialization = materialization;
    }

    public CandidateFlow<B> candidates() {
        return candidates;
    }

    public DataFlowDefinition<BooleanScalarResult> exists() {
        return DataFlowDefinition.of(
                new PointExistsOperation<B>(candidates.compileProgram()));
    }

    public DataFlowDefinition<IndexSnapshot> indexSnapshot() {
        return candidates.indexSnapshot();
    }

    public DataFlowDefinition<List<T>> materialize(
            MaterializationBudget budget) {
        if (budget == null) {
            throw new NullPointerException("budget");
        }
        return DataFlowDefinition.of(
                new CandidateMaterializeOperation<B, T>(
                        candidates.compileProgram(),
                        materialization,
                        budget,
                        "Point"));
    }
}
