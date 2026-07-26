package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.IndexSnapshot;

/**
 * Immutable, lazy Candidate transformation authoring handle.
 *
 * <p>Intermediate calls only build logical semantics. A terminal creates a
 * reusable {@link DataFlowDefinition}; live state is read only when an
 * invocation executes.</p>
 */
public final class CandidateFlow<B extends DataFlowBinding> {
    private final CandidatePlan<B> plan;

    CandidateFlow(SourceSlot<B> source) {
        this(new CandidatePlan<B>(source));
    }

    CandidateFlow(CandidatePlan<B> plan) {
        this.plan = plan;
    }

    CandidateFlow(CandidateProgram<B> program) {
        this(new CandidatePlan<B>(
                new ProgramCandidateInput<B>(program)));
    }

    public CandidateFlow<B> filter(BooleanExpression<B> predicate) {
        requireSource(predicate == null ? null : predicate.source, "predicate");
        return new CandidateFlow<B>(plan.filter(predicate));
    }

    public CandidateFlow<B> skip(long count) {
        if (count < 0L) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        return new CandidateFlow<B>(plan.skip(count));
    }

    public CandidateFlow<B> limit(long count) {
        if (count < 0L) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        return new CandidateFlow<B>(plan.limit(count));
    }

    public CandidateFlow<B> sortedBy(CandidateOrder<B> order) {
        requireSource(order == null ? null : order.source, "order");
        return new CandidateFlow<B>(plan.sort(order));
    }

    public CandidateFlow<B> topK(long count, CandidateOrder<B> order) {
        if (count < 0L) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        return sortedBy(order).limit(count);
    }

    public DataFlowDefinition<LongScalarResult> count() {
        return DataFlowDefinition.of(
                new CandidateCountOperation<B>(plan.compileProgram()));
    }

    public DataFlowDefinition<BooleanScalarResult> anyMatch(
            BooleanExpression<B> predicate) {
        requireSource(predicate == null ? null : predicate.source, "predicate");
        return DataFlowDefinition.of(
                new CandidateMatchOperation<B>(
                        plan.compileProgram(),
                        predicate,
                        CandidateMatchOperation.ANY));
    }

    public DataFlowDefinition<BooleanScalarResult> noneMatch(
            BooleanExpression<B> predicate) {
        requireSource(predicate == null ? null : predicate.source, "predicate");
        return DataFlowDefinition.of(
                new CandidateMatchOperation<B>(
                        plan.compileProgram(),
                        predicate,
                        CandidateMatchOperation.NONE));
    }

    public DataFlowDefinition<BooleanScalarResult> allMatch(
            BooleanExpression<B> predicate) {
        requireSource(predicate == null ? null : predicate.source, "predicate");
        return DataFlowDefinition.of(
                new CandidateMatchOperation<B>(
                        plan.compileProgram(),
                        predicate,
                        CandidateMatchOperation.ALL));
    }

    public DataFlowDefinition<IndexSnapshot> indexSnapshot() {
        return DataFlowDefinition.of(
                new CandidateIndexSnapshotOperation<B>(plan.compileProgram()));
    }

    public DataFlowDefinition<IndexSnapshot> firstIndexSnapshot() {
        return limit(1L).indexSnapshot();
    }

    public DataFlowDefinition<IndexSnapshot> bestIndexSnapshot(
            CandidateOrder<B> order) {
        return topK(1L, order).indexSnapshot();
    }

    public DataFlowDefinition<IndexSnapshot> argMinIndexSnapshot(
            LongExpression<B> expression) {
        requireSource(expression == null ? null : expression.source, "expression");
        return bestIndexSnapshot(expression.ascending());
    }

    public DataFlowDefinition<IndexSnapshot> argMaxIndexSnapshot(
            LongExpression<B> expression) {
        requireSource(expression == null ? null : expression.source, "expression");
        return bestIndexSnapshot(expression.descending());
    }

