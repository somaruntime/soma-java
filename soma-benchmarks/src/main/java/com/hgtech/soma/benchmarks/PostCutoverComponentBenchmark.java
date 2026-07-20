package com.hgtech.soma.benchmarks;

import com.hgtech.soma.examples.fjsp.schema.JobId;
import com.hgtech.soma.examples.fjsp.schema.MachineCandidate;
import com.hgtech.soma.examples.fjsp.schema.MachineId;
import com.hgtech.soma.examples.fjsp.schema.OperationId;
import com.hgtech.soma.examples.fjsp.schema.OperationKey;
import com.hgtech.soma.examples.fjsp.schema.OperationMachineKey;
import com.hgtech.soma.examples.fjsp.schema.SetupFamilyId;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateRow;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateRows;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateTable;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.TableStats;
import com.hgtech.soma.runtime.generated.GroupedExactIndex;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Packed/exact cutover 后 allocation 与 exact-index cardinality 的诊断 runner。 */
public final class PostCutoverComponentBenchmark {
    static final String SCHEMA_VERSION = "soma-post-cutover-component-v1";
    static final String ARTIFACT_VERSION = "soma-java-post-cutover-component-v1";
    private static final int CANDIDATE_ROWS = 4096;
    private static final int MACHINE_COUNT = 64;
    private static final int OPTIONS_PER_OPERATION = 4;
    private static final MachineId QUERY_MACHINE = new MachineId(17L);
    private static volatile long LONG_SINK;
    private static volatile Object OBJECT_SINK;

    private static final MachineCandidateRows.Predicate READY =
            new MachineCandidateRows.Predicate() {
                @Override
                public boolean test(MachineCandidateRow row) {
                    return row.indicatorReady();
                }
            };

    private static final MachineCandidateRows.Comparator DISPATCH_ORDER =
            new MachineCandidateRows.Comparator() {
                @Override
                public int compare(MachineCandidateRow left, MachineCandidateRow right) {
                    int value = Long.compare(left.fcfsValue(), right.fcfsValue());
                    if (value != 0) return value;
                    value = Long.compare(left.sptValue(), right.sptValue());
                    if (value != 0) return value;
                    value = Long.compare(left.candidateKeyOperationKeyJobIdValue(),
                            right.candidateKeyOperationKeyJobIdValue());
                    if (value != 0) return value;
                    value = Long.compare(left.candidateKeyOperationKeyOperationIdValue(),
                            right.candidateKeyOperationKeyOperationIdValue());
                    if (value != 0) return value;
                    return Long.compare(left.candidateKeyMachineIdValue(),
                            right.candidateKeyMachineIdValue());
                }
            };

