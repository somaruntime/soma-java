package io.github.somaruntime.soma;

/** Detached primitive-key grouped result entry. */
public interface ShortGroupedDoubleSummaryEntry {
    short key();
    SomaDoubleSummary value();
}
