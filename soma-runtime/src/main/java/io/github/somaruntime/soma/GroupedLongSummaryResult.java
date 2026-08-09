package io.github.somaruntime.soma;

import java.util.List;

/** Detached reference-key grouped result. */
public interface GroupedLongSummaryResult<K> {
    long size();
    void forEach(SomaObjLongSummaryConsumer<? super K> consumer);
    List<GroupedLongSummaryEntry<K>> toList();
    GroupedLongSummaryEntry<K>[] toArray();
}
