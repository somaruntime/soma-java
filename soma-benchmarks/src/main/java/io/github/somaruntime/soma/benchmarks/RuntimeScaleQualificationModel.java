package io.github.somaruntime.soma.benchmarks;

import io.github.somaruntime.soma.benchmarks.schema.generated.SchemaMetadata;
import io.github.somaruntime.soma.dataflow.GeneratedDataFlow;
import io.github.somaruntime.soma.runtime.RuntimePlan;
import io.github.somaruntime.soma.runtime.generated.RuntimeCompatibility;

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

/**
 * Runtime-scale production qualification 的独立、严格 JSONL contract。
 *
 * <p>它不复用 smoke record shape，也不产生 public claim。每条 record 表示一个
 * preregistered lane 的完整 verdict，而不是某个最好样本。</p>
 */
final class RuntimeScaleQualificationModel {
    static final String SCHEMA_VERSION =
            "soma-runtime-scale-qualification-v2";
    static final String ARTIFACT_VERSION =
            "soma-runtime-scale-qualification-runner-v2";

    static final List<String> REQUIRED_LANES =
            Collections.unmodifiableList(Arrays.asList(
                    "small-fast",
                    "medium",
                    "1m-single",
                    "1m-double",
                    "string",
                    "expansion",
                    "delivery",
                    "soak"));

    static final List<String> RESEARCH_LANES =
            Collections.unmodifiableList(Arrays.asList(
                    "10m-research",
                    "100m-single-stress",
                    "100m-double-stress",
                    "100m-string-stress"));

    static final List<String> SUPPORTED_LANES;

    static {
        ArrayList<String> lanes =
                new ArrayList<String>(REQUIRED_LANES);
        lanes.addAll(RESEARCH_LANES);
        SUPPORTED_LANES =
                Collections.unmodifiableList(lanes);
    }

    static final List<String> FIELDS =
            Collections.unmodifiableList(Arrays.asList(
                    "schemaVersion",
                    "artifactVersion",
                    "qualificationId",
                    "lane",
                    "profile",
                    "required",
                    "applicable",
                    "status",
                    "claimAllowed",
                    "sourceIdentity",
                    "environment",
                    "preregistration",
                    "workload",
                    "timing",
                    "resource",
                    "observation",
                    "checksum",
                    "limitations",
                    "failureReason"));

    static final List<String> SOURCE_FIELDS =
            Collections.unmodifiableList(Arrays.asList(
                    "commit",
                    "treeState",
                    "schemaHash",
                    "runtimePlanHash",
                    "runtimeCompatibility",
                    "generatedProtocol",
                    "planProtocol",
                    "transformationProtocol",
                    "kernelProtocol"));

    static final List<String> ENVIRONMENT_FIELDS =
            Collections.unmodifiableList(Arrays.asList(
                    "javaRuntimeVersion",
                    "javaVendor",
                    "javaVmName",
                    "javaVmVersion",
                    "jvmArgs",
                    "osName",
                    "osVersion",
                    "architecture",
                    "cpu",
                    "processors",
                    "maxHeapBytes",
                    "gcCollectors"));

    static final List<String> PREREGISTRATION_FIELDS =
            Collections.unmodifiableList(Arrays.asList(
                    "seed",
                    "leftRows",
                    "rightRows",
                    "structuralBytesPerRow",
                    "stringProfile",
                    "multiplicity",
                    "skew",
                    "oracle",
                    "resourceBudget",
                    "timeoutSeconds",
                    "warmupIterations",
                    "measurementIterations",
                    "forks",
                    "passRule"));

    static final List<String> TIMING_FIELDS =
            Collections.unmodifiableList(Arrays.asList(
                    "setupNanos",
                    "workloadNanos",
                    "verificationNanos",
                    "releaseNanos",
                    "totalNanos"));

