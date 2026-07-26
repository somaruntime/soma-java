package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Projected reference/value authoring handle. */
public final class ObjectValueFlow<B extends DataFlowBinding, T> {
    private final CandidateProgram<B> program;
    private final ObjectExpression<B, T> expression;

    ObjectValueFlow(
            CandidateProgram<B> program, ObjectExpression<B, T> expression) {
        this.program = program;
        this.expression = expression;
    }

    public DataFlowDefinition<ObjectColumnResult<T>> toColumn() {
        return DataFlowDefinition.of(
                new ObjectColumnOperation<B, T>(program, expression));
    }
}
