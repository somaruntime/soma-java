package com.hgtech.soma.runtime.metadata;

/** Detached current/high-water snapshot of one generated exact-access binding。 */
public interface SomaExactAccessRuntimeMetadata {
    String name();
    String implementationIdentity();
    long entryCount();
    long groupCount();
    long probeCount();
    long collisionCount();
    long rehashCount();
    long retainedStructuralBytes();
    long structuralHighWaterBytes();
}
