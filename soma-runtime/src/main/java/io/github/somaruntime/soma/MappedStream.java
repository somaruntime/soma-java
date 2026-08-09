package io.github.somaruntime.soma;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/** One-shot arbitrary reference projection with no Table mutation lineage. */
public interface MappedStream<R> {
    MappedStream<R> parallel();
    MappedStream<R> filter(SomaPredicate<? super R> predicate);
    <U> MappedStream<U> map(Function<? super R, ? extends U> mapper);
    SomaIntStream mapToInt(SomaToIntFunction<? super R> mapper);
    SomaLongStream mapToLong(SomaToLongFunction<? super R> mapper);
    SomaDoubleStream mapToDouble(SomaToDoubleFunction<? super R> mapper);
    MappedStream<R> distinct();
    MappedStream<R> sorted(Comparator<? super R> comparator);
    MappedStream<R> top(long count, Comparator<? super R> comparator);
    MappedStream<R> skip(long count);
    MappedStream<R> limit(long count);
    long count();
    boolean anyMatch(SomaPredicate<? super R> predicate);
    boolean allMatch(SomaPredicate<? super R> predicate);
    boolean noneMatch(SomaPredicate<? super R> predicate);
    Optional<R> findFirst();
    Optional<R> min(Comparator<? super R> comparator);
    Optional<R> max(Comparator<? super R> comparator);
    void forEach(Consumer<? super R> action);
    void forEachOrdered(Consumer<? super R> action);
    List<R> toList();
    <A> A[] toArray(Class<A> componentType);
    String _explain();
}
