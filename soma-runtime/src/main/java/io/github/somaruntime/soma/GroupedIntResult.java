package io.github.somaruntime.soma;

import java.util.List;

/** Detached reference-key grouped result. */
public interface GroupedIntResult<K> {
    long size();
    void forEach(java.util.function.ObjIntConsumer<? super K> consumer);
    List<GroupedIntEntry<K>> toList();
    GroupedIntEntry<K>[] toArray();
}
