package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface LongGroupedLongSummaryResult {
    long size();
    void forEach(SomaLongLongSummaryConsumer consumer);
    List<LongGroupedLongSummaryEntry> toList();
    LongGroupedLongSummaryEntry[] toArray();
}
