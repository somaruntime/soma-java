package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface ByteGroupedLongResult {
    long size();
    void forEach(SomaByteLongConsumer consumer);
    List<ByteGroupedLongEntry> toList();
    ByteGroupedLongEntry[] toArray();
}
