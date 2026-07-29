package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

/** Projected long-value authoring handle retaining Candidate lineage. */
public final class LongValueFlow<B extends DataFlowBinding> {
    private final CandidateProgram<B> program;
    private final LongExpression<B> expression;

    LongValueFlow(
            CandidateProgram<B> program, LongExpression<B> expression) {
        this.program = program;
        this.expression = expression;
    }

    /** Returns the retained single-Table lineage for further legal operators. */
    public CandidateFlow<B> candidates() {
        return new CandidateFlow<B>(program);
    }

    public DataFlowDefinition<LongColumnResult> toColumn() {
        return DataFlowDefinition.of(
                new LongColumnOperation<B>(program, expression));
    }

    /**
     * Sums through exact wide state and fails if the final long is not
     * representable.
     */
    public DataFlowDefinition<LongScalarResult> sum() {
        return DataFlowDefinition.of(
                LongReductionOperation.sum(program, expression));
    }

    /** Computes a double average from the exact integral total. */
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

    public DataFlowDefinition<LongScalarResult> reduce(
            RegisteredLongReducer reducer) {
        if (reducer == null) {
            throw new NullPointerException("reducer");
        }
        return DataFlowDefinition.of(
                new RegisteredLongReductionOperation<B>(
                        program, expression, reducer));
    }

    /**
     * Produces inclusive sums and fails at the first unrepresentable prefix.
     */
    public DataFlowDefinition<LongColumnResult> inclusivePrefixSum() {
        return DataFlowDefinition.of(
                LongPrefixOperation.inclusive(program, expression));
    }

    /**
     * Produces exclusive sums and fails at the first unrepresentable emitted
     * prefix.
     */
    public DataFlowDefinition<LongColumnResult> exclusivePrefixSum(long seed) {
        return DataFlowDefinition.of(
                LongPrefixOperation.exclusive(program, expression, seed));
    }

    public CallbackDeliveryDefinition<LongValueVisitor> deliver() {
        ParameterSlot<LongValueVisitor> visitor =
                ParameterSlot.callback(LongValueVisitor.class);
        return CallbackDeliveryDefinition.of(
                new LongValueDeliveryOperation<B>(
                        program, expression, visitor),
                visitor);
    }
}
