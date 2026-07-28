package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

/**
 * Invocation-local key partition preserving first-key and member order.
 *
 * <p>The grouped view provides branch-scoped terminals. Combining all
 * disjoint branches restores the original Candidate sequence.</p>
 */
public final class KeyPartitionedFlow<B extends DataFlowBinding> {
    private final CandidateProgram<B> program;
    private final GroupedFlow<B> groups;

    KeyPartitionedFlow(
            CandidateProgram<B> program, KeyExpression<B> key) {
        this.program = program;
        this.groups = new GroupedFlow<B>(program, key);
    }

    public GroupedFlow<B> groups() {
        return groups;
    }

    public CandidateFlow<B> combine() {
        return new CandidateFlow<B>(program);
    }
}
