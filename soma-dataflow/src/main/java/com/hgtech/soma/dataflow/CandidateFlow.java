package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Schema-bound Candidate source authoring handle. */
public final class CandidateFlow<B extends DataFlowBinding> {
    private final SourceSlot<B> source;

    CandidateFlow(SourceSlot<B> source) {
        this.source = source;
    }

    public DataFlowDefinition<LongScalarResult> count() {
        return DataFlowDefinition.packedCount(source);
    }
}
