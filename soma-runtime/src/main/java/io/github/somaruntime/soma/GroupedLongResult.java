package io.github.somaruntime.soma;

import java.util.List;

/** Detached reference-key grouped result. */
public interface GroupedLongResult<K> {
    long size();
    void forEach(java.util.function.ObjLongConsumer<? super K> consumer);
    List<GroupedLongEntry<K>> toList();
    GroupedLongEntry<K>[] toArray();
}
