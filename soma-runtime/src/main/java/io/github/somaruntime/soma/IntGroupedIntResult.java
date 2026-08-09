package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface IntGroupedIntResult {
    long size();
    void forEach(SomaIntIntConsumer consumer);
    List<IntGroupedIntEntry> toList();
    IntGroupedIntEntry[] toArray();
}
