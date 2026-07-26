package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.IndexSnapshot;

/** Read-only left-driven equi Join shape. */
public final class JoinedFlow<
        L extends DataFlowBinding, R extends DataFlowBinding> {
    private final CandidateProgram<L> left;
    private final CandidateProgram<R> right;
    private final KeyExpression<L> leftKey;
    private final KeyExpression<R> rightKey;
    private final JoinType type;
    private final JoinedStagePlan<L, R> stages;

    JoinedFlow(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type) {
        this(
                left,
                right,
                leftKey,
                rightKey,
                type,
                JoinedStagePlan.<L, R>empty());
    }

    private JoinedFlow(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            JoinedStagePlan<L, R> stages) {
        this.left = left;
        this.right = right;
        this.leftKey = leftKey;
        this.rightKey = rightKey;
        this.type = type;
        this.stages = stages;
    }

    public JoinedFlow<L, R> filter(
            JoinedPredicate<L, R> predicate) {
        requirePredicate(predicate);
        return with(stages.filter(predicate));
    }

    public JoinedFlow<L, R> skip(long count) {
        if (count < 0L) {
            throw new IllegalArgumentException(
                    "count must be non-negative");
        }
        return with(stages.skip(count));
    }

    public JoinedFlow<L, R> limit(long count) {
        if (count < 0L) {
            throw new IllegalArgumentException(
                    "count must be non-negative");
        }
        return with(stages.limit(count));
    }

    public JoinedFlow<L, R> sortedBy(
            JoinedOrder<L, R> order) {
        requireOrder(order);
        return with(stages.sort(order));
    }

    public JoinedFlow<L, R> topK(
            long count, JoinedOrder<L, R> order) {
        if (count < 0L) {
            throw new IllegalArgumentException(
                    "count must be non-negative");
        }
        return sortedBy(order).limit(count);
    }

    public JoinedPredicate<L, R> leftPredicate(
            BooleanExpression<L> predicate) {
        requireLeft(predicate);
        return JoinedPredicate.left(
                left.source(), right.source(), predicate);
    }

    public JoinedPredicate<L, R> rightPredicate(
            BooleanExpression<R> predicate) {
        requireJoinedShape("dataflow.join.rightPredicate");
        requireRight(predicate);
        return JoinedPredicate.right(
                left.source(), right.source(), predicate);
    }

    public JoinedPredicate<L, R> rightPresent() {
        requireJoinedShape("dataflow.join.rightPresent");
        return JoinedPredicate.presence(
                left.source(), right.source(), true);
    }

    public JoinedPredicate<L, R> rightAbsent() {
        requireJoinedShape("dataflow.join.rightAbsent");
        return JoinedPredicate.presence(
                left.source(), right.source(), false);
    }

    public JoinedOrder<L, R> leftOrder(
            CandidateOrder<L> order) {
        if (order == null) {
            throw new NullPointerException("order");
        }
        if (order.source != left.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_order_source_mismatch",
                    left.source().alias(),
                    "dataflow.join.leftOrder");
        }
        return JoinedOrder.left(
                left.source(), right.source(), order);
    }

    public JoinedOrder<L, R> rightOrder(
            CandidateOrder<R> order, AbsenceOrder absenceOrder) {
        requireJoinedShape("dataflow.join.rightOrder");
        if (order == null) {
            throw new NullPointerException("order");
        }
        if (absenceOrder == null) {
            throw new NullPointerException("absenceOrder");
        }
        if (order.source != right.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_order_source_mismatch",
                    right.source().alias(),
                    "dataflow.join.rightOrder");
        }
        return JoinedOrder.right(
                left.source(),
                right.source(),
                order,
                absenceOrder);
    }

    public DataFlowDefinition<LongScalarResult> count() {
        return DataFlowDefinition.of(
                new JoinCountOperation<L, R>(
                        left,
                        right,
                        leftKey,
                        rightKey,
                        type,
                        stages));
    }

    public DataFlowDefinition<JoinedIndexResult> indexSnapshot() {
        if (type == JoinType.LEFT_SEMI || type == JoinType.LEFT_ANTI) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_shape_mismatch",
                    left.source().alias(),
                    "dataflow.join.indexSnapshot");
        }
        return DataFlowDefinition.of(
                new JoinIndexOperation<L, R>(
                        left,
                        right,
                        leftKey,
                        rightKey,
                        type,
                        stages));
    }

    public DataFlowDefinition<IndexSnapshot> leftIndexSnapshot() {
        return DataFlowDefinition.of(
                new JoinLeftIndexOperation<L, R>(
                        left,
                        right,
                        leftKey,
                        rightKey,
                        type,
                        stages));
    }

    public DataFlowDefinition<LongScalarResult> borrow(
            JoinedIndexConsumer consumer) {
        requireJoinedShape("dataflow.join.borrow");
        if (consumer == null) {
            throw new NullPointerException("consumer");
        }
        return DataFlowDefinition.of(
                new JoinBorrowOperation<L, R>(
                        left,
                        right,
                        leftKey,
                        rightKey,
                        type,
                        stages,
                        consumer));
    }

    public DataFlowDefinition<LongColumnResult> projectLeft(
            LongExpression<L> expression) {
        requireLeft(expression);
        return DataFlowDefinition.of(
                JoinLongProjectionOperation.left(
                        left,
                        right,
                        leftKey,
                        rightKey,
                        type,
                        stages,
                        expression));
    }

    public DataFlowDefinition<OptionalLongColumnResult> projectRight(
            LongExpression<R> expression) {
        requireJoinedShape("dataflow.join.projectRight");
        requireRight(expression);
        return DataFlowDefinition.of(
                JoinLongProjectionOperation.right(
                        left,
                        right,
                        leftKey,
                        rightKey,
                        type,
                        stages,
                        expression));
    }

    public DataFlowDefinition<GroupedLongResult> groupCountsByLeft(
            KeyExpression<L> key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (key.source() != left.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_group_key_source_mismatch",
                    left.source().alias(),
                    "dataflow.join.groupByLeft");
        }
        return DataFlowDefinition.of(
                new JoinLeftGroupCountOperation<L, R>(
                        left,
                        right,
                        leftKey,
                        rightKey,
                        type,
                        stages,
                        key));
    }

    public DataFlowDefinition<LongColumnResult> windowCounts(
            int width,
            int step,
            PartialWindowPolicy partialPolicy) {
        return DataFlowDefinition.of(
                new JoinCountWindowOperation<L, R>(
                        left,
                        right,
                        leftKey,
                        rightKey,
                        type,
                        stages,
                        width,
                        step,
                        partialPolicy));
    }

    private JoinedFlow<L, R> with(
            JoinedStagePlan<L, R> next) {
        return new JoinedFlow<L, R>(
                left, right, leftKey, rightKey, type, next);
    }

    private void requirePredicate(
            JoinedPredicate<L, R> predicate) {
        if (predicate == null) {
            throw new NullPointerException("predicate");
        }
        if (predicate.leftSource() != left.source()
                || predicate.rightSource() != right.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_predicate_source_mismatch",
                    left.source().alias(),
                    "dataflow.join.filter");
        }
    }

    private void requireOrder(JoinedOrder<L, R> order) {
        if (order == null) {
            throw new NullPointerException("order");
        }
        if (order.leftSource() != left.source()
                || order.rightSource() != right.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_order_source_mismatch",
                    left.source().alias(),
                    "dataflow.join.sortedBy");
        }
    }

    private void requireLeft(BooleanExpression<L> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        if (expression.source != left.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_expression_source_mismatch",
                    left.source().alias(),
                    "dataflow.join.leftPredicate");
        }
    }

    private void requireLeft(LongExpression<L> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        if (expression.source != left.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_expression_source_mismatch",
                    left.source().alias(),
                    "dataflow.join.projectLeft");
        }
    }

    private void requireRight(LongExpression<R> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        if (expression.source != right.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_expression_source_mismatch",
                    right.source().alias(),
                    "dataflow.join.projectRight");
        }
    }

    private void requireRight(BooleanExpression<R> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        if (expression.source != right.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_expression_source_mismatch",
                    right.source().alias(),
                    "dataflow.join.rightPredicate");
        }
    }

    private void requireJoinedShape(String operation) {
        if (type == JoinType.LEFT_SEMI || type == JoinType.LEFT_ANTI) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_shape_mismatch",
                    left.source().alias(),
                    operation);
        }
    }
}
