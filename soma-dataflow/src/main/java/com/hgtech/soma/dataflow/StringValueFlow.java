package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Projected String-value authoring handle. */
public final class StringValueFlow<B extends DataFlowBinding> {
    private final CandidateProgram<B> program;
    private final StringExpression<B> expression;

    StringValueFlow(
            CandidateProgram<B> program, StringExpression<B> expression) {
        this.program = program;
        this.expression = expression;
    }

    public CandidateFlow<B> candidates() {
        return new CandidateFlow<B>(program);
    }

    public DataFlowDefinition<StringColumnResult> toColumn() {
        return DataFlowDefinition.of(
                new StringColumnOperation<B>(program, expression));
    }

    public DataFlowDefinition<LongScalarResult> borrow(
            StringValueConsumer consumer) {
        if (consumer == null) {
            throw new NullPointerException("consumer");
        }
        return DataFlowDefinition.of(
                new StringValueBorrowOperation<B>(
                        program, expression, consumer));
    }
}
