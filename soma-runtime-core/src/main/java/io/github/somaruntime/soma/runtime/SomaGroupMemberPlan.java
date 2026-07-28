package io.github.somaruntime.soma.runtime;

import io.github.somaruntime.soma.runtime.metadata.SomaMetadata;
import io.github.somaruntime.soma.runtime.metadata.SomaTableMetadata;

/** Immutable stable member slot in one SomaGroupPlan。 */
public final class SomaGroupMemberPlan {
    private final String memberId;
    private final SomaMetadata metadata;
    private final SomaTableMetadata rootTable;
    private final RuntimePlan runtimePlan;

    SomaGroupMemberPlan(
            String memberId,
            SomaMetadata metadata,
            SomaTableMetadata rootTable,
            RuntimePlan runtimePlan) {
        this.memberId = memberId;
        this.metadata = metadata;
        this.rootTable = rootTable;
        this.runtimePlan = runtimePlan;
    }

    public String memberId() { return memberId; }
    public SomaMetadata metadata() { return metadata; }
    public SomaTableMetadata rootTable() { return rootTable; }
    public RuntimePlan runtimePlan() { return runtimePlan; }
    public long maximumStructuralBytes() {
        return runtimePlan.maximumAggregateStorageBytes();
    }
    public long maximumTableInstances() {
        return runtimePlan.maximumOwnershipTableInstances();
    }

    String toCanonicalJson() {
        return "{\"maximumStructuralBytes\":" + maximumStructuralBytes()
                + ",\"maximumTableInstances\":" + maximumTableInstances()
                + ",\"memberId\":" + CanonicalSupport.quote(memberId)
                + ",\"rootTable\":"
                + CanonicalSupport.quote(rootTable.logicalName())
                + ",\"runtimePlanHash\":"
                + CanonicalSupport.quote(runtimePlan.runtimePlanHash())
                + ",\"schemaHash\":"
                + CanonicalSupport.quote(runtimePlan.schemaHash()) + "}";
    }
}