    static final List<String> RESOURCE_FIELDS =
            Collections.unmodifiableList(Arrays.asList(
                    "structuralCurrentBytes",
                    "structuralHighWaterBytes",
                    "retainedReachableStringBytesModel",
                    "heapUsedBeforeBytes",
                    "heapUsedPeakBytes",
                    "heapUsedAfterReleaseBytes",
                    "allocatedBytes",
                    "allocationMethod",
                    "youngGcCount",
                    "youngGcTimeMillis",
                    "fullGcCount",
                    "fullGcTimeMillis",
                    "unknownGcCount",
                    "unknownGcTimeMillis"));

    private RuntimeScaleQualificationModel() {
    }

    static LinkedHashMap<String, Object> record(
            QualificationConfig config,
            QualificationObservation observation) {
        BenchmarkEnvironment environment = new BenchmarkEnvironment();
        RuntimePlan defaultPlan = SchemaMetadata.defaultRuntimePlan();
        LinkedHashMap<String, Object> result =
                new LinkedHashMap<String, Object>();
        result.put("schemaVersion", SCHEMA_VERSION);
        result.put("artifactVersion", ARTIFACT_VERSION);
        result.put("qualificationId", config.qualificationId);
        result.put("lane", observation.lane);
        result.put("profile", observation.profile);
        result.put(
                "required",
                Boolean.valueOf(REQUIRED_LANES.contains(
                        observation.lane)));
        result.put("applicable", Boolean.TRUE);
        result.put("status", observation.status);
        result.put("claimAllowed", Boolean.FALSE);
        result.put("sourceIdentity", BenchmarkModel.object(
                "commit", config.commit,
                "treeState", config.treeState,
                "schemaHash", defaultPlan.schemaHash(),
                "runtimePlanHash", defaultPlan.runtimePlanHash(),
                "runtimeCompatibility",
                RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                "generatedProtocol",
                RuntimeCompatibility.GENERATED_PROTOCOL,
                "planProtocol", RuntimeCompatibility.PLAN_PROTOCOL,
                "transformationProtocol",
                GeneratedDataFlow.TRANSFORMATION_PROTOCOL,
                "kernelProtocol", GeneratedDataFlow.KERNEL_PROTOCOL));
        result.put("environment", BenchmarkModel.object(
                "javaRuntimeVersion", environment.javaVersion,
                "javaVendor", environment.javaVendor,
                "javaVmName", environment.javaVmName,
                "javaVmVersion", environment.javaVmVersion,
                "jvmArgs", environment.jvmArgs,
                "osName", environment.osName,
                "osVersion", environment.osVersion,
                "architecture", environment.architecture,
                "cpu", environment.cpu,
                "processors",
                Integer.valueOf(Runtime.getRuntime().availableProcessors()),
                "maxHeapBytes", Long.valueOf(environment.memory),
                "gcCollectors", JvmRuntimeMetrics.collectorNamesValue()));
        result.put("preregistration", BenchmarkModel.object(
                "seed", Long.valueOf(config.seed),
                "leftRows", Long.valueOf(observation.leftRows),
                "rightRows", Long.valueOf(observation.rightRows),
                "structuralBytesPerRow",
                Integer.valueOf(observation.structuralBytesPerRow),
                "stringProfile", observation.stringProfile,
                "multiplicity", observation.multiplicity,
                "skew", observation.skew,
                "oracle", observation.oracle,
                "resourceBudget", observation.resourceBudget,
                "timeoutSeconds",
                Long.valueOf(observation.timeoutSeconds),
                "warmupIterations",
                Integer.valueOf(observation.warmupIterations),
                "measurementIterations",
                Integer.valueOf(observation.measurementIterations),
                "forks", Integer.valueOf(observation.forks),
                "passRule", observation.passRule));
        result.put("workload", observation.workload);
        result.put("timing", BenchmarkModel.object(
                "setupNanos", Long.valueOf(observation.setupNanos),
                "workloadNanos", Long.valueOf(observation.workloadNanos),
                "verificationNanos",
                Long.valueOf(observation.verificationNanos),
                "releaseNanos", Long.valueOf(observation.releaseNanos),
                "totalNanos", Long.valueOf(observation.totalNanos)));
        result.put("resource", BenchmarkModel.object(
                "structuralCurrentBytes",
                Long.valueOf(observation.structuralCurrentBytes),
                "structuralHighWaterBytes",
                Long.valueOf(observation.structuralHighWaterBytes),
                "retainedReachableStringBytesModel",
                Long.valueOf(observation.retainedReachableStringBytesModel),
                "heapUsedBeforeBytes",
                Long.valueOf(observation.heapUsedBeforeBytes),
                "heapUsedPeakBytes",
                Long.valueOf(observation.heapUsedPeakBytes),
                "heapUsedAfterReleaseBytes",
                Long.valueOf(observation.heapUsedAfterReleaseBytes),
                "allocatedBytes", observation.allocatedBytes < 0L
                        ? null : Long.valueOf(observation.allocatedBytes),
                "allocationMethod",
                JvmRuntimeMetrics.allLiveThreadAllocationMethod(),
                "youngGcCount",
                Long.valueOf(observation.youngGcCount),
                "youngGcTimeMillis",
                Long.valueOf(observation.youngGcTimeMillis),
                "fullGcCount",
                Long.valueOf(observation.fullGcCount),
                "fullGcTimeMillis",
                Long.valueOf(observation.fullGcTimeMillis),
                "unknownGcCount",
                Long.valueOf(observation.unknownGcCount),
                "unknownGcTimeMillis",
                Long.valueOf(observation.unknownGcTimeMillis)));
        result.put("observation", observation.observation);
        result.put("checksum", observation.checksum);
        result.put("limitations", observation.limitations);
        result.put("failureReason", observation.failureReason);
        validateRecord(result);
        return result;
    }

