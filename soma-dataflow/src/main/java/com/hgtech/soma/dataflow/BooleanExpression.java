package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Immutable schema-bound boolean expression. */
public final class BooleanExpression<B extends DataFlowBinding> {
    final SourceSlot<B> source;
    final BooleanNode node;
    final BooleanNode presence;
    final String path;
    final boolean parallelSafe;
    private final String identity;

    BooleanExpression(SourceSlot<B> source, BooleanNode node) {
        this(source, node, ExpressionNodes.alwaysPresent(), "boolean", true);
    }

    BooleanExpression(
            SourceSlot<B> source,
            BooleanNode node,
            BooleanNode presence,
            String path) {
        this(source, node, presence, path, true);
    }

    BooleanExpression(
            SourceSlot<B> source,
            BooleanNode node,
            BooleanNode presence,
            String path,
            boolean parallelSafe) {
        this.source = source;
        this.node = node;
        this.presence = presence;
        this.path = path;
        this.parallelSafe = parallelSafe;
        identity = DataFlowSupport.identity(
                "boolean-expression-v1\n" + source.alias() + "\n"
                        + node.canonical() + "\n"
                        + presence.canonical() + "\n");
    }

    public BooleanExpression<B> isPresent() {
        return new BooleanExpression<B>(source, presence);
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
                    public boolean evaluate(DataFlowBinding binding, int index) {
                        return available.evaluate(binding, index)
                                ? value.evaluate(binding, index) : fallback;
                    }

                    @Override
                    public String canonical() {
                        return "boolean-coalesce(" + value.canonical() + ","
                                + fallback + ")";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".coalesce",
                parallelSafe);
    }

    public BooleanExpression<B> and(BooleanExpression<B> other) {
        requireSameSource(other);
        return new BooleanExpression<B>(
                source,
                ExpressionNodes.and(node, other.node),
                ExpressionNodes.andPresence(presence, other.presence),
                path + ".and",
                parallelSafe && other.parallelSafe);
    }

    public BooleanExpression<B> or(BooleanExpression<B> other) {
        requireSameSource(other);
        return new BooleanExpression<B>(
                source,
                ExpressionNodes.or(node, other.node),
                ExpressionNodes.andPresence(presence, other.presence),
                path + ".or",
                parallelSafe && other.parallelSafe);
    }

    public BooleanExpression<B> not() {
        return new BooleanExpression<B>(
                source,
                ExpressionNodes.negate(node),
                presence,
                path + ".not",
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

    boolean evaluate(DataFlowBinding binding, int index) {
        ExpressionNodes.requirePresent(presence, binding, index, path);
        return node.evaluate(binding, index);
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
