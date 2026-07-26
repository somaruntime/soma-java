package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.Comparator;

/** Immutable schema-bound reference/enum/value expression. */
public final class ObjectExpression<B extends DataFlowBinding, T> {
    final SourceSlot<B> source;
    final ObjectNode node;
    final BooleanNode presence;
    final String path;
    private final String identity;

    ObjectExpression(
            SourceSlot<B> source,
            ObjectNode node,
            BooleanNode presence,
            String path) {
        this.source = source;
        this.node = node;
        this.presence = presence;
        this.path = path;
        identity = DataFlowSupport.identity(
                "object-expression-v1\n" + source.alias() + "\n"
                        + node.canonical() + "\n" + presence.canonical() + "\n");
    }

    public BooleanExpression<B> isPresent() {
        return new BooleanExpression<B>(source, presence);
    }

    public BooleanExpression<B> isAbsent() {
        return isPresent().not();
    }

    public BooleanExpression<B> equalTo(final T right) {
        final ObjectNode left = node;
        final BooleanNode available = presence;
        return new BooleanExpression<B>(
                source,
                new BooleanNode() {
                    @Override
                    public boolean evaluate(DataFlowBinding binding, int index) {
                        ExpressionNodes.requirePresent(
                                available, binding, index, path);
                        return ExpressionNodes.equalObjects(
                                left.evaluate(binding, index), right);
                    }

                    @Override
                    public String canonical() {
                        return "object-equal(" + left.canonical()
                                + ",redacted-constant)";
                    }
                });
    }

    public ObjectExpression<B, T> coalesce(final T fallback) {
        final ObjectNode value = node;
        final BooleanNode available = presence;
        return new ObjectExpression<B, T>(
                source,
                new ObjectNode() {
                    @Override
                    public Object evaluate(DataFlowBinding binding, int index) {
                        return available.evaluate(binding, index)
                                ? value.evaluate(binding, index) : fallback;
                    }

                    @Override
                    public String canonical() {
                        return "object-coalesce(" + value.canonical()
                                + ",redacted-constant)";
                    }
                },
                ExpressionNodes.alwaysPresent(),
                path + ".coalesce");
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
    T evaluate(DataFlowBinding binding, int index) {
        ExpressionNodes.requirePresent(presence, binding, index, path);
        return (T) node.evaluate(binding, index);
    }

    boolean required() {
        return ExpressionNodes.isAlwaysPresent(presence);
    }
}
