package io.github.somaruntime.soma.benchmarks;

import io.github.somaruntime.soma.benchmarks.schema.CandidateKey;
import io.github.somaruntime.soma.benchmarks.schema.CategoryId;
import io.github.somaruntime.soma.benchmarks.schema.GroupCandidate;
import io.github.somaruntime.soma.benchmarks.schema.GroupId;
import io.github.somaruntime.soma.benchmarks.schema.ItemId;
import io.github.somaruntime.soma.benchmarks.schema.NamespaceId;
import io.github.somaruntime.soma.benchmarks.schema.WorkKey;
import io.github.somaruntime.soma.benchmarks.schema.generated.GroupCandidateBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.GroupCandidateCursor;
import io.github.somaruntime.soma.benchmarks.schema.generated.GroupCandidateScan;
import io.github.somaruntime.soma.benchmarks.schema.generated.GroupCandidateTable;
import io.github.somaruntime.soma.runtime.IndexSnapshot;
import io.github.somaruntime.soma.runtime.TableStats;
import io.github.somaruntime.soma.runtime.generated.GroupedExactIndex;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.LongConsumer;

/** Access/Candidate allocation 与 exact-index cardinality 的领域中性诊断 runner。 */
public final class AccessComponentBenchmark {
    static final String SCHEMA_VERSION = "soma-access-component-v1";
    static final String ARTIFACT_VERSION = "soma-java-access-component-v1";
    private static final int CANDIDATE_ROWS = 4096;
    private static final int GROUP_COUNT = 64;
    private static final int OPTIONS_PER_WORK = 4;
    private static final GroupId QUERY_GROUP = new GroupId(17L);
    private static final CandidateKey QUERY_CANDIDATE_KEY =
            new CandidateKey(
                    new WorkKey(new NamespaceId(0L), new ItemId(0L)),
                    new GroupId(0L));
    private static volatile long LONG_SINK;
    private static volatile Object OBJECT_SINK;
    private static final LongSum LONG_SUM = new LongSum();

    private static final GroupCandidateScan.Predicate READY =
            new GroupCandidateScan.Predicate() {
                @Override
                public boolean test(GroupCandidateCursor row) {
                    return row.selected();
                }
            };

    private static final GroupCandidateScan.Comparator DISPATCH_ORDER =
            new GroupCandidateScan.Comparator() {
                @Override
                public int compare(GroupCandidateCursor left, GroupCandidateCursor right) {
                    int value = Long.compare(left.metric7(), right.metric7());
                    if (value != 0) return value;
                    value = Long.compare(left.metric8(), right.metric8());
                    if (value != 0) return value;
                    value = Long.compare(left.candidateKeyWorkKeyNamespaceIdValue(),
                            right.candidateKeyWorkKeyNamespaceIdValue());
                    if (value != 0) return value;
                    value = Long.compare(left.candidateKeyWorkKeyItemIdValue(),
                            right.candidateKeyWorkKeyItemIdValue());
                    if (value != 0) return value;
                    return Long.compare(left.candidateKeyGroupIdValue(),
                            right.candidateKeyGroupIdValue());
                }
            };

