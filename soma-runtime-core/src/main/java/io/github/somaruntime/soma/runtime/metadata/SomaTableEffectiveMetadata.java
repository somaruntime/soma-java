package io.github.somaruntime.soma.runtime.metadata;

import io.github.somaruntime.soma.runtime.StringResourceProfile;

/** Immutable effective plan projection for one logical Table。 */
public interface SomaTableEffectiveMetadata {
    String logicalName();
    String algorithmIdentity();
    SomaStorageLayout storageLayout();
    SomaWorkloadProfile workloadProfile();
    String storageLayoutFormulaIdentity();
    int structuralBytesPerRow();
    int flatHeadRows();
    int segmentRows();
    SomaPrimaryLocator primaryLocator();
    SomaPrimaryLocatorLayout primaryLocatorLayout();
    String primaryLocatorLayoutFormulaIdentity();
    SomaExactAccess exactAccess();
    int initialCapacity();
    int planningRows();
    int maximumRows();
    int growthNumerator();
    int growthDenominator();
    long maximumUpdateScratchBytes();
    long maximumOperationScratchBytes();
    long maximumBulkScratchBytes();
    long maximumTableStorageBytes();
    boolean stringCapable();
    StringResourceProfile stringResourceProfile();
}
