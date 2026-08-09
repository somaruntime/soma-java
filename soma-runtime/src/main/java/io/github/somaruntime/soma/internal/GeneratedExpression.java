package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaRelationExpression;

/** Unforgeable public carrier for one immutable, data-only predicate IR tree. */
final class GeneratedExpression<R> implements SomaExpression<R> {

    private final GeneratedTable owner;
    private final PredicateIr predicate;

    GeneratedExpression(GeneratedTable owner, PredicateIr predicate) {
        this.owner = owner;
        this.predicate = predicate;
    }

    GeneratedTable owner() { return owner; }
    PredicateIr predicate() { return predicate; }

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
        return new GeneratedExpression<R>(owner, PredicateIr.not(predicate));
    }

    private SomaExpression<R> combine(Object other, final boolean conjunction) {
        if (!(other instanceof SomaExpression)) {
            throw SomaFailures.invalid(
                    io.github.somaruntime.soma.SomaOperation.QUERY,
                    "expression is not issued by SOMA");
        }
        PredicateIr right = owner.requireOwnedExpression((SomaExpression<?>) other);
        return new GeneratedExpression<R>(owner, PredicateIr.binary(
                conjunction ? PredicateIr.Kind.AND : PredicateIr.Kind.OR,
                predicate,
                right));
    }
}
