package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface BooleanGroupedLongResult {
    long size();
    void forEach(SomaBooleanLongConsumer consumer);
    List<BooleanGroupedLongEntry> toList();
    BooleanGroupedLongEntry[] toArray();
}
