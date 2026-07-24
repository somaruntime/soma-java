package com.hgtech.soma.benchmarks;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Benchmark runner内部的exact JSONL model与无第三方JSON codec。 */
final class BenchmarkModel {
    static final String SCHEMA_VERSION = "soma-benchmark-smoke-v4";
    static final String ARTIFACT_VERSION = "soma-java-benchmark-runner-v4";

    static final List<String> FIELDS = Collections.unmodifiableList(Arrays.asList(
            "schemaVersion", "scenario", "lane", "workloadId", "workloadEvidence",
            "level", "status", "commit",
            "artifactVersion", "javaVersion", "javaVendor", "jvmArgs", "os",
            "architecture", "cpu", "memory", "scale", "seed", "warmupIterations",
            "forks", "measurementIterations", "baselineId", "phaseTimings",
            "throughput", "latency", "rowCounts", "candidateCounts",
            "operationCounts", "accessPatternCard", "observationKinds",
            "touchedBytesEstimate",
            "workingSetEstimate", "allocationPerOperation", "allocatedBytes",
            "gcStats", "exactIndexStats", "keySpaceStats", "selectorStats",
            "optionalDensity", "mutationReadRatio", "statsMode",
            "materializationStats", "effectiveMaterializationBudget",
            "materializationBudgetDimension", "materializationPath",
            "allocationEstimatorVersion", "externalDtoStats", "columnViewStats",
            "allocationEstimate", "hardwareCounterStats", "knownLimitations",
            "claimAllowed", "failureReason"));

    private BenchmarkModel() {
    }

