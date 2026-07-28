package io.github.somaruntime.soma.dataflow.generated;

import io.github.somaruntime.soma.runtime.RemoveResult;
import io.github.somaruntime.soma.runtime.UpdateResult;

/**
 * Generated single-source Candidate effect bridge.
 *
 * <p>Selection happens while the invocation owns the read guard. Commit is
 * attempted only after that guard is released and the generated Table has
 * acquired its normal mutation safe point. This is not an application SPI.</p>
 */
public interface CandidateEffectAccess<B extends DataFlowBinding> {
    UpdateResult update(
            B binding,
            long expectedStructuralEpoch,
            int[] indexes,
            int count,
            Object updater);

    RemoveResult remove(
            B binding,
            long expectedStructuralEpoch,
            int[] indexes,
            int count);

    String identity();
}
