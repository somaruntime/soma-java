package com.hgtech.soma.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Immutable schema-bound effective runtime plan。 */
public final class RuntimePlan {
    private static final String HASH_PREFIX = "soma-java:v1:runtime-plan\n";

    private final String schemaHash;
    private final String runtimeCompatibility;
    private final String generatedProtocol;
    private final String planProtocol;
    private final String allocationEstimator;
    private final MaterializationBudget defaultMaterializationBudget;
    private final StatsMode statsMode;
    private final TreeMap<String, TablePlan> tablesByName;
    private final List<TablePlan> tables;
    private final String runtimePlanHash;

    private RuntimePlan(Builder builder) {
        schemaHash = builder.schemaHash;
        runtimeCompatibility = builder.runtimeCompatibility;
        generatedProtocol = builder.generatedProtocol;
        planProtocol = builder.planProtocol;
        allocationEstimator = builder.allocationEstimator;
        defaultMaterializationBudget = builder.defaultMaterializationBudget;
        statsMode = builder.statsMode;
        tablesByName = new TreeMap<String, TablePlan>(builder.tables);
        tables = Collections.unmodifiableList(
                new ArrayList<TablePlan>(tablesByName.values()));
        runtimePlanHash = CanonicalSupport.sha256(HASH_PREFIX, toCanonicalJson());
    }

    public static Builder builder(
            String schemaHash,
            String runtimeCompatibility,
            String generatedProtocol,
            String planProtocol,
            String allocationEstimator) {
        return new Builder(schemaHash, runtimeCompatibility, generatedProtocol,
                planProtocol, allocationEstimator);
    }

    public Builder toBuilder() {
        Builder builder = new Builder(schemaHash, runtimeCompatibility, generatedProtocol,
                planProtocol, allocationEstimator)
                .defaultMaterializationBudget(defaultMaterializationBudget)
                .statsMode(statsMode);
        for (TablePlan table : tables) {
            builder.addTable(table);
        }
        return builder;
    }

    public String schemaHash() { return schemaHash; }
    public String runtimeCompatibility() { return runtimeCompatibility; }
    public String generatedProtocol() { return generatedProtocol; }
    public String planProtocol() { return planProtocol; }
    public String allocationEstimator() { return allocationEstimator; }
    public String runtimePlanHash() { return runtimePlanHash; }
    public MaterializationBudget defaultMaterializationBudget() { return defaultMaterializationBudget; }
    public StatsMode statsMode() { return statsMode; }
    public List<TablePlan> tables() { return tables; }

    public TablePlan requireTable(String logicalName) {
        String required = CanonicalSupport.required(logicalName, "logicalName");
        TablePlan table = tablesByName.get(required);
        if (table == null) {
            throw invalidPlan("tables." + required, "unknown table");
        }
        return table;
    }

    private String toCanonicalJson() {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"allocationEstimator\":").append(CanonicalSupport.quote(allocationEstimator)).append(',');
        json.append("\"defaultMaterializationBudget\":")
                .append(defaultMaterializationBudget.toCanonicalJson()).append(',');
        json.append("\"generatedProtocol\":").append(CanonicalSupport.quote(generatedProtocol)).append(',');
        json.append("\"planProtocol\":").append(CanonicalSupport.quote(planProtocol)).append(',');
        json.append("\"runtimeCompatibility\":").append(CanonicalSupport.quote(runtimeCompatibility)).append(',');
        json.append("\"schemaHash\":").append(CanonicalSupport.quote(schemaHash)).append(',');
        json.append("\"statsMode\":")
                .append(CanonicalSupport.quote(statsMode.name().toLowerCase(Locale.ROOT))).append(',');
        json.append("\"tables\":[");
        for (int i = 0; i < tables.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(tables.get(i).toCanonicalJson());
        }
        return json.append("]}").toString();
    }

    private static SomaRuntimeException invalidPlan(String path, String reason) {
        Map<String, String> context = new TreeMap<String, String>();
        context.put("reason", reason);
        return SomaRuntimeException.create(SomaErrorCategory.INVALID_INPUT,
                "invalid_runtime_plan", "table.create", path, context, null);
    }

    public static final class Builder {
        private final String schemaHash;
        private final String runtimeCompatibility;
        private final String generatedProtocol;
        private final String planProtocol;
        private final String allocationEstimator;
        private MaterializationBudget defaultMaterializationBudget = MaterializationBudget.defaults();
        private StatsMode statsMode = StatsMode.SUMMARY;
        private final TreeMap<String, TablePlan> tables = new TreeMap<String, TablePlan>();

        private Builder(
                String schemaHash,
                String runtimeCompatibility,
                String generatedProtocol,
                String planProtocol,
                String allocationEstimator) {
            this.schemaHash = CanonicalSupport.required(schemaHash, "schemaHash");
            this.runtimeCompatibility = CanonicalSupport.required(
                    runtimeCompatibility, "runtimeCompatibility");
            this.generatedProtocol = CanonicalSupport.required(generatedProtocol, "generatedProtocol");
            this.planProtocol = CanonicalSupport.required(planProtocol, "planProtocol");
            this.allocationEstimator = CanonicalSupport.required(allocationEstimator, "allocationEstimator");
        }

        public Builder defaultMaterializationBudget(MaterializationBudget value) {
            if (value == null) {
                throw new NullPointerException("defaultMaterializationBudget");
            }
            defaultMaterializationBudget = value;
            return this;
        }

        public Builder statsMode(StatsMode value) {
            if (value == null) {
                throw new NullPointerException("statsMode");
            }
            statsMode = value;
            return this;
        }

        public Builder addTable(TablePlan value) {
            TablePlan table = requiredTable(value);
            if (tables.containsKey(table.tableLogicalName())) {
                throw invalidPlan("tables." + table.tableLogicalName(), "duplicate table");
            }
            tables.put(table.tableLogicalName(), table);
            return this;
        }

        public Builder replaceTable(TablePlan value) {
            TablePlan table = requiredTable(value);
            if (!tables.containsKey(table.tableLogicalName())) {
                throw invalidPlan("tables." + table.tableLogicalName(), "missing table");
            }
            tables.put(table.tableLogicalName(), table);
            return this;
        }

        public RuntimePlan build() {
            if (tables.isEmpty()) {
                throw invalidPlan("tables", "at least one table is required");
            }
            return new RuntimePlan(this);
        }

        private TablePlan requiredTable(TablePlan value) {
            if (value == null) {
                throw new NullPointerException("table");
            }
            return value;
        }
    }
}
