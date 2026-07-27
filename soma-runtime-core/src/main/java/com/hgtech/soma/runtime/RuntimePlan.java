package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.GeneratedPlanToken;
import com.hgtech.soma.runtime.metadata.SomaEffectiveMetadata;
import com.hgtech.soma.runtime.metadata.SomaTableMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Immutable schema-bound effective runtime plan。 */
public final class RuntimePlan {
    private static final String HASH_PREFIX = "soma-java:v2:runtime-plan\n";

    private final String schemaHash;
    private final String runtimeCompatibility;
    private final String generatedProtocol;
    private final String planProtocol;
    private final String allocationEstimator;
    private final MaterializationBudget defaultMaterializationBudget;
    private final StatsMode statsMode;
    private final long maximumAggregateStorageBytes;
    private final long maximumOwnershipTableInstances;
    private final TreeMap<String, TablePlan> tablesByName;
    private final List<TablePlan> tables;
    private final TreeMap<String, ChildPlan> childrenByIdentity;
    private final List<ChildPlan> children;
    private final String runtimePlanHash;
    private final SomaEffectiveMetadata effectiveMetadata;

    private RuntimePlan(
            Builder builder, TreeMap<String, TablePlan> effectiveTables) {
        schemaHash = builder.schemaHash;
        runtimeCompatibility = builder.runtimeCompatibility;
        generatedProtocol = builder.generatedProtocol;
        planProtocol = builder.planProtocol;
        allocationEstimator = builder.allocationEstimator;
        defaultMaterializationBudget = builder.defaultMaterializationBudget;
        statsMode = builder.statsMode;
        maximumAggregateStorageBytes =
                builder.maximumAggregateStorageBytes;
        maximumOwnershipTableInstances =
                builder.maximumOwnershipTableInstances;
        tablesByName = new TreeMap<String, TablePlan>(
                UnicodeCodePointOrder.INSTANCE);
        tablesByName.putAll(effectiveTables);
        tables = Collections.unmodifiableList(
                new ArrayList<TablePlan>(tablesByName.values()));
        childrenByIdentity = new TreeMap<String, ChildPlan>(
                UnicodeCodePointOrder.INSTANCE);
        childrenByIdentity.putAll(builder.children);
        children = Collections.unmodifiableList(
                new ArrayList<ChildPlan>(childrenByIdentity.values()));
        runtimePlanHash = CanonicalSupport.sha256(
                HASH_PREFIX, toCanonicalJson());
        effectiveMetadata = new EffectiveMetadataProjection(this);
    }

    static Builder builder(
            String schemaHash,
            String runtimeCompatibility,
            String generatedProtocol,
            String planProtocol,
            String allocationEstimator) {
        return new Builder(schemaHash, runtimeCompatibility, generatedProtocol,
                planProtocol, allocationEstimator);
    }

    public static Builder generatedBuilder(
            GeneratedPlanToken token,
            String schemaHash,
            String runtimeCompatibility,
            String generatedProtocol,
            String planProtocol,
            String allocationEstimator) {
        GeneratedPlanToken.require(token);
        return new Builder(schemaHash, runtimeCompatibility, generatedProtocol,
                planProtocol, allocationEstimator);
    }

    public Builder toBuilder() {
        Builder builder = new Builder(
                schemaHash,
                runtimeCompatibility,
                generatedProtocol,
                planProtocol,
                allocationEstimator)
                .defaultMaterializationBudget(defaultMaterializationBudget)
                .statsMode(statsMode)
                .maximumAggregateStorageBytes(maximumAggregateStorageBytes)
                .maximumOwnershipTableInstances(
                        maximumOwnershipTableInstances);
        for (TablePlan table : tables) {
            builder.addTable(table);
        }
        for (ChildPlan child : children) {
            builder.addChild(child);
        }
        return builder;
    }

