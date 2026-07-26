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

    public CandidateFlow<B> candidates() {
        return new CandidateFlow<B>(program);
    }

    public DataFlowDefinition<BooleanColumnResult> toColumn() {
        return DataFlowDefinition.of(
                new BooleanColumnOperation<B>(program, expression));
    }

    public DataFlowDefinition<LongScalarResult> borrow(
            BooleanValueConsumer consumer) {
        if (consumer == null) {
            throw new NullPointerException("consumer");
        }
        return DataFlowDefinition.of(
                new BooleanValueBorrowOperation<B>(
                        program, expression, consumer));
    }
}
