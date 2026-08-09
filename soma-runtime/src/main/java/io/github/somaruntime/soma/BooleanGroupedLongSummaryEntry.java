package io.github.somaruntime.soma;

/** Detached primitive-key grouped result entry. */
public interface BooleanGroupedLongSummaryEntry {
    boolean key();
    SomaLongSummary value();
}
