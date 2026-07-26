package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/**
 * Two disjoint Candidate branches produced by one predicate Partition.
 *
 * <p>Branch order is matching then remaining. Combining preserves that
 * declaration order and duplicate semantics.</p>
 */
public final class PartitionedFlow<B extends DataFlowBinding> {
    private final CandidateFlow<B> matching;
    private final CandidateFlow<B> remaining;

    PartitionedFlow(
            CandidateFlow<B> matching, CandidateFlow<B> remaining) {
        this.matching = matching;
        this.remaining = remaining;
    }

    public CandidateFlow<B> matching() {
        return matching;
    }

    public CandidateFlow<B> remaining() {
        return remaining;
    }

    public CandidateFlow<B> combine() {
        return new CandidateFlow<B>(
                new CandidatePlan<B>(
                        new CombinedCandidateInput<B>(
                                matching.compileProgram(),
                                remaining.compileProgram())));
    }
}
