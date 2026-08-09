package io.github.somaruntime.soma;

/** Detached primitive-key grouped result entry. */
public interface CharGroupedLongSummaryEntry {
    char key();
    SomaLongSummary value();
}
