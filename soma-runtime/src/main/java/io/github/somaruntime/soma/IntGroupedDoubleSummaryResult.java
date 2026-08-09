package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface IntGroupedDoubleSummaryResult {
    long size();
    void forEach(SomaIntDoubleSummaryConsumer consumer);
    List<IntGroupedDoubleSummaryEntry> toList();
    IntGroupedDoubleSummaryEntry[] toArray();
}