    static LinkedHashMap<String, Object> object(Object... values) {
        if ((values.length & 1) != 0) {
            throw new IllegalArgumentException("key/value pairs required");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    static List<String> limitations(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    static BenchmarkRecord record(
            BenchmarkEnvironment environment,
            BenchmarkConfig config,
            LaneObservation observation) {
        long operations = Math.max(1L, observation.operations);
        long rows = Math.max(1L, observation.rows);
        double seconds = Math.max(1L, observation.measurementNanos) / 1_000_000_000.0d;
        LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("schemaVersion", SCHEMA_VERSION);
        values.put("scenario", observation.scenario);
        values.put("lane", observation.lane);
        values.put("workloadId", observation.workloadId);
        values.put("workloadEvidence", observation.workloadEvidence);
        values.put("level", "smoke");
        values.put("status", "passed");
        values.put("commit", config.commit);
        values.put("artifactVersion", ARTIFACT_VERSION);
        values.put("javaVersion", environment.javaVersion);
        values.put("javaVendor", environment.javaVendor);
        values.put("jvmArgs", environment.jvmArgs);
        values.put("os", environment.os);
        values.put("architecture", environment.architecture);
        values.put("cpu", environment.cpu);
        values.put("memory", Long.valueOf(environment.memory));
        values.put("scale", object("preset", config.scalePreset,
                "rows", Integer.valueOf(config.rows), "operations", Long.valueOf(operations)));
        values.put("seed", Long.valueOf(config.seed));
        values.put("warmupIterations", Integer.valueOf(config.warmupIterations));
        values.put("forks", Integer.valueOf(config.forks));
        values.put("measurementIterations", Integer.valueOf(config.measurementIterations));
        values.put("baselineId", observation.baselineId);
        values.put("phaseTimings", object("setupNanos", Long.valueOf(observation.setupNanos),
                "measurementNanos", Long.valueOf(observation.measurementNanos),
                "exportNanos", Long.valueOf(observation.exportNanos)));
        values.put("throughput", object("operationsPerSecond",
                Double.valueOf(operations / seconds), "rowsPerSecond",
                Double.valueOf(rows / seconds)));
        values.put("latency", object("nanosPerOperation",
                Double.valueOf((double) observation.measurementNanos / operations),
                "nanosPerRow", Double.valueOf((double) observation.measurementNanos / rows)));
        values.put("rowCounts", object("source", Long.valueOf(observation.rows),
                "scanned", Long.valueOf(observation.scanned),
                "matched", Long.valueOf(observation.matched),
                "changed", Long.valueOf(observation.changed),
                "removed", Long.valueOf(observation.removed),
                "materialized", Long.valueOf(observation.materialized)));
        values.put("candidateCounts", object("source", Long.valueOf(observation.candidates),
                "selected", Long.valueOf(observation.selected)));
        values.put("operationCounts", object("operations", Long.valueOf(observation.operations),
                "lookups", Long.valueOf(observation.lookups),
                "missing", Long.valueOf(observation.missing),
                "duplicates", Long.valueOf(observation.duplicates),
                "materializations", Long.valueOf(observation.materializationInvocations)));
        values.put("accessPatternCard", observation.accessPatternCard);
        values.put("observationKinds", object(
                "allocation", observationKind("not-observed",
                        "no-jvm-allocation-profiler", "actual JVM heap allocation"),
                "materialization", observationKind("measured",
                        "soma-workload-counter-v1", "published schema objects/collections"),
                "reads", observationKind("measured",
                        "soma-workload-counter-v1", "executed scan/lookup facts"),
                "mutations", observationKind("measured",
                        "soma-workload-counter-v1", "executed changed/removed facts"),
                "touchedBytes", observationKind("deterministic-estimate",
                        "soma-touched-bytes-estimator-v1", observation.touchedBytesScope),
                "workingSet", observationKind("deterministic-estimate",
                        "soma-working-set-estimator-v1", observation.workingSetScope)));
        values.put("touchedBytesEstimate", Long.valueOf(observation.touchedBytes));
        values.put("workingSetEstimate", Long.valueOf(observation.workingSetBytes));
        values.put("allocationPerOperation", object("method", "not-observed",
                "estimatedBytes", null));
        values.put("allocatedBytes", null);
        values.put("gcStats", object("method", "not-observed-in-smoke",
                "count", null, "timeMillis", null));
        values.put("exactIndexStats", observation.exactIndexStats);
        values.put("keySpaceStats", observation.keySpaceStats);
        values.put("selectorStats", observation.selectorStats);
        values.put("optionalDensity", observation.optionalDensity);
        values.put("mutationReadRatio", observation.mutationReadRatio);
        values.put("statsMode", observation.statsMode);
        values.put("materializationStats", observation.materializationStats);
        values.put("effectiveMaterializationBudget", observation.effectiveMaterializationBudget);
        values.put("materializationBudgetDimension", observation.materializationBudgetDimension);
        values.put("materializationPath", observation.materializationPath);
        values.put("allocationEstimatorVersion", observation.allocationEstimatorVersion);
        values.put("externalDtoStats", observation.externalDtoStats);
        values.put("columnViewStats", observation.columnViewStats);
        values.put("allocationEstimate", object("method", "soma-smoke-measurement-allocation-v2",
                "bytes", Long.valueOf(observation.estimatedAllocationBytes),
                "exactJvmHeap", Boolean.FALSE));
        values.put("hardwareCounterStats", object("method", "unavailable",
                "cacheMisses", null, "branchMisses", null));
        values.put("knownLimitations", observation.limitations);
        values.put("claimAllowed", Boolean.FALSE);
        values.put("failureReason", "");
        BenchmarkRecord record = new BenchmarkRecord(values);
        validateRecord(values);
        return record;
    }

    private static Map<String, Object> observationKind(
            String kind, String method, String scope) {
        return object("kind", kind, "method", method, "scope", scope);
    }

    static void write(File target, List<BenchmarkRecord> records) throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create output directory: " + parent);
        }
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8));
        try {
            for (BenchmarkRecord record : records) {
                writer.write(Json.write(record.values));
                writer.newLine();
            }
        } finally {
            writer.close();
        }
    }

    static List<Map<String, Object>> read(File source) throws IOException {
        ArrayList<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(source), StandardCharsets.UTF_8));
        try {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.trim().isEmpty()) {
                    throw new IllegalArgumentException("blank JSONL line " + number);
                }
                Object parsed = Json.parse(line);
                if (!(parsed instanceof Map)) {
                    throw new IllegalArgumentException("JSONL line is not object: " + number);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> record = (Map<String, Object>) parsed;
                validateRecord(record);
                records.add(record);
            }
        } finally {
            reader.close();
        }
        if (records.isEmpty()) {
            throw new IllegalArgumentException("benchmark JSONL must not be empty");
        }
        return records;
    }

    static void validateArtifact(List<Map<String, Object>> records) {
        LinkedHashSet<String> lanes = new LinkedHashSet<String>();
        for (Map<String, Object> record : records) {
            String lane = string(record, "lane");
            if (!lanes.add(lane)) {
                throw new IllegalArgumentException("duplicate benchmark lane: " + lane);
            }
            if (!Boolean.FALSE.equals(record.get("claimAllowed"))) {
                throw new IllegalArgumentException("smoke claimAllowed must be false: " + lane);
            }
            if (!"smoke".equals(record.get("level")) || !"passed".equals(record.get("status"))) {
                throw new IllegalArgumentException("invalid smoke status: " + lane);
            }
            if (((List<?>) record.get("knownLimitations")).isEmpty()) {
                throw new IllegalArgumentException("knownLimitations required: " + lane);
            }
            String expectedWorkload = SmokeLaneContract.workloadId(lane);
            if (!expectedWorkload.equals(record.get("workloadId"))) {
                throw new IllegalArgumentException("wrong workloadId for " + lane);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> evidence = (Map<String, Object>) record.get("workloadEvidence");
            SmokeLaneContract.validateWorkloadEvidence(lane, evidence);
            SmokeLaneContract.validateLaneRecord(lane, record);
        }
        Set<String> missing = new LinkedHashSet<String>(SmokeLaneContract.REQUIRED_LANES);
        missing.removeAll(lanes);
        Set<String> unexpected = new LinkedHashSet<String>(lanes);
        unexpected.removeAll(SmokeLaneContract.REQUIRED_LANES);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new IllegalArgumentException("lane manifest mismatch missing=" + missing
                    + " unexpected=" + unexpected);
        }
    }

    static void validateRecord(Map<String, Object> record) {
        if (!new ArrayList<String>(record.keySet()).equals(FIELDS)) {
            throw new IllegalArgumentException("exact root field order/schema mismatch: "
                    + record.keySet());
        }
        if (!SCHEMA_VERSION.equals(record.get("schemaVersion"))) {
            throw new IllegalArgumentException("unknown schemaVersion");
        }
        requireNonEmpty(record, "scenario");
        requireNonEmpty(record, "lane");
        requireNonEmpty(record, "workloadId");
        requireNonEmpty(record, "commit");
        requireNonEmpty(record, "artifactVersion");
        requireNonEmpty(record, "javaVersion");
        requireNonEmpty(record, "javaVendor");
        requireNonEmpty(record, "os");
        requireNonEmpty(record, "architecture");
        requireNonEmpty(record, "cpu");
        requireNonEmpty(record, "baselineId");
        requireNonEmpty(record, "statsMode");
        requireString(record, "optionalDensity");
        requireString(record, "materializationBudgetDimension");
        requireString(record, "materializationPath");
        requireNonEmpty(record, "allocationEstimatorVersion");
        requireString(record, "failureReason");
        if (!(record.get("jvmArgs") instanceof List)) {
            throw new IllegalArgumentException("jvmArgs must be array");
        }
        requireMap(record, "scale");
        requireNonEmptyMap(record, "workloadEvidence");
        requireMap(record, "phaseTimings");
        requireMap(record, "throughput");
        requireMap(record, "latency");
        requireMap(record, "rowCounts");
        requireMap(record, "candidateCounts");
        requireMap(record, "operationCounts");
        requireNonEmptyMap(record, "accessPatternCard");
        requireNonEmptyMap(record, "observationKinds");
        requireMap(record, "allocationPerOperation");
        requireNonEmptyMap(record, "gcStats");
        requireNonEmptyMap(record, "exactIndexStats");
        requireNonEmptyMap(record, "keySpaceStats");
        requireNonEmptyMap(record, "selectorStats");
        requireNonEmptyMap(record, "materializationStats");
        requireNonEmptyMap(record, "effectiveMaterializationBudget");
        requireNonEmptyMap(record, "externalDtoStats");
        requireNonEmptyMap(record, "columnViewStats");
        requireMap(record, "allocationEstimate");
        requireNonEmptyMap(record, "hardwareCounterStats");
        if (!(record.get("knownLimitations") instanceof List)) {
            throw new IllegalArgumentException("knownLimitations must be array");
        }
        if (!(record.get("claimAllowed") instanceof Boolean)) {
            throw new IllegalArgumentException("claimAllowed must be boolean");
        }
        requireNonNegativeNumber(record, "memory");
        requireNonNegativeNumber(record, "seed");
        requireNonNegativeNumber(record, "warmupIterations");
        requireNonNegativeNumber(record, "forks");
        requireNonNegativeNumber(record, "measurementIterations");
        requireNonNegativeNumber(record, "touchedBytesEstimate");
        requireNonNegativeNumber(record, "workingSetEstimate");
        if (record.get("allocatedBytes") != null) {
            throw new IllegalArgumentException("allocatedBytes must be null when allocation is not observed");
        }
        requireExactMap(record, "scale", new String[] {"preset", "rows", "operations"});
        requireExactMap(record, "phaseTimings",
                new String[] {"setupNanos", "measurementNanos", "exportNanos"});
        requireExactMap(record, "throughput",
                new String[] {"operationsPerSecond", "rowsPerSecond"});
        requireExactMap(record, "latency", new String[] {"nanosPerOperation", "nanosPerRow"});
        requireExactMap(record, "rowCounts",
                new String[] {"source", "scanned", "matched", "changed", "removed", "materialized"});
        requireExactMap(record, "candidateCounts", new String[] {"source", "selected"});
        requireExactMap(record, "operationCounts",
                new String[] {"operations", "lookups", "missing", "duplicates",
                        "materializations"});
        requireExactMap(record, "observationKinds",
                new String[] {"allocation", "materialization", "reads", "mutations",
                        "touchedBytes", "workingSet"});
        requireExactMap(record, "mutationReadRatio", new String[] {"mutations", "reads"});
        requireExactMap(record, "allocationPerOperation", new String[] {"method", "estimatedBytes"});
        requireExactMap(record, "allocationEstimate", new String[] {"method", "bytes", "exactJvmHeap"});
        requireExactMap(record, "gcStats", new String[] {"method", "count", "timeMillis"});
        requireExactMap(record, "hardwareCounterStats",
                new String[] {"method", "cacheMisses", "branchMisses"});
        requirePositiveNestedNumber(record, "scale", "rows");
        requirePositiveNestedNumber(record, "scale", "operations");
        requireNonNegativeNestedNumbers(record, "phaseTimings");
        requirePositiveNestedNumber(record, "phaseTimings", "measurementNanos");
        requirePositiveNestedNumber(record, "throughput", "operationsPerSecond");
        requirePositiveNestedNumber(record, "throughput", "rowsPerSecond");
        requirePositiveNestedNumber(record, "latency", "nanosPerOperation");
        requirePositiveNestedNumber(record, "latency", "nanosPerRow");
        requirePositiveNestedNumber(record, "rowCounts", "source");
        requireNonNegativeNestedNumbers(record, "rowCounts");
        requireNonNegativeNestedNumbers(record, "candidateCounts");
        requirePositiveNestedNumber(record, "operationCounts", "operations");
        requireNonNegativeNestedNumbers(record, "operationCounts");
        requireNonNegativeNestedNumbers(record, "mutationReadRatio");
        requireNestedIntegers(record, "scale", new String[] {"rows", "operations"});
        requireNestedIntegers(record, "phaseTimings",
                new String[] {"setupNanos", "measurementNanos", "exportNanos"});
        requireNestedIntegers(record, "rowCounts",
                new String[] {"source", "scanned", "matched", "changed", "removed", "materialized"});
        requireNestedIntegers(record, "candidateCounts", new String[] {"source", "selected"});
        requireNestedIntegers(record, "operationCounts",
                new String[] {"operations", "lookups", "missing", "duplicates",
                        "materializations"});
        requireNestedIntegers(record, "mutationReadRatio", new String[] {"mutations", "reads"});
        requirePositiveInteger(record, "forks");
        requirePositiveInteger(record, "measurementIterations");
        requireInteger(record, "memory", true);
        requireInteger(record, "seed", true);
        requireInteger(record, "warmupIterations", true);
        requireInteger(record, "forks", false);
        requireInteger(record, "measurementIterations", false);
        if (!ARTIFACT_VERSION.equals(record.get("artifactVersion"))) {
            throw new IllegalArgumentException("unknown artifactVersion");
        }
        requireStringItems(record, "jvmArgs", false);
        requireStringItems(record, "knownLimitations", true);
        requireNestedConst(record, "allocationPerOperation", "method", "not-observed");
        requireNestedConst(record, "allocationPerOperation", "estimatedBytes", null);
        requireNestedConst(record, "allocationEstimate", "method",
                "soma-smoke-measurement-allocation-v2");
        requireNestedConst(record, "allocationEstimate", "exactJvmHeap", Boolean.FALSE);
        requireNestedConst(record, "gcStats", "method", "not-observed-in-smoke");
        requireNestedConst(record, "gcStats", "count", null);
        requireNestedConst(record, "gcStats", "timeMillis", null);
        requireNestedConst(record, "hardwareCounterStats", "method", "unavailable");
        requireNestedConst(record, "hardwareCounterStats", "cacheMisses", null);
        requireNestedConst(record, "hardwareCounterStats", "branchMisses", null);
        validateObservationKinds(record);
        validateObservationSemantics(record);
    }

    @SuppressWarnings("unchecked")
    private static void validateObservationKinds(Map<String, Object> record) {
        Map<String, Object> kinds = (Map<String, Object>) record.get("observationKinds");
        requireObservation(kinds, "allocation", "not-observed", "no-jvm-allocation-profiler");
        requireObservation(kinds, "materialization", "measured", "soma-workload-counter-v1");
        requireObservation(kinds, "reads", "measured", "soma-workload-counter-v1");
        requireObservation(kinds, "mutations", "measured", "soma-workload-counter-v1");
        requireObservation(kinds, "touchedBytes", "deterministic-estimate",
                "soma-touched-bytes-estimator-v1");
        requireObservation(kinds, "workingSet", "deterministic-estimate",
                "soma-working-set-estimator-v1");
    }

    @SuppressWarnings("unchecked")
    private static void requireObservation(Map<String, Object> kinds, String field,
                                           String kind, String method) {
        Object value = kinds.get(field);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("observationKinds." + field + " must be object");
        }
        Map<String, Object> observation = (Map<String, Object>) value;
        if (!new ArrayList<String>(observation.keySet()).equals(
                Arrays.asList("kind", "method", "scope"))) {
            throw new IllegalArgumentException("observationKinds." + field
                    + " exact fields mismatch");
        }
        if (!kind.equals(observation.get("kind")) || !method.equals(observation.get("method"))) {
            throw new IllegalArgumentException("observationKinds." + field + " identity mismatch");
        }
        Object scope = observation.get("scope");
        if (!(scope instanceof String) || ((String) scope).isEmpty()) {
            throw new IllegalArgumentException("observationKinds." + field + ".scope required");
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateObservationSemantics(Map<String, Object> record) {
        Map<String, Object> rows = (Map<String, Object>) record.get("rowCounts");
        Map<String, Object> operations = (Map<String, Object>) record.get("operationCounts");
        Map<String, Object> ratio = (Map<String, Object>) record.get("mutationReadRatio");
        long scanned = ((Number) rows.get("scanned")).longValue();
        long changed = ((Number) rows.get("changed")).longValue();
        long removed = ((Number) rows.get("removed")).longValue();
        long materialized = ((Number) rows.get("materialized")).longValue();
        long lookups = ((Number) operations.get("lookups")).longValue();
        long operationCount = ((Number) operations.get("operations")).longValue();
        long materializations = ((Number) operations.get("materializations")).longValue();
        long reads = ((Number) ratio.get("reads")).longValue();
        long mutations = ((Number) ratio.get("mutations")).longValue();
        if ((scanned > 0L || lookups > 0L) && reads == 0L) {
            throw new IllegalArgumentException("read workload cannot report zero reads");
        }
        if ((changed > 0L || removed > 0L) && mutations == 0L) {
            throw new IllegalArgumentException("mutation workload cannot report zero mutations");
        }
        if ((materializations == 0L) != (materialized == 0L)) {
            throw new IllegalArgumentException(
                    "materialization invocations and published rows contradict");
        }
        Map<String, Object> materializationStats =
                (Map<String, Object>) record.get("materializationStats");
        if (materializations > 0L && Boolean.FALSE.equals(materializationStats.get("applicable"))) {
            throw new IllegalArgumentException("executed materialization cannot be not-applicable");
        }
        long touchedBytes = ((Number) record.get("touchedBytesEstimate")).longValue();
        long workingSetBytes = ((Number) record.get("workingSetEstimate")).longValue();
        if ((scanned > 0L || lookups > 0L || materializations > 0L)
                && touchedBytes <= 0L) {
            throw new IllegalArgumentException(
                    "read/materialization workload cannot report zero touched bytes: "
                            + record.get("lane"));
        }
        if (((Number) rows.get("source")).longValue() > 0L && workingSetBytes <= 0L) {
            throw new IllegalArgumentException("executed workload cannot report zero working set: "
                    + record.get("lane"));
        }
        Map<String, Object> allocationEstimate =
                (Map<String, Object>) record.get("allocationEstimate");
        if (materializations > 0L
                && ((Number) allocationEstimate.get("bytes")).longValue() <= 0L) {
            throw new IllegalArgumentException(
                    "materialization workload cannot report false-zero allocation estimate: "
                            + record.get("lane"));
        }
        Map<String, Object> card = (Map<String, Object>) record.get("accessPatternCard");
        String expectedExport = materializations > 0L
                ? "explicit-materialization-boundary" : "no-materialization-in-workload";
        if (!expectedExport.equals(card.get("allocationExport"))) {
            throw new IllegalArgumentException("accessPatternCard allocationExport contradiction");
        }
        Map<String, Object> scale = (Map<String, Object>) record.get("scale");
        Map<String, Object> workload = (Map<String, Object>) record.get("workloadEvidence");
        Map<String, Object> cardRatio = (Map<String, Object>) card.get("readMutationMix");
        if (((Number) scale.get("operations")).longValue() != operationCount
                || ((Number) workload.get("positiveCount")).longValue() != operationCount) {
            throw new IllegalArgumentException("operation counters contradict scale/workload evidence");
        }
        if (((Number) card.get("rowsCardinality")).longValue()
                        != ((Number) rows.get("source")).longValue()
                || ((Number) card.get("workingSetBytes")).longValue() != workingSetBytes
                || ((Number) cardRatio.get("reads")).longValue() != reads
                || ((Number) cardRatio.get("mutations")).longValue() != mutations) {
            throw new IllegalArgumentException("Access Pattern Card contradicts root facts");
        }
    }

    private static String string(Map<String, Object> record, String field) {
        return (String) record.get(field);
    }

    private static void requireNonEmpty(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException(field + " must be non-empty string");
        }
    }

    private static void requireString(Map<String, Object> values, String field) {
        if (!(values.get(field) instanceof String)) {
            throw new IllegalArgumentException(field + " must be string");
        }
    }

    private static void requireMap(Map<String, Object> values, String field) {
        if (!(values.get(field) instanceof Map)) {
            throw new IllegalArgumentException(field + " must be object");
        }
    }

    @SuppressWarnings("unchecked")
    private static void requireNonEmptyMap(Map<String, Object> values, String field) {
        requireMap(values, field);
        if (((Map<String, Object>) values.get(field)).isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
    }

    @SuppressWarnings("unchecked")
    private static void requireExactMap(Map<String, Object> values, String field, String[] keys) {
        Map<String, Object> nested = (Map<String, Object>) values.get(field);
        if (!new ArrayList<String>(nested.keySet()).equals(Arrays.asList(keys))) {
            throw new IllegalArgumentException(field + " exact fields mismatch: " + nested.keySet());
        }
    }

    @SuppressWarnings("unchecked")
    private static void requirePositiveNestedNumber(Map<String, Object> values,
                                                     String field, String nestedField) {
        Object value = ((Map<String, Object>) values.get(field)).get(nestedField);
        if (!(value instanceof Number) || !finite((Number) value)
                || ((Number) value).doubleValue() <= 0.0d) {
            throw new IllegalArgumentException(field + "." + nestedField + " must be positive");
        }
    }

    @SuppressWarnings("unchecked")
    private static void requireNonNegativeNestedNumbers(Map<String, Object> values, String field) {
        for (Map.Entry<String, Object> entry
                : ((Map<String, Object>) values.get(field)).entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof Number) || !finite((Number) value)
                    || ((Number) value).doubleValue() < 0.0d) {
                throw new IllegalArgumentException(field + "." + entry.getKey()
                        + " must be non-negative number");
            }
        }
    }

    private static void requirePositiveInteger(Map<String, Object> values, String field) {
        requireInteger(values, field, false);
        if (((Number) values.get(field)).longValue() <= 0L) {
            throw new IllegalArgumentException(field + " must be positive integer");
        }
    }

    @SuppressWarnings("unchecked")
    private static void requireNestedIntegers(Map<String, Object> values, String field,
                                              String[] nestedFields) {
        Map<String, Object> nested = (Map<String, Object>) values.get(field);
        for (String nestedField : nestedFields) {
            Object value = nested.get(nestedField);
            if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
                    || value instanceof Long)) {
                throw new IllegalArgumentException(field + "." + nestedField + " must be integer");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void requireNestedConst(Map<String, Object> values, String field,
                                           String nestedField, Object expected) {
        Object actual = ((Map<String, Object>) values.get(field)).get(nestedField);
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IllegalArgumentException(field + "." + nestedField + " const mismatch");
        }
    }

    @SuppressWarnings("unchecked")
    private static void requireNestedNonNegativeNumber(Map<String, Object> values,
                                                       String field, String nestedField) {
        Object value = ((Map<String, Object>) values.get(field)).get(nestedField);
        if (!(value instanceof Number) || !finite((Number) value)
                || ((Number) value).doubleValue() < 0.0d) {
            throw new IllegalArgumentException(field + "." + nestedField
                    + " must be non-negative number");
        }
    }

    private static void requireInteger(Map<String, Object> values, String field,
                                       boolean allowZero) {
        Object value = values.get(field);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long)) {
            throw new IllegalArgumentException(field + " must be integer");
        }
        if ((!allowZero && ((Number) value).longValue() <= 0L)
                || (allowZero && ((Number) value).longValue() < 0L)) {
            throw new IllegalArgumentException(field + " integer range");
        }
    }

    private static boolean finite(Number value) {
        double number = value.doubleValue();
        return !Double.isNaN(number) && !Double.isInfinite(number);
    }

    @SuppressWarnings("unchecked")
    private static void requireStringItems(Map<String, Object> values, String field,
                                           boolean nonEmpty) {
        List<Object> list = (List<Object>) values.get(field);
        if (nonEmpty && list.isEmpty()) throw new IllegalArgumentException(field + " must not be empty");
        for (Object item : list) {
            if (!(item instanceof String) || (nonEmpty && ((String) item).isEmpty())) {
                throw new IllegalArgumentException(field + " must contain strings");
            }
        }
    }

    private static void requireNonNegativeNumber(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof Number) || !finite((Number) value)
                || ((Number) value).doubleValue() < 0.0d) {
            throw new IllegalArgumentException(field + " must be non-negative number");
        }
    }

    static final class Json {
        private Json() {
        }

        static String write(Object value) {
            StringBuilder out = new StringBuilder();
            append(out, value);
            return out.toString();
        }

        private static void append(StringBuilder out, Object value) {
            if (value == null) {
                out.append("null");
            } else if (value instanceof String) {
                quote(out, (String) value);
            } else if (value instanceof Boolean || value instanceof Integer
                    || value instanceof Long || value instanceof Short || value instanceof Byte) {
                out.append(value);
            } else if (value instanceof Number) {
                double number = ((Number) value).doubleValue();
                if (Double.isNaN(number) || Double.isInfinite(number)) {
                    throw new IllegalArgumentException("non-finite JSON number");
                }
                out.append(value);
            } else if (value instanceof Map) {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!first) out.append(',');
                    quote(out, (String) entry.getKey());
                    out.append(':');
                    append(out, entry.getValue());
                    first = false;
                }
                out.append('}');
            } else if (value instanceof Iterable) {
                out.append('[');
                boolean first = true;
                for (Object element : (Iterable<?>) value) {
                    if (!first) out.append(',');
                    append(out, element);
                    first = false;
                }
                out.append(']');
            } else {
                throw new IllegalArgumentException("unsupported JSON value " + value.getClass());
            }
        }

        private static void quote(StringBuilder out, String value) {
            out.append('"');
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                switch (current) {
                    case '"': out.append("\\\""); break;
                    case '\\': out.append("\\\\"); break;
                    case '\b': out.append("\\b"); break;
                    case '\f': out.append("\\f"); break;
                    case '\n': out.append("\\n"); break;
                    case '\r': out.append("\\r"); break;
                    case '\t': out.append("\\t"); break;
                    default:
                        if (current < 0x20) {
                            out.append("\\u");
                            String hex = Integer.toHexString(current);
                            for (int pad = hex.length(); pad < 4; pad++) out.append('0');
                            out.append(hex);
                        } else {
                            out.append(current);
                        }
                }
            }
            out.append('"');
        }

        static Object parse(String source) {
            Parser parser = new Parser(source);
            Object value = parser.value();
            parser.space();
            if (!parser.end()) throw parser.error("trailing JSON content");
            return value;
        }

        private static final class Parser {
            private final String source;
            private int index;

            Parser(String source) { this.source = source; }
            boolean end() { return index == source.length(); }
            void space() { while (!end() && Character.isWhitespace(source.charAt(index))) index++; }
            IllegalArgumentException error(String message) {
                return new IllegalArgumentException(message + " at JSON offset " + index);
            }

            Object value() {
                space();
                if (end()) throw error("unexpected end");
                char current = source.charAt(index);
                if (current == '{') return object();
                if (current == '[') return array();
                if (current == '"') return string();
                if (current == 't') { literal("true"); return Boolean.TRUE; }
                if (current == 'f') { literal("false"); return Boolean.FALSE; }
                if (current == 'n') { literal("null"); return null; }
                return number();
            }

            private Map<String, Object> object() {
                LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
                index++;
                space();
                if (take('}')) return result;
                while (true) {
                    space();
                    if (end() || source.charAt(index) != '"') throw error("object key required");
                    String key = string();
                    if (result.containsKey(key)) throw error("duplicate object key " + key);
                    space();
                    require(':');
                    result.put(key, value());
                    space();
                    if (take('}')) return result;
                    require(',');
                }
            }

            private List<Object> array() {
                ArrayList<Object> result = new ArrayList<Object>();
                index++;
                space();
                if (take(']')) return result;
                while (true) {
                    result.add(value());
                    space();
                    if (take(']')) return result;
                    require(',');
                }
            }

            private String string() {
                require('"');
                StringBuilder result = new StringBuilder();
                while (!end()) {
                    char current = source.charAt(index++);
                    if (current == '"') return result.toString();
                    if (current == '\\') {
                        if (end()) throw error("incomplete escape");
                        char escaped = source.charAt(index++);
                        switch (escaped) {
                            case '"': result.append('"'); break;
                            case '\\': result.append('\\'); break;
                            case '/': result.append('/'); break;
                            case 'b': result.append('\b'); break;
                            case 'f': result.append('\f'); break;
                            case 'n': result.append('\n'); break;
                            case 'r': result.append('\r'); break;
                            case 't': result.append('\t'); break;
                            case 'u':
                                if (index + 4 > source.length()) throw error("short unicode escape");
                                result.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                                index += 4;
                                break;
                            default: throw error("invalid escape");
                        }
                    } else {
                        if (current < 0x20) throw error("control character in string");
                        result.append(current);
                    }
                }
                throw error("unterminated string");
            }

            private Number number() {
                int start = index;
                take('-');
                if (end()) throw error("digit required");
                if (source.charAt(index) == '0') {
                    index++;
                    if (!end() && asciiDigit(source.charAt(index))) {
                        throw error("leading zero in number");
                    }
                } else {
                    if (source.charAt(index) < '1' || source.charAt(index) > '9') {
                        throw error("digit required");
                    }
                    digits();
                }
                boolean decimal = false;
                if (take('.')) { decimal = true; digits(); }
                if (!end() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                    decimal = true; index++; if (!end() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++; digits();
                }
                String value = source.substring(start, index);
                try {
                    if (decimal) {
                        Double parsed = Double.valueOf(value);
                        if (!finite(parsed)) throw error("non-finite number");
                        return parsed;
                    }
                    return Long.valueOf(value);
                }
                catch (NumberFormatException failure) { throw error("invalid number"); }
            }

            private void digits() {
                int start = index;
                while (!end() && asciiDigit(source.charAt(index))) index++;
                if (start == index) throw error("digit required");
            }

            private boolean asciiDigit(char value) {
                return value >= '0' && value <= '9';
            }

            private void literal(String value) {
                if (!source.regionMatches(index, value, 0, value.length())) throw error("invalid literal");
                index += value.length();
            }

            private boolean take(char value) {
                if (!end() && source.charAt(index) == value) { index++; return true; }
                return false;
            }

            private void require(char value) {
                if (!take(value)) throw error("expected " + value);
            }
        }
    }
}

