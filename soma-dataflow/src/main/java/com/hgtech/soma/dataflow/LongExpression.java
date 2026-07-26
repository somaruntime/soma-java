package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Immutable schema-bound integral expression with a long carrier. */
public final class LongExpression<B extends DataFlowBinding> {
    final SourceSlot<B> source;
    final LongNode node;
    final BooleanNode presence;
    final String path;
    private final String identity;

    LongExpression(
            SourceSlot<B> source,
            LongNode node,
            BooleanNode presence,
            String path) {
        this.source = source;
        this.node = node;
        this.presence = presence;
        this.path = path;
        identity = DataFlowSupport.identity(
                "long-expression-v1\n" + source.alias() + "\n"
                        + node.canonical() + "\n" + presence.canonical() + "\n");
    }

    public BooleanExpression<B> isPresent() {
        return new BooleanExpression<B>(source, presence);
    }

    public BooleanExpression<B> isAbsent() {
        return isPresent().not();
    }

    public LongExpression<B> coalesce(final long fallback) {
        final LongNode value = node;
        final BooleanNode available = presence;
        return new LongExpression<B>(
                source,
                new LongNode() {
                    @Override
                    public long evaluate(DataFlowBinding binding, int index) {
                        return available.evaluate(binding, index)
                                ? value.evaluate(binding, index) : fallback;
                    }

                    @Override
                    public String canonical() {
                        return "coalesce(" + value.canonical() + "," + fallback + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".coalesce");
    }

    public LongExpression<B> plus(final long value) {
        return unary("add", value, 0);
    }

    public LongExpression<B> minus(final long value) {
        return unary("subtract", value, 1);
    }

    public LongExpression<B> multipliedBy(final long value) {
        return unary("multiply", value, 2);
    }

    public LongExpression<B> dividedBy(final long value) {
        return unary("divide", value, 3);
    }

    public LongExpression<B> plus(LongExpression<B> other) {
        return binary("add", other, 0);
    }

    public LongExpression<B> minus(LongExpression<B> other) {
        return binary("subtract", other, 1);
    }

    public LongExpression<B> multipliedBy(LongExpression<B> other) {
        return binary("multiply", other, 2);
    }

    public BooleanExpression<B> equalTo(final long value) {
        return compare(value, 0);
    }

    public BooleanExpression<B> notEqualTo(final long value) {
        return compare(value, 1);
    }

    public BooleanExpression<B> lessThan(final long value) {
        return compare(value, 2);
    }

    public BooleanExpression<B> lessThanOrEqualTo(final long value) {
        return compare(value, 3);
    }

    public BooleanExpression<B> greaterThan(final long value) {
        return compare(value, 4);
    }

    public BooleanExpression<B> greaterThanOrEqualTo(final long value) {
        return compare(value, 5);
    }

    public BooleanExpression<B> equalTo(LongExpression<B> other) {
        return compare(other, 0);
    }

    public BooleanExpression<B> lessThan(LongExpression<B> other) {
        return compare(other, 2);
    }

    public BooleanExpression<B> greaterThan(LongExpression<B> other) {
        return compare(other, 4);
    }

    public CandidateOrder<B> ascending() {
        return CandidateOrder.longOrder(this, false);
    }

    public CandidateOrder<B> descending() {
        return CandidateOrder.longOrder(this, true);
    }

    public String identity() {
        return identity;
    }

    long evaluate(DataFlowBinding binding, int index) {
        ExpressionNodes.requirePresent(presence, binding, index, path);
        return node.evaluate(binding, index);
    }

    boolean required() {
        return ExpressionNodes.isAlwaysPresent(presence);
    }

    private LongExpression<B> unary(
            final String operation, final long right, final int kind) {
        final LongNode left = node;
        return new LongExpression<B>(
                source,
                new LongNode() {
                    @Override
                    public long evaluate(DataFlowBinding binding, int index) {
                        long value = left.evaluate(binding, index);
                        if (kind == 0) return value + right;
                        if (kind == 1) return value - right;
                        if (kind == 2) return value * right;
                        return value / right;
                    }

                    @Override
                    public String canonical() {
                        return operation + "(" + left.canonical() + "," + right + ")";
                    }
                },
                presence,
                path + "." + operation);
    }

    private LongExpression<B> binary(
            final String operation, LongExpression<B> other, final int kind) {
        requireSameSource(other);
        final LongNode left = node;
        final LongNode right = other.node;
        return new LongExpression<B>(
                source,
                new LongNode() {
                    @Override
                    public long evaluate(DataFlowBinding binding, int index) {
                        long a = left.evaluate(binding, index);
                        long b = right.evaluate(binding, index);
                        if (kind == 0) return a + b;
                        if (kind == 1) return a - b;
                        return a * b;
                    }

                    @Override
                    public String canonical() {
                        return operation + "(" + left.canonical() + ","
                                + right.canonical() + ")";
                    }
                },
                ExpressionNodes.andPresence(presence, other.presence),
                path + "." + operation);
    }

    private BooleanExpression<B> compare(final long right, final int kind) {
        final LongNode left = node;
        final BooleanNode available = presence;
        return new BooleanExpression<B>(
                source,
                new BooleanNode() {
                    @Override
                    public boolean evaluate(DataFlowBinding binding, int index) {
                        ExpressionNodes.requirePresent(
                                available, binding, index, path);
                        long value = left.evaluate(binding, index);
                        if (kind == 0) return value == right;
                        if (kind == 1) return value != right;
                        if (kind == 2) return value < right;
                        if (kind == 3) return value <= right;
                        if (kind == 4) return value > right;
                        return value >= right;
                    }

                    @Override
                    public String canonical() {
                        return "compare-" + kind + "(" + left.canonical()
                                + "," + right + ")";
                    }
                });
    }

    private BooleanExpression<B> compare(
            LongExpression<B> other, final int kind) {
        requireSameSource(other);
        final LongNode left = node;
        final LongNode right = other.node;
        final BooleanNode available =
                ExpressionNodes.andPresence(presence, other.presence);
        return new BooleanExpression<B>(
                source,
                new BooleanNode() {
                    @Override
                    public boolean evaluate(DataFlowBinding binding, int index) {
                        ExpressionNodes.requirePresent(
                                available, binding, index, path);
                        long a = left.evaluate(binding, index);
                        long b = right.evaluate(binding, index);
                        if (kind == 0) return a == b;
                        if (kind == 2) return a < b;
                        return a > b;
                    }

                    @Override
                    public String canonical() {
                        return "compare-" + kind + "(" + left.canonical()
                                + "," + right.canonical() + ")";
                    }
                });
    }

    private void requireSameSource(LongExpression<B> other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        if (source != other.source) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_expression_source_mismatch",
                    source.alias(),
                    "dataflow.expression");
        }
    }
}
