package io.github.somaruntime.benchmarks.scheduling;

import io.github.somaruntime.benchmarks.BenchmarkResult;
import io.github.somaruntime.benchmarks.BenchmarkSupport;
import io.github.somaruntime.benchmarks.LongMeasurement;
import io.github.somaruntime.examples.scheduling.Job;
import io.github.somaruntime.examples.scheduling.JobTable;
import io.github.somaruntime.examples.scheduling.MachineState;
import io.github.somaruntime.examples.scheduling.MachineStateTable;
import io.github.somaruntime.examples.scheduling.ProcessingOption;
import io.github.somaruntime.examples.scheduling.ProcessingOptionTable;
import io.github.somaruntime.examples.scheduling.Soma;
import io.github.somaruntime.examples.scheduling.SomaGroup;
import io.github.somaruntime.examples.scheduling.schema.JobStatus;
import io.github.somaruntime.soma.LongGroupedLongResult;
import io.github.somaruntime.soma.BooleanGroupedLongEntry;
import io.github.somaruntime.soma.BooleanGroupedLongResult;
import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.TableMetadata;
import io.github.somaruntime.soma.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

/** Medium-shape scheduling benchmark and scenario correctness consumer. */
public final class SchedulingBenchmarkMain {
    private SchedulingBenchmarkMain() {}

    public static void main(String[] args) {
        int rows = BenchmarkSupport.rows(args);
        String implementation = BenchmarkSupport.implementation(args);
        Expected expected = expected(rows);
        if ("manual".equals(implementation)) runManual(rows, expected);
        else runSoma(rows, implementation, expected);
    }

