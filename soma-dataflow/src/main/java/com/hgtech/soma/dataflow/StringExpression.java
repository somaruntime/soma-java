package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable schema-bound String expression. */
public final class StringExpression<B extends DataFlowBinding> {
    final SourceSlot<B> source;
    final StringNode node;
    final BooleanNode presence;
    final String path;
    final List<ParameterSlot<?>> parameters;
    final boolean parallelSafe;
    private final String identity;

    StringExpression(
            SourceSlot<B> source,
            StringNode node,
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

    StringExpression(
            SourceSlot<B> source,
            StringNode node,
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
                new StringBuilder("string-expression-v1");
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

    public BooleanExpression<B> equalTo(final String right) {
        final StringNode left = node;
        final BooleanNode available = presence;
        final String constantIdentity =
                DataFlowSupport.constantIdentity(right);
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
                        return Objects.equals(
                                left.evaluate(frame, binding, index), right);
                    }

                    @Override
                    public String canonical() {
                        return "string-equal(" + left.canonical()
                                + "," + constantIdentity + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".equal",
                parameters,
                true);
    }

    public BooleanExpression<B> equalTo(
            StringExpression<B> other) {
        requireSameSource(other);
        final StringNode left = node;
        final StringNode right = other.node;
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
                        return Objects.equals(
                                left.evaluate(frame, binding, index),
                                right.evaluate(frame, binding, index));
                    }

                    @Override
                    public String canonical() {
                        return "string-equal(" + left.canonical()
                                + "," + right.canonical() + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".equal",
                DataFlowSupport.unionParameters(
                        parameters, other.parameters),
                parallelSafe && other.parallelSafe);
    }

    public StringExpression<B> coalesce(final String fallback) {
        final StringNode value = node;
        final BooleanNode available = presence;
        final String constantIdentity =
                DataFlowSupport.constantIdentity(fallback);
        return new StringExpression<B>(
                source,
                new StringNode() {
                    @Override
                    public String evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        return available.evaluate(frame, binding, index)
                                ? value.evaluate(frame, binding, index)
                                : fallback;
                    }

                    @Override
                    public String canonical() {
                        return "string-coalesce(" + value.canonical()
                                + "," + constantIdentity + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".coalesce",
                parameters,
                parallelSafe);
    }

    public CandidateOrder<B> ascending() {
        return CandidateOrder.stringOrder(this, null, false);
    }

    public CandidateOrder<B> ascending(
            Comparator<? super String> comparator) {
        if (comparator == null) {
            throw new NullPointerException("comparator");
        }
        return CandidateOrder.stringOrder(this, comparator, false);
    }

    public CandidateOrder<B> descending() {
        return CandidateOrder.stringOrder(this, null, true);
    }

    public CandidateOrder<B> descending(
            Comparator<? super String> comparator) {
        if (comparator == null) {
            throw new NullPointerException("comparator");
        }
        return CandidateOrder.stringOrder(this, comparator, true);
    }

    public String identity() {
        return identity;
    }

    String evaluate(
            ExecutionFrame frame, DataFlowBinding binding, int index) {
        ExpressionNodes.requirePresent(
                frame, presence, binding, index, path);
        return node.evaluate(frame, binding, index);
    }

    boolean required() {
        return ExpressionNodes.isAlwaysPresent(presence);
    }

    private void requireSameSource(StringExpression<B> other) {
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
