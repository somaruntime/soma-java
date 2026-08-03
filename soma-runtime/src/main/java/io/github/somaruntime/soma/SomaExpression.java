package io.github.somaruntime.soma;

/**
 * Processor-issued typed predicate plan. It is intentionally not a functional
 * interface; opaque application callbacks use {@link SomaPredicate} instead.
 *
 * @param <R> generated row view type
 */
public interface SomaExpression<R> extends SomaRelationExpression {
    @Override
    SomaRelationExpression and(SomaRelationExpression other);

    SomaExpression<R> and(SomaExpression<R> other);

    @Override
    SomaRelationExpression or(SomaRelationExpression other);

    SomaExpression<R> or(SomaExpression<R> other);

    @Override
    SomaExpression<R> not();
}
