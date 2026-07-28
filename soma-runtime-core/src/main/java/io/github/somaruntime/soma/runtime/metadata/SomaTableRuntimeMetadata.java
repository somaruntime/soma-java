package io.github.somaruntime.soma.runtime.metadata;

import io.github.somaruntime.soma.runtime.TableStats;

import java.util.List;

/** Detached historical topology and observation snapshot for one Table instance。 */
public interface SomaTableRuntimeMetadata {
    SomaTableMetadata descriptor();
    String logicalName();
    String runtimePlanHash();
    SomaStorageLayout storageLayout();
    SomaPrimaryLocator primaryLocator();
    SomaPrimaryLocatorLayout primaryLocatorLayout();
    String primaryLocatorImplementationIdentity();
    int rows();
    int capacity();
    long structuralEpoch();
    boolean released();
    List<SomaSegmentMetadata> segments();
    List<SomaIndexRuntimeMetadata> indexes();
    SomaIndexRuntimeMetadata requireIndex(String name);
    List<SomaUniqueRuntimeMetadata> uniques();
    SomaUniqueRuntimeMetadata requireUnique(String name);
    TableStats observation();
}
