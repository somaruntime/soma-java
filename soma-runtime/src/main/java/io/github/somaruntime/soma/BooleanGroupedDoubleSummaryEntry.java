package io.github.somaruntime.soma;

/** Detached primitive-key grouped result entry. */
public interface BooleanGroupedDoubleSummaryEntry {
    boolean key();
    SomaDoubleSummary value();
}
