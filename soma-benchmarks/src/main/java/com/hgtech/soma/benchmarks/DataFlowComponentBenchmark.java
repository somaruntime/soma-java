package com.hgtech.soma.benchmarks;

import com.hgtech.soma.benchmarks.schema.EntityKind;
import com.hgtech.soma.benchmarks.schema.VariableKind;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactBatch;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactCursor;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactDataFlow;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactScan;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactTable;
import com.hgtech.soma.dataflow.DataFlowContext;
import com.hgtech.soma.dataflow.DataFlowDefinition;
import com.hgtech.soma.dataflow.DataFlowInvocation;
import com.hgtech.soma.dataflow.DataFlowResults;
import com.hgtech.soma.dataflow.DataFlowTemplate;
import com.hgtech.soma.dataflow.ExecutionBudget;
import com.hgtech.soma.dataflow.ExecutionPolicy;
import com.hgtech.soma.dataflow.GroupedLongResult;
import com.hgtech.soma.dataflow.KeyExpression;
import com.hgtech.soma.dataflow.LongColumnResult;
import com.hgtech.soma.dataflow.LongScalarResult;
import com.hgtech.soma.dataflow.OutputSlot;
import com.hgtech.soma.dataflow.PartialWindowPolicy;
import com.hgtech.soma.dataflow.StatsMode;
import com.hgtech.soma.runtime.UpdateResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Bounded component evidence for DataFlow authoring, invocation and execution.
 *
 * <p>Definitions and Tables are prepared outside execution lanes. Every lane
 * owns a semantic checksum; timing is never accepted without the matching
 * result contract.</p>
 */
public final class DataFlowComponentBenchmark {
    static final String SCHEMA_VERSION = "soma-dataflow-component-v1";
    static final String ARTIFACT_VERSION =
            "soma-java-dataflow-component-v1";
    private static final int SMALL_ROWS = 4096;
    private static final int LARGE_ROWS = 65536;
    private static final int GROUPS = 256;
    private static volatile long SINK;

