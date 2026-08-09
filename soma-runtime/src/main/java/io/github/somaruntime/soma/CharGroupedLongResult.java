package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface CharGroupedLongResult {
    long size();
    void forEach(SomaCharLongConsumer consumer);
    List<CharGroupedLongEntry> toList();
    CharGroupedLongEntry[] toArray();
}
