package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface CharGroupedIntResult {
    long size();
    void forEach(SomaCharIntConsumer consumer);
    List<CharGroupedIntEntry> toList();
    CharGroupedIntEntry[] toArray();
}
