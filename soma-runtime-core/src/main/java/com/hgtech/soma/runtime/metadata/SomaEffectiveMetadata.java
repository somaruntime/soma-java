package com.hgtech.soma.runtime.metadata;

import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.StatsMode;

import java.util.List;

/** Immutable validated RuntimePlan projection；不包含 live topology 或 counters。 */
public interface SomaEffectiveMetadata {
    String schemaHash();
    String runtimePlanHash();
    String runtimeCompatibility();
    String generatedProtocol();
    String planProtocol();
    String allocationEstimator();
    MaterializationBudget defaultMaterializationBudget();
    StatsMode statsMode();
    long maximumAggregateStorageBytes();
    long maximumOwnershipTableInstances();
    List<SomaTableEffectiveMetadata> tables();
    SomaTableEffectiveMetadata requireTable(String logicalName);
}
