package io.github.somaruntime.soma.dataflow.generated;

import io.github.somaruntime.soma.runtime.IndexSnapshot;

/** Generated currentness check for a caller-bound IndexSnapshot gather source. */
public interface SnapshotGatherAccess<B extends DataFlowBinding> {
    void requireCurrent(B binding, IndexSnapshot snapshot);

    String identity();
}
