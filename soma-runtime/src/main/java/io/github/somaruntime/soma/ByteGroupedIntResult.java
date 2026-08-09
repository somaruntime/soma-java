package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface ByteGroupedIntResult {
    long size();
    void forEach(SomaByteIntConsumer consumer);
    List<ByteGroupedIntEntry> toList();
    ByteGroupedIntEntry[] toArray();
}
