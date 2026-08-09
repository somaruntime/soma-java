package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface IntGroupedLongResult {
    long size();
    void forEach(SomaIntLongConsumer consumer);
    List<IntGroupedLongEntry> toList();
    IntGroupedLongEntry[] toArray();
}
