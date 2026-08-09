package io.github.somaruntime.soma;

import java.util.OptionalDouble;
import java.util.OptionalInt;

public interface SomaIntStream {
    SomaIntStream parallel();
    SomaIntStream filter(SomaIntPredicate predicate);
    SomaIntStream map(SomaIntUnaryOperator mapper);
    SomaLongStream mapToLong(SomaIntToLongFunction mapper);
    SomaDoubleStream mapToDouble(SomaIntToDoubleFunction mapper);
    SomaIntStream distinct();
    SomaIntStream sorted();
    SomaIntStream top(long count);
    SomaIntStream skip(long count);
    SomaIntStream limit(long count);
    long count();
    boolean anyMatch(SomaIntPredicate predicate);
    boolean allMatch(SomaIntPredicate predicate);
    boolean noneMatch(SomaIntPredicate predicate);
    OptionalInt findFirst();
    OptionalInt min();
    OptionalInt max();
    long sum();
    OptionalDouble average();
    SomaLongSummary summaryStatistics();
    void forEach(SomaIntConsumer action);
    void forEachOrdered(SomaIntConsumer action);
    int[] toArray();
    String _explain();
}
