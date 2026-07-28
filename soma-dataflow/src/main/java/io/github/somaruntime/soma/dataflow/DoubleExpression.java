package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

import java.util.Collections;
import java.util.List;

/** Immutable schema-bound floating expression with a double carrier. */
public final class DoubleExpression<B extends DataFlowBinding> {
    final SourceSlot<B> source;
    final DoubleNode node;
    final BooleanNode presence;
    final String path;
    final List<ParameterSlot<?>> parameters;
    final boolean parallelSafe;
    private final String identity;

    DoubleExpression(
            SourceSlot<B> source,
            DoubleNode node,
            BooleanNode presence,
            String path) {
        this(
                source,
                node,
                presence,
                path,
                Collections.<ParameterSlot<?>>emptyList(),
                true);
    }

    DoubleExpression(
            SourceSlot<B> source,
            DoubleNode node,
            BooleanNode presence,
            String path,
            List<ParameterSlot<?>> parameters,
            boolean parallelSafe) {
        this.source = source;
        this.node = node;
        this.presence = presence;
        this.path = path;
        this.parameters = parameters;
        this.parallelSafe = parallelSafe;
        StringBuilder canonical =
                new StringBuilder("double-expression-v1");
        DataFlowSupport.appendCanonical(
                canonical, "source", source.alias());
        DataFlowSupport.appendCanonical(
                canonical, "node", node.canonical());
        DataFlowSupport.appendCanonical(
                canonical, "presence", presence.canonical());
        identity = DataFlowSupport.identity(canonical.toString());
    }

    public BooleanExpression<B> isPresent() {
        return new BooleanExpression<B>(
                source,
                presence,
                ExpressionNodes.alwaysPresent(),
                path + ".present",
                parameters,
                parallelSafe);
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
                    public double evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        return available.evaluate(frame, binding, index)
                                ? value.evaluate(frame, binding, index)
                                : fallback;
                    }

                    @Override
                    public String canonical() {
                        return "coalesce(" + value.canonical() + ","
                                + Double.toString(fallback) + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".coalesce",
                parameters,
                parallelSafe);
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

    public DoubleExpression<B> plus(DoubleExpression<B> other) {
        return arithmetic("add", other, 0);
    }

    public DoubleExpression<B> minus(DoubleExpression<B> other) {
        return arithmetic("subtract", other, 1);
    }

    public DoubleExpression<B> multipliedBy(DoubleExpression<B> other) {
        return arithmetic("multiply", other, 2);
    }

    public DoubleExpression<B> dividedBy(DoubleExpression<B> other) {
        return arithmetic("divide", other, 3);
    }

    public BooleanExpression<B> equalTo(final double right) {
        return compare(right, 0);
    }

    public BooleanExpression<B> notEqualTo(final double right) {
        return compare(right, 1);
    }

    public BooleanExpression<B> lessThan(final double right) {
        return compare(right, 2);
    }

    public BooleanExpression<B> lessThanOrEqualTo(final double right) {
        return compare(right, 3);
    }

    public BooleanExpression<B> greaterThan(final double right) {
        return compare(right, 4);
    }

    public BooleanExpression<B> greaterThanOrEqualTo(final double right) {
        return compare(right, 5);
    }

    public BooleanExpression<B> equalTo(DoubleExpression<B> other) {
        return compare(other, 0);
    }

    public BooleanExpression<B> notEqualTo(DoubleExpression<B> other) {
        return compare(other, 1);
    }

    public BooleanExpression<B> lessThan(DoubleExpression<B> other) {
        return compare(other, 2);
    }

    public BooleanExpression<B> lessThanOrEqualTo(
            DoubleExpression<B> other) {
        return compare(other, 3);
    }

    public BooleanExpression<B> greaterThan(DoubleExpression<B> other) {
        return compare(other, 4);
    }

    public BooleanExpression<B> greaterThanOrEqualTo(
            DoubleExpression<B> other) {
        return compare(other, 5);
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

    double evaluate(
            ExecutionFrame frame, DataFlowBinding binding, int index) {
        ExpressionNodes.requirePresent(
                frame, presence, binding, index, path);
        return node.evaluate(frame, binding, index);
    }

    boolean required() {
        return ExpressionNodes.isAlwaysPresent(presence);
    }

    private DoubleExpression<B> arithmetic(
            final String operation, final double right, final int kind) {
        final DoubleNode left = node;
        return new DoubleExpression<B>(
                source,
                new DoubleNode() {
                    @Override
                    public double evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        double value = left.evaluate(frame, binding, index);
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
                path + "." + operation,
                parameters,
                parallelSafe);
    }

    private DoubleExpression<B> arithmetic(
            final String operation,
            DoubleExpression<B> other,
            final int kind) {
        requireSameSource(other);
        final DoubleNode left = node;
        final DoubleNode right = other.node;
        return new DoubleExpression<B>(
                source,
                new DoubleNode() {
                    @Override
                    public double evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        double a = left.evaluate(frame, binding, index);
                        double b = right.evaluate(frame, binding, index);
                        if (kind == 0) return a + b;
                        if (kind == 1) return a - b;
                        if (kind == 2) return a * b;
                        return a / b;
                    }

                    @Override
                    public String canonical() {
                        return operation + "(" + left.canonical() + ","
                                + right.canonical() + ")";
                    }
                },
                ExpressionNodes.andPresence(presence, other.presence),
                path + "." + operation,
                DataFlowSupport.unionParameters(
                        parameters, other.parameters),
                parallelSafe && other.parallelSafe);
    }

    private BooleanExpression<B> compare(final double right, final int kind) {
        final DoubleNode left = node;
        final BooleanNode available = presence;
        return new BooleanExpression<B>(
                source,
                new BooleanNode() {
                    @Override
                    public boolean evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        ExpressionNodes.requirePresent(
                                frame, available, binding, index, path);
                        double value = left.evaluate(frame, binding, index);
                        int compared = Double.compare(value, right);
                        if (kind == 0) return compared == 0;
                        if (kind == 1) return compared != 0;
                        if (kind == 2) return compared < 0;
                        if (kind == 3) return compared <= 0;
                        if (kind == 4) return compared > 0;
                        return compared >= 0;
                    }

                    @Override
                    public String canonical() {
                        return "double-compare-" + kind + "("
                                + left.canonical() + ","
                                + Double.toString(right) + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                "boolean",
                parameters,
                parallelSafe);
    }

    private BooleanExpression<B> compare(
            DoubleExpression<B> other, final int kind) {
        requireSameSource(other);
        final DoubleNode left = node;
        final DoubleNode right = other.node;
        final BooleanNode available =
                ExpressionNodes.andPresence(presence, other.presence);
        return new BooleanExpression<B>(
                source,
                new BooleanNode() {
                    @Override
                    public boolean evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        ExpressionNodes.requirePresent(
                                frame, available, binding, index, path);
                        int compared = Double.compare(
                                left.evaluate(frame, binding, index),
                                right.evaluate(frame, binding, index));
                        if (kind == 0) return compared == 0;
                        if (kind == 1) return compared != 0;
                        if (kind == 2) return compared < 0;
                        if (kind == 3) return compared <= 0;
                        if (kind == 4) return compared > 0;
                        return compared >= 0;
                    }

                    @Override
                    public String canonical() {
                        return "double-compare-" + kind + "("
                                + left.canonical() + ","
                                + right.canonical() + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                "boolean",
                DataFlowSupport.unionParameters(
                        parameters, other.parameters),
                parallelSafe && other.parallelSafe);
    }

    private void requireSameSource(DoubleExpression<B> other) {
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
