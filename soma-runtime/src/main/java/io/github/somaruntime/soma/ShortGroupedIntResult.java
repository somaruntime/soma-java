package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface ShortGroupedIntResult {
    long size();
    void forEach(SomaShortIntConsumer consumer);
    List<ShortGroupedIntEntry> toList();
    ShortGroupedIntEntry[] toArray();
}