    private DataFlowComponentBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        BenchmarkEnvironment environment = new BenchmarkEnvironment();
        Workload small = new Workload(SMALL_ROWS);
        Workload large = new Workload(LARGE_ROWS);
        DataFlowContext sequential = DataFlowContext.sequential();
        DataFlowContext parallel = DataFlowContext.managedParallel(
                4,
                ExecutionPolicy.adaptiveParallel()
                        .withMinimumParallelCardinality(1024),
                ExecutionBudget.defaults());
        List<LinkedHashMap<String, Object>> records =
                new ArrayList<LinkedHashMap<String, Object>>();
        try {
            warmExecutor(large, parallel);
            runLanes(
                    options,
                    environment,
                    small,
                    large,
                    sequential,
                    parallel,
                    records);
        } finally {
            parallel.close();
            sequential.close();
            large.close();
            small.close();
        }
        write(options.output, records);
        System.out.println(
                "dataflow-component-artifact: "
                        + options.output.getAbsolutePath());
    }

    private static void runLanes(
            Options options,
            BenchmarkEnvironment environment,
            final Workload small,
            final Workload large,
            final DataFlowContext sequential,
            final DataFlowContext parallel,
            List<LinkedHashMap<String, Object>> records) {
        measure(
                options,
                environment,
                records,
                new Lane(
                        "authoring.compile",
                        "authoring-compile",
                        "sequential",
                        0,
                        1,
                        1,
                        false) {
                    @Override
                    int warmupIterations(Options current) {
                        return Math.max(
                                current.warmupIterations,
                                2048);
                    }

                    @Override
                    long run() {
                        NumericFactDataFlow.Source source =
                                NumericFactDataFlow.source("authoring");
                        return source.candidates()
                                .filter(source.columns().factIndex()
                                        .lessThan(LARGE_ROWS / 2L))
                                .project(source.columns().entityId()
                                        .multipliedBy(3L)
                                        .plus(source.columns().factIndex()))
                                .sum()
                                .compile()
                                .identity()
                                .hashCode();
                    }
                });
        measure(
                options,
                environment,
                records,
                large.directCountLane());
        measure(
                options,
                environment,
                records,
                large.countLane(sequential, "sequential"));
        measure(
                options,
                environment,
                records,
                large.countLane(parallel, "adaptive-parallel"));
        measure(
                options,
                environment,
                records,
                small.sumLane(sequential, "sequential"));
        measure(
                options,
                environment,
                records,
                small.sumLane(parallel, "adaptive-parallel"));
        measure(
                options,
                environment,
                records,
                large.sumLane(sequential, "sequential"));
        measure(
                options,
                environment,
                records,
                large.sumLane(parallel, "adaptive-parallel"));
        measure(
                options,
                environment,
                records,
                large.projectionLane(sequential, "sequential"));
        measure(
                options,
                environment,
                records,
                large.projectionLane(parallel, "adaptive-parallel"));
        measure(
                options,
                environment,
                records,
                large.groupLane(sequential));
        measure(
                options,
                environment,
                records,
                large.joinLane(sequential));
        measure(
                options,
                environment,
                records,
                large.windowLane(sequential));
        measure(
                options,
                environment,
                records,
                large.graphLane(parallel));
        measure(
                options,
                environment,
                records,
                large.effectLane(sequential));
    }

    private static void warmExecutor(
            Workload workload, DataFlowContext parallel) {
        for (int iteration = 0; iteration < 4; iteration++) {
            SINK ^= workload.executeCount(parallel);
        }
    }

    private static void measure(
            Options options,
            BenchmarkEnvironment environment,
            List<LinkedHashMap<String, Object>> records,
            Lane lane) {
        int warmupIterations =
                lane.warmupIterations(options);
        for (int iteration = 0;
             iteration < warmupIterations;
             iteration++) {
            SINK = mix(SINK, lane.run());
        }

        long[] invocationNanos =
                new long[options.measurementIterations];
        long allThreadsBefore = lane.allThreads
                ? JvmRuntimeMetrics.allLiveThreadAllocatedBytes() : -1L;
        JvmRuntimeMetrics.Snapshot before = JvmRuntimeMetrics.snapshot();
        long started = System.nanoTime();
        long checksum = 0L;
        for (int iteration = 0;
             iteration < options.measurementIterations;
             iteration++) {
            long invocationStarted = System.nanoTime();
            long value = lane.run();
            invocationNanos[iteration] =
                    System.nanoTime() - invocationStarted;
            checksum = mix(checksum, value);
        }
        long elapsed = System.nanoTime() - started;
        JvmRuntimeMetrics.Delta delta =
                JvmRuntimeMetrics.snapshot().since(before);
        Latency latency = Latency.from(invocationNanos);
        long allocatedBytes;
        String allocationMethod;
        if (lane.allThreads) {
            long after = JvmRuntimeMetrics.allLiveThreadAllocatedBytes();
            allocatedBytes = allThreadsBefore < 0L || after < allThreadsBefore
                    ? -1L : after - allThreadsBefore;
            allocationMethod =
                    JvmRuntimeMetrics.allLiveThreadAllocationMethod();
        } else {
            allocatedBytes = delta.allocatedBytes;
            allocationMethod = JvmRuntimeMetrics.allocationMethod();
        }
        require(allocatedBytes >= 0L,
                "allocation observation required for " + lane.name);
        SINK = checksum;

        LinkedHashMap<String, Object> record = common(
                options, environment, lane);
        record.put(
                "warmupIterations",
                Integer.valueOf(warmupIterations));
        record.put(
                "measurementIterations",
                Integer.valueOf(options.measurementIterations));
        record.put("rows", Integer.valueOf(lane.rows));
        record.put(
                "logicalOutputElements",
                Integer.valueOf(lane.logicalOutputElements));
        record.put("workers", Integer.valueOf(lane.workers));
        record.put("statsMode", StatsMode.BASIC.name());
        record.put("allocationMethod", allocationMethod);
        record.put("allocatedBytes", Long.valueOf(allocatedBytes));
        record.put(
                "allocatedBytesPerOperation",
                Double.valueOf(
                        (double) allocatedBytes
                                / options.measurementIterations));
        record.put("elapsedNanos", Long.valueOf(elapsed));
        record.put(
                "nanosPerOperation",
                Double.valueOf(
                        (double) elapsed
                                / options.measurementIterations));
        record.put("latencyNanos", latency.toMap());
        record.put("gcStats", gcStats(delta));
        record.put("checksum", Long.valueOf(checksum));
        record.put("observationKind", "measured");
        record.put(
                "knownLimitations",
                BenchmarkModel.limitations(
                        "local Zulu JDK 8 diagnostic component evidence",
                        lane.allThreads
                                ? "allocation covers live Java threads; dead-thread allocation is not recoverable"
                                : "allocation covers the benchmark thread",
                        "per-invocation latency includes System.nanoTime sampling overhead",
                        "claimAllowed=false; no cross-machine or release claim"));
        record.put("claimAllowed", Boolean.FALSE);
        records.add(record);
    }

    private static LinkedHashMap<String, Object> common(
            Options options,
            BenchmarkEnvironment environment,
            Lane lane) {
        LinkedHashMap<String, Object> record =
                new LinkedHashMap<String, Object>();
        record.put("schemaVersion", SCHEMA_VERSION);
        record.put("artifactVersion", ARTIFACT_VERSION);
        record.put("kind", "component-operation");
        record.put("lane", lane.name);
        record.put("phase", lane.phase);
        record.put("strategy", lane.strategy);
        record.put("status", "passed");
        record.put("fork", Integer.valueOf(options.fork));
        record.put(
                "configuredForks",
                Integer.valueOf(options.forks));
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
        record.put(
                "maxHeapBytes",
                Long.valueOf(environment.memory));
        return record;
    }

    private static LinkedHashMap<String, Object> gcStats(
            JvmRuntimeMetrics.Delta delta) {
        return BenchmarkModel.object(
                "collectors", JvmRuntimeMetrics.collectorNamesValue(),
                "youngCount", Long.valueOf(delta.youngGcCount),
                "youngTimeMillis",
                Long.valueOf(delta.youngGcTimeMillis),
                "fullCount", Long.valueOf(delta.fullGcCount),
                "fullTimeMillis",
                Long.valueOf(delta.fullGcTimeMillis),
                "unknownCount", Long.valueOf(delta.unknownGcCount),
                "unknownTimeMillis",
                Long.valueOf(delta.unknownGcTimeMillis));
    }

    private static void write(
            File output,
            List<LinkedHashMap<String, Object>> records)
            throws IOException {
        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null
                && !parent.isDirectory()
                && !parent.mkdirs()) {
            throw new IOException(
                    "cannot create output directory: " + parent);
        }
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(output),
                        StandardCharsets.UTF_8));
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
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private abstract static class Lane {
        final String name;
        final String phase;
        final String strategy;
        final int rows;
        final int logicalOutputElements;
        final int workers;
        final boolean allThreads;

        Lane(
                String name,
                String phase,
                String strategy,
                int rows,
                int logicalOutputElements,
                int workers,
                boolean allThreads) {
            this.name = name;
            this.phase = phase;
            this.strategy = strategy;
            this.rows = rows;
            this.logicalOutputElements = logicalOutputElements;
            this.workers = workers;
            this.allThreads = allThreads;
        }

        int warmupIterations(Options options) {
            return options.warmupIterations;
        }

        abstract long run();
    }

    private static final class Latency {
        final int samples;
        final long p50;
        final long p90;
        final long p99;
        final long maximum;

        private Latency(
                int samples,
                long p50,
                long p90,
                long p99,
                long maximum) {
            this.samples = samples;
            this.p50 = p50;
            this.p90 = p90;
            this.p99 = p99;
            this.maximum = maximum;
        }

        static Latency from(long[] values) {
            if (values.length == 0) {
                throw new IllegalArgumentException(
                        "latency samples required");
            }
            long[] sorted = Arrays.copyOf(values, values.length);
            Arrays.sort(sorted);
            return new Latency(
                    sorted.length,
                    percentile(sorted, 0.50d),
                    percentile(sorted, 0.90d),
                    percentile(sorted, 0.99d),
                    sorted[sorted.length - 1]);
        }

        LinkedHashMap<String, Object> toMap() {
            return BenchmarkModel.object(
                    "samples", Integer.valueOf(samples),
                    "p50", Long.valueOf(p50),
                    "p90", Long.valueOf(p90),
                    "p99", Long.valueOf(p99),
                    "maximum", Long.valueOf(maximum),
                    "method",
                    "per-invocation-system-nanotime-nearest-rank");
        }

        private static long percentile(
                long[] sorted, double percentile) {
            int rank = (int) Math.ceil(
                    percentile * sorted.length);
            return sorted[Math.max(0, rank - 1)];
        }
    }

    private static final class FactIndexBefore
            implements NumericFactScan.Predicate {
        private final int exclusiveUpperBound;

        FactIndexBefore(int exclusiveUpperBound) {
            this.exclusiveUpperBound = exclusiveUpperBound;
        }

        @Override
        public boolean test(NumericFactCursor fact) {
            return fact.factIndex() < exclusiveUpperBound;
        }
    }

    private static final class Workload implements AutoCloseable {
        private final int rows;
        private final int selectedRows;
        private final NumericFactTable table;
        private final NumericFactDataFlow.Source source;
        private final NumericFactDataFlow.Source left;
        private final NumericFactDataFlow.Source right;
        private final DataFlowTemplate<LongScalarResult> count;
        private final DataFlowTemplate<LongScalarResult> sum;
        private final DataFlowTemplate<LongColumnResult> projection;
        private final DataFlowTemplate<GroupedLongResult> group;
        private final DataFlowTemplate<LongScalarResult> join;
        private final DataFlowTemplate<LongColumnResult> window;
        private final GraphTemplate graph;
        private final DataFlowTemplate<UpdateResult> effect;
        private final NumericFactScan.Predicate directPredicate;
        private final long expectedSum;

        Workload(int rows) {
            this.rows = rows;
            selectedRows = rows / 2;
            table = NumericFactTable.create();
            table.addBatch(batch(rows));
            source = NumericFactDataFlow.source("facts-" + rows);
            left = NumericFactDataFlow.source(0, "left-" + rows);
            right = NumericFactDataFlow.source(1, "right-" + rows);
            count = source.candidates()
                    .filter(source.columns().factIndex()
                            .lessThan(selectedRows))
                    .count()
                    .compile();
            sum = source.candidates()
                    .filter(source.columns().factIndex()
                            .lessThan(selectedRows))
                    .project(source.columns().entityId())
                    .sum()
                    .compile();
            projection = source.candidates()
                    .filter(source.columns().factIndex()
                            .lessThan(selectedRows))
                    .project(source.columns().entityId()
                            .multipliedBy(3L)
                            .plus(source.columns().factIndex()))
                    .toColumn()
                    .compile();
            group = source.candidates()
                    .groupBy(KeyExpression.of(
                            source.columns().entityId()))
                    .sum(source.columns().factIndex())
                    .compile();
            join = left.candidates()
                    .innerJoin(right.candidates())
                    .on(
                            left.columns().factIndex(),
                            right.columns().factIndex())
                    .count()
                    .compile();
            window = source.candidates()
                    .windowByCount(
                            32,
                            16,
                            PartialWindowPolicy.INCLUDE_PARTIAL)
                    .sum(source.columns().entityId())
                    .compile();
            graph = graph(source, selectedRows);
            effect = source.update(
                    source.candidates().limit(1L),
                    new NumericFactScan.Updater() {
                        @Override
                        public void update(
                                com.hgtech.soma.benchmarks.schema.generated
                                        .NumericFactUpdateCursor fact) {
                            fact.setScale(fact.scale() + 1.0d);
                        }
                    })
                    .compile();
            directPredicate = new FactIndexBefore(selectedRows);
            expectedSum = ((long) selectedRows / GROUPS)
                    * ((GROUPS - 1L) * GROUPS / 2L);
        }

        Lane directCountLane() {
            return new Lane(
                    "candidate.direct-filter-count.large",
                    "execute",
                    "direct-candidate-scan",
                    rows,
                    1,
                    1,
                    false) {
                @Override
                long run() {
                    long value = table.filter(directPredicate).count();
                    require(value == selectedRows,
                            "direct count result");
                    return value;
                }
            };
        }

        Lane countLane(
                final DataFlowContext context,
                final String strategy) {
            return new Lane(
                    "candidate.filter-count."
                            + sizeName() + "." + strategy,
                    "bind-execute",
                    strategy,
                    rows,
                    1,
                    workers(strategy),
                    parallel(strategy)) {
                @Override
                long run() {
                    return executeCount(context);
                }
            };
        }

        Lane sumLane(
                final DataFlowContext context,
                final String strategy) {
            return new Lane(
                    "reduction.sum."
                            + sizeName() + "." + strategy,
                    "bind-execute",
                    strategy,
                    rows,
                    1,
                    workers(strategy),
                    parallel(strategy)) {
                @Override
                long run() {
                    long value = execute(sum, context).value();
                    require(value == expectedSum,
                            "sum result");
                    return value;
                }
            };
        }

        Lane projectionLane(
                final DataFlowContext context,
                final String strategy) {
            return new Lane(
                    "projection.long.large." + strategy,
                    "bind-execute",
                    strategy,
                    rows,
                    selectedRows,
                    workers(strategy),
                    parallel(strategy)) {
                @Override
                long run() {
                    LongColumnResult result =
                            execute(projection, context);
                    require(result.size() == selectedRows,
                            "projection size");
                    long expectedLast =
                            ((selectedRows - 1L) & 255L) * 3L
                                    + selectedRows - 1L;
                    require(result.valueAt(0) == 0L
                                    && result.valueAt(
                                            selectedRows - 1)
                                            == expectedLast,
                            "projection values");
                    return mix(
                            result.size(),
                            result.valueAt(selectedRows / 2));
                }
            };
        }

        Lane groupLane(final DataFlowContext context) {
            return new Lane(
                    "rearrange.group-sum.large.sequential",
                    "bind-execute",
                    "sequential",
                    rows,
                    GROUPS,
                    1,
                    false) {
                @Override
                long run() {
                    GroupedLongResult result =
                            execute(group, context);
                    require(result.size() == GROUPS,
                            "group count");
                    long checksum = 0L;
                    long total = 0L;
                    for (int index = 0;
                         index < result.size();
                         index++) {
                        require(
                                result.representativeIndexAt(index)
                                        == index,
                                "group first-key order");
                        total += result.valueAt(index);
                        checksum = mix(
                                checksum,
                                result.valueAt(index));
                    }
                    require(total
                                    == (long) rows
                                            * (rows - 1L) / 2L,
                            "group sum total");
                    return checksum;
                }
            };
        }

        Lane joinLane(final DataFlowContext context) {
            return new Lane(
                    "rearrange.inner-join-count.large.sequential",
                    "bind-execute",
                    "sequential",
                    rows,
                    1,
                    1,
                    false) {
                @Override
                long run() {
                    LongScalarResult result = join.newInvocation(context)
                            .bind(
                                    left,
                                    NumericFactDataFlow.bind(table))
                            .bind(
                                    right,
                                    NumericFactDataFlow.bind(table))
                            .execute();
                    require(result.value() == rows,
                            "join count");
                    return result.value();
                }
            };
        }

        Lane windowLane(final DataFlowContext context) {
            return new Lane(
                    "rearrange.window-sum.large.sequential",
                    "bind-execute",
                    "sequential",
                    rows,
                    rows / 16,
                    1,
                    false) {
                @Override
                long run() {
                    LongColumnResult result =
                            execute(window, context);
                    require(result.size() == rows / 16,
                            "window count");
                    long checksum = result.size();
                    for (int index = 0;
                         index < result.size();
                         index++) {
                        checksum = mix(
                                checksum,
                                result.valueAt(index));
                    }
                    return checksum;
                }
            };
        }

        Lane graphLane(final DataFlowContext context) {
            return new Lane(
                    "graph.shared-projection.large.adaptive-parallel",
                    "bind-execute",
                    "adaptive-parallel",
                    rows,
                    selectedRows + 1,
                    4,
                    true) {
                @Override
                long run() {
                    DataFlowResults results =
                            execute(graph.template, context);
                    LongColumnResult first =
                            results.get(graph.first);
                    LongColumnResult second =
                            results.get(graph.second);
                    LongScalarResult cardinality =
                            results.get(graph.count);
                    require(first == second,
                            "shared graph output identity");
                    require(first.size() == selectedRows
                                    && cardinality.value()
                                            == selectedRows,
                            "shared graph result");
                    return mix(
                            cardinality.value(),
                            first.valueAt(selectedRows - 1));
                }
            };
        }

        Lane effectLane(final DataFlowContext context) {
            return new Lane(
                    "effect.update-one.large.sequential",
                    "bind-execute-effect",
                    "sequential",
                    rows,
                    1,
                    1,
                    false) {
                @Override
                long run() {
                    UpdateResult result =
                            execute(effect, context);
                    require(result.matched() == 1L
                                    && result.changed() == 1L,
                            "effect result");
                    return result.matched() * 31L
                            + result.changed();
                }
            };
        }

        long executeCount(DataFlowContext context) {
            long value = execute(count, context).value();
            require(value == selectedRows,
                    "candidate count result");
            return value;
        }

        private <R> R execute(
                DataFlowTemplate<R> template,
                DataFlowContext context) {
            DataFlowInvocation<R> invocation =
                    template.newInvocation(context)
                            .bind(
                                    source,
                                    NumericFactDataFlow.bind(table));
            return invocation.execute();
        }

        private String sizeName() {
            return rows == SMALL_ROWS ? "small" : "large";
        }

        private static boolean parallel(String strategy) {
            return "adaptive-parallel".equals(strategy);
        }

        private static int workers(String strategy) {
            return parallel(strategy) ? 4 : 1;
        }

        @Override
        public void close() {
            table.release();
        }

        private static GraphTemplate graph(
                NumericFactDataFlow.Source source,
                int selectedRows) {
            DataFlowDefinition<LongColumnResult> shared =
                    source.candidates()
                            .filter(source.columns().factIndex()
                                    .lessThan(selectedRows))
                            .project(source.columns().entityId()
                                    .multipliedBy(3L)
                                    .plus(source.columns().factIndex()))
                            .toColumn();
            DataFlowDefinition<LongScalarResult> count =
                    source.candidates()
                            .filter(source.columns().factIndex()
                                    .lessThan(selectedRows))
                            .count();
            DataFlowDefinition.Builder builder =
                    DataFlowDefinition.builder();
            OutputSlot<LongColumnResult> first =
                    builder.output("first", shared);
            OutputSlot<LongColumnResult> second =
                    builder.output("second", shared);
            OutputSlot<LongScalarResult> cardinality =
                    builder.output("count", count);
            return new GraphTemplate(
                    builder.build().compile(),
                    first,
                    second,
                    cardinality);
        }
    }

    private static NumericFactBatch batch(int rows) {
        NumericFactBatch batch = new NumericFactBatch(rows);
        for (int index = 0; index < rows; index++) {
            batch.addValues(
                    index,
                    (index & 1) == 0
                            ? EntityKind.PRIMARY
                            : EntityKind.SECONDARY,
                    index & (GROUPS - 1),
                    (index & 1) == 0
                            ? VariableKind.VALUE
                            : VariableKind.RATE,
                    index,
                    index * 0.5d,
                    1.0d);
        }
        return batch;
    }

    private static final class GraphTemplate {
        final DataFlowTemplate<DataFlowResults> template;
        final OutputSlot<LongColumnResult> first;
        final OutputSlot<LongColumnResult> second;
        final OutputSlot<LongScalarResult> count;

        GraphTemplate(
                DataFlowTemplate<DataFlowResults> template,
                OutputSlot<LongColumnResult> first,
                OutputSlot<LongColumnResult> second,
                OutputSlot<LongScalarResult> count) {
            this.template = template;
            this.first = first;
            this.second = second;
            this.count = count;
        }
    }

    private static final class Options {
        final File output;
        final String commit;
        final int fork;
        final int forks;
        final int warmupIterations;
        final int measurementIterations;

        Options(
                File output,
                String commit,
                int fork,
                int forks,
                int warmupIterations,
                int measurementIterations) {
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
            int warmup = 16;
            int iterations = 32;
            for (int index = 0; index < args.length; index += 2) {
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException(
                            "missing value for " + args[index]);
                }
                String option = args[index];
                String value = args[index + 1];
                if ("--output".equals(option)) {
                    output = new File(value);
                } else if ("--commit".equals(option)) {
                    commit = value;
                } else if ("--fork".equals(option)) {
                    fork = positive(value, option, 16);
                } else if ("--forks".equals(option)) {
                    forks = positive(value, option, 16);
                } else if ("--warmup".equals(option)) {
                    warmup = nonNegative(value, option, 10000);
                } else if ("--iterations".equals(option)) {
                    iterations = positive(value, option, 10000);
                } else {
                    throw new IllegalArgumentException(
                            "unknown option " + option);
                }
            }
            if (output == null) {
                throw new IllegalArgumentException("--output required");
            }
            if (commit == null || commit.isEmpty()) {
                throw new IllegalArgumentException("--commit required");
            }
            if (fork > forks) {
                throw new IllegalArgumentException(
                        "--fork exceeds --forks");
            }
            return new Options(
                    output,
                    commit,
                    fork,
                    forks,
                    warmup,
                    iterations);
        }

        private static int positive(
                String value, String option, int maximum) {
            int parsed = parse(value, option);
            if (parsed <= 0 || parsed > maximum) {
                throw new IllegalArgumentException(
                        "invalid " + option);
            }
            return parsed;
        }

        private static int nonNegative(
                String value, String option, int maximum) {
            int parsed = parse(value, option);
            if (parsed < 0 || parsed > maximum) {
                throw new IllegalArgumentException(
                        "invalid " + option);
            }
            return parsed;
        }

        private static int parse(String value, String option) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException(
                        "invalid " + option,
                        failure);
            }
        }
    }
}
