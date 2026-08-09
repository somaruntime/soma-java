package io.github.somaruntime.soma;

import java.util.OptionalDouble;

public interface SomaDoubleStream {
    SomaDoubleStream parallel();
    SomaDoubleStream filter(SomaDoublePredicate predicate);
    SomaDoubleStream map(SomaDoubleUnaryOperator mapper);
    SomaIntStream mapToInt(SomaDoubleToIntFunction mapper);
    SomaLongStream mapToLong(SomaDoubleToLongFunction mapper);
    SomaDoubleStream distinct();
    SomaDoubleStream sorted();
    SomaDoubleStream top(long count);
    SomaDoubleStream skip(long count);
    SomaDoubleStream limit(long count);
    long count();
    boolean anyMatch(SomaDoublePredicate predicate);
    boolean allMatch(SomaDoublePredicate predicate);
    boolean noneMatch(SomaDoublePredicate predicate);
    OptionalDouble findFirst();
    OptionalDouble min();
    OptionalDouble max();
    double sum();
    OptionalDouble average();
    SomaDoubleSummary summaryStatistics();
    void forEach(SomaDoubleConsumer action);
    void forEachOrdered(SomaDoubleConsumer action);
    double[] toArray();
    String _explain();
}
