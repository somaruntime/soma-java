package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.Collections;
import java.util.List;

/**
 * Immutable typed predicate over one joined pair.
 *
 * <p>A right-side expression evaluates to {@code false} when the right side is
 * absent. Presence itself is represented by {@link JoinedFlow#rightPresent()}
 * and {@link JoinedFlow#rightAbsent()}.</p>
 */
public final class JoinedPredicate<
        L extends DataFlowBinding, R extends DataFlowBinding> {
    private final SourceSlot<L> leftSource;
    private final SourceSlot<R> rightSource;
    private final JoinedPredicateNode node;
    private final List<ParameterSlot<?>> parameters;
    private final String identity;

    JoinedPredicate(
            SourceSlot<L> leftSource,
            SourceSlot<R> rightSource,
            JoinedPredicateNode node,
            List<ParameterSlot<?>> parameters) {
        this.leftSource = leftSource;
        this.rightSource = rightSource;
        this.node = node;
        this.parameters = parameters;
        StringBuilder canonical =
                new StringBuilder("joined-predicate-v1");
        DataFlowSupport.appendCanonical(
                canonical, "left", leftSource.alias());
        DataFlowSupport.appendCanonical(
                canonical, "right", rightSource.alias());
        DataFlowSupport.appendCanonical(
                canonical, "node", node.canonical());
        identity = DataFlowSupport.identity(canonical.toString());
    }

    public JoinedPredicate<L, R> and(
            JoinedPredicate<L, R> other) {
        requireCompatible(other);
        final JoinedPredicateNode left = node;
        final JoinedPredicateNode right = other.node;
        return new JoinedPredicate<L, R>(
                leftSource,
                rightSource,
                new JoinedPredicateNode() {
                    @Override
                    public boolean evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding leftBinding,
                            int leftIndex,
                            DataFlowBinding rightBinding,
                            boolean rightPresent,
                            int rightIndex) {
                        return left.evaluate(
                                frame,
                                leftBinding,
                                leftIndex,
                                rightBinding,
                                rightPresent,
                                rightIndex)
                                && right.evaluate(
                                frame,
                                leftBinding,
                                leftIndex,
                                rightBinding,
                                rightPresent,
                                rightIndex);
                    }

                    @Override
                    public String canonical() {
                        return "and(" + left.canonical() + ","
                                + right.canonical() + ")";
                    }
                },
                DataFlowSupport.unionParameters(
                        parameters, other.parameters));
    }

    public JoinedPredicate<L, R> or(
            JoinedPredicate<L, R> other) {
        requireCompatible(other);
        final JoinedPredicateNode left = node;
        final JoinedPredicateNode right = other.node;
        return new JoinedPredicate<L, R>(
                leftSource,
                rightSource,
                new JoinedPredicateNode() {
                    @Override
                    public boolean evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding leftBinding,
                            int leftIndex,
                            DataFlowBinding rightBinding,
                            boolean rightPresent,
                            int rightIndex) {
                        return left.evaluate(
                                frame,
                                leftBinding,
                                leftIndex,
                                rightBinding,
                                rightPresent,
                                rightIndex)
                                || right.evaluate(
                                frame,
                                leftBinding,
                                leftIndex,
                                rightBinding,
                                rightPresent,
                                rightIndex);
                    }

                    @Override
                    public String canonical() {
                        return "or(" + left.canonical() + ","
                                + right.canonical() + ")";
                    }
                },
                DataFlowSupport.unionParameters(
                        parameters, other.parameters));
    }

    public JoinedPredicate<L, R> not() {
        final JoinedPredicateNode upstream = node;
        return new JoinedPredicate<L, R>(
                leftSource,
                rightSource,
                new JoinedPredicateNode() {
                    @Override
                    public boolean evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding leftBinding,
                            int leftIndex,
                            DataFlowBinding rightBinding,
                            boolean rightPresent,
                            int rightIndex) {
                        return !upstream.evaluate(
                                frame,
                                leftBinding,
                                leftIndex,
                                rightBinding,
                                rightPresent,
                                rightIndex);
                    }

                    @Override
                    public String canonical() {
                        return "not(" + upstream.canonical() + ")";
                    }
                },
                parameters);
    }

    String identity() {
        return identity;
    }

    List<ParameterSlot<?>> parameters() {
        return parameters;
    }

    boolean evaluate(
            ExecutionFrame frame,
            DataFlowBinding leftBinding,
            int leftIndex,
            DataFlowBinding rightBinding,
            boolean rightPresent,
            int rightIndex) {
        return node.evaluate(
                frame,
                leftBinding,
                leftIndex,
                rightBinding,
                rightPresent,
                rightIndex);
    }

    SourceSlot<L> leftSource() {
        return leftSource;
    }

    SourceSlot<R> rightSource() {
        return rightSource;
    }

    static <L extends DataFlowBinding, R extends DataFlowBinding>
    JoinedPredicate<L, R> left(
            final SourceSlot<L> leftSource,
            SourceSlot<R> rightSource,
            final BooleanExpression<L> expression) {
        return new JoinedPredicate<L, R>(
                leftSource,
                rightSource,
                new JoinedPredicateNode() {
                    @Override
                    public boolean evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding leftBinding,
                            int leftIndex,
                            DataFlowBinding rightBinding,
                            boolean rightPresent,
                            int rightIndex) {
                        return expression.evaluate(
                                frame, leftBinding, leftIndex);
                    }

                    @Override
                    public String canonical() {
                        return "left(" + expression.identity() + ")";
                    }
                },
                expression.parameters);
    }

    static <L extends DataFlowBinding, R extends DataFlowBinding>
    JoinedPredicate<L, R> right(
            SourceSlot<L> leftSource,
            final SourceSlot<R> rightSource,
            final BooleanExpression<R> expression) {
        return new JoinedPredicate<L, R>(
                leftSource,
                rightSource,
                new JoinedPredicateNode() {
                    @Override
                    public boolean evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding leftBinding,
                            int leftIndex,
                            DataFlowBinding rightBinding,
                            boolean rightPresent,
                            int rightIndex) {
                        return rightPresent
                                && expression.evaluate(
                                frame, rightBinding, rightIndex);
                    }

                    @Override
                    public String canonical() {
                        return "right(" + expression.identity() + ")";
                    }
                },
                expression.parameters);
    }

    static <L extends DataFlowBinding, R extends DataFlowBinding>
    JoinedPredicate<L, R> presence(
            SourceSlot<L> leftSource,
            SourceSlot<R> rightSource,
            final boolean expected) {
        return new JoinedPredicate<L, R>(
                leftSource,
                rightSource,
                new JoinedPredicateNode() {
                    @Override
                    public boolean evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding leftBinding,
                            int leftIndex,
                            DataFlowBinding rightBinding,
                            boolean rightPresent,
                            int rightIndex) {
                        return rightPresent == expected;
                    }

                    @Override
                    public String canonical() {
                        return expected
                                ? "right-present" : "right-absent";
                    }
                },
                Collections.<ParameterSlot<?>>emptyList());
    }

    private void requireCompatible(JoinedPredicate<L, R> other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        if (leftSource != other.leftSource
                || rightSource != other.rightSource) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_predicate_source_mismatch",
                    leftSource.alias(),
                    "dataflow.join.predicate");
        }
    }
}

interface JoinedPredicateNode {
    boolean evaluate(
            ExecutionFrame frame,
            DataFlowBinding leftBinding,
            int leftIndex,
            DataFlowBinding rightBinding,
            boolean rightPresent,
            int rightIndex);

    String canonical();
}
