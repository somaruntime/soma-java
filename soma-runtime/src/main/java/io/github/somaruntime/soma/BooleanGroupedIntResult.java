package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface BooleanGroupedIntResult {
    long size();
    void forEach(SomaBooleanIntConsumer consumer);
    List<BooleanGroupedIntEntry> toList();
    BooleanGroupedIntEntry[] toArray();
}
