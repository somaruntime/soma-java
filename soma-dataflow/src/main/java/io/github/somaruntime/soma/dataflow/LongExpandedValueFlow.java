package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

/** Projected long values derived from owned-child expansion. */
public final class LongExpandedValueFlow<
        P extends DataFlowBinding, C extends DataFlowBinding> {
    private final ExpandedProgram<P, C> program;
    private final LongExpression<C> expression;

    LongExpandedValueFlow(
            ExpandedProgram<P, C> program,
            LongExpression<C> expression) {
        this.program = program;
        this.expression = expression;
    }

    public DataFlowDefinition<LongColumnResult> toColumn() {
        return DataFlowDefinition.of(
                new ExpandedLongColumnOperation<P, C>(
                        program, expression));
    }

    /**
     * Uses exact integral state and fails if the expanded sum is not
     * representable as a long.
     */
    public DataFlowDefinition<LongScalarResult> sum() {
        return DataFlowDefinition.of(
                new ExpandedLongSumOperation<P, C>(
                        program, expression));
    }
}
