package io.github.somaruntime.soma;

/** A same-record typed expression. This is not an application extension SPI. */
public interface SomaExpression<R> extends SomaRelationExpression {

    SomaExpression<R> and(SomaExpression<R> other);

    SomaExpression<R> or(SomaExpression<R> other);

    @Override
    SomaExpression<R> not();
}
