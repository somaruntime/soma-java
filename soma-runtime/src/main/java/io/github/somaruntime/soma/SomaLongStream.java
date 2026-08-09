package io.github.somaruntime.soma;

import java.util.OptionalDouble;
import java.util.OptionalLong;

public interface SomaLongStream {
    SomaLongStream filter(SomaLongPredicate predicate);
    SomaLongStream map(SomaLongUnaryOperator mapper);
    SomaIntStream mapToInt(SomaLongToIntFunction mapper);
    SomaDoubleStream mapToDouble(SomaLongToDoubleFunction mapper);
    SomaLongStream distinct();
    SomaLongStream sorted();
    SomaLongStream top(long count);
    SomaLongStream skip(long count);
    SomaLongStream limit(long count);
    long count();
    boolean anyMatch(SomaLongPredicate predicate);
    boolean allMatch(SomaLongPredicate predicate);
    boolean noneMatch(SomaLongPredicate predicate);
    OptionalLong findFirst();
    OptionalLong min();
    OptionalLong max();
    long sum();
    OptionalDouble average();
    SomaLongSummary summaryStatistics();
    void forEach(SomaLongConsumer action);
    void forEachOrdered(SomaLongConsumer action);
    long[] toArray();
    String _explain();
}
