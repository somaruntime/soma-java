package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Immutable schema-bound floating expression with a double carrier. */
public final class DoubleExpression<B extends DataFlowBinding> {
    final SourceSlot<B> source;
    final DoubleNode node;
    final BooleanNode presence;
    final String path;
    private final String identity;

    DoubleExpression(
            SourceSlot<B> source,
            DoubleNode node,
            BooleanNode presence,
            String path) {
        this.source = source;
        this.node = node;
        this.presence = presence;
        this.path = path;
        identity = DataFlowSupport.identity(
                "double-expression-v1\n" + source.alias() + "\n"
                        + node.canonical() + "\n" + presence.canonical() + "\n");
    }

    public BooleanExpression<B> isPresent() {
        return new BooleanExpression<B>(source, presence);
    }

    public BooleanExpression<B> isAbsent() {
        return isPresent().not();
    }

    public DoubleExpression<B> coalesce(final double fallback) {
        final DoubleNode value = node;
        final BooleanNode available = presence;
        return new DoubleExpression<B>(
                source,
                new DoubleNode() {
                    @Override
                    public double evaluate(DataFlowBinding binding, int index) {
                        return available.evaluate(binding, index)
                                ? value.evaluate(binding, index) : fallback;
                    }

                    @Override
                    public String canonical() {
                        return "coalesce(" + value.canonical() + ","
                                + Double.toString(fallback) + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".coalesce");
    }

    public DoubleExpression<B> plus(final double right) {
        return arithmetic("add", right, 0);
    }

    public DoubleExpression<B> minus(final double right) {
        return arithmetic("subtract", right, 1);
    }

    public DoubleExpression<B> multipliedBy(final double right) {
        return arithmetic("multiply", right, 2);
    }

    public DoubleExpression<B> dividedBy(final double right) {
        return arithmetic("divide", right, 3);
    }

    public BooleanExpression<B> equalTo(final double right) {
        return compare(right, 0);
    }

    public BooleanExpression<B> lessThan(final double right) {
        return compare(right, 1);
    }

    public BooleanExpression<B> greaterThan(final double right) {
        return compare(right, 2);
    }

    public CandidateOrder<B> ascending() {
        return CandidateOrder.doubleOrder(this, false);
    }

    public CandidateOrder<B> descending() {
        return CandidateOrder.doubleOrder(this, true);
    }

    public String identity() {
        return identity;
    }

    double evaluate(DataFlowBinding binding, int index) {
        ExpressionNodes.requirePresent(presence, binding, index, path);
        return node.evaluate(binding, index);
    }

    private DoubleExpression<B> arithmetic(
            final String operation, final double right, final int kind) {
        final DoubleNode left = node;
        return new DoubleExpression<B>(
                source,
                new DoubleNode() {
                    @Override
                    public double evaluate(DataFlowBinding binding, int index) {
                        double value = left.evaluate(binding, index);
                        if (kind == 0) return value + right;
                        if (kind == 1) return value - right;
                        if (kind == 2) return value * right;
                        return value / right;
                    }

                    @Override
                    public String canonical() {
                        return operation + "(" + left.canonical() + ","
                                + Double.toString(right) + ")";
                    }
                },
                presence,
                path + "." + operation);
    }

    private BooleanExpression<B> compare(final double right, final int kind) {
        final DoubleNode left = node;
        final BooleanNode available = presence;
        return new BooleanExpression<B>(
                source,
                new BooleanNode() {
                    @Override
                    public boolean evaluate(DataFlowBinding binding, int index) {
                        ExpressionNodes.requirePresent(
                                available, binding, index, path);
                        double value = left.evaluate(binding, index);
                        if (kind == 0) {
                            return Double.compare(value, right) == 0;
                        }
                        if (kind == 1) return Double.compare(value, right) < 0;
                        return Double.compare(value, right) > 0;
                    }

                    @Override
                    public String canonical() {
                        return "double-compare-" + kind + "("
                                + left.canonical() + ","
                                + Double.toString(right) + ")";
                    }
                });
    }
}
