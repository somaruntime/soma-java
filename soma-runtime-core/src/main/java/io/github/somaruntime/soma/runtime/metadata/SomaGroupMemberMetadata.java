package io.github.somaruntime.soma.runtime.metadata;

import io.github.somaruntime.soma.runtime.SomaGroupMemberState;

/** Detached historical snapshot of one stable SomaGroup member slot。 */
public interface SomaGroupMemberMetadata {
    String memberId();
    String schemaHash();
    String rootTable();
    String runtimePlanHash();
    SomaGroupMemberState state();
    long aggregateInstanceId();
    long attachmentOrdinal();
    long maximumStructuralBytes();
    long retainedStructuralBytes();
    long transientStructuralBytes();
    long currentStructuralBytes();
    long structuralHighWaterBytes();
    long maximumTableInstances();
    long currentTableInstances();
    long tableInstanceHighWater();
    /**
     * Attached root snapshot；planned or unpublished member returns {@code null}。
     */
    SomaTableRuntimeMetadata rootTableRuntimeMetadata();
}
