package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Immutable schema-bound reference/enum/value expression. */
public final class ObjectExpression<B extends DataFlowBinding, T> {
    final SourceSlot<B> source;
    final ObjectNode node;
    final BooleanNode presence;
    final String path;
    final List<ParameterSlot<?>> parameters;
    final boolean parallelSafe;
    private final String identity;

    ObjectExpression(
            SourceSlot<B> source,
            ObjectNode node,
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

    ObjectExpression(
            SourceSlot<B> source,
            ObjectNode node,
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
                new StringBuilder("object-expression-v1");
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

    public BooleanExpression<B> equalTo(final T right) {
        final ObjectNode left = node;
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
                        return ExpressionNodes.equalObjects(
                                left.evaluate(frame, binding, index), right);
                    }

                    @Override
                    public String canonical() {
                        return "object-equal(" + left.canonical()
                                + "," + constantIdentity + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".equal",
                parameters,
                false);
    }

    public BooleanExpression<B> equalTo(
            ObjectExpression<B, T> other) {
        requireSameSource(other);
        final ObjectNode left = node;
        final ObjectNode right = other.node;
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
                        return ExpressionNodes.equalObjects(
                                left.evaluate(frame, binding, index),
                                right.evaluate(frame, binding, index));
                    }

                    @Override
                    public String canonical() {
                        return "object-equal(" + left.canonical()
                                + "," + right.canonical() + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".equal",
                DataFlowSupport.unionParameters(
                        parameters, other.parameters),
                false);
    }

    public ObjectExpression<B, T> coalesce(final T fallback) {
        final ObjectNode value = node;
        final BooleanNode available = presence;
        final String constantIdentity =
                DataFlowSupport.constantIdentity(fallback);
        return new ObjectExpression<B, T>(
                source,
                new ObjectNode() {
                    @Override
                    public Object evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        return available.evaluate(frame, binding, index)
                                ? value.evaluate(frame, binding, index)
                                : fallback;
                    }

                    @Override
                    public String canonical() {
                        return "object-coalesce(" + value.canonical()
                                + "," + constantIdentity + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".coalesce",
                parameters,
                parallelSafe);
    }

    public CandidateOrder<B> ascending() {
        return CandidateOrder.objectOrder(this, null, false);
    }

    public CandidateOrder<B> ascending(Comparator<? super T> comparator) {
        if (comparator == null) {
            throw new NullPointerException("comparator");
        }
        return CandidateOrder.objectOrder(this, comparator, false);
    }

    public CandidateOrder<B> descending() {
        return CandidateOrder.objectOrder(this, null, true);
    }

    public CandidateOrder<B> descending(Comparator<? super T> comparator) {
        if (comparator == null) {
            throw new NullPointerException("comparator");
        }
        return CandidateOrder.objectOrder(this, comparator, true);
    }

    public String identity() {
        return identity;
    }

    @SuppressWarnings("unchecked")
    T evaluate(
            ExecutionFrame frame, DataFlowBinding binding, int index) {
        ExpressionNodes.requirePresent(
                frame, presence, binding, index, path);
        return (T) node.evaluate(frame, binding, index);
    }

    boolean required() {
        return ExpressionNodes.isAlwaysPresent(presence);
    }

    private void requireSameSource(ObjectExpression<B, T> other) {
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
