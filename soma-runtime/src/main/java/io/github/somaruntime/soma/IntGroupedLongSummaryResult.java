package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface IntGroupedLongSummaryResult {
    long size();
    void forEach(SomaIntLongSummaryConsumer consumer);
    List<IntGroupedLongSummaryEntry> toList();
    IntGroupedLongSummaryEntry[] toArray();
}
