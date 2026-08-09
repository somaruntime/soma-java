package io.github.somaruntime.soma;

/** Unboxed object-key grouped summary consumer. */
@FunctionalInterface
public interface SomaObjLongSummaryConsumer<K> {
    void accept(K key, SomaLongSummary value);
}