    public String schemaHash() { return schemaHash; }
    public String runtimeCompatibility() { return runtimeCompatibility; }
    public String generatedProtocol() { return generatedProtocol; }
    public String planProtocol() { return planProtocol; }
    public String allocationEstimator() { return allocationEstimator; }
    public String runtimePlanHash() { return runtimePlanHash; }
    public MaterializationBudget defaultMaterializationBudget() {
        return defaultMaterializationBudget;
    }
    public StatsMode statsMode() { return statsMode; }
    public long maximumAggregateStorageBytes() {
        return maximumAggregateStorageBytes;
    }
    public long maximumOwnershipTableInstances() {
        return maximumOwnershipTableInstances;
    }
    public List<TablePlan> tables() { return tables; }
    public List<ChildPlan> children() { return children; }
    public SomaEffectiveMetadata effectiveMetadata() {
        return effectiveMetadata;
    }

    public TablePlan requireTable(String logicalName) {
        String required = CanonicalSupport.required(
                logicalName, "logicalName");
        TablePlan table = tablesByName.get(required);
        if (table == null) {
            throw invalidPlan("tables." + required, "unknown table");
        }
        return table;
    }

    public ChildPlan requireChild(String ownerTable, String childField) {
        String owner = CanonicalSupport.required(ownerTable, "ownerTable");
        String field = CanonicalSupport.required(childField, "childField");
        ChildPlan child = childrenByIdentity.get(
                ChildPlan.identity(owner, field));
        if (child == null) {
            throw invalidPlan(
                    "children." + owner + "." + field,
                    "unknown child field");
        }
        return child;
    }

