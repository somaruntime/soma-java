package io.github.somaruntime.soma;

/** Detached primitive-key grouped result entry. */
public interface ShortGroupedLongSummaryEntry {
    short key();
    SomaLongSummary value();
}
