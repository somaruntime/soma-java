package io.github.somaruntime.soma;

/** A typed relation expression issued by generated SOMA Field endpoints. */
public interface SomaRelationExpression {

    SomaRelationExpression and(SomaRelationExpression other);

    SomaRelationExpression or(SomaRelationExpression other);

    SomaRelationExpression not();
}