final class BenchmarkRecord {
    final LinkedHashMap<String, Object> values;
    BenchmarkRecord(LinkedHashMap<String, Object> values) { this.values = values; }
}

final class BenchmarkConfig {
    final String commit;
    final String scalePreset;
    final int rows;
    final long seed;
    final int warmupIterations;
    final int forks;
    final int measurementIterations;

    BenchmarkConfig(String commit, String scalePreset, int rows, long seed,
                    int warmupIterations, int forks, int measurementIterations) {
        if (commit == null || commit.isEmpty()) throw new IllegalArgumentException("commit required");
        if (scalePreset == null || scalePreset.isEmpty()) throw new IllegalArgumentException("scale required");
        if (rows < 65 || warmupIterations < 0 || forks != 1 || measurementIterations <= 0) {
            throw new IllegalArgumentException("invalid smoke configuration");
        }
        this.commit = commit;
        this.scalePreset = scalePreset;
        this.rows = rows;
        this.seed = seed;
        this.warmupIterations = warmupIterations;
        this.forks = forks;
        this.measurementIterations = measurementIterations;
    }
}

final class BenchmarkEnvironment {
    final String javaVersion = System.getProperty("java.runtime.version", System.getProperty("java.version"));
    final String javaVendor = System.getProperty("java.vendor", "unknown");
    final String javaVmName = System.getProperty("java.vm.name", "unknown");
    final String javaVmVersion = System.getProperty("java.vm.version", "unknown");
    final List<String> jvmArgs = Collections.unmodifiableList(
            new ArrayList<String>(ManagementFactory.getRuntimeMXBean().getInputArguments()));
    final String osName = System.getProperty("os.name", "unknown");
    final String osVersion = System.getProperty("os.version", "unknown");
    final String os = osName + " " + osVersion;
    final String architecture = System.getProperty("os.arch", "unknown");
    final String cpu = System.getenv("SOMA_BENCHMARK_CPU") == null
            ? "availableProcessors=" + Runtime.getRuntime().availableProcessors()
            : System.getenv("SOMA_BENCHMARK_CPU");
    final long memory = Runtime.getRuntime().maxMemory();
}

