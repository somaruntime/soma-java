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
import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.TableMetadata;
import io.github.somaruntime.soma.UpdateResult;
import java.util.ArrayList;
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

            new BenchmarkResult("scheduling", "MEDIUM", implementation, rows)
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
                    .put("fingerprint", fingerprint)
                    .print();
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
                indexCount, joinCount, bestOption, jobCount);
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

        Expected(
                int jobCount,
                int machineCount,
                long selectedJob,
                long scan,
                long key,
                long indexCount,
                long joinCount,
                long bestOption,
                long groupCount) {
            this.jobCount = jobCount;
            this.machineCount = machineCount;
            this.selectedJob = selectedJob;
            this.scan = scan;
            this.key = key;
            this.indexCount = indexCount;
            this.joinCount = joinCount;
            this.bestOption = bestOption;
            this.groupCount = groupCount;
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
