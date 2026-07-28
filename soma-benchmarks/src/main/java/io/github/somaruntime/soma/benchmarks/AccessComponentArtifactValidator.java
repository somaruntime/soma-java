package io.github.somaruntime.soma.benchmarks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 独立复读并验证 Access component JSONL artifact。 */
public final class AccessComponentArtifactValidator {
    private static final List<String> ALLOCATION_LANES = Arrays.asList(
            "candidate_scan.packed_zero_count",
            "candidate_scan.packed_one_filter_count",
            "candidate_scan.exact_zero_count",
            "candidate_scan.exact_zero_index",
            "candidate_scan.exact_one_filter_count",
            "candidate_scan.exact_two_stage_count",
            "candidate_scan.exact_three_stage_count",
            "candidate_scan.exact_four_stage_overflow_count",
            "candidate_scan.exact_five_stage_overflow_count",
            "candidate_scan.exact_sixteen_stage_overflow_count",
            "candidate_scan.exact_filter_sort_index",
            "candidate_scan.exact_filter_sort_snapshot",
            "candidate_scan.exact_filter_sort_materialize",
            "point.primary_find_index",
            "key_traversal.first_materialize",
            "column_traversal.long_for_each");
    private static final List<String> ALLOCATION_FIELDS = Arrays.asList(
            "schemaVersion", "artifactVersion", "kind", "lane", "status",
            "fork", "configuredForks", "commit",
            "javaVersion", "javaVendor", "javaVmName", "javaVmVersion", "jvmArgs",
            "osName", "osVersion", "architecture", "cpu",
            "maxHeapBytes", "warmupIterations", "measurementIterations", "rows",
            "distinctGroups", "groupRows", "matchingRows", "allocationMethod",
            "allocatedBytes", "allocatedBytesPerOperation", "elapsedNanos",
            "nanosPerOperation", "gcStats", "checksum", "observationKind",
            "knownLimitations", "claimAllowed");
    private static final List<String> MEMORY_FIELDS = Arrays.asList(
            "schemaVersion", "artifactVersion", "kind", "lane", "status",
            "fork", "configuredForks", "commit",
            "javaVersion", "javaVendor", "javaVmName", "javaVmVersion", "jvmArgs",
            "osName", "osVersion", "architecture", "cpu",
            "maxHeapBytes", "strategy", "warmupIterations", "measurementIterations", "rows",
            "distinctGroups", "entryCount", "groupCount", "retainedBytes",
            "rightSizedRetainedBytesEstimate", "overRetainedBytesEstimate",
            "storageHighWaterBytes", "estimatorVersion", "checksum", "observationKind",
            "knownLimitations", "claimAllowed");

