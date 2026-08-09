package io.github.somaruntime.soma;

/** Detached primitive-key grouped result entry. */
public interface IntGroupedDoubleSummaryEntry {
    int key();
    SomaDoubleSummary value();
}
