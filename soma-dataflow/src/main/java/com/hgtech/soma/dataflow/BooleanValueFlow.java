package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Projected boolean-value authoring handle. */
public final class BooleanValueFlow<B extends DataFlowBinding> {
    private final CandidateProgram<B> program;
    private final BooleanExpression<B> expression;

    BooleanValueFlow(
            CandidateProgram<B> program, BooleanExpression<B> expression) {
        this.program = program;
        this.expression = expression;
    }

    public DataFlowDefinition<BooleanColumnResult> toColumn() {
        return DataFlowDefinition.of(
                new BooleanColumnOperation<B>(program, expression));
    }
}
