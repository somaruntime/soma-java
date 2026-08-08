package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaRelationExpression;

/** I2 typed predicate carrier over the unified leaf runtime. */
final class GeneratedExpression<R> implements SomaExpression<R> {

    interface Node {
        boolean matches(TableStateRoot root, long locator);
    }

    private final GeneratedTable owner;
    private final Node node;

    GeneratedExpression(GeneratedTable owner, Node node) {
        this.owner = owner;
        this.node = node;
    }

    GeneratedTable owner() { return owner; }
    Node node() { return node; }

    @Override
    public SomaExpression<R> and(SomaExpression<R> other) {
        return combine(other, true);
    }

    @Override
    public SomaRelationExpression and(SomaRelationExpression other) {
        return combine(other, true);
    }

    @Override
    public SomaExpression<R> or(SomaExpression<R> other) {
        return combine(other, false);
    }

    @Override
    public SomaRelationExpression or(SomaRelationExpression other) {
        return combine(other, false);
    }

    @Override
    public SomaExpression<R> not() {
        final Node source = node;
        return new GeneratedExpression<R>(owner, new Node() {
            @Override public boolean matches(TableStateRoot root, long locator) {
                return !source.matches(root, locator);
            }
        });
    }

    private SomaExpression<R> combine(Object other, final boolean conjunction) {
        if (!(other instanceof SomaExpression)) {
            throw SomaFailures.invalid(
                    io.github.somaruntime.soma.SomaOperation.QUERY,
                    "expression is not issued by SOMA");
        }
        final Node right = owner.requireOwnedExpression((SomaExpression<?>) other);
        final Node left = node;
        return new GeneratedExpression<R>(owner, new Node() {
            @Override public boolean matches(TableStateRoot root, long locator) {
                return conjunction
                        ? left.matches(root, locator) && right.matches(root, locator)
                        : left.matches(root, locator) || right.matches(root, locator);
            }
        });
    }
}
