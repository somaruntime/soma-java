package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Invocation-time GroupBy shape preserving key first-occurrence order. */
public final class GroupedFlow<B extends DataFlowBinding> {
    private final CandidateProgram<B> program;
    private final KeyExpression<B> key;

    GroupedFlow(CandidateProgram<B> program, KeyExpression<B> key) {
        this.program = program;
        this.key = key;
    }

    public DataFlowDefinition<GroupIndexResult> indexSnapshot() {
        return DataFlowDefinition.of(
                new GroupIndexOperation<B>(program, key));
    }

    public DataFlowDefinition<LongScalarResult> borrow(GroupConsumer consumer) {
        if (consumer == null) {
            throw new NullPointerException("consumer");
        }
        return DataFlowDefinition.of(
                new GroupBorrowOperation<B>(program, key, consumer));
    }

    public DataFlowDefinition<GroupedLongResult> counts() {
        return DataFlowDefinition.of(
                GroupLongAggregationOperation.counts(program, key));
    }

    public DataFlowDefinition<GroupedLongResult> sum(
            LongExpression<B> expression) {
        requireSource(expression);
        return DataFlowDefinition.of(
                GroupLongAggregationOperation.sum(program, key, expression));
    }

    public DataFlowDefinition<GroupedLongResult> min(
            LongExpression<B> expression) {
        requireSource(expression);
        return DataFlowDefinition.of(
                GroupLongAggregationOperation.min(program, key, expression));
    }

    public DataFlowDefinition<GroupedLongResult> max(
            LongExpression<B> expression) {
        requireSource(expression);
        return DataFlowDefinition.of(
                GroupLongAggregationOperation.max(program, key, expression));
    }

    private void requireSource(LongExpression<B> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        if (expression.source != program.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_group_expression_source_mismatch",
                    program.source().alias(),
                    "dataflow.groupBy");
        }
    }
}