    private static void runSoma(int rows, String implementation, Expected expected) {
        int parallelism = BenchmarkSupport.parallelism();
        ForkJoinPool executor = new ForkJoinPool(parallelism);
        try {
            long budget = Long.getLong("soma.benchmark.memoryBudgetBytes", 6L << 30);
            BenchmarkSupport.require(budget > 0L && budget <= (32L << 30),
                    "memory budget outside benchmark envelope");
            SomaCompression compression = "soma-off".equals(implementation)
                    ? SomaCompression.OFF : SomaCompression.AUTO;
            Soma.configure(SomaConfiguration.builder()
                    .memoryBudgetBytes(budget)
                    .parallelExecutor(executor)
                    .compression(compression)
                    .build());

            SomaGroup group = Soma.createGroup();
            JobTable jobs = group.jobTable();
            MachineStateTable machines = group.machineStateTable();
            ProcessingOptionTable options = group.processingOptionTable();
            jobs.reserve(expected.jobCount);
            machines.reserve(expected.machineCount);
            options.reserve(rows);

            long ingestStarted = System.nanoTime();
            for (int index = 0; index < expected.jobCount; index++) {
                jobs.add(new Job(index + 1L, JobStatus.PENDING,
                        index % 1_440, 2_000L + index % 10_000));
            }
            for (int index = 0; index < expected.machineCount; index++) {
                machines.add(new MachineState(index + 1L, index % 64,
                        index % 2_000, (index & 15) != 0));
            }
            for (int index = 0; index < rows; index++) {
                options.add(new ProcessingOption(
                        index + 1L,
                        index % expected.jobCount + 1L,
                        index % expected.machineCount + 1L,
                        5L + index % 120,
                        index % 11,
                        (index & 7) != 0));
            }
            long ingestNanos = System.nanoTime() - ingestStarted;
            BenchmarkSupport.require(options.size(), rows, "scheduling ingest size");

            LongMeasurement scan = BenchmarkSupport.measure(() -> options
                    .filter(options.enabled.eq(true))
                    .mapToLong(options.processingMinutes)
                    .sum());
            BenchmarkSupport.require(scan.value(), expected.scan, "scheduling scan");

            LongMeasurement parallelScan = BenchmarkSupport.measure(() -> options.parallel()
                    .filter(options.enabled.eq(true))
                    .mapToLong(options.processingMinutes)
                    .sum());
            BenchmarkSupport.require(parallelScan.value(), expected.scan,
                    "scheduling parallel scan");

            LongMeasurement key = BenchmarkSupport.measure(() -> {
                long value = 0L;
                for (int index = 0; index < 10_000; index++) {
                    long id = (index * 7_919L) % rows + 1L;
                    value += options.get(id).processingMinutes();
                }
                return value;
            });
            BenchmarkSupport.require(key.value(), expected.key, "scheduling Key lookup");

            LongMeasurement index = BenchmarkSupport.measure(() ->
                    options.byJobId(expected.selectedJob).count());
            BenchmarkSupport.require(index.value(), expected.indexCount,
                    "scheduling Index selection");

            LongMeasurement join = BenchmarkSupport.measure(() -> options.join(machines)
                    .on(options.machineId, machines.machineId)
                    .inner()
                    .filter(options.jobId.eq(expected.selectedJob))
                    .filter(machines.enabled.eq(true))
                    .count());
            BenchmarkSupport.require(join.value(), expected.joinCount, "scheduling Join");

            LongMeasurement top = BenchmarkSupport.measure(() -> options
                    .filter(options.jobId.eq(expected.selectedJob))
                    .top(1L, options.processingMinutes.asc().then(options.optionId.asc()))
                    .findFirst().get().optionId());
            BenchmarkSupport.require(top.value(), expected.bestOption, "scheduling top");

            LongMeasurement grouping = BenchmarkSupport.measure(() -> {
                LongGroupedLongResult grouped = options.groupBy(options.jobId).count();
                return grouped.size();
            });
            BenchmarkSupport.require(grouping.value(), expected.groupCount,
                    "scheduling GroupBy");

            ComposedMetrics composed = BenchmarkSupport.composedWorkload()
                    ? runComposed(options, machines, expected.composed)
                    : null;

            long updateKey = Math.max(1L, rows / 2L);
            long oldSetup = options.get(updateKey).setupMinutes();
            UpdateResult update = options.update(updateKey, editor ->
                    editor.setupMinutes(Math.addExact(editor.setupMinutes(), 1L)));
            BenchmarkSupport.require(update.matched(), 1L, "scheduling update matched");
            BenchmarkSupport.require(update.changed(), 1L, "scheduling update changed");
            BenchmarkSupport.require(options.get(updateKey).setupMinutes(), oldSetup + 1L,
                    "scheduling update payload");

            TableMetadata metadata = options._metadata();
            BenchmarkSupport.require(metadata.size(), rows, "scheduling metadata size");
            long sharedFingerprint = BenchmarkSupport.fingerprint(rows, scan.value(), key.value());
            long fingerprint = BenchmarkSupport.fingerprint(
                    sharedFingerprint,
                    index.value(), join.value(), top.value(), grouping.value(),
                    update.matched(), update.changed(), metadata.size());

            BenchmarkResult result = new BenchmarkResult(
                    "scheduling", "MEDIUM", implementation, rows)
                    .put("correctness", true)
                    .put("compression", compression.name())
                    .put("ingestNanos", ingestNanos)
                    .put("scan", scan)
                    .put("parallelScan", parallelScan)
                    .put("key10k", key)
                    .put("index", index)
                    .put("join", join)
                    .put("top", top)
                    .put("group", grouping)
                    .put("retainedBytes", group._metadata().retainedBytes())
                    .put("representationBytes", metadata.representationBytes())
                    .put("plainEquivalentBytes", metadata.plainEquivalentBytes())
                    .put("sharedFingerprint", sharedFingerprint)
                    .put("fingerprint", composed == null
                            ? fingerprint
                            : BenchmarkSupport.fingerprint(fingerprint, composed.fingerprint));
            if (composed != null) composed.appendTo(result);
            result.print();
        } finally {
            executor.shutdown();
        }
    }

