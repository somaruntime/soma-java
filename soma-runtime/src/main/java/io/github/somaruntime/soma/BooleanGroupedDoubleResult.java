package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface BooleanGroupedDoubleResult {
    long size();
    void forEach(SomaBooleanDoubleConsumer consumer);
    List<BooleanGroupedDoubleEntry> toList();
    BooleanGroupedDoubleEntry[] toArray();
}
