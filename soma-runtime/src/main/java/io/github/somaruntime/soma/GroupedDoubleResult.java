package io.github.somaruntime.soma;

import java.util.List;

/** Detached reference-key grouped result. */
public interface GroupedDoubleResult<K> {
    long size();
    void forEach(java.util.function.ObjDoubleConsumer<? super K> consumer);
    List<GroupedDoubleEntry<K>> toList();
    GroupedDoubleEntry<K>[] toArray();
}
