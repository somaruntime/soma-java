package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface ShortGroupedDoubleSummaryResult {
    long size();
    void forEach(SomaShortDoubleSummaryConsumer consumer);
    List<ShortGroupedDoubleSummaryEntry> toList();
    ShortGroupedDoubleSummaryEntry[] toArray();
}
