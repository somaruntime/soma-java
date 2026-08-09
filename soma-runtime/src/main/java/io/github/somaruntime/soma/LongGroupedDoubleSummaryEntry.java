package io.github.somaruntime.soma;

/** Detached primitive-key grouped result entry. */
public interface LongGroupedDoubleSummaryEntry {
    long key();
    SomaDoubleSummary value();
}