    private AccessComponentBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        BenchmarkEnvironment environment = new BenchmarkEnvironment();
        List<LinkedHashMap<String, Object>> records =
                new ArrayList<LinkedHashMap<String, Object>>();
        runAllocationLanes(options, environment, records);
        runMemoryMatrix(options, environment, records);
        write(options.output, records);
        System.out.println("access-component-artifact: "
                + options.output.getAbsolutePath());
    }

    private static void runAllocationLanes(
            Options options,
            BenchmarkEnvironment environment,
            List<LinkedHashMap<String, Object>> records) {
        GroupCandidateTable table = GroupCandidateTable.create();
        try {
            table.addBatch(candidateBatch());
            TableStats exactStats = table.statsSnapshot();
            long expectedExactBytes = GroupedExactIndex.estimatedRetainedBytes(
                    table.capacity(), GROUP_COUNT)
                    + GroupedExactIndex.estimatedRetainedBytes(
                    table.capacity(), CANDIDATE_ROWS / OPTIONS_PER_WORK);
            require(exactStats.exactIndexEntryCount() == 2L * CANDIDATE_ROWS,
                    "generated exact-index entry count");
            require(exactStats.exactIndexGroupCount()
                            == GROUP_COUNT + CANDIDATE_ROWS / OPTIONS_PER_WORK,
                    "generated exact-index group count");
            require(exactStats.exactIndexStorageCurrentBytes() == expectedExactBytes,
                    "generated exact-index cardinality-aware capacity");
            int groupRows = (int) table.scanByGroup(QUERY_GROUP).count();
            int matchingRows = (int) table.scanByGroup(QUERY_GROUP)
                    .filter(READY).count();
            require(groupRows == CANDIDATE_ROWS / GROUP_COUNT,
                    "unexpected exact group size");
            require(matchingRows > 0 && matchingRows < groupRows,
                    "filter must select a strict subset");
            require(table.findIndex(QUERY_CANDIDATE_KEY) >= 0,
                    "primary point benchmark key");

            measure(options, environment, records, table,
                    "candidate_scan.packed_zero_count", CANDIDATE_ROWS, CANDIDATE_ROWS,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            return value.count();
                        }
                    });
            measure(options, environment, records, table,
                    "candidate_scan.packed_one_filter_count", CANDIDATE_ROWS,
                    CANDIDATE_ROWS - CANDIDATE_ROWS / 4,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            return value.filter(READY).count();
                        }
                    });
            measure(options, environment, records, table,
                    "candidate_scan.exact_zero_count", groupRows, groupRows,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            return value.scanByGroup(QUERY_GROUP).count();
                        }
                    });
            measure(options, environment, records, table,
                    "candidate_scan.exact_zero_index", groupRows, 1,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            return value.scanByGroup(QUERY_GROUP).requireIndex();
                        }
                    });
            measure(options, environment, records, table,
                    "candidate_scan.exact_one_filter_count", groupRows, matchingRows,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            return value.scanByGroup(QUERY_GROUP)
                                    .filter(READY).count();
                        }
                    });
            measure(options, environment, records, table,
                    "candidate_scan.exact_two_stage_count", groupRows, matchingRows,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            return exactFilterStages(value, 2).count();
                        }
                    });
            measure(options, environment, records, table,
                    "candidate_scan.exact_three_stage_count", groupRows, matchingRows,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            return exactFilterStages(value, 3).count();
                        }
                    });
            measure(options, environment, records, table,
                    "candidate_scan.exact_four_stage_overflow_count", groupRows,
                    matchingRows,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            return exactFilterStages(value, 4).count();
                        }
                    });
            measure(options, environment, records, table,
                    "candidate_scan.exact_five_stage_overflow_count", groupRows,
                    matchingRows,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            return exactFilterStages(value, 5).count();
                        }
                    });
            measure(options, environment, records, table,
                    "candidate_scan.exact_sixteen_stage_overflow_count", groupRows,
                    matchingRows,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            return exactFilterStages(value, 16).count();
                        }
                    });
            measure(options, environment, records, table,
                    "candidate_scan.exact_filter_sort_index", groupRows, matchingRows,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            return value.scanByGroup(QUERY_GROUP)
                                    .filter(READY).sorted(DISPATCH_ORDER)
                                    .requireIndex();
                        }
                    });
            measure(options, environment, records, table,
                    "candidate_scan.exact_filter_sort_snapshot", groupRows, matchingRows,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            IndexSnapshot snapshot = value.scanByGroup(QUERY_GROUP)
                                    .filter(READY).sorted(DISPATCH_ORDER).limit(1)
                                    .indexSnapshot();
                            OBJECT_SINK = snapshot;
                            return snapshot.size();
                        }
                    });
            measure(options, environment, records, table,
                    "candidate_scan.exact_filter_sort_materialize", groupRows,
                    matchingRows,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            GroupCandidate candidate = value.scanByGroup(QUERY_GROUP)
                                    .filter(READY).sorted(DISPATCH_ORDER).firstOrThrow();
                            OBJECT_SINK = candidate;
                            return candidate.candidateKey.workKey.itemId.value;
                        }
                    });
            measure(options, environment, records, table,
                    "point.primary_find_index", 1, 1,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            return value.findIndex(QUERY_CANDIDATE_KEY);
                        }
                    });
            measure(options, environment, records, table,
                    "key_traversal.first_materialize", CANDIDATE_ROWS, 1,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            CandidateKey key = value.keys().firstOrThrow();
                            OBJECT_SINK = key;
                            return key.workKey.itemId.value;
                        }
                    });
            measure(options, environment, records, table,
                    "column_traversal.long_for_each", CANDIDATE_ROWS, CANDIDATE_ROWS,
                    new Lane() {
                        @Override
                        public long run(GroupCandidateTable value) {
                            LONG_SUM.value = 0L;
                            value.metric4Values().forEachLong(LONG_SUM);
                            return LONG_SUM.value;
                        }
                    });
        } finally {
            table.release();
        }
    }

    private static GroupCandidateScan exactFilterStages(
            GroupCandidateTable table, int stages) {
        GroupCandidateScan scan = table.scanByGroup(QUERY_GROUP);
        for (int stage = 0; stage < stages; stage++) {
            scan = scan.filter(READY);
        }
        return scan;
    }

    private static void measure(
            Options options,
            BenchmarkEnvironment environment,
            List<LinkedHashMap<String, Object>> records,
            GroupCandidateTable table,
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
        record.put("distinctGroups", Integer.valueOf(GROUP_COUNT));
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
        AccessComponentArtifactValidator.validateRecord(record);
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
            AccessComponentArtifactValidator.validateRecord(record);
            records.add(record);
        } finally {
            index.release();
        }
    }

    private static GroupCandidateBatch candidateBatch() {
        GroupCandidateBatch batch = new GroupCandidateBatch(CANDIDATE_ROWS);
        CategoryId[] categories = new CategoryId[16];
        GroupId[] groups = new GroupId[GROUP_COUNT];
        for (int index = 0; index < categories.length; index++) {
            categories[index] = new CategoryId(index);
        }
        for (int index = 0; index < groups.length; index++) {
            groups[index] = new GroupId(index);
        }
        for (int row = 0; row < CANDIDATE_ROWS; row++) {
            int work = row / OPTIONS_PER_WORK;
            int option = row % OPTIONS_PER_WORK;
            long namespace = work / 16L;
            int group = (work + option * 17) % GROUP_COUNT;
            long ready = work * 3L + option;
            long payload = 1L + ((work * 7L + option) % 43L);
            batch.addValues(new CandidateKey(
                            new WorkKey(new NamespaceId(namespace), new ItemId(work)),
                            groups[group]),
                    categories[work % categories.length],
                    work, work, option, ready, payload,
                    option, ready, ready, payload + option,
                    (work & 3) != 0);
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
        record.put("fork", Integer.valueOf(options.fork));
        record.put("configuredForks", Integer.valueOf(options.forks));
        record.put("commit", options.commit);
        record.put("javaVersion", environment.javaVersion);
        record.put("javaVendor", environment.javaVendor);
        record.put("javaVmName", environment.javaVmName);
        record.put("javaVmVersion", environment.javaVmVersion);
        record.put("jvmArgs", environment.jvmArgs);
        record.put("osName", environment.osName);
        record.put("osVersion", environment.osVersion);
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
        long run(GroupCandidateTable table);
    }

    private static final class LongSum implements LongConsumer {
        long value;

        LongSum() {
        }

        @Override
        public void accept(long current) {
            value += current;
        }
    }

    private static final class Options {
        final File output;
        final String commit;
        final int fork;
        final int forks;
        final int warmupIterations;
        final int measurementIterations;

        Options(File output, String commit, int fork, int forks,
                int warmupIterations, int measurementIterations) {
            this.output = output;
            this.commit = commit;
            this.fork = fork;
            this.forks = forks;
            this.warmupIterations = warmupIterations;
            this.measurementIterations = measurementIterations;
        }

        static Options parse(String[] args) {
            File output = null;
            String commit = null;
            int fork = 1;
            int forks = 1;
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
                else if ("--fork".equals(option)) fork = integer(value, option, false);
                else if ("--forks".equals(option)) forks = integer(value, option, false);
                else if ("--warmup".equals(option)) warmup = integer(value, option, true);
                else if ("--iterations".equals(option)) iterations = integer(value, option, false);
                else throw new IllegalArgumentException("unknown option " + option);
            }
            if (output == null) throw new IllegalArgumentException("--output required");
            if (commit == null || commit.isEmpty()) {
                throw new IllegalArgumentException("--commit required");
            }
            if (fork > forks) throw new IllegalArgumentException("--fork exceeds --forks");
            return new Options(output, commit, fork, forks, warmup, iterations);
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
