package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.Comparator;

/** Immutable stable ordering definition for one Candidate source. */
public final class CandidateOrder<B extends DataFlowBinding> {
    final SourceSlot<B> source;
    final OrderNode node;
    private final String identity;

    private CandidateOrder(SourceSlot<B> source, OrderNode node) {
        this.source = source;
        this.node = node;
        identity = DataFlowSupport.identity(
                "candidate-order-v1\n" + source.alias() + "\n"
                        + node.canonical() + "\n");
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
                            DataFlowBinding binding, int left, int right) {
                        int result = first.compare(binding, left, right);
                        return result != 0
                                ? result : second.compare(binding, left, right);
                    }

                    @Override
                    public String canonical() {
                        return "then(" + first.canonical() + ","
                                + second.canonical() + ")";
                    }
                });
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
                            DataFlowBinding binding, int left, int right) {
                        int result = Long.compare(
                                expression.evaluate(binding, left),
                                expression.evaluate(binding, right));
                        return descending ? -result : result;
                    }

                    @Override
                    public String canonical() {
                        return (descending ? "desc(" : "asc(")
                                + expression.identity() + ")";
                    }
                });
    }

    static <B extends DataFlowBinding> CandidateOrder<B> doubleOrder(
            final DoubleExpression<B> expression, final boolean descending) {
        return new CandidateOrder<B>(
                expression.source,
                new OrderNode() {
                    @Override
                    public int compare(
                            DataFlowBinding binding, int left, int right) {
                        int result = Double.compare(
                                expression.evaluate(binding, left),
                                expression.evaluate(binding, right));
                        return descending ? -result : result;
                    }

                    @Override
                    public String canonical() {
                        return (descending ? "desc(" : "asc(")
                                + expression.identity() + ")";
                    }
                });
    }

    static <B extends DataFlowBinding> CandidateOrder<B> booleanOrder(
            final BooleanExpression<B> expression, final boolean descending) {
        return new CandidateOrder<B>(
                expression.source,
                new OrderNode() {
                    @Override
                    public int compare(
                            DataFlowBinding binding, int left, int right) {
                        boolean a = expression.evaluate(binding, left);
                        boolean b = expression.evaluate(binding, right);
                        int result = a == b ? 0 : (a ? 1 : -1);
                        return descending ? -result : result;
                    }

                    @Override
                    public String canonical() {
                        return (descending ? "desc(" : "asc(")
                                + expression.identity() + ")";
                    }
                });
    }

    static <B extends DataFlowBinding, T> CandidateOrder<B> objectOrder(
            final ObjectExpression<B, T> expression,
            final Comparator<? super T> comparator,
            final boolean descending) {
        return new CandidateOrder<B>(
                expression.source,
                new OrderNode() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public int compare(
                            DataFlowBinding binding, int left, int right) {
                        Object a = expression.evaluate(binding, left);
                        Object b = expression.evaluate(binding, right);
                        int result = ExpressionNodes.compareObjects(
                                a, b, (Comparator<Object>) comparator);
                        return descending ? -result : result;
                    }

                    @Override
                    public String canonical() {
                        return (descending ? "object-desc(" : "object-asc(")
                                + expression.identity() + ","
                                + (comparator == null
                                ? "natural" : "opaque-comparator") + ")";
                    }
                });
    }
}