final class LaneObservation {
    String scenario;
    String lane;
    String workloadId;
    Map<String, Object> workloadEvidence = BenchmarkModel.object();
    String baselineId = "same-semantics-smoke-baseline-v1";
    long setupNanos;
    long measurementNanos;
    long exportNanos;
    long rows;
    long scanned;
    long matched;
    long changed;
    long removed;
    long materialized;
    long materializationInvocations;
    long candidates;
    long selected;
    long operations = 1L;
    long lookups;
    long missing;
    long duplicates;
    long touchedBytes;
    long workingSetBytes;
    long estimatedAllocationBytes;
    long materializationEstimatedAllocationBytes;
    long explicitReads;
    long explicitMutations;
    String allocationEstimatorVersion = "soma-smoke-measurement-allocation-v2";
    String touchedBytesScope = "lane-declared primitive columns and bitmap words touched in measurement";
    String workingSetScope = "runtime-owned retained primitive arrays and scratch used in measurement";
    Map<String, Object> accessPatternCard = BenchmarkModel.object("applicable", Boolean.FALSE,
            "reason", "populated-at-finish");
    Map<String, Object> exactIndexStats = BenchmarkModel.object("applicable", Boolean.FALSE,
            "reason", "lane-does-not-use-exact-index");
    Map<String, Object> keySpaceStats = BenchmarkModel.object("applicable", Boolean.FALSE,
            "reason", "lane-does-not-use-keyspace");
    Map<String, Object> selectorStats = BenchmarkModel.object("applicable", Boolean.FALSE,
            "reason", "lane-does-not-use-selector");
    String optionalDensity = "not-applicable";
    Map<String, Object> mutationReadRatio = BenchmarkModel.object("mutations", 0L, "reads", 0L);
    String statsMode = "summary";
    Map<String, Object> materializationStats = BenchmarkModel.object("applicable", Boolean.FALSE,
            "reason", "lane-does-not-materialize");
    Map<String, Object> effectiveMaterializationBudget = BenchmarkModel.object("applicable", Boolean.FALSE,
            "reason", "lane-does-not-materialize");
    String materializationBudgetDimension = "not-applicable";
    String materializationPath = "not-applicable";
    Map<String, Object> externalDtoStats = BenchmarkModel.object("applicable", Boolean.FALSE,
            "reason", "lane-has-no-external-dto");
    Map<String, Object> columnViewStats = BenchmarkModel.object("applicable", Boolean.FALSE,
            "reason", "lane-does-not-use-column-view");
    List<String> limitations = BenchmarkModel.limitations(
            "smoke evidence only; claimAllowed=false",
            "GC and hardware counters are not observed by this dependency-free runner");
}
