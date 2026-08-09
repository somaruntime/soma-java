package io.github.somaruntime.soma;

import java.util.function.Consumer;
import java.util.function.Function;

/** Query-only stream whose element is a borrowed Join Pair. */
public interface SomaPairStream<L, R> {
    SomaPairStream<L, R> filter(SomaRelationExpression expression);
    SomaPairStream<L, R> filter(SomaPredicate<? super JoinPair<L, R>> callback);
    <T> MappedStream<T> map(
            Function<? super JoinPair<L, R>, ? extends T> mapper);
    SomaIntStream mapToInt(SomaToIntFunction<? super JoinPair<L, R>> mapper);
    SomaLongStream mapToLong(SomaToLongFunction<? super JoinPair<L, R>> mapper);
    SomaDoubleStream mapToDouble(
            SomaToDoubleFunction<? super JoinPair<L, R>> mapper);
    long count();
    boolean anyMatch(SomaPredicate<? super JoinPair<L, R>> predicate);
    boolean allMatch(SomaPredicate<? super JoinPair<L, R>> predicate);
    boolean noneMatch(SomaPredicate<? super JoinPair<L, R>> predicate);
    void forEach(Consumer<? super JoinPair<L, R>> action);
    void forEachOrdered(Consumer<? super JoinPair<L, R>> action);
    String _explain();
}