    private AccessComponentArtifactValidator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("artifact path required");
        List<File> artifacts = new ArrayList<File>();
        for (String arg : args) artifacts.add(new File(arg));
        validate(artifacts);
        System.out.println("access-component-artifact-validation: ok");
    }

    static void validate(File artifact) throws IOException {
        validate(Collections.singletonList(artifact));
    }

    static void validate(List<File> artifacts) throws IOException {
        int allocations = 0;
        int memories = 0;
        Integer configuredForks = null;
        Set<Integer> forks = new LinkedHashSet<Integer>();
        Set<String> unique = new LinkedHashSet<String>();
        Map<Integer, List<String>> allocationLanes =
                new LinkedHashMap<Integer, List<String>>();
        Map<Integer, Integer> memoryCounts =
                new LinkedHashMap<Integer, Integer>();
        for (File artifact : artifacts) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(artifact), StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        throw new IllegalArgumentException("empty JSONL line");
                    }
                    Object parsed = BenchmarkModel.Json.parse(line);
                    if (!(parsed instanceof Map)) {
                        throw new IllegalArgumentException("record must be object");
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> record = (Map<String, Object>) parsed;
                    validateRecord(record);
                    int fork = (int) number(record, "fork");
                    int configured = (int) number(record, "configuredForks");
                    if (configuredForks == null) configuredForks = Integer.valueOf(configured);
                    else require(configuredForks.intValue() == configured,
                            "configured forks");
                    forks.add(Integer.valueOf(fork));
                    String lane = (String) record.get("lane");
                    require(unique.add(fork + ":" + lane),
                            "duplicate lane " + fork + ":" + lane);
                    String kind = (String) record.get("kind");
                    if ("allocation".equals(kind)) {
                        allocations++;
                        List<String> lanes = allocationLanes.get(Integer.valueOf(fork));
                        if (lanes == null) {
                            lanes = new ArrayList<String>();
                            allocationLanes.put(Integer.valueOf(fork), lanes);
                        }
                        lanes.add(lane);
                    } else {
                        memories++;
                        Integer count = memoryCounts.get(Integer.valueOf(fork));
                        memoryCounts.put(Integer.valueOf(fork),
                                Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                    }
                }
            } finally {
                reader.close();
            }
        }
        require(configuredForks != null
                        && configuredForks.intValue() == forks.size(),
                "observed forks");
        for (int fork = 1; fork <= configuredForks.intValue(); fork++) {
            require(forks.contains(Integer.valueOf(fork)), "fork coverage");
            require(ALLOCATION_LANES.equals(allocationLanes.get(Integer.valueOf(fork))),
                    "allocation lane order or coverage for fork " + fork);
            require(Integer.valueOf(24).equals(memoryCounts.get(Integer.valueOf(fork))),
                    "memory lane coverage for fork " + fork);
        }
        require(allocations == ALLOCATION_LANES.size() * configuredForks.intValue(),
                "allocation record count");
        require(memories == 24 * configuredForks.intValue(),
                "memory record count");
    }

    static void validateRecord(Map<String, Object> record) {
        require(AccessComponentBenchmark.SCHEMA_VERSION.equals(record.get("schemaVersion")),
                "schemaVersion");
        require(AccessComponentBenchmark.ARTIFACT_VERSION.equals(
                record.get("artifactVersion")), "artifactVersion");
        require("passed".equals(record.get("status")), "status");
        require(Boolean.FALSE.equals(record.get("claimAllowed")), "claimAllowed");
        long fork = positive(record, "fork");
        long forks = positive(record, "configuredForks");
        require(fork <= forks, "fork range");
        string(record, "lane");
        string(record, "commit");
        string(record, "javaVersion");
        string(record, "javaVendor");
        string(record, "javaVmName");
        string(record, "javaVmVersion");
        string(record, "osName");
        string(record, "osVersion");
        string(record, "architecture");
        string(record, "cpu");
        positive(record, "maxHeapBytes");
        stringList(record, "jvmArgs", false);
        stringList(record, "knownLimitations", true);

        String kind = string(record, "kind");
        List<String> expected;
        if ("allocation".equals(kind)) {
            expected = ALLOCATION_FIELDS;
            require("measured".equals(record.get("observationKind")), "allocation kind");
            require("thread-mxbean-current-thread-exact".equals(
                    record.get("allocationMethod")), "allocation method");
            positive(record, "measurementIterations");
            nonNegative(record, "warmupIterations");
            positive(record, "rows");
            positive(record, "distinctGroups");
            positive(record, "groupRows");
            positive(record, "matchingRows");
            nonNegative(record, "allocatedBytes");
            nonNegativeDecimal(record, "allocatedBytesPerOperation");
            positive(record, "elapsedNanos");
            positiveDecimal(record, "nanosPerOperation");
            gcStats(record.get("gcStats"));
        } else if ("memory".equals(kind)) {
            expected = MEMORY_FIELDS;
            require("deterministic-estimate".equals(record.get("observationKind")),
                    "memory kind");
            require("grouped-exact-index-array-payload-v1".equals(
                    record.get("estimatorVersion")), "estimator version");
            require(number(record, "warmupIterations") == 0L, "memory warmup");
            require(number(record, "measurementIterations") == 1L, "memory iteration");
            long rows = positive(record, "rows");
            long groups = positive(record, "distinctGroups");
            require(number(record, "entryCount") == rows, "entry count");
            require(number(record, "groupCount") == groups, "group count");
            long retained = positive(record, "retainedBytes");
            long rightSized = positive(record, "rightSizedRetainedBytesEstimate");
            long over = nonNegative(record, "overRetainedBytesEstimate");
            require(retained - rightSized == over, "retained estimate delta");
            require(number(record, "storageHighWaterBytes") >= retained,
                    "storage high-water");
            String strategy = string(record, "strategy");
            if ("row-worst-case-v1".equals(strategy)) {
                require((groups == rows) == (over == 0L),
                        "worst-case retention boundary");
            } else if ("cardinality-aware-v1".equals(strategy)) {
                require(over <= 63L, "cardinality-aware growth slack");
            } else {
                throw new IllegalArgumentException("unknown memory strategy " + strategy);
            }
        } else {
            throw new IllegalArgumentException("unknown kind " + kind);
        }
        require(new ArrayList<String>(record.keySet()).equals(expected),
                "exact field order for " + kind);
    }

    @SuppressWarnings("unchecked")
    private static void gcStats(Object value) {
        require(value instanceof Map, "gcStats object");
        Map<String, Object> stats = (Map<String, Object>) value;
        require(new ArrayList<String>(stats.keySet()).equals(Arrays.asList(
                "collectors", "youngCount", "youngTimeMillis", "fullCount",
                "fullTimeMillis", "unknownCount", "unknownTimeMillis")),
                "gcStats exact fields");
        string(stats, "collectors");
        nonNegative(stats, "youngCount");
        nonNegative(stats, "youngTimeMillis");
        nonNegative(stats, "fullCount");
        nonNegative(stats, "fullTimeMillis");
        nonNegative(stats, "unknownCount");
        nonNegative(stats, "unknownTimeMillis");
    }

    private static String string(Map<String, Object> values, String field) {
        Object value = values.get(field);
        require(value instanceof String && !((String) value).isEmpty(), field);
        return (String) value;
    }

    private static long positive(Map<String, Object> values, String field) {
        long value = number(values, field);
        require(value > 0L, field);
        return value;
    }

    private static long nonNegative(Map<String, Object> values, String field) {
        long value = number(values, field);
        require(value >= 0L, field);
        return value;
    }

    private static double positiveDecimal(Map<String, Object> values, String field) {
        Object value = values.get(field);
        require(value instanceof Number, field);
        double decimal = ((Number) value).doubleValue();
        require(!Double.isNaN(decimal) && !Double.isInfinite(decimal)
                && decimal > 0.0d, field);
        return decimal;
    }

    private static double nonNegativeDecimal(Map<String, Object> values, String field) {
        Object value = values.get(field);
        require(value instanceof Number, field);
        double decimal = ((Number) value).doubleValue();
        require(!Double.isNaN(decimal) && !Double.isInfinite(decimal)
                && decimal >= 0.0d, field);
        return decimal;
    }

    private static long number(Map<String, Object> values, String field) {
        Object value = values.get(field);
        require(value instanceof Number, field);
        double decimal = ((Number) value).doubleValue();
        require(!Double.isNaN(decimal) && !Double.isInfinite(decimal), field);
        return ((Number) value).longValue();
    }

    @SuppressWarnings("unchecked")
    private static void stringList(
            Map<String, Object> values, String field, boolean nonEmpty) {
        Object value = values.get(field);
        require(value instanceof List, field);
        List<Object> list = (List<Object>) value;
        require(!nonEmpty || !list.isEmpty(), field);
        for (Object item : list) {
            require(item instanceof String && !((String) item).isEmpty(), field);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
