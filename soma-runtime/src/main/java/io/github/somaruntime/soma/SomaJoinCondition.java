package io.github.somaruntime.soma;

/** Equality condition plus the still-open Join-kind choice. */
public interface SomaJoinCondition<L, R, LS>
        extends SomaMatchedJoinStream<L, R> {
    @Override SomaJoinCondition<L, R, LS> parallel();
    <V> SomaJoinCondition<L, R, LS> and(
            SomaKeyableField<L, V> left,
            SomaKeyableField<R, V> right);
    SomaMatchedJoinStream<L, R> inner();
    SomaOuterJoinStream<L, R> left();
    SomaOuterJoinStream<L, R> full();
    LS semi();
    LS anti();
}
