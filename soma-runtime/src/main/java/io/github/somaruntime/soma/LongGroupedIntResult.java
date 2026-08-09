package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface LongGroupedIntResult {
    long size();
    void forEach(SomaLongIntConsumer consumer);
    List<LongGroupedIntEntry> toList();
    LongGroupedIntEntry[] toArray();
}
