package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Invocation-time GroupBy shape preserving key first-occurrence order. */
public final class GroupedFlow<B extends DataFlowBinding> {
    private final CandidateProgram<B> program;
    private final KeyExpression<B> key;
    private final GroupShapePlan<B> shape;

    GroupedFlow(CandidateProgram<B> program, KeyExpression<B> key) {
        this(program, key, GroupShapePlan.<B>empty());
    }

    private GroupedFlow(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            GroupShapePlan<B> shape) {
        this.program = program;
        this.key = key;
        this.shape = shape;
    }

    public GroupedFlow<B> havingCountAtLeast(long count) {
        if (count < 0L) {
            throw new IllegalArgumentException(
                    "count must be non-negative");
        }
        return new GroupedFlow<B>(
                program, key, shape.havingAtLeast(count));
    }

    public GroupedFlow<B> havingCountAtMost(long count) {
        if (count < 0L) {
            throw new IllegalArgumentException(
                    "count must be non-negative");
        }
        return new GroupedFlow<B>(
                program, key, shape.havingAtMost(count));
    }

    public GroupedFlow<B> sortedByCountAscending() {
        return new GroupedFlow<B>(
                program, key, shape.orderByCount(false));
    }

    public GroupedFlow<B> sortedByCountDescending() {
        return new GroupedFlow<B>(
                program, key, shape.orderByCount(true));
    }

    public DataFlowDefinition<GroupIndexResult> indexSnapshot() {
        return DataFlowDefinition.of(
                new GroupIndexOperation<B>(program, key, shape));
    }

    public DataFlowDefinition<LongScalarResult> borrow(GroupConsumer consumer) {
        if (consumer == null) {
            throw new NullPointerException("consumer");
        }
        return DataFlowDefinition.of(
                new GroupBorrowOperation<B>(
                        program, key, shape, consumer));
    }

    public DataFlowDefinition<GroupedLongResult> counts() {
        return DataFlowDefinition.of(
                GroupLongAggregationOperation.counts(
                        program, key, shape));
    }

    public DataFlowDefinition<GroupedLongResult> sum(
            LongExpression<B> expression) {
        requireSource(expression);
        return DataFlowDefinition.of(
                GroupLongAggregationOperation.sum(
                        program, key, shape, expression));
    }

    public DataFlowDefinition<GroupedLongResult> min(
            LongExpression<B> expression) {
        requireSource(expression);
        return DataFlowDefinition.of(
                GroupLongAggregationOperation.min(
                        program, key, shape, expression));
    }

    public DataFlowDefinition<GroupedLongResult> max(
            LongExpression<B> expression) {
        requireSource(expression);
        return DataFlowDefinition.of(
                GroupLongAggregationOperation.max(
                        program, key, shape, expression));
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
