package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface BooleanGroupedDoubleSummaryResult {
    long size();
    void forEach(SomaBooleanDoubleSummaryConsumer consumer);
    List<BooleanGroupedDoubleSummaryEntry> toList();
    BooleanGroupedDoubleSummaryEntry[] toArray();
}
