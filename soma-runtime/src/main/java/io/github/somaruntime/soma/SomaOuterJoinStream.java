package io.github.somaruntime.soma;

/** Left/Full Join stream with explicit missing-side semantics. */
public interface SomaOuterJoinStream<L, R> extends SomaPairStream<L, R> {
    @Override SomaOuterJoinStream<L, R> filter(SomaRelationExpression expression);
    @Override SomaOuterJoinStream<L, R> filter(
            SomaPredicate<? super JoinPair<L, R>> callback);
}
