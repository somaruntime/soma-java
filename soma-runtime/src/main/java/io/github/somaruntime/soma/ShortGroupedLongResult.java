package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface ShortGroupedLongResult {
    long size();
    void forEach(SomaShortLongConsumer consumer);
    List<ShortGroupedLongEntry> toList();
    ShortGroupedLongEntry[] toArray();
}
