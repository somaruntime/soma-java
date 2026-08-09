package io.github.somaruntime.soma;

/** Equality/Cross Join stream whose two sides are always present. */
public interface SomaMatchedJoinStream<L, R> extends SomaPairStream<L, R> {
    @Override SomaMatchedJoinStream<L, R> parallel();
    @Override SomaMatchedJoinStream<L, R> filter(SomaRelationExpression expression);
    @Override SomaMatchedJoinStream<L, R> filter(
            SomaPredicate<? super JoinPair<L, R>> callback);
    <A, B> MappedStream<SomaTuple2<A, B>> select(
            SomaFieldEndpoint<L, A> left,
            SomaFieldEndpoint<R, B> right);
}
