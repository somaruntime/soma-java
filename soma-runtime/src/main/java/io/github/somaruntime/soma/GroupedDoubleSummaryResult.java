package io.github.somaruntime.soma;

import java.util.List;

/** Detached reference-key grouped result. */
public interface GroupedDoubleSummaryResult<K> {
    long size();
    void forEach(SomaObjDoubleSummaryConsumer<? super K> consumer);
    List<GroupedDoubleSummaryEntry<K>> toList();
    GroupedDoubleSummaryEntry<K>[] toArray();
}
