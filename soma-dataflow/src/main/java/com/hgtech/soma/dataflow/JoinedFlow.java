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

    JoinedFlow(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type) {
        this.left = left;
        this.right = right;
        this.leftKey = leftKey;
        this.rightKey = rightKey;
        this.type = type;
    }

    public DataFlowDefinition<LongScalarResult> count() {
        return DataFlowDefinition.of(
                new JoinCountOperation<L, R>(
                        left, right, leftKey, rightKey, type));
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
                        left, right, leftKey, rightKey, type));
    }

    public DataFlowDefinition<IndexSnapshot> leftIndexSnapshot() {
        return DataFlowDefinition.of(
                new JoinLeftIndexOperation<L, R>(
                        left, right, leftKey, rightKey, type));
    }

    public DataFlowDefinition<LongScalarResult> borrow(
            JoinedIndexConsumer consumer) {
        if (consumer == null) {
            throw new NullPointerException("consumer");
        }
        return DataFlowDefinition.of(
                new JoinBorrowOperation<L, R>(
                        left, right, leftKey, rightKey, type, consumer));
    }
}