    public LongValueFlow<B> project(LongExpression<B> expression) {
        requireSource(expression == null ? null : expression.source, "expression");
        return new LongValueFlow<B>(plan.compileProgram(), expression);
    }

    public DoubleValueFlow<B> project(DoubleExpression<B> expression) {
        requireSource(expression == null ? null : expression.source, "expression");
        return new DoubleValueFlow<B>(plan.compileProgram(), expression);
    }

    public BooleanValueFlow<B> project(BooleanExpression<B> expression) {
        requireSource(expression == null ? null : expression.source, "expression");
        return new BooleanValueFlow<B>(plan.compileProgram(), expression);
    }

    public <T> ObjectValueFlow<B, T> project(
            ObjectExpression<B, T> expression) {
        requireSource(expression == null ? null : expression.source, "expression");
        return new ObjectValueFlow<B, T>(plan.compileProgram(), expression);
    }

    public PartitionedFlow<B> partition(BooleanExpression<B> predicate) {
        requireSource(predicate == null ? null : predicate.source, "predicate");
        return new PartitionedFlow<B>(
                new CandidateFlow<B>(plan.filter(predicate)),
                new CandidateFlow<B>(plan.filter(predicate.not())));
    }

    public KeyPartitionedFlow<B> partitionBy(KeyExpression<B> key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (key.source() != plan.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_partition_key_source_mismatch",
                    plan.source().alias(),
                    "dataflow.partitionBy");
        }
        return new KeyPartitionedFlow<B>(
                plan.compileProgram(), key);
    }

    public <R extends DataFlowBinding> JoinBuilder<B, R> join(
            CandidateFlow<R> right, JoinType type) {
        if (right == null) {
            throw new NullPointerException("right");
        }
        if (type == null) {
            throw new NullPointerException("type");
        }
        return new JoinBuilder<B, R>(
                plan.compileProgram(), right.compileProgram(), type);
    }

    public <R extends DataFlowBinding> JoinBuilder<B, R> innerJoin(
            CandidateFlow<R> right) {
        return join(right, JoinType.INNER);
    }

    public <R extends DataFlowBinding> JoinBuilder<B, R> leftOuterJoin(
            CandidateFlow<R> right) {
        return join(right, JoinType.LEFT_OUTER);
    }

    public <R extends DataFlowBinding> JoinBuilder<B, R> leftSemiJoin(
            CandidateFlow<R> right) {
        return join(right, JoinType.LEFT_SEMI);
    }

    public <R extends DataFlowBinding> JoinBuilder<B, R> leftAntiJoin(
            CandidateFlow<R> right) {
        return join(right, JoinType.LEFT_ANTI);
    }

    public GroupedFlow<B> groupBy(KeyExpression<B> key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (key.source() != plan.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_group_key_source_mismatch",
                    plan.source().alias(),
                    "dataflow.groupBy");
        }
        return new GroupedFlow<B>(plan.compileProgram(), key);
    }

    public WindowedFlow<B> windowByCount(
            int width, int step, PartialWindowPolicy partialPolicy) {
        return WindowedFlow.count(
                plan.compileProgram(), width, step, partialPolicy);
    }

    public WindowedFlow<B> windowByTime(
            LongExpression<B> orderKey,
            long width,
            long step,
            long origin,
            PartialWindowPolicy partialPolicy) {
        requireSource(orderKey == null ? null : orderKey.source, "orderKey");
        if (!orderKey.required()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_optional_window_key",
                    orderKey.path,
                    "dataflow.window");
        }
        return WindowedFlow.time(
                plan.compileProgram(),
                orderKey,
                width,
                step,
                origin,
                partialPolicy);
    }

    CandidateProgram<B> compileProgram() {
        return plan.compileProgram();
    }

    private void requireSource(SourceSlot<?> source, String name) {
        if (source == null) {
            throw new NullPointerException(name);
        }
        if (source != plan.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_expression_source_mismatch",
                    plan.source().alias(),
                    "dataflow." + name);
        }
    }
}
