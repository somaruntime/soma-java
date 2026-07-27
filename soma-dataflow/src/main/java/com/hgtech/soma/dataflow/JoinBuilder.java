package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Equi Join key-completion boundary. */
public final class JoinBuilder<
        L extends DataFlowBinding, R extends DataFlowBinding> {
    private final CandidateProgram<L> left;
    private final CandidateProgram<R> right;
    private final JoinType type;

    JoinBuilder(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            JoinType type) {
        this.left = left;
        this.right = right;
        this.type = type;
    }

    public JoinedFlow<L, R> on(
            LongExpression<L> leftKey, LongExpression<R> rightKey) {
        return on(KeyExpression.of(leftKey), KeyExpression.of(rightKey));
    }

    public JoinedFlow<L, R> on(
            StringExpression<L> leftKey,
            StringExpression<R> rightKey) {
        return on(KeyExpression.of(leftKey), KeyExpression.of(rightKey));
    }

    public JoinedFlow<L, R> on(
            KeyExpression<L> leftKey, KeyExpression<R> rightKey) {
        if (leftKey == null || rightKey == null) {
            throw new NullPointerException("join key");
        }
        if (left.source() != leftKey.source()
                || right.source() != rightKey.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_key_source_mismatch",
                    left.source().alias(),
                    "dataflow.join");
        }
        leftKey.requireCompatible(rightKey);
        return new JoinedFlow<L, R>(
                left, right, leftKey, rightKey, type);
    }
}
