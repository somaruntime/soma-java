package io.github.somaruntime.soma;

/** Detached grouped result entry. */
public interface GroupedDoubleSummaryEntry<K> {
    K key();
    SomaDoubleSummary value();
}