    private static void runManual(int rows, Expected expected) {
        long started = System.nanoTime();
        List<ManualOption> list = new ArrayList<ManualOption>(rows);
        Map<Long, ManualOption> byId = new HashMap<Long, ManualOption>(rows * 4 / 3 + 1);
        long[] durations = new long[rows];
        boolean[] enabled = new boolean[rows];
        for (int index = 0; index < rows; index++) {
            ManualOption option = new ManualOption(index + 1L, 5L + index % 120);
            list.add(option);
            byId.put(option.id, option);
            durations[index] = option.duration;
            enabled[index] = (index & 7) != 0;
        }
        long ingestNanos = System.nanoTime() - started;

        LongMeasurement scan = BenchmarkSupport.measure(() -> {
            long value = 0L;
            for (int index = 0; index < rows; index++) {
                if (enabled[index]) value += durations[index];
            }
            return value;
        });
        BenchmarkSupport.require(scan.value(), expected.scan, "manual scheduling scan");

        LongMeasurement key = BenchmarkSupport.measure(() -> {
            long value = 0L;
            for (int index = 0; index < 10_000; index++) {
                value += byId.get((index * 7_919L) % rows + 1L).duration;
            }
            return value;
        });
        BenchmarkSupport.require(key.value(), expected.key, "manual scheduling Key lookup");
        BenchmarkSupport.require(list.size(), rows, "manual scheduling ingest size");

        long sharedFingerprint = BenchmarkSupport.fingerprint(rows, scan.value(), key.value());
        new BenchmarkResult("scheduling", "MEDIUM", "manual", rows)
                .put("correctness", true)
                .put("ingestNanos", ingestNanos)
                .put("scan", scan)
                .put("key10k", key)
                .put("sharedFingerprint", sharedFingerprint)
                .put("fingerprint", sharedFingerprint)
                .print();
    }

    private static Expected expected(int rows) {
        int jobCount = Math.max(1, rows / 4);
        int machineCount = Math.min(16_384, Math.max(1_024, rows / 100));
        long selectedJob = Math.min(42L, jobCount);
        long scan = 0L;
        long indexCount = 0L;
        long joinCount = 0L;
        long bestOption = -1L;
        long bestDuration = Long.MAX_VALUE;
        for (int index = 0; index < rows; index++) {
            long optionId = index + 1L;
            long jobId = index % jobCount + 1L;
            long machineId = index % machineCount + 1L;
            long duration = 5L + index % 120;
            if ((index & 7) != 0) scan += duration;
            if (jobId == selectedJob) {
                indexCount++;
                if ((((machineId - 1L) & 15L) != 0L)) joinCount++;
                if (duration < bestDuration
                        || (duration == bestDuration && optionId < bestOption)) {
                    bestDuration = duration;
                    bestOption = optionId;
                }
            }
        }
        long key = 0L;
        for (int index = 0; index < 10_000; index++) {
            long id = (index * 7_919L) % rows + 1L;
            key += 5L + (id - 1L) % 120L;
        }
        return new Expected(jobCount, machineCount, selectedJob, scan, key,
                indexCount, joinCount, bestOption, jobCount,
                BenchmarkSupport.composedWorkload()
                        ? composedExpected(rows, jobCount, machineCount) : null);
    }

    private static ComposedExpected composedExpected(
            int rows,
            int jobCount,
            int machineCount) {
        long selectiveSum = 0L;
        long broadJoinSum = 0L;
        long disabledCount = 0L;
        long enabledCount = 0L;
        long[] jobSums = new long[jobCount];
        List<ExpectedOption> indexed = new ArrayList<ExpectedOption>();
        long selectedMachine = Math.min(42L, machineCount);
        for (int index = 0; index < rows; index++) {
            long optionId = index + 1L;
            long jobId = index % jobCount + 1L;
            long machineId = index % machineCount + 1L;
            long processing = 5L + index % 120;
            boolean enabled = (index & 7) != 0;
            if (enabled) enabledCount++; else disabledCount++;
            if (enabled && processing >= 32L && processing <= 96L) {
                selectiveSum += processing;
            }
            jobSums[(int) jobId - 1] += processing;
            boolean machineEnabled = (((machineId - 1L) & 15L) != 0L);
            if (enabled && machineEnabled) {
                broadJoinSum += processing + (machineId - 1L) % 2_000L;
            }
            if (machineId == selectedMachine && enabled) {
                indexed.add(new ExpectedOption(optionId, processing));
            }
        }
        Collections.sort(indexed, new Comparator<ExpectedOption>() {
            @Override public int compare(ExpectedOption left, ExpectedOption right) {
                int duration = Long.compare(left.processing, right.processing);
                return duration != 0 ? duration : Long.compare(left.optionId, right.optionId);
            }
        });
        long indexTopFingerprint = 0xcbf29ce484222325L;
        int topSize = Math.min(128, indexed.size());
        for (int index = 0; index < topSize; index++) {
            ExpectedOption option = indexed.get(index);
            indexTopFingerprint = BenchmarkSupport.mix(indexTopFingerprint, option.optionId);
            indexTopFingerprint = BenchmarkSupport.mix(indexTopFingerprint, option.processing);
        }

        long highGroupFingerprint = 0xcbf29ce484222325L;
        for (int index = 0; index < jobSums.length; index++) {
            highGroupFingerprint = BenchmarkSupport.mix(highGroupFingerprint, index + 1L);
            highGroupFingerprint = BenchmarkSupport.mix(highGroupFingerprint, jobSums[index]);
        }
        long lowGroupFingerprint = BenchmarkSupport.fingerprint(
                2L, 0L, disabledCount, 1L, enabledCount);
        return new ComposedExpected(
                selectiveSum,
                indexTopFingerprint,
                lowGroupFingerprint,
                highGroupFingerprint,
                broadJoinSum,
                jobCount);
    }

