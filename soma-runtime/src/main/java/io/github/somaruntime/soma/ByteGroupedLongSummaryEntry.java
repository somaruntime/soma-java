package io.github.somaruntime.soma;

/** Detached primitive-key grouped result entry. */
public interface ByteGroupedLongSummaryEntry {
    byte key();
    SomaLongSummary value();
}
