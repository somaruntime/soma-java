package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface ShortGroupedDoubleResult {
    long size();
    void forEach(SomaShortDoubleConsumer consumer);
    List<ShortGroupedDoubleEntry> toList();
    ShortGroupedDoubleEntry[] toArray();
}