    private static ComposedMetrics runComposed(
            ProcessingOptionTable options,
            MachineStateTable machines,
            ComposedExpected expected) {
        LongMeasurement selective = BenchmarkSupport.measure(() -> options
                .filter(options.enabled.eq(true))
                .filter(options.processingMinutes.between(32L, 96L))
                .mapToLong(options.processingMinutes)
                .sum());
        BenchmarkSupport.require(selective.value(), expected.selectiveSum,
                "scheduling composed selective pipeline");

        LongMeasurement selectiveParallel = BenchmarkSupport.measure(() -> options.parallel()
                .filter(options.enabled.eq(true))
                .filter(options.processingMinutes.between(32L, 96L))
                .mapToLong(options.processingMinutes)
                .sum());
        BenchmarkSupport.require(selectiveParallel.value(), expected.selectiveSum,
                "scheduling composed selective parallel pipeline");

        long selectedMachine = Math.min(42L, machines.size());
        LongMeasurement indexTop = BenchmarkSupport.measure(() -> {
            ProcessingOption[] values = options.byMachineId(selectedMachine)
                    .filter(options.enabled.eq(true))
                    .top(128L, options.processingMinutes.asc().then(options.optionId.asc()))
                    .toArray();
            long hash = 0xcbf29ce484222325L;
            for (ProcessingOption value : values) {
                hash = BenchmarkSupport.mix(hash, value.optionId());
                hash = BenchmarkSupport.mix(hash, value.processingMinutes());
            }
            return hash;
        });
        BenchmarkSupport.require(indexTop.value(), expected.indexTopFingerprint,
                "scheduling composed Index residual/top/materialization");

        LongMeasurement lowGroup = BenchmarkSupport.measure(() -> {
            BooleanGroupedLongResult grouped = options.groupBy(options.enabled).count();
            BooleanGroupedLongEntry[] entries = grouped.toArray();
            long hash = 0xcbf29ce484222325L;
            hash = BenchmarkSupport.mix(hash, grouped.size());
            for (BooleanGroupedLongEntry entry : entries) {
                hash = BenchmarkSupport.mix(hash, entry.key() ? 1L : 0L);
                hash = BenchmarkSupport.mix(hash, entry.value());
            }
            return hash;
        });
        BenchmarkSupport.require(lowGroup.value(), expected.lowGroupFingerprint,
                "scheduling composed low-cardinality GroupBy");

        LongMeasurement highGroup = BenchmarkSupport.measure(() -> {
            LongGroupedLongResult grouped = options.groupBy(options.jobId)
                    .sum(options.processingMinutes);
            final long[] hash = {0xcbf29ce484222325L};
            grouped.forEach((key, value) -> {
                hash[0] = BenchmarkSupport.mix(hash[0], key);
                hash[0] = BenchmarkSupport.mix(hash[0], value);
            });
            BenchmarkSupport.require(grouped.size(), expected.highGroupCount,
                    "scheduling composed high-cardinality GroupBy size");
            return hash[0];
        });
        BenchmarkSupport.require(highGroup.value(), expected.highGroupFingerprint,
                "scheduling composed high-cardinality GroupBy");

        LongMeasurement broadJoin = BenchmarkSupport.measure(() -> options.join(machines)
                .on(options.machineId, machines.machineId)
                .inner()
                .filter(options.enabled.eq(true))
                .filter(machines.enabled.eq(true))
                .mapToLong(pair -> pair.left().processingMinutes()
                        + pair.right().availableMinute())
                .sum());
        BenchmarkSupport.require(broadJoin.value(), expected.broadJoinSum,
                "scheduling composed broad Join");

        LongMeasurement broadJoinParallel = BenchmarkSupport.measure(() -> options.join(machines)
                .on(options.machineId, machines.machineId)
                .parallel()
                .inner()
                .filter(options.enabled.eq(true))
                .filter(machines.enabled.eq(true))
                .mapToLong(pair -> pair.left().processingMinutes()
                        + pair.right().availableMinute())
                .sum());
        BenchmarkSupport.require(broadJoinParallel.value(), expected.broadJoinSum,
                "scheduling composed broad parallel Join");

        return new ComposedMetrics(
                selective, selectiveParallel, indexTop, lowGroup, highGroup,
                broadJoin, broadJoinParallel);
    }

