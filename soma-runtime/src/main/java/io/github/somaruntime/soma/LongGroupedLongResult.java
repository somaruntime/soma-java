package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface LongGroupedLongResult {
    long size();
    void forEach(SomaLongLongConsumer consumer);
    List<LongGroupedLongEntry> toList();
    LongGroupedLongEntry[] toArray();
}
