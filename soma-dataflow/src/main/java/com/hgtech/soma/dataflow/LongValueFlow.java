package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Projected long-value authoring handle retaining Candidate lineage. */
public final class LongValueFlow<B extends DataFlowBinding> {
    private final CandidateProgram<B> program;
    private final LongExpression<B> expression;

    LongValueFlow(
            CandidateProgram<B> program, LongExpression<B> expression) {
        this.program = program;
        this.expression = expression;
    }

    public DataFlowDefinition<LongColumnResult> toColumn() {
        return DataFlowDefinition.of(
                new LongColumnOperation<B>(program, expression));
    }

    public DataFlowDefinition<LongScalarResult> sum() {
        return DataFlowDefinition.of(
                LongReductionOperation.sum(program, expression));
    }

    public DataFlowDefinition<OptionalDoubleResult> average() {
        return DataFlowDefinition.of(
                LongReductionOperation.average(program, expression));
    }

    public DataFlowDefinition<OptionalLongResult> min() {
        return DataFlowDefinition.of(
                LongReductionOperation.min(program, expression));
    }

    public DataFlowDefinition<OptionalLongResult> max() {
        return DataFlowDefinition.of(
                LongReductionOperation.max(program, expression));
    }

    public DataFlowDefinition<LongColumnResult> inclusivePrefixSum() {
        return DataFlowDefinition.of(
                LongPrefixOperation.inclusive(program, expression));
    }

    public DataFlowDefinition<LongColumnResult> exclusivePrefixSum(long seed) {
        return DataFlowDefinition.of(
                LongPrefixOperation.exclusive(program, expression, seed));
    }
}
