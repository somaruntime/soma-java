package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface CharGroupedDoubleSummaryResult {
    long size();
    void forEach(SomaCharDoubleSummaryConsumer consumer);
    List<CharGroupedDoubleSummaryEntry> toList();
    CharGroupedDoubleSummaryEntry[] toArray();
}
