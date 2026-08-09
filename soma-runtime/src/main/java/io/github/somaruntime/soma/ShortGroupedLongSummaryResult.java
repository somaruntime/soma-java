package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface ShortGroupedLongSummaryResult {
    long size();
    void forEach(SomaShortLongSummaryConsumer consumer);
    List<ShortGroupedLongSummaryEntry> toList();
    ShortGroupedLongSummaryEntry[] toArray();
}