    private PostCutoverComponentBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        BenchmarkEnvironment environment = new BenchmarkEnvironment();
        List<LinkedHashMap<String, Object>> records =
                new ArrayList<LinkedHashMap<String, Object>>();
        runAllocationLanes(options, environment, records);
        runMemoryMatrix(options, environment, records);
        write(options.output, records);
        System.out.println("post-cutover-component-artifact: "
                + options.output.getAbsolutePath());
    }

    private static void runAllocationLanes(
            Options options,
            BenchmarkEnvironment environment,
            List<LinkedHashMap<String, Object>> records) {
        MachineCandidateTable table = MachineCandidateTable.create();
        try {
            table.addBatch(candidateBatch());
            TableStats exactStats = table.statsSnapshot();
            long expectedExactBytes = GroupedExactIndex.estimatedRetainedBytes(
                    table.capacity(), MACHINE_COUNT)
                    + GroupedExactIndex.estimatedRetainedBytes(
                    table.capacity(), CANDIDATE_ROWS / OPTIONS_PER_OPERATION);
            require(exactStats.exactIndexEntryCount() == 2L * CANDIDATE_ROWS,
                    "generated exact-index entry count");
            require(exactStats.exactIndexGroupCount()
                            == MACHINE_COUNT + CANDIDATE_ROWS / OPTIONS_PER_OPERATION,
                    "generated exact-index group count");
            require(exactStats.exactIndexStorageCurrentBytes() == expectedExactBytes,
                    "generated exact-index cardinality-aware capacity");
            int groupRows = (int) table.findByMachine(QUERY_MACHINE).count();
            int matchingRows = (int) table.findByMachine(QUERY_MACHINE)
                    .filter(READY).count();
            require(groupRows == CANDIDATE_ROWS / MACHINE_COUNT,
                    "unexpected by-machine group size");
            require(matchingRows > 0 && matchingRows < groupRows,
                    "filter must select a strict subset");

            measure(options, environment, records, table,
                    "pipeline.exact_source_count", groupRows, matchingRows,
                    new Lane() {
                        @Override
                        public long run(MachineCandidateTable value) {
                            return value.findByMachine(QUERY_MACHINE).count();
                        }
                    });
            measure(options, environment, records, table,
                    "pipeline.exact_filter_count", groupRows, matchingRows,
                    new Lane() {
                        @Override
                        public long run(MachineCandidateTable value) {
                            return value.findByMachine(QUERY_MACHINE)
                                    .filter(READY).count();
                        }
                    });
            measure(options, environment, records, table,
                    "pipeline.exact_filter_sort_snapshot", groupRows, matchingRows,
                    new Lane() {
                        @Override
                        public long run(MachineCandidateTable value) {
                            IndexSnapshot snapshot = value.findByMachine(QUERY_MACHINE)
                                    .filter(READY).sorted(DISPATCH_ORDER)
                                    .limit(1).rowIndexes();
                            OBJECT_SINK = snapshot;
                            return snapshot.size() == 0 ? -1L : snapshot.indexAt(0);
                        }
                    });
            measure(options, environment, records, table,
                    "pipeline.exact_filter_sort_materialize", groupRows, matchingRows,
                    new Lane() {
                        @Override
                        public long run(MachineCandidateTable value) {
                            MachineCandidate candidate = value.findByMachine(QUERY_MACHINE)
                                    .filter(READY).sorted(DISPATCH_ORDER).firstOrThrow();
                            OBJECT_SINK = candidate;
                            return candidate.candidateKey.operationKey.operationId.value;
                        }
                    });
        } finally {
            table.release();
        }
    }

    private static void measure(
            Options options,
            BenchmarkEnvironment environment,
            List<LinkedHashMap<String, Object>> records,
            MachineCandidateTable table,
            String laneName,
            int groupRows,
            int matchingRows,
            Lane lane) {
        for (int iteration = 0; iteration < options.warmupIterations; iteration++) {
            LONG_SINK ^= lane.run(table);
        }
        JvmRuntimeMetrics.Snapshot before = JvmRuntimeMetrics.snapshot();
        long started = System.nanoTime();
        long checksum = 0L;
        for (int iteration = 0; iteration < options.measurementIterations; iteration++) {
            checksum = mix(checksum, lane.run(table));
        }
        long elapsed = System.nanoTime() - started;
        JvmRuntimeMetrics.Delta delta = JvmRuntimeMetrics.snapshot().since(before);
        require(delta.allocatedBytes >= 0L,
                "current-thread allocation observation is required");
        LONG_SINK = checksum;

        LinkedHashMap<String, Object> record = common(
                options, environment, "allocation", laneName);
        record.put("warmupIterations", Integer.valueOf(options.warmupIterations));
        record.put("measurementIterations", Integer.valueOf(options.measurementIterations));
        record.put("rows", Integer.valueOf(CANDIDATE_ROWS));
        record.put("distinctGroups", Integer.valueOf(MACHINE_COUNT));
        record.put("groupRows", Integer.valueOf(groupRows));
        record.put("matchingRows", Integer.valueOf(matchingRows));
        record.put("allocationMethod", JvmRuntimeMetrics.allocationMethod());
        record.put("allocatedBytes", Long.valueOf(delta.allocatedBytes));
        record.put("allocatedBytesPerOperation", Double.valueOf(
                (double) delta.allocatedBytes / options.measurementIterations));
        record.put("elapsedNanos", Long.valueOf(elapsed));
        record.put("nanosPerOperation", Double.valueOf(
                (double) elapsed / options.measurementIterations));
        record.put("gcStats", gcStats(delta));
        record.put("checksum", Long.valueOf(checksum));
        record.put("observationKind", "measured");
        record.put("knownLimitations", BenchmarkModel.limitations(
                "single JVM process and current benchmark thread only",
                "diagnostic evidence; no absolute performance or release claim"));
        record.put("claimAllowed", Boolean.FALSE);
        PostCutoverComponentArtifactValidator.validateRecord(record);
        records.add(record);
    }

    private static void runMemoryMatrix(
            Options options,
            BenchmarkEnvironment environment,
            List<LinkedHashMap<String, Object>> records) {
        int[] rowCounts = {1024, 16384, 65536};
        for (int rows : rowCounts) {
            int root = (int) Math.sqrt(rows);
            int[] groupCounts = {1, 16, root, rows};
            for (int groups : groupCounts) {
                memoryCase(options, environment, records, rows, groups,
                        "row-worst-case-v1");
                memoryCase(options, environment, records, rows, groups,
                        "cardinality-aware-v1");
            }
        }
    }

    private static void memoryCase(
            Options options,
            BenchmarkEnvironment environment,
            List<LinkedHashMap<String, Object>> records,
            int rows,
            int groups,
            String strategy) {
        boolean cardinalityAware = "cardinality-aware-v1".equals(strategy);
        GroupedExactIndex index = cardinalityAware
                ? new GroupedExactIndex(rows, 0)
                : new GroupedExactIndex(rows);
        try {
            if (cardinalityAware) index.ensureCapacity(rows, groups);
            int[] groupIds = new int[groups];
            for (int group = 0; group < groups; group++) {
                groupIds[group] = index.createGroup(mix(0L, group));
            }
            for (int row = 0; row < rows; row++) {
                index.link(groupIds[row % groups], row);
            }
            require(index.entryCount() == rows, "memory matrix entry count");
            require(index.groupCount() == groups, "memory matrix group count");
            long retained = index.retainedBytes();
            long rightSized = GroupedExactIndex.estimatedRetainedBytes(rows, groups);
            require(retained >= rightSized, "right-sized estimate exceeds current payload");

            LinkedHashMap<String, Object> record = common(options, environment,
                    "memory", "exact_index.cardinality_" + rows + "_" + groups
                            + (cardinalityAware ? "_aware" : "_worst_case"));
            record.put("strategy", strategy);
            record.put("warmupIterations", Integer.valueOf(0));
            record.put("measurementIterations", Integer.valueOf(1));
            record.put("rows", Integer.valueOf(rows));
            record.put("distinctGroups", Integer.valueOf(groups));
            record.put("entryCount", Integer.valueOf(index.entryCount()));
            record.put("groupCount", Integer.valueOf(index.groupCount()));
            record.put("retainedBytes", Long.valueOf(retained));
            record.put("rightSizedRetainedBytesEstimate", Long.valueOf(rightSized));
            record.put("overRetainedBytesEstimate", Long.valueOf(retained - rightSized));
            record.put("storageHighWaterBytes", Long.valueOf(index.storageHighWaterBytes()));
            record.put("estimatorVersion", "grouped-exact-index-array-payload-v1");
            record.put("checksum", Long.valueOf(mix(index.entryCount(), index.groupCount())));
            record.put("observationKind", "deterministic-estimate");
            record.put("knownLimitations", BenchmarkModel.limitations(
                    "primitive array payload only; JVM headers and alignment excluded",
                    "diagnostic cardinality matrix; no heap-retention or release claim"));
            record.put("claimAllowed", Boolean.FALSE);
            PostCutoverComponentArtifactValidator.validateRecord(record);
            records.add(record);
        } finally {
            index.release();
        }
    }

    private static MachineCandidateBatch candidateBatch() {
        MachineCandidateBatch batch = new MachineCandidateBatch(CANDIDATE_ROWS);
        SetupFamilyId[] families = new SetupFamilyId[16];
        MachineId[] machines = new MachineId[MACHINE_COUNT];
        for (int index = 0; index < families.length; index++) {
            families[index] = new SetupFamilyId(index);
        }
        for (int index = 0; index < machines.length; index++) {
            machines[index] = new MachineId(index);
        }
        for (int row = 0; row < CANDIDATE_ROWS; row++) {
            int operation = row / OPTIONS_PER_OPERATION;
            int option = row % OPTIONS_PER_OPERATION;
            long job = operation / 16L;
            int machine = (operation + option * 17) % MACHINE_COUNT;
            long ready = operation * 3L + option;
            long processing = 1L + ((operation * 7L + option) % 43L);
            batch.addValues(new OperationMachineKey(
                            new OperationKey(new JobId(job), new OperationId(operation)),
                            machines[machine]),
                    families[operation % families.length],
                    operation, operation, option, ready, processing,
                    option, ready, ready, processing + option,
                    (operation & 3) != 0);
        }
        return batch;
    }

    private static LinkedHashMap<String, Object> common(
            Options options, BenchmarkEnvironment environment, String kind, String lane) {
        LinkedHashMap<String, Object> record = new LinkedHashMap<String, Object>();
        record.put("schemaVersion", SCHEMA_VERSION);
        record.put("artifactVersion", ARTIFACT_VERSION);
        record.put("kind", kind);
        record.put("lane", lane);
        record.put("status", "passed");
        record.put("commit", options.commit);
        record.put("javaVersion", environment.javaVersion);
        record.put("javaVendor", environment.javaVendor);
        record.put("jvmArgs", environment.jvmArgs);
        record.put("os", environment.os);
        record.put("architecture", environment.architecture);
        record.put("cpu", environment.cpu);
        record.put("maxHeapBytes", Long.valueOf(environment.memory));
        return record;
    }

    private static LinkedHashMap<String, Object> gcStats(JvmRuntimeMetrics.Delta delta) {
        return BenchmarkModel.object(
                "collectors", JvmRuntimeMetrics.collectorNamesValue(),
                "youngCount", Long.valueOf(delta.youngGcCount),
                "youngTimeMillis", Long.valueOf(delta.youngGcTimeMillis),
                "fullCount", Long.valueOf(delta.fullGcCount),
                "fullTimeMillis", Long.valueOf(delta.fullGcTimeMillis),
                "unknownCount", Long.valueOf(delta.unknownGcCount),
                "unknownTimeMillis", Long.valueOf(delta.unknownGcTimeMillis));
    }

    private static void write(
            File output, List<LinkedHashMap<String, Object>> records) throws IOException {
        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create output directory: " + parent);
        }
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(output), StandardCharsets.UTF_8));
        try {
            for (LinkedHashMap<String, Object> record : records) {
                writer.write(BenchmarkModel.Json.write(record));
                writer.newLine();
            }
        } finally {
            writer.close();
        }
    }

    private static long mix(long state, long value) {
        long result = state ^ value;
        result ^= result >>> 33;
        result *= 0xff51afd7ed558ccdl;
        result ^= result >>> 33;
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private interface Lane {
        long run(MachineCandidateTable table);
    }

    private static final class Options {
        final File output;
        final String commit;
        final int warmupIterations;
        final int measurementIterations;

        Options(File output, String commit, int warmupIterations,
                int measurementIterations) {
            this.output = output;
            this.commit = commit;
            this.warmupIterations = warmupIterations;
            this.measurementIterations = measurementIterations;
        }

        static Options parse(String[] args) {
            File output = null;
            String commit = null;
            int warmup = 2000;
            int iterations = 5000;
            for (int index = 0; index < args.length; index += 2) {
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException("missing value for " + args[index]);
                }
                String option = args[index];
                String value = args[index + 1];
                if ("--output".equals(option)) output = new File(value);
                else if ("--commit".equals(option)) commit = value;
                else if ("--warmup".equals(option)) warmup = integer(value, option, true);
                else if ("--iterations".equals(option)) iterations = integer(value, option, false);
                else throw new IllegalArgumentException("unknown option " + option);
            }
            if (output == null) throw new IllegalArgumentException("--output required");
            if (commit == null || commit.isEmpty()) {
                throw new IllegalArgumentException("--commit required");
            }
            return new Options(output, commit, warmup, iterations);
        }

        private static int integer(String value, String option, boolean allowZero) {
            int parsed;
            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("invalid " + option, failure);
            }
            if (allowZero ? parsed < 0 : parsed <= 0) {
                throw new IllegalArgumentException("invalid " + option);
            }
            return parsed;
        }
    }
}
