package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface CharGroupedDoubleResult {
    long size();
    void forEach(SomaCharDoubleConsumer consumer);
    List<CharGroupedDoubleEntry> toList();
    CharGroupedDoubleEntry[] toArray();
}
