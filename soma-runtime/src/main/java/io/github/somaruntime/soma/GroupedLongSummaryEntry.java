package io.github.somaruntime.soma;

/** Detached grouped result entry. */
public interface GroupedLongSummaryEntry<K> {
    K key();
    SomaLongSummary value();
}
