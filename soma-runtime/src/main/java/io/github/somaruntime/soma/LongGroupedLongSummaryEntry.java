package io.github.somaruntime.soma;

/** Detached primitive-key grouped result entry. */
public interface LongGroupedLongSummaryEntry {
    long key();
    SomaLongSummary value();
}
