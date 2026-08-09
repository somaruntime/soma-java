package io.github.somaruntime.soma;

/** Unboxed object-key grouped summary consumer. */
@FunctionalInterface
public interface SomaObjDoubleSummaryConsumer<K> {
    void accept(K key, SomaDoubleSummary value);
}
