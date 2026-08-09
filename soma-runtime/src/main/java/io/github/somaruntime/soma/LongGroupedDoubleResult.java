package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface LongGroupedDoubleResult {
    long size();
    void forEach(SomaLongDoubleConsumer consumer);
    List<LongGroupedDoubleEntry> toList();
    LongGroupedDoubleEntry[] toArray();
}