    static void write(File target, List<Map<String, Object>> records)
            throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException(
                    "cannot create qualification output directory: " + parent);
        }
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8));
        try {
            for (Map<String, Object> record : records) {
                validateRecord(record);
                writer.write(BenchmarkModel.Json.write(record));
                writer.newLine();
            }
        } finally {
            writer.close();
        }
    }

    static List<Map<String, Object>> read(File source) throws IOException {
        ArrayList<Map<String, Object>> records =
                new ArrayList<Map<String, Object>>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(source), StandardCharsets.UTF_8));
        try {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "blank qualification JSONL line " + number);
                }
                Object parsed = BenchmarkModel.Json.parse(line);
                if (!(parsed instanceof Map)) {
                    throw new IllegalArgumentException(
                            "qualification JSONL line is not object: "
                                    + number);
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
            throw new IllegalArgumentException(
                    "qualification artifact must not be empty");
        }
        return records;
    }

    static void validateArtifact(
            List<Map<String, Object>> records, boolean complete) {
        LinkedHashSet<String> lanes = new LinkedHashSet<String>();
        String qualificationId = null;
        String commit = null;
        String treeState = null;
        String javaRuntimeVersion = null;
        String javaVendor = null;
        String osName = null;
        String osVersion = null;
        String architecture = null;
        String cpu = null;
        for (Map<String, Object> record : records) {
            validateRecord(record);
            String lane = string(record, "lane");
            if (!lanes.add(lane)) {
                throw new IllegalArgumentException(
                        "duplicate qualification lane: " + lane);
            }
            if (!SUPPORTED_LANES.contains(lane)) {
                throw new IllegalArgumentException(
                        "unknown qualification lane: " + lane);
            }
            qualificationId = same(
                    qualificationId,
                    string(record, "qualificationId"),
                    "qualificationId");
            Map<String, Object> source = object(record, "sourceIdentity");
            commit = same(commit, string(source, "commit"), "commit");
            treeState = same(
                    treeState, string(source, "treeState"), "treeState");
            Map<String, Object> environment =
                    object(record, "environment");
            javaRuntimeVersion = same(
                    javaRuntimeVersion,
                    string(environment, "javaRuntimeVersion"),
                    "javaRuntimeVersion");
            javaVendor = same(
                    javaVendor,
                    string(environment, "javaVendor"),
                    "javaVendor");
            osName = same(
                    osName,
                    string(environment, "osName"),
                    "osName");
            osVersion = same(
                    osVersion,
                    string(environment, "osVersion"),
                    "osVersion");
            architecture = same(
                    architecture,
                    string(environment, "architecture"),
                    "architecture");
            cpu = same(
                    cpu,
                    string(environment, "cpu"),
                    "cpu");
            boolean required = REQUIRED_LANES.contains(lane);
            require(Boolean.valueOf(required).equals(
                            record.get("required")),
                    "lane required classification: " + lane);
            require(Boolean.TRUE.equals(record.get("applicable")),
                    "lane must be applicable: " + lane);
            if (required) {
                require("passed".equals(record.get("status")),
                        "required applicable lane must pass: " + lane);
            }
        }
        if (complete) {
            require(lanes.size() == REQUIRED_LANES.size(),
                    "complete artifact lane count");
            require(lanes.equals(
                            new LinkedHashSet<String>(REQUIRED_LANES)),
                    "complete artifact lane order/set");
        }
    }

    static void validateRecord(Map<String, Object> record) {
        exactKeys(record, FIELDS, "record");
        require(SCHEMA_VERSION.equals(record.get("schemaVersion")),
                "schemaVersion");
        require(ARTIFACT_VERSION.equals(record.get("artifactVersion")),
                "artifactVersion");
        nonEmpty(string(record, "qualificationId"), "qualificationId");
        String lane = string(record, "lane");
        require(SUPPORTED_LANES.contains(lane), "lane");
        String expectedProfile = REQUIRED_LANES.contains(lane)
                ? "production-exact-v1" : "research-stress-v1";
        require(expectedProfile.equals(string(record, "profile")),
                "profile must match lane classification");
        require(record.get("required") instanceof Boolean, "required");
        require(record.get("applicable") instanceof Boolean, "applicable");
        String status = string(record, "status");
        require("passed".equals(status)
                        || "failed".equals(status)
                        || "inconclusive".equals(status)
                        || "not-applicable".equals(status),
                "status");
        require(Boolean.FALSE.equals(record.get("claimAllowed")),
                "claimAllowed must be false");

        Map<String, Object> source = object(record, "sourceIdentity");
        exactKeys(source, SOURCE_FIELDS, "sourceIdentity");
        for (String field : SOURCE_FIELDS) {
            nonEmpty(string(source, field), "sourceIdentity." + field);
        }
        Map<String, Object> environment = object(record, "environment");
        exactKeys(environment, ENVIRONMENT_FIELDS, "environment");
        for (String field : Arrays.asList(
                "javaRuntimeVersion", "javaVendor", "javaVmName",
                "javaVmVersion", "osName", "osVersion", "architecture",
                "cpu", "gcCollectors")) {
            nonEmpty(string(environment, field), "environment." + field);
        }
        require(string(environment, "javaRuntimeVersion")
                        .startsWith("1.8.0_502-b07"),
                "Corretto Java runtime build");
        require("Amazon.com Inc.".equals(
                        string(environment, "javaVendor")),
                "Corretto Java vendor");
        list(environment, "jvmArgs");
        nonNegative(number(environment, "processors"), "processors");
        positive(number(environment, "maxHeapBytes"), "maxHeapBytes");

        Map<String, Object> preregistration =
                object(record, "preregistration");
        exactKeys(
                preregistration,
                PREREGISTRATION_FIELDS,
                "preregistration");
        number(preregistration, "seed");
        nonNegative(number(preregistration, "leftRows"), "leftRows");
        nonNegative(number(preregistration, "rightRows"), "rightRows");
        positive(number(preregistration, "structuralBytesPerRow"),
                "structuralBytesPerRow");
        for (String field : Arrays.asList(
                "stringProfile", "multiplicity", "skew", "oracle",
                "resourceBudget", "passRule")) {
            nonEmpty(
                    string(preregistration, field),
                    "preregistration." + field);
        }
        positive(number(preregistration, "timeoutSeconds"), "timeoutSeconds");
        nonNegative(
                number(preregistration, "warmupIterations"),
                "warmupIterations");
        positive(
                number(preregistration, "measurementIterations"),
                "measurementIterations");
        positive(number(preregistration, "forks"), "forks");
        validateLanePreregistration(
                lane, preregistration, object(record, "workload"));

        require(!object(record, "workload").isEmpty(), "workload");
        Map<String, Object> timing = object(record, "timing");
        exactKeys(timing, TIMING_FIELDS, "timing");
        for (String field : TIMING_FIELDS) {
            nonNegative(number(timing, field), "timing." + field);
        }
        Map<String, Object> resource = object(record, "resource");
        exactKeys(resource, RESOURCE_FIELDS, "resource");
        for (String field : RESOURCE_FIELDS) {
            if ("allocationMethod".equals(field)) {
                nonEmpty(
                        string(resource, field), "resource." + field);
            } else if ("allocatedBytes".equals(field)) {
                Object value = resource.get(field);
                require(value == null || value instanceof Number,
                        "resource.allocatedBytes");
                if (value != null) {
                    nonNegative(
                            ((Number) value).longValue(),
                            "resource.allocatedBytes");
                }
            } else {
                nonNegative(
                        number(resource, field), "resource." + field);
            }
        }
        require(!object(record, "observation").isEmpty(), "observation");
        nonEmpty(string(record, "checksum"), "checksum");
        List<Object> limitations = list(record, "limitations");
        require(!limitations.isEmpty(), "limitations");
        for (Object limitation : limitations) {
            require(limitation instanceof String
                            && !((String) limitation).trim().isEmpty(),
                    "limitations item");
        }
        require(record.get("failureReason") instanceof String,
                "failureReason");
        if ("passed".equals(status)) {
            require(((String) record.get("failureReason")).isEmpty(),
                    "passed failureReason");
        }
    }

    private static void validateLanePreregistration(
            String lane,
            Map<String, Object> preregistration,
            Map<String, Object> workload) {
        long left = number(preregistration, "leftRows");
        long right = number(preregistration, "rightRows");
        long expectedLeft;
        long expectedRight;
        if ("small-fast".equals(lane)) {
            expectedLeft = 4_096L;
            expectedRight = 4_096L;
            exactNumericList(
                    workload.get("rowPoints"),
                    new long[] {0L, 1L, 16L, 256L, 1_024L, 4_096L},
                    "small-fast rowPoints");
        } else if ("medium".equals(lane)) {
            expectedLeft = 262_144L;
            expectedRight = 262_144L;
            exactNumericList(
                    workload.get("rowPoints"),
                    new long[] {32_768L, 65_536L, 262_144L},
                    "medium rowPoints");
        } else if ("1m-single".equals(lane)) {
            expectedLeft = 1_000_000L;
            expectedRight = 0L;
        } else if ("1m-double".equals(lane)
                || "string".equals(lane)) {
            expectedLeft = 1_000_000L;
            expectedRight = 1_000_000L;
        } else if ("10m-research".equals(lane)) {
            expectedLeft = 10_000_000L;
            expectedRight = 65_536L;
        } else if ("100m-single-stress".equals(lane)) {
            expectedLeft = 100_000_000L;
            expectedRight = 0L;
        } else if ("100m-double-stress".equals(lane)
                || "100m-string-stress".equals(lane)) {
            expectedLeft = 100_000_000L;
            expectedRight = 100_000_000L;
        } else if ("expansion".equals(lane)) {
            expectedLeft = 8L;
            expectedRight = 8L;
        } else {
            expectedLeft = 4_096L;
            expectedRight = 4_096L;
        }
        require(left == expectedLeft && right == expectedRight,
                lane + " exact row profile");
    }

    private static void exactNumericList(
            Object value, long[] expected, String name) {
        require(value instanceof List, name);
        List<?> actual = (List<?>) value;
        require(actual.size() == expected.length, name);
        for (int index = 0; index < expected.length; index++) {
            require(actual.get(index) instanceof Number
                            && ((Number) actual.get(index)).longValue()
                            == expected[index],
                    name);
        }
    }

    private static String same(
            String expected, String actual, String name) {
        if (expected == null) return actual;
        require(expected.equals(actual), "cross-record " + name);
        return expected;
    }

    private static void exactKeys(
            Map<String, Object> value,
            List<String> expected,
            String path) {
        require(new ArrayList<String>(value.keySet()).equals(expected),
                path + " exact field order/set");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(
            Map<String, Object> value, String field) {
        Object found = value.get(field);
        require(found instanceof Map, field);
        return (Map<String, Object>) found;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(
            Map<String, Object> value, String field) {
        Object found = value.get(field);
        require(found instanceof List, field);
        return (List<Object>) found;
    }

    private static String string(
            Map<String, Object> value, String field) {
        Object found = value.get(field);
        require(found instanceof String, field);
        return (String) found;
    }

    private static long number(
            Map<String, Object> value, String field) {
        Object found = value.get(field);
        require(found instanceof Number, field);
        return ((Number) found).longValue();
    }

    private static void positive(long value, String name) {
        require(value > 0L, name + " must be positive");
    }

    private static void nonNegative(long value, String name) {
        require(value >= 0L, name + " must be non-negative");
    }

    private static void nonEmpty(String value, String name) {
        require(!value.trim().isEmpty(), name + " must not be empty");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(
                    "invalid runtime-scale qualification: " + message);
        }
    }
}