    private static final class Expected {
        final int jobCount;
        final int machineCount;
        final long selectedJob;
        final long scan;
        final long key;
        final long indexCount;
        final long joinCount;
        final long bestOption;
        final long groupCount;
        final ComposedExpected composed;

        Expected(
                int jobCount,
                int machineCount,
                long selectedJob,
                long scan,
                long key,
                long indexCount,
                long joinCount,
                long bestOption,
                long groupCount,
                ComposedExpected composed) {
            this.jobCount = jobCount;
            this.machineCount = machineCount;
            this.selectedJob = selectedJob;
            this.scan = scan;
            this.key = key;
            this.indexCount = indexCount;
            this.joinCount = joinCount;
            this.bestOption = bestOption;
            this.groupCount = groupCount;
            this.composed = composed;
        }
    }

    private static final class ComposedExpected {
        final long selectiveSum;
        final long indexTopFingerprint;
        final long lowGroupFingerprint;
        final long highGroupFingerprint;
        final long broadJoinSum;
        final long highGroupCount;

        ComposedExpected(
                long selectiveSum,
                long indexTopFingerprint,
                long lowGroupFingerprint,
                long highGroupFingerprint,
                long broadJoinSum,
                long highGroupCount) {
            this.selectiveSum = selectiveSum;
            this.indexTopFingerprint = indexTopFingerprint;
            this.lowGroupFingerprint = lowGroupFingerprint;
            this.highGroupFingerprint = highGroupFingerprint;
            this.broadJoinSum = broadJoinSum;
            this.highGroupCount = highGroupCount;
        }
    }

    private static final class ComposedMetrics {
        final LongMeasurement selective;
        final LongMeasurement selectiveParallel;
        final LongMeasurement indexTop;
        final LongMeasurement lowGroup;
        final LongMeasurement highGroup;
        final LongMeasurement broadJoin;
        final LongMeasurement broadJoinParallel;
        final long fingerprint;

        ComposedMetrics(
                LongMeasurement selective,
                LongMeasurement selectiveParallel,
                LongMeasurement indexTop,
                LongMeasurement lowGroup,
                LongMeasurement highGroup,
                LongMeasurement broadJoin,
                LongMeasurement broadJoinParallel) {
            this.selective = selective;
            this.selectiveParallel = selectiveParallel;
            this.indexTop = indexTop;
            this.lowGroup = lowGroup;
            this.highGroup = highGroup;
            this.broadJoin = broadJoin;
            this.broadJoinParallel = broadJoinParallel;
            this.fingerprint = BenchmarkSupport.fingerprint(
                    selective.value(), selectiveParallel.value(), indexTop.value(),
                    lowGroup.value(), highGroup.value(), broadJoin.value(),
                    broadJoinParallel.value());
        }

        void appendTo(BenchmarkResult result) {
            result.put("composedSelective", selective)
                    .put("composedSelectiveParallel", selectiveParallel)
                    .put("composedIndexTop", indexTop)
                    .put("composedLowGroup", lowGroup)
                    .put("composedHighGroup", highGroup)
                    .put("composedBroadJoin", broadJoin)
                    .put("composedBroadJoinParallel", broadJoinParallel)
                    .put("composedFingerprint", fingerprint);
        }
    }

    private static final class ExpectedOption {
        final long optionId;
        final long processing;

        ExpectedOption(long optionId, long processing) {
            this.optionId = optionId;
            this.processing = processing;
        }
    }

    private static final class ManualOption {
        final long id;
        final long duration;

        ManualOption(long id, long duration) {
            this.id = id;
            this.duration = duration;
        }
    }
}
