package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface CharGroupedLongSummaryResult {
    long size();
    void forEach(SomaCharLongSummaryConsumer consumer);
    List<CharGroupedLongSummaryEntry> toList();
    CharGroupedLongSummaryEntry[] toArray();
}
