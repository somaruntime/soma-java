package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.metadata.SomaEffectiveMetadata;
import com.hgtech.soma.runtime.metadata.SomaExactAccess;
import com.hgtech.soma.runtime.metadata.SomaPrimaryLocator;
import com.hgtech.soma.runtime.metadata.SomaStorageLayout;
import com.hgtech.soma.runtime.metadata.SomaTableEffectiveMetadata;
import com.hgtech.soma.runtime.metadata.SomaWorkloadProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/** RuntimePlan-owned detached Effective Metadata projection。 */
final class EffectiveMetadataProjection implements SomaEffectiveMetadata {
    private final RuntimePlan plan;
    private final List<SomaTableEffectiveMetadata> tables;
    private final TreeMap<String, SomaTableEffectiveMetadata> tablesByName =
            new TreeMap<String, SomaTableEffectiveMetadata>(
                    UnicodeCodePointOrder.INSTANCE);

    EffectiveMetadataProjection(RuntimePlan plan) {
        this.plan = plan;
        for (TablePlan table : plan.tables()) {
            TableProjection projection = new TableProjection(table);
            tablesByName.put(table.tableLogicalName(), projection);
        }
        tables = Collections.unmodifiableList(
                new ArrayList<SomaTableEffectiveMetadata>(
                        tablesByName.values()));
    }

    @Override public String schemaHash() { return plan.schemaHash(); }
    @Override public String runtimePlanHash() {
        return plan.runtimePlanHash();
    }
    @Override public String runtimeCompatibility() {
        return plan.runtimeCompatibility();
    }
    @Override public String generatedProtocol() {
        return plan.generatedProtocol();
    }
    @Override public String planProtocol() { return plan.planProtocol(); }
    @Override public String allocationEstimator() {
        return plan.allocationEstimator();
    }
    @Override public MaterializationBudget defaultMaterializationBudget() {
        return plan.defaultMaterializationBudget();
    }
    @Override public StatsMode statsMode() { return plan.statsMode(); }
    @Override public long maximumAggregateStorageBytes() {
        return plan.maximumAggregateStorageBytes();
    }
    @Override public long maximumOwnershipTableInstances() {
        return plan.maximumOwnershipTableInstances();
    }
    @Override public List<SomaTableEffectiveMetadata> tables() {
        return tables;
    }
    @Override public SomaTableEffectiveMetadata requireTable(
            String logicalName) {
        String required = CanonicalSupport.required(
                logicalName, "logicalName");
        SomaTableEffectiveMetadata table = tablesByName.get(required);
        if (table == null) {
            throw RuntimePlan.invalidPlan(
                    "effective.tables." + required, "unknown table");
        }
        return table;
    }

    private static final class TableProjection
            implements SomaTableEffectiveMetadata {
        private final TablePlan plan;

        private TableProjection(TablePlan plan) {
            this.plan = plan;
        }

        @Override public String logicalName() {
            return plan.tableLogicalName();
        }
        @Override public String algorithmIdentity() {
            return plan.algorithm();
        }
        @Override public SomaStorageLayout storageLayout() {
            return plan.storageLayout();
        }
        @Override public SomaWorkloadProfile workloadProfile() {
            return plan.workloadProfile();
        }
        @Override public String storageLayoutFormulaIdentity() {
            return plan.storageLayoutFormula();
        }
        @Override public int structuralBytesPerRow() {
            return plan.structuralBytesPerRow();
        }
        @Override public int flatHeadRows() {
            return plan.flatHeadRows();
        }
        @Override public int segmentRows() {
            return plan.segmentRows();
        }
        @Override public SomaPrimaryLocator primaryLocator() {
            return plan.primaryLocator();
        }
        @Override public SomaExactAccess exactAccess() {
            return plan.exactAccess();
        }
        @Override public int initialCapacity() {
            return plan.initialCapacity();
        }
        @Override public int planningRows() {
            return plan.planningRows();
        }
        @Override public int maximumRows() {
            return plan.maximumRows();
        }
        @Override public int growthNumerator() {
            return plan.growthNumerator();
        }
        @Override public int growthDenominator() {
            return plan.growthDenominator();
        }
        @Override public long maximumUpdateScratchBytes() {
            return plan.maximumUpdateScratchBytes();
        }
        @Override public long maximumOperationScratchBytes() {
            return plan.maximumOperationScratchBytes();
        }
        @Override public long maximumBulkScratchBytes() {
            return plan.maximumBulkScratchBytes();
        }
        @Override public long maximumTableStorageBytes() {
            return plan.maximumTableStorageBytes();
        }
        @Override public boolean stringCapable() {
            return plan.stringCapable();
        }
        @Override public StringResourceProfile stringResourceProfile() {
            return plan.stringResourceProfile();
        }
    }
}
