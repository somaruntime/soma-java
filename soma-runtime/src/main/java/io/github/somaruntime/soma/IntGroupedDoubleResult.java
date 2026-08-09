package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface IntGroupedDoubleResult {
    long size();
    void forEach(SomaIntDoubleConsumer consumer);
    List<IntGroupedDoubleEntry> toList();
    IntGroupedDoubleEntry[] toArray();
}
