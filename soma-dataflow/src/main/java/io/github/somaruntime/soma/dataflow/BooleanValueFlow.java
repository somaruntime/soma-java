package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

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

    public CallbackDeliveryDefinition<BooleanValueVisitor> deliver() {
        ParameterSlot<BooleanValueVisitor> visitor =
                ParameterSlot.callback(BooleanValueVisitor.class);
        return CallbackDeliveryDefinition.of(
                new BooleanValueDeliveryOperation<B>(
                        program, expression, visitor),
                visitor);
    }
}
