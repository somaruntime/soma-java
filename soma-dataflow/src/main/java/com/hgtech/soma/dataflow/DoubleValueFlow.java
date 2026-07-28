package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Projected double-value authoring handle retaining Candidate lineage. */
public final class DoubleValueFlow<B extends DataFlowBinding> {
    private final CandidateProgram<B> program;
    private final DoubleExpression<B> expression;

    DoubleValueFlow(
            CandidateProgram<B> program, DoubleExpression<B> expression) {
        this.program = program;
        this.expression = expression;
    }

    public CandidateFlow<B> candidates() {
        return new CandidateFlow<B>(program);
    }

    public DataFlowDefinition<DoubleColumnResult> toColumn() {
        return DataFlowDefinition.of(
                new DoubleColumnOperation<B>(program, expression));
    }

    public DataFlowDefinition<DoubleScalarResult> sum() {
        return DataFlowDefinition.of(
                DoubleReductionOperation.sum(program, expression));
    }

    public DataFlowDefinition<OptionalDoubleResult> average() {
        return DataFlowDefinition.of(
                DoubleReductionOperation.average(program, expression));
    }

    public DataFlowDefinition<OptionalDoubleResult> min() {
        return DataFlowDefinition.of(
                DoubleReductionOperation.min(program, expression));
    }

    public DataFlowDefinition<OptionalDoubleResult> max() {
        return DataFlowDefinition.of(
                DoubleReductionOperation.max(program, expression));
    }

    public CallbackDeliveryDefinition<DoubleValueVisitor> deliver() {
        ParameterSlot<DoubleValueVisitor> visitor =
                ParameterSlot.callback(DoubleValueVisitor.class);
        return CallbackDeliveryDefinition.of(
                new DoubleValueDeliveryOperation<B>(
                        program, expression, visitor),
                visitor);
    }
}
