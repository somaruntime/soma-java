package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface BooleanGroupedLongSummaryResult {
    long size();
    void forEach(SomaBooleanLongSummaryConsumer consumer);
    List<BooleanGroupedLongSummaryEntry> toList();
    BooleanGroupedLongSummaryEntry[] toArray();
}
