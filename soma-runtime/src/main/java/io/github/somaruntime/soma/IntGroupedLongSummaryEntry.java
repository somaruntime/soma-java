package io.github.somaruntime.soma;

/** Detached primitive-key grouped result entry. */
public interface IntGroupedLongSummaryEntry {
    int key();
    SomaLongSummary value();
}
