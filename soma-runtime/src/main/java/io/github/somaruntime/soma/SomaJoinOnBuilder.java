package io.github.somaruntime.soma;

/** Typed first-condition builder for an Equality Join. */
public interface SomaJoinOnBuilder<L, R, LS> {
    <V> SomaJoinCondition<L, R, LS> on(
            SomaKeyableField<L, V> left,
            SomaKeyableField<R, V> right);
}
