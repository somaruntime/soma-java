package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface ByteGroupedLongSummaryResult {
    long size();
    void forEach(SomaByteLongSummaryConsumer consumer);
    List<ByteGroupedLongSummaryEntry> toList();
    ByteGroupedLongSummaryEntry[] toArray();
}
