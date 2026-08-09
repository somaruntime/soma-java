package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface ByteGroupedDoubleSummaryResult {
    long size();
    void forEach(SomaByteDoubleSummaryConsumer consumer);
    List<ByteGroupedDoubleSummaryEntry> toList();
    ByteGroupedDoubleSummaryEntry[] toArray();
}
