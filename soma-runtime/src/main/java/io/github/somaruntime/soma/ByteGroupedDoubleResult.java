package io.github.somaruntime.soma;

import java.util.List;

/** Detached columnar primitive-key grouped result. */
public interface ByteGroupedDoubleResult {
    long size();
    void forEach(SomaByteDoubleConsumer consumer);
    List<ByteGroupedDoubleEntry> toList();
    ByteGroupedDoubleEntry[] toArray();
}
