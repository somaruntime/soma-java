package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.SomaRuntimeException;

import java.util.Collections;
import java.util.List;

/** Immutable schema-bound integral expression with a long carrier. */
public final class LongExpression<B extends DataFlowBinding> {
    final SourceSlot<B> source;
    final LongNode node;
    final BooleanNode presence;
    final String path;
    final List<ParameterSlot<?>> parameters;
    final boolean parallelSafe;
    private final String identity;

    LongExpression(
            SourceSlot<B> source,
            LongNode node,
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

    LongExpression(
            SourceSlot<B> source,
            LongNode node,
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
                new StringBuilder("long-expression-v1");
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

    public LongExpression<B> coalesce(final long fallback) {
        final LongNode value = node;
        final BooleanNode available = presence;
        return new LongExpression<B>(
                source,
                new LongNode() {
                    @Override
                    public long evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        return available.evaluate(frame, binding, index)
                                ? value.evaluate(frame, binding, index)
                                : fallback;
                    }

                    @Override
                    public String canonical() {
                        return "coalesce(" + value.canonical() + "," + fallback + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".coalesce",
                parameters,
                parallelSafe);
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

    public LongExpression<B> dividedBy(LongExpression<B> other) {
        return binary("divide", other, 3);
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

    public BooleanExpression<B> notEqualTo(LongExpression<B> other) {
        return compare(other, 1);
    }

    public BooleanExpression<B> lessThan(LongExpression<B> other) {
        return compare(other, 2);
    }

    public BooleanExpression<B> lessThanOrEqualTo(
            LongExpression<B> other) {
        return compare(other, 3);
    }

    public BooleanExpression<B> greaterThan(LongExpression<B> other) {
        return compare(other, 4);
    }

    public BooleanExpression<B> greaterThanOrEqualTo(
            LongExpression<B> other) {
        return compare(other, 5);
    }

    public CandidateOrder<B> ascending() {
        return CandidateOrder.longOrder(this, false);
    }

    public CandidateOrder<B> descending() {
        return CandidateOrder.longOrder(this, true);
    }

    public LongExpression<B> map(final RegisteredLongFunction function) {
        if (function == null) {
            throw new NullPointerException("function");
        }
        final String registered = DataFlowSupport.registeredIdentity(
                "registered-long-function",
                function.semanticId(),
                function.version());
        final LongNode upstream = node;
        return new LongExpression<B>(
                source,
                new LongNode() {
                    @Override
                    public long evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        try {
                            return function.applyAsLong(
                                    upstream.evaluate(frame, binding, index));
                        } catch (SomaRuntimeException failure) {
                            throw failure;
                        } catch (RuntimeException callback) {
                            throw DataFlowFailures.callback(
                                    "dataflow_registered_long_function_failed",
                                    path,
                                    "dataflow.expression",
                                    callback);
                        }
                    }

                    @Override
                    public String canonical() {
                        return registered + "(" + upstream.canonical() + ")";
                    }
                },
                presence,
                path + ".registered",
                parameters,
                parallelSafe
                        && function.deterministic()
                        && function.threadSafe());
    }

    public LongExpression<B> mapOpaque(final OpaqueLongFunction function) {
        if (function == null) {
            throw new NullPointerException("function");
        }
        final long opaqueIdentity = DataFlowSupport.nextOpaqueIdentity();
        final LongNode upstream = node;
        return new LongExpression<B>(
                source,
                new LongNode() {
                    @Override
                    public long evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        try {
                            return function.applyAsLong(
                                    upstream.evaluate(frame, binding, index));
                        } catch (SomaRuntimeException failure) {
                            throw failure;
                        } catch (RuntimeException callback) {
                            throw DataFlowFailures.callback(
                                    "dataflow_opaque_long_function_failed",
                                    path,
                                    "dataflow.expression",
                                    callback);
                        }
                    }

                    @Override
                    public String canonical() {
                        return "opaque-long-function-instance("
                                + opaqueIdentity + ","
                                + upstream.canonical() + ")";
                    }
                },
                presence,
                path + ".opaque",
                parameters,
                false);
    }

    public String identity() {
        return identity;
    }

    long evaluate(
            ExecutionFrame frame, DataFlowBinding binding, int index) {
        ExpressionNodes.requirePresent(
                frame, presence, binding, index, path);
        return node.evaluate(frame, binding, index);
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
                    public long evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        long value = left.evaluate(frame, binding, index);
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
                path + "." + operation,
                parameters,
                parallelSafe);
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
                    public long evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        long a = left.evaluate(frame, binding, index);
                        long b = right.evaluate(frame, binding, index);
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

    private BooleanExpression<B> compare(final long right, final int kind) {
        final LongNode left = node;
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
                        long value = left.evaluate(frame, binding, index);
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
                },
                ExpressionNodes.alwaysPresent(),
                "boolean",
                parameters,
                parallelSafe);
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
                    public boolean evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        ExpressionNodes.requirePresent(
                                frame, available, binding, index, path);
                        long a = left.evaluate(frame, binding, index);
                        long b = right.evaluate(frame, binding, index);
                        if (kind == 0) return a == b;
                        if (kind == 1) return a != b;
                        if (kind == 2) return a < b;
                        if (kind == 3) return a <= b;
                        if (kind == 4) return a > b;
                        return a >= b;
                    }

                    @Override
                    public String canonical() {
                        return "compare-" + kind + "(" + left.canonical()
                                + "," + right.canonical() + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                "boolean",
                DataFlowSupport.unionParameters(
                        parameters, other.parameters),
                parallelSafe && other.parallelSafe);
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
