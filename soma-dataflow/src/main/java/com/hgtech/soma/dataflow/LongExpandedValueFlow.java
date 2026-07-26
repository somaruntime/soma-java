package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

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

    public DataFlowDefinition<LongScalarResult> sum() {
        return DataFlowDefinition.of(
                new ExpandedLongSumOperation<P, C>(
                        program, expression));
    }
}