    String toCanonicalJson() {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"allocationEstimator\":")
                .append(CanonicalSupport.quote(allocationEstimator))
                .append(',');
        if (!children.isEmpty()) {
            json.append("\"children\":[");
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) json.append(',');
                json.append(children.get(i).toCanonicalJson());
            }
            json.append("],");
        }
        json.append("\"defaultMaterializationBudget\":")
                .append(defaultMaterializationBudget.toCanonicalJson())
                .append(',');
        json.append("\"generatedProtocol\":")
                .append(CanonicalSupport.quote(generatedProtocol))
                .append(',');
        json.append("\"maximumAggregateStorageBytes\":")
                .append(maximumAggregateStorageBytes)
                .append(',');
        json.append("\"maximumOwnershipTableInstances\":")
                .append(maximumOwnershipTableInstances)
                .append(',');
        json.append("\"planProtocol\":")
                .append(CanonicalSupport.quote(planProtocol))
                .append(',');
        json.append("\"runtimeCompatibility\":")
                .append(CanonicalSupport.quote(runtimeCompatibility))
                .append(',');
        json.append("\"schemaHash\":")
                .append(CanonicalSupport.quote(schemaHash))
                .append(',');
        json.append("\"statsMode\":")
                .append(CanonicalSupport.quote(
                        statsMode.name().toLowerCase(Locale.ROOT)))
                .append(',');
        json.append("\"tables\":[");
        for (int i = 0; i < tables.size(); i++) {
            if (i > 0) json.append(',');
            json.append(tables.get(i).toCanonicalJson());
        }
        return json.append("]}").toString();
    }

    static SomaRuntimeException invalidPlan(String path, String reason) {
        Map<String, String> context = new TreeMap<String, String>(
                UnicodeCodePointOrder.INSTANCE);
        context.put("reason", reason);
        return SomaRuntimeException.create(
                SomaErrorCategory.INVALID_INPUT,
                "invalid_runtime_plan",
                "table.create",
                path,
                context,
                null);
    }

    public static final class Builder {
        private final String schemaHash;
        private final String runtimeCompatibility;
        private final String generatedProtocol;
        private final String planProtocol;
        private final String allocationEstimator;
        private MaterializationBudget defaultMaterializationBudget =
                MaterializationBudget.defaults();
        private StatsMode statsMode = StatsMode.SUMMARY;
        private long maximumAggregateStorageBytes =
                1024L * 1024L * 1024L;
        private long maximumOwnershipTableInstances = 65536L;
        private final TreeMap<String, TablePlan.Builder> tableDrafts =
                new TreeMap<String, TablePlan.Builder>(
                        UnicodeCodePointOrder.INSTANCE);
        private final TreeMap<String, ChildPlan> children =
                new TreeMap<String, ChildPlan>(
                        UnicodeCodePointOrder.INSTANCE);
        private boolean open = true;

        private Builder(
                String schemaHash,
                String runtimeCompatibility,
                String generatedProtocol,
                String planProtocol,
                String allocationEstimator) {
            this.schemaHash = CanonicalSupport.required(
                    schemaHash, "schemaHash");
            this.runtimeCompatibility = CanonicalSupport.required(
                    runtimeCompatibility, "runtimeCompatibility");
            this.generatedProtocol = CanonicalSupport.required(
                    generatedProtocol, "generatedProtocol");
            this.planProtocol = CanonicalSupport.required(
                    planProtocol, "planProtocol");
            this.allocationEstimator = CanonicalSupport.required(
                    allocationEstimator, "allocationEstimator");
        }

        public Builder defaultMaterializationBudget(
                MaterializationBudget value) {
            requireOpen();
            if (value == null) {
                throw new NullPointerException(
                        "defaultMaterializationBudget");
            }
            defaultMaterializationBudget = value;
            return this;
        }

        public Builder statsMode(StatsMode value) {
            requireOpen();
            if (value == null) throw new NullPointerException("statsMode");
            statsMode = value;
            return this;
        }

        public Builder maximumAggregateStorageBytes(long value) {
            requireOpen();
            if (value <= 0L) {
                throw new IllegalArgumentException(
                        "maximumAggregateStorageBytes must be positive");
            }
            maximumAggregateStorageBytes = value;
            return this;
        }

        public Builder maximumOwnershipTableInstances(long value) {
            requireOpen();
            if (value <= 0L) {
                throw new IllegalArgumentException(
                        "maximumOwnershipTableInstances must be positive");
            }
            maximumOwnershipTableInstances = value;
            return this;
        }

        public TableEditor table(String logicalName) {
            requireOpen();
            String required = CanonicalSupport.required(
                    logicalName, "logicalName");
            TablePlan.Builder draft = tableDrafts.get(required);
            if (draft == null) {
                throw invalidPlan(
                        "tables." + required, "unknown table");
            }
            return new TableEditor(this, draft, required);
        }

        public TableEditor table(SomaTableMetadata tableMetadata) {
            requireOpen();
            if (tableMetadata == null) {
                throw new NullPointerException("tableMetadata");
            }
            return table(tableMetadata.logicalName());
        }

        public ChildEditor child(String ownerTable, String childField) {
            requireOpen();
            String owner = CanonicalSupport.required(
                    ownerTable, "ownerTable");
            String field = CanonicalSupport.required(
                    childField, "childField");
            String identity = ChildPlan.identity(owner, field);
            if (!children.containsKey(identity)) {
                throw invalidPlan(
                        "children." + owner + "." + field,
                        "unknown child field");
            }
            return new ChildEditor(this, identity);
        }

        Builder addTable(TablePlan value) {
            requireOpen();
            TablePlan table = requiredTable(value);
            if (tableDrafts.containsKey(table.tableLogicalName())) {
                throw invalidPlan(
                        "tables." + table.tableLogicalName(),
                        "duplicate table");
            }
            tableDrafts.put(
                    table.tableLogicalName(), table.toBuilder());
            return this;
        }

        public Builder generatedAddTable(
                GeneratedPlanToken token, TablePlan value) {
            GeneratedPlanToken.require(token);
            return addTable(value);
        }

        Builder replaceTable(TablePlan value) {
            requireOpen();
            TablePlan table = requiredTable(value);
            if (!tableDrafts.containsKey(table.tableLogicalName())) {
                throw invalidPlan(
                        "tables." + table.tableLogicalName(),
                        "missing table");
            }
            tableDrafts.put(
                    table.tableLogicalName(), table.toBuilder());
            return this;
        }

        Builder addChild(ChildPlan value) {
            requireOpen();
            ChildPlan child = requiredChild(value);
            if (children.containsKey(child.identity())) {
                throw invalidPlan(
                        "children." + child.ownerTable() + "."
                                + child.childField(),
                        "duplicate child field");
            }
            children.put(child.identity(), child);
            return this;
        }

        public Builder generatedAddChild(
                GeneratedPlanToken token, ChildPlan value) {
            GeneratedPlanToken.require(token);
            return addChild(value);
        }

        Builder replaceChild(ChildPlan value) {
            requireOpen();
            ChildPlan child = requiredChild(value);
            if (!children.containsKey(child.identity())) {
                throw invalidPlan(
                        "children." + child.ownerTable() + "."
                                + child.childField(),
                        "missing child field");
            }
            children.put(child.identity(), child);
            return this;
        }

        public RuntimePlan build() {
            requireOpen();
            open = false;
            if (tableDrafts.isEmpty()) {
                throw invalidPlan(
                        "tables", "at least one table is required");
            }
            TreeMap<String, TablePlan> effectiveTables =
                    new TreeMap<String, TablePlan>(
                            UnicodeCodePointOrder.INSTANCE);
            for (Map.Entry<String, TablePlan.Builder> entry
                    : tableDrafts.entrySet()) {
                effectiveTables.put(entry.getKey(), entry.getValue().build());
            }
            for (ChildPlan child : children.values()) {
                if (!effectiveTables.containsKey(child.ownerTable())) {
                    throw invalidPlan(
                            "children." + child.ownerTable(),
                            "unknown owner table");
                }
                TablePlan childTable =
                        effectiveTables.get(child.childTable());
                if (childTable == null) {
                    throw invalidPlan(
                            "children." + child.ownerTable() + "."
                                    + child.childField(),
                            "unknown child table");
                }
                if (child.initialCapacity() > childTable.maximumRows()) {
                    throw invalidPlan(
                            "children." + child.ownerTable() + "."
                                    + child.childField(),
                            "child initial capacity exceeds table maximumRows");
                }
            }
            return new RuntimePlan(this, effectiveTables);
        }

        private void requireOpen() {
            if (!open) {
                throw new IllegalStateException(
                        "RuntimePlan.Builder is closed");
            }
        }

        private static TablePlan requiredTable(TablePlan value) {
            if (value == null) throw new NullPointerException("table");
            return value;
        }

        private static ChildPlan requiredChild(ChildPlan value) {
            if (value == null) throw new NullPointerException("child");
            return value;
        }
    }

    /** Parent-owned mutable editor；parent build 后自动失效。 */
    public static final class TableEditor {
        private final Builder owner;
        private final TablePlan.Builder draft;
        private final String logicalName;

        private TableEditor(
                Builder owner,
                TablePlan.Builder draft,
                String logicalName) {
            this.owner = owner;
            this.draft = draft;
            this.logicalName = logicalName;
        }

        public String logicalName() {
            owner.requireOpen();
            return logicalName;
        }

        public TableEditor initialCapacity(int value) {
            owner.requireOpen();
            draft.initialCapacity(value);
            return this;
        }

        public TableEditor planningRows(int value) {
            owner.requireOpen();
            draft.planningRows(value);
            return this;
        }

        public TableEditor maximumRows(int value) {
            owner.requireOpen();
            draft.maximumRows(value);
            return this;
        }

        public TableEditor growthRatio(int numerator, int denominator) {
            owner.requireOpen();
            draft.growthRatio(numerator, denominator);
            return this;
        }

        public TableEditor maximumUpdateScratchBytes(long value) {
            owner.requireOpen();
            draft.maximumUpdateScratchBytes(value);
            return this;
        }

        public TableEditor maximumOperationScratchBytes(long value) {
            owner.requireOpen();
            draft.maximumOperationScratchBytes(value);
            return this;
        }

        public TableEditor maximumBulkScratchBytes(long value) {
            owner.requireOpen();
            draft.maximumBulkScratchBytes(value);
            return this;
        }

        public TableEditor maximumTableStorageBytes(long value) {
            owner.requireOpen();
            draft.maximumTableStorageBytes(value);
            return this;
        }

        public TableEditor stringResourceProfile(
                StringResourceProfile value) {
            owner.requireOpen();
            draft.stringResourceProfile(value);
            return this;
        }
    }

    /** Parent-owned child-edge editor；parent build 后自动失效。 */
    public static final class ChildEditor {
        private final Builder owner;
        private final String identity;

        private ChildEditor(Builder owner, String identity) {
            this.owner = owner;
            this.identity = identity;
        }

        public ChildEditor initialCapacity(int value) {
            owner.requireOpen();
            ChildPlan current = owner.children.get(identity);
            owner.children.put(
                    identity,
                    ChildPlan.create(
                            current.ownerTable(),
                            current.childField(),
                            current.childTable(),
                            value));
            return this;
        }
    }
}
