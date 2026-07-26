package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.Comparator;
import java.util.List;

/** Immutable stable ordering definition for one Candidate source. */
public final class CandidateOrder<B extends DataFlowBinding> {
    final SourceSlot<B> source;
    final OrderNode node;
    final List<ParameterSlot<?>> parameters;
    final boolean parallelSafe;
    private final String identity;

    private CandidateOrder(
            SourceSlot<B> source,
            OrderNode node,
            List<ParameterSlot<?>> parameters,
            boolean parallelSafe) {
        this.source = source;
        this.node = node;
        this.parameters = parameters;
        this.parallelSafe = parallelSafe;
        StringBuilder canonical =
                new StringBuilder("candidate-order-v1");
        DataFlowSupport.appendCanonical(
                canonical, "source", source.alias());
        DataFlowSupport.appendCanonical(
                canonical, "order", node.canonical());
        identity = DataFlowSupport.identity(canonical.toString());
    }

    public CandidateOrder<B> then(CandidateOrder<B> next) {
        if (next == null) {
            throw new NullPointerException("next");
        }
        if (source != next.source) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_order_source_mismatch",
                    source.alias(),
                    "dataflow.order");
        }
        final OrderNode first = node;
        final OrderNode second = next.node;
        return new CandidateOrder<B>(
                source,
                new OrderNode() {
                    @Override
                    public int compare(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int left,
                            int right) {
                        int result = first.compare(
                                frame, binding, left, right);
                        return result != 0
                                ? result : second.compare(
                                frame, binding, left, right);
                    }

                    @Override
                    public String canonical() {
                        return "then(" + first.canonical() + ","
                                + second.canonical() + ")";
                    }
                },
                DataFlowSupport.unionParameters(
                        parameters, next.parameters),
                parallelSafe && next.parallelSafe);
    }

    public String identity() {
        return identity;
    }

    static <B extends DataFlowBinding> CandidateOrder<B> longOrder(
            final LongExpression<B> expression, final boolean descending) {
        return new CandidateOrder<B>(
                expression.source,
                new OrderNode() {
                    @Override
                    public int compare(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int left,
                            int right) {
                        int result = Long.compare(
                                expression.evaluate(frame, binding, left),
                                expression.evaluate(frame, binding, right));
                        return descending ? -result : result;
                    }

                    @Override
                    public String canonical() {
                        return (descending ? "desc(" : "asc(")
                                + expression.identity() + ")";
                    }
                },
                expression.parameters,
                expression.parallelSafe);
    }

    static <B extends DataFlowBinding> CandidateOrder<B> doubleOrder(
            final DoubleExpression<B> expression, final boolean descending) {
        return new CandidateOrder<B>(
                expression.source,
                new OrderNode() {
                    @Override
                    public int compare(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int left,
                            int right) {
                        int result = Double.compare(
                                expression.evaluate(frame, binding, left),
                                expression.evaluate(frame, binding, right));
                        return descending ? -result : result;
                    }

                    @Override
                    public String canonical() {
                        return (descending ? "desc(" : "asc(")
                                + expression.identity() + ")";
                    }
                },
                expression.parameters,
                expression.parallelSafe);
    }

    static <B extends DataFlowBinding> CandidateOrder<B> booleanOrder(
            final BooleanExpression<B> expression, final boolean descending) {
        return new CandidateOrder<B>(
                expression.source,
                new OrderNode() {
                    @Override
                    public int compare(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int left,
                            int right) {
                        boolean a = expression.evaluate(
                                frame, binding, left);
                        boolean b = expression.evaluate(
                                frame, binding, right);
                        int result = a == b ? 0 : (a ? 1 : -1);
                        return descending ? -result : result;
                    }

                    @Override
                    public String canonical() {
                        return (descending ? "desc(" : "asc(")
                                + expression.identity() + ")";
                    }
                },
                expression.parameters,
                expression.parallelSafe);
    }

    static <B extends DataFlowBinding, T> CandidateOrder<B> objectOrder(
            final ObjectExpression<B, T> expression,
            final Comparator<? super T> comparator,
            final boolean descending) {
        final String comparatorIdentity = comparator == null
                ? "natural"
                : "opaque-comparator-instance-"
                + DataFlowSupport.nextOpaqueIdentity();
        return new CandidateOrder<B>(
                expression.source,
                new OrderNode() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public int compare(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int left,
                            int right) {
                        Object a = expression.evaluate(
                                frame, binding, left);
                        Object b = expression.evaluate(
                                frame, binding, right);
                        int result = ExpressionNodes.compareObjects(
                                a, b, (Comparator<Object>) comparator);
                        return descending ? -result : result;
                    }

                    @Override
                    public String canonical() {
                        return (descending ? "object-desc(" : "object-asc(")
                                + expression.identity() + ","
                                + comparatorIdentity + ")";
                    }
                },
                expression.parameters,
                expression.parallelSafe && comparator == null);
    }
}
