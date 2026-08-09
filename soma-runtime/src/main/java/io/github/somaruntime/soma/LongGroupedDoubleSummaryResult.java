package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface LongGroupedDoubleSummaryResult {
    long size();
    void forEach(SomaLongDoubleSummaryConsumer consumer);
    List<LongGroupedDoubleSummaryEntry> toList();
    LongGroupedDoubleSummaryEntry[] toArray();
}
