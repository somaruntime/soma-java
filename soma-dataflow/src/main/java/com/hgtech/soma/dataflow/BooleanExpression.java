package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.Collections;
import java.util.List;

/** Immutable schema-bound boolean expression. */
public final class BooleanExpression<B extends DataFlowBinding> {
    final SourceSlot<B> source;
    final BooleanNode node;
    final BooleanNode presence;
    final String path;
    final boolean parallelSafe;
    final List<ParameterSlot<?>> parameters;
    private final String identity;

    BooleanExpression(SourceSlot<B> source, BooleanNode node) {
        this(
                source,
                node,
                ExpressionNodes.alwaysPresent(),
                "boolean",
                Collections.<ParameterSlot<?>>emptyList(),
                true);
    }

    BooleanExpression(
            SourceSlot<B> source,
            BooleanNode node,
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

    BooleanExpression(
            SourceSlot<B> source,
            BooleanNode node,
            BooleanNode presence,
            String path,
            boolean parallelSafe) {
        this(
                source,
                node,
                presence,
                path,
                Collections.<ParameterSlot<?>>emptyList(),
                parallelSafe);
    }

    BooleanExpression(
            SourceSlot<B> source,
            BooleanNode node,
            BooleanNode presence,
            String path,
            List<ParameterSlot<?>> parameters,
            boolean parallelSafe) {
        this.source = source;
        this.node = node;
        this.presence = presence;
        this.path = path;
        this.parallelSafe = parallelSafe;
        this.parameters = parameters;
        StringBuilder canonical =
                new StringBuilder("boolean-expression-v1");
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

    public BooleanExpression<B> coalesce(final boolean fallback) {
        final BooleanNode value = node;
        final BooleanNode available = presence;
        return new BooleanExpression<B>(
                source,
                new BooleanNode() {
                    @Override
                    public boolean evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        return available.evaluate(frame, binding, index)
                                ? value.evaluate(frame, binding, index)
                                : fallback;
                    }

                    @Override
                    public String canonical() {
                        return "boolean-coalesce(" + value.canonical() + ","
                                + fallback + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".coalesce",
                parameters,
                parallelSafe);
    }

    public BooleanExpression<B> and(BooleanExpression<B> other) {
        requireSameSource(other);
        return new BooleanExpression<B>(
                source,
                ExpressionNodes.and(node, other.node),
                ExpressionNodes.andPresence(presence, other.presence),
                path + ".and",
                DataFlowSupport.unionParameters(
                        parameters, other.parameters),
                parallelSafe && other.parallelSafe);
    }

    public BooleanExpression<B> or(BooleanExpression<B> other) {
        requireSameSource(other);
        return new BooleanExpression<B>(
                source,
                ExpressionNodes.or(node, other.node),
                ExpressionNodes.andPresence(presence, other.presence),
                path + ".or",
                DataFlowSupport.unionParameters(
                        parameters, other.parameters),
                parallelSafe && other.parallelSafe);
    }

    public BooleanExpression<B> not() {
        return new BooleanExpression<B>(
                source,
                ExpressionNodes.negate(node),
                presence,
                path + ".not",
                parameters,
                parallelSafe);
    }

    public CandidateOrder<B> ascending() {
        return CandidateOrder.booleanOrder(this, false);
    }

    public CandidateOrder<B> descending() {
        return CandidateOrder.booleanOrder(this, true);
    }

    public String identity() {
        return identity;
    }

    boolean evaluate(
            ExecutionFrame frame, DataFlowBinding binding, int index) {
        ExpressionNodes.requirePresent(
                frame, presence, binding, index, path);
        return node.evaluate(frame, binding, index);
    }

    boolean required() {
        return ExpressionNodes.isAlwaysPresent(presence);
    }

    private void requireSameSource(BooleanExpression<B> other) {
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
