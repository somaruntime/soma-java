package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

import java.util.List;

/** Immutable stable ordering over one joined pair. */
public final class JoinedOrder<
        L extends DataFlowBinding, R extends DataFlowBinding> {
    private final SourceSlot<L> leftSource;
    private final SourceSlot<R> rightSource;
    private final JoinedOrderNode node;
    private final List<ParameterSlot<?>> parameters;
    private final String identity;

    JoinedOrder(
            SourceSlot<L> leftSource,
            SourceSlot<R> rightSource,
            JoinedOrderNode node,
            List<ParameterSlot<?>> parameters) {
        this.leftSource = leftSource;
        this.rightSource = rightSource;
        this.node = node;
        this.parameters = parameters;
        StringBuilder canonical = new StringBuilder("joined-order-v1");
        DataFlowSupport.appendCanonical(
                canonical, "left", leftSource.alias());
        DataFlowSupport.appendCanonical(
                canonical, "right", rightSource.alias());
        DataFlowSupport.appendCanonical(
                canonical, "node", node.canonical());
        identity = DataFlowSupport.identity(canonical.toString());
    }

    public JoinedOrder<L, R> then(JoinedOrder<L, R> next) {
        requireCompatible(next);
        final JoinedOrderNode first = node;
        final JoinedOrderNode second = next.node;
        return new JoinedOrder<L, R>(
                leftSource,
                rightSource,
                new JoinedOrderNode() {
                    @Override
                    public int compare(
                            ExecutionFrame frame,
                            DataFlowBinding leftBinding,
                            DataFlowBinding rightBinding,
                            int leftIndexA,
                            boolean rightPresentA,
                            int rightIndexA,
                            int leftIndexB,
                            boolean rightPresentB,
                            int rightIndexB) {
                        int compared = first.compare(
                                frame,
                                leftBinding,
                                rightBinding,
                                leftIndexA,
                                rightPresentA,
                                rightIndexA,
                                leftIndexB,
                                rightPresentB,
                                rightIndexB);
                        return compared != 0 ? compared : second.compare(
                                frame,
                                leftBinding,
                                rightBinding,
                                leftIndexA,
                                rightPresentA,
                                rightIndexA,
                                leftIndexB,
                                rightPresentB,
                                rightIndexB);
                    }

                    @Override
                    public String canonical() {
                        return "then(" + first.canonical() + ","
                                + second.canonical() + ")";
                    }
                },
                DataFlowSupport.unionParameters(
                        parameters, next.parameters));
    }

    String identity() {
        return identity;
    }

    List<ParameterSlot<?>> parameters() {
        return parameters;
    }

    int compare(
            ExecutionFrame frame,
            DataFlowBinding leftBinding,
            DataFlowBinding rightBinding,
            int leftIndexA,
            boolean rightPresentA,
            int rightIndexA,
            int leftIndexB,
            boolean rightPresentB,
            int rightIndexB) {
        return node.compare(
                frame,
                leftBinding,
                rightBinding,
                leftIndexA,
                rightPresentA,
                rightIndexA,
                leftIndexB,
                rightPresentB,
                rightIndexB);
    }

    SourceSlot<L> leftSource() {
        return leftSource;
    }

    SourceSlot<R> rightSource() {
        return rightSource;
    }

    static <L extends DataFlowBinding, R extends DataFlowBinding>
    JoinedOrder<L, R> left(
            final SourceSlot<L> leftSource,
            SourceSlot<R> rightSource,
            final CandidateOrder<L> order) {
        return new JoinedOrder<L, R>(
                leftSource,
                rightSource,
                new JoinedOrderNode() {
                    @Override
                    public int compare(
                            ExecutionFrame frame,
                            DataFlowBinding leftBinding,
                            DataFlowBinding rightBinding,
                            int leftIndexA,
                            boolean rightPresentA,
                            int rightIndexA,
                            int leftIndexB,
                            boolean rightPresentB,
                            int rightIndexB) {
                        return order.node.compare(
                                frame,
                                leftBinding,
                                leftIndexA,
                                leftIndexB);
                    }

                    @Override
                    public String canonical() {
                        return "left(" + order.identity() + ")";
                    }
                },
                order.parameters);
    }

    static <L extends DataFlowBinding, R extends DataFlowBinding>
    JoinedOrder<L, R> right(
            SourceSlot<L> leftSource,
            final SourceSlot<R> rightSource,
            final CandidateOrder<R> order,
            final AbsenceOrder absenceOrder) {
        return new JoinedOrder<L, R>(
                leftSource,
                rightSource,
                new JoinedOrderNode() {
                    @Override
                    public int compare(
                            ExecutionFrame frame,
                            DataFlowBinding leftBinding,
                            DataFlowBinding rightBinding,
                            int leftIndexA,
                            boolean rightPresentA,
                            int rightIndexA,
                            int leftIndexB,
                            boolean rightPresentB,
                            int rightIndexB) {
                        if (rightPresentA != rightPresentB) {
                            int absent = absenceOrder == AbsenceOrder.FIRST
                                    ? -1 : 1;
                            return rightPresentA ? -absent : absent;
                        }
                        if (!rightPresentA) {
                            return 0;
                        }
                        return order.node.compare(
                                frame,
                                rightBinding,
                                rightIndexA,
                                rightIndexB);
                    }

                    @Override
                    public String canonical() {
                        return "right(" + order.identity() + ","
                                + absenceOrder + ")";
                    }
                },
                order.parameters);
    }

    private void requireCompatible(JoinedOrder<L, R> other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        if (leftSource != other.leftSource
                || rightSource != other.rightSource) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_join_order_source_mismatch",
                    leftSource.alias(),
                    "dataflow.join.order");
        }
    }
}

interface JoinedOrderNode {
    int compare(
            ExecutionFrame frame,
            DataFlowBinding leftBinding,
            DataFlowBinding rightBinding,
            int leftIndexA,
            boolean rightPresentA,
            int rightIndexA,
            int leftIndexB,
            boolean rightPresentB,
            int rightIndexB);

    String canonical();
}
