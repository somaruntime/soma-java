package io.github.somaruntime.soma;

/** Typed logical relation-expression composition contract. */
public interface SomaRelationExpression {
    SomaRelationExpression and(SomaRelationExpression other);

    SomaRelationExpression or(SomaRelationExpression other);

    SomaRelationExpression not();
}
