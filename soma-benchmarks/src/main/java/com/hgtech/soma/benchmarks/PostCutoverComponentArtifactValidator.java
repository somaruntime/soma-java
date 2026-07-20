package com.hgtech.soma.benchmarks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 独立复读并验证 post-cutover component JSONL artifact。 */
public final class PostCutoverComponentArtifactValidator {
    private static final List<String> ALLOCATION_FIELDS = Arrays.asList(
            "schemaVersion", "artifactVersion", "kind", "lane", "status", "commit",
            "javaVersion", "javaVendor", "jvmArgs", "os", "architecture", "cpu",
            "maxHeapBytes", "warmupIterations", "measurementIterations", "rows",
            "distinctGroups", "groupRows", "matchingRows", "allocationMethod",
            "allocatedBytes", "allocatedBytesPerOperation", "elapsedNanos",
            "nanosPerOperation", "gcStats", "checksum", "observationKind",
            "knownLimitations", "claimAllowed");
    private static final List<String> MEMORY_FIELDS = Arrays.asList(
            "schemaVersion", "artifactVersion", "kind", "lane", "status", "commit",
            "javaVersion", "javaVendor", "jvmArgs", "os", "architecture", "cpu",
            "maxHeapBytes", "strategy", "warmupIterations", "measurementIterations", "rows",
            "distinctGroups", "entryCount", "groupCount", "retainedBytes",
            "rightSizedRetainedBytesEstimate", "overRetainedBytesEstimate",
            "storageHighWaterBytes", "estimatorVersion", "checksum", "observationKind",
            "knownLimitations", "claimAllowed");

    private PostCutoverComponentArtifactValidator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("artifact path required");
        validate(new File(args[0]));
        System.out.println("post-cutover-component-artifact-validation: ok");
    }

    static void validate(File artifact) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(artifact), StandardCharsets.UTF_8));
        int allocations = 0;
        int memories = 0;
        List<String> lanes = new ArrayList<String>();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) throw new IllegalArgumentException("empty JSONL line");
                Object parsed = BenchmarkModel.Json.parse(line);
                if (!(parsed instanceof Map)) {
                    throw new IllegalArgumentException("record must be object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> record = (Map<String, Object>) parsed;
                validateRecord(record);
                String kind = (String) record.get("kind");
                if ("allocation".equals(kind)) allocations++;
                else memories++;
                String lane = (String) record.get("lane");
                if (lanes.contains(lane)) throw new IllegalArgumentException("duplicate lane " + lane);
                lanes.add(lane);
            }
        } finally {
            reader.close();
        }
        if (allocations != 4 || memories != 24) {
            throw new IllegalArgumentException("expected 4 allocation and 24 memory records");
        }
    }

    static void validateRecord(Map<String, Object> record) {
        require(PostCutoverComponentBenchmark.SCHEMA_VERSION.equals(record.get("schemaVersion")),
                "schemaVersion");
        require(PostCutoverComponentBenchmark.ARTIFACT_VERSION.equals(
                record.get("artifactVersion")), "artifactVersion");
        require("passed".equals(record.get("status")), "status");
        require(Boolean.FALSE.equals(record.get("claimAllowed")), "claimAllowed");
        string(record, "lane");
        string(record, "commit");
        string(record, "javaVersion");
        string(record, "javaVendor");
        string(record, "os");
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
            positive(record, "allocatedBytes");
            positive(record, "allocatedBytesPerOperation");
            positive(record, "elapsedNanos");
            positive(record, "nanosPerOperation");
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
