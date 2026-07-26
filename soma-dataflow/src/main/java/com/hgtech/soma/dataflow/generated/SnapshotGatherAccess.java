package com.hgtech.soma.dataflow.generated;

import com.hgtech.soma.runtime.IndexSnapshot;

/** Generated currentness check for a caller-bound IndexSnapshot gather source. */
public interface SnapshotGatherAccess<B extends DataFlowBinding> {
    void requireCurrent(B binding, IndexSnapshot snapshot);

    String identity();
}