final class QualificationConfig {
    final String qualificationId;
    final String commit;
    final String treeState;
    final long seed;

    QualificationConfig(
            String qualificationId,
            String commit,
            String treeState,
            long seed) {
        this.qualificationId = required(
                qualificationId, "qualificationId");
        this.commit = required(commit, "commit");
        this.treeState = required(treeState, "treeState");
        this.seed = seed;
    }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}

final class QualificationObservation {
    String lane;
    String profile;
    String status = "passed";
    long leftRows;
    long rightRows;
    int structuralBytesPerRow = 1;
    String stringProfile = "not-applicable";
    String multiplicity = "one-to-one-or-not-applicable";
    String skew = "uniform-or-not-applicable";
    String oracle = "deterministic-checksum-and-invariants";
    String resourceBudget = "lane-specific-hard-runtime-budgets";
    long timeoutSeconds = 300L;
    int warmupIterations;
    int measurementIterations = 1;
    int forks = 1;
    String passRule = "all-preregistered-correctness-and-resource-invariants";
    Map<String, Object> workload = BenchmarkModel.object(
            "identity", "not-populated");
    long setupNanos;
    long workloadNanos;
    long verificationNanos;
    long releaseNanos;
    long totalNanos;
    long structuralCurrentBytes;
    long structuralHighWaterBytes;
    long retainedReachableStringBytesModel;
    long heapUsedBeforeBytes;
    long heapUsedPeakBytes;
    long heapUsedAfterReleaseBytes;
    long allocatedBytes = -1L;
    long youngGcCount;
    long youngGcTimeMillis;
    long fullGcCount;
    long fullGcTimeMillis;
    long unknownGcCount;
    long unknownGcTimeMillis;
    Map<String, Object> observation = BenchmarkModel.object(
            "identity", "not-populated");
    String checksum = "not-populated";
    List<String> limitations = BenchmarkModel.limitations(
            "local qualification only; claimAllowed=false");
    String failureReason = "";
}
