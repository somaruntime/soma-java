package io.github.somaruntime.examples.scheduling.profile;

import io.github.somaruntime.soma.LongGroupedLongResult;
import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.TableMetadata;
import io.github.somaruntime.examples.scheduling.Job;
import io.github.somaruntime.examples.scheduling.JobTable;
import io.github.somaruntime.examples.scheduling.MachineState;
import io.github.somaruntime.examples.scheduling.MachineStateTable;
import io.github.somaruntime.examples.scheduling.ProcessingOption;
import io.github.somaruntime.examples.scheduling.ProcessingOptionTable;
import io.github.somaruntime.examples.scheduling.Soma;
import io.github.somaruntime.examples.scheduling.SomaGroup;
import io.github.somaruntime.examples.scheduling.schema.JobStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.function.LongSupplier;

/** Medium-shape million-row profile; not part of the published example artifact. */
public final class SchedulingProfileMain {
    private static volatile long blackhole;

    private SchedulingProfileMain() {}

    public static void main(String[] args) {
        int rows = rows(args);
        Baseline baseline = baseline(rows);
        blackhole ^= baseline.fingerprint;
        baseline = null;
        System.gc();

        ForkJoinPool executor = new ForkJoinPool(parallelism());
        try {
            Soma.configure(SomaConfiguration.builder()
                    .memoryBudgetBytes(6L << 30)
                    .parallelExecutor(executor)
                    .compression(SomaCompression.AUTO)
                    .build());
            SomaGroup group = Soma.createGroup();
            JobTable jobs = group.jobTable();
            MachineStateTable machines = group.machineStateTable();
            ProcessingOptionTable options = group.processingOptionTable();
            int jobCount = Math.max(1, rows / 4);
            int machineCount = Math.min(16_384, Math.max(1_024, rows / 100));
            jobs.reserve(jobCount);
            machines.reserve(machineCount);
            options.reserve(rows);

            long ingestStarted = System.nanoTime();
            for (int index = 0; index < jobCount; index++) {
                jobs.add(new Job(index + 1L, JobStatus.PENDING,
                        index % 1_440, 2_000L + index % 10_000));
            }
            for (int index = 0; index < machineCount; index++) {
                machines.add(new MachineState(index + 1L, index % 64,
                        index % 2_000, (index & 15) != 0));
            }
            for (int index = 0; index < rows; index++) {
                options.add(new ProcessingOption(
                        index + 1L,
                        index % jobCount + 1L,
                        index % machineCount + 1L,
                        5L + index % 120,
                        index % 11,
                        (index & 7) != 0));
            }
            long ingestNanos = System.nanoTime() - ingestStarted;

            LongSupplier scan = () -> options
                    .filter(options.enabled.eq(true))
                    .mapToLong(options.processingMinutes)
                    .sum();
            long somaScanNanos = median(scan, 5);
            long scanFingerprint = scan.getAsLong();
            LongSupplier parallelScan = () -> options.parallel()
                    .filter(options.enabled.eq(true))
                    .mapToLong(options.processingMinutes)
                    .sum();
            long parallelScanNanos = median(parallelScan, 5);
            long parallelFingerprint = parallelScan.getAsLong();
            require(scanFingerprint == parallelFingerprint,
                    "sequential/parallel scheduling scan");

            long keyStarted = System.nanoTime();
            long keyFingerprint = 0L;
            for (int index = 0; index < 10_000; index++) {
                long id = (index * 7_919L) % rows + 1L;
                keyFingerprint += options.get(id).processingMinutes();
            }
            long keyNanos = System.nanoTime() - keyStarted;

            long selectedJob = Math.min(42L, jobCount);
            long indexStarted = System.nanoTime();
            long indexCount = options.byJobId(selectedJob).count();
            long indexNanos = System.nanoTime() - indexStarted;
            long joinStarted = System.nanoTime();
            long joinCount = options.join(machines)
                    .on(options.machineId, machines.machineId)
                    .inner()
                    .filter(options.jobId.eq(selectedJob))
                    .filter(machines.enabled.eq(true))
                    .count();
            long joinNanos = System.nanoTime() - joinStarted;
            long topStarted = System.nanoTime();
            long bestOption = options.filter(options.jobId.eq(selectedJob))
                    .top(1L, options.processingMinutes.asc()
                            .then(options.optionId.asc()))
                    .findFirst().get().optionId();
            long topNanos = System.nanoTime() - topStarted;
            long groupStarted = System.nanoTime();
            LongGroupedLongResult grouped = options.groupBy(options.jobId).count();
            long groupCount = grouped.size();
            long groupNanos = System.nanoTime() - groupStarted;
            options.update(Math.max(1L, rows / 2L), editor ->
                    editor.setupMinutes(Math.addExact(editor.setupMinutes(), 1L)));

            TableMetadata metadata = options._metadata();
            long fingerprint = scanFingerprint ^ keyFingerprint ^ indexCount
                    ^ joinCount ^ bestOption ^ groupCount ^ metadata.size();
            blackhole ^= fingerprint;
            System.out.println("PROFILE scenario=scheduling model=MEDIUM rows=" + rows
                    + " baselineIngestMs=" + ms(baselineIngestNanos)
                    + " baselineScanMs=" + ms(baselineScanNanos)
                    + " baselineKeyMs=" + ms(baselineKeyNanos)
                    + " somaIngestMs=" + ms(ingestNanos)
                    + " somaScanMs=" + ms(somaScanNanos)
                    + " parallelScanMs=" + ms(parallelScanNanos)
                    + " somaKeyMs=" + ms(keyNanos)
                    + " indexMs=" + ms(indexNanos)
                    + " joinMs=" + ms(joinNanos)
                    + " topMs=" + ms(topNanos)
                    + " groupMs=" + ms(groupNanos)
                    + " retainedBytes=" + group._metadata().retainedBytes()
                    + " representationBytes=" + metadata.representationBytes()
                    + " plainEquivalentBytes=" + metadata.plainEquivalentBytes()
                    + " fingerprint=" + fingerprint);
        } finally {
            executor.shutdown();
        }
    }

    private static long baselineIngestNanos;
    private static long baselineScanNanos;
    private static long baselineKeyNanos;

    private static Baseline baseline(int rows) {
        long started = System.nanoTime();
        List<BaselineOption> list = new ArrayList<BaselineOption>(rows);
        Map<Long, BaselineOption> byId = new HashMap<Long, BaselineOption>(rows * 4 / 3 + 1);
        long[] durations = new long[rows];
        boolean[] enabled = new boolean[rows];
        for (int index = 0; index < rows; index++) {
            BaselineOption option = new BaselineOption(index + 1L, 5L + index % 120);
            list.add(option);
            byId.put(option.id, option);
            durations[index] = option.duration;
            enabled[index] = (index & 7) != 0;
        }
        baselineIngestNanos = System.nanoTime() - started;
        started = System.nanoTime();
        long scan = 0L;
        for (int index = 0; index < rows; index++) if (enabled[index]) scan += durations[index];
        baselineScanNanos = System.nanoTime() - started;
        started = System.nanoTime();
        long key = 0L;
        for (int index = 0; index < 10_000; index++) {
            key += byId.get((index * 7_919L) % rows + 1L).duration;
        }
        baselineKeyNanos = System.nanoTime() - started;
        return new Baseline(scan ^ key ^ list.size());
    }

    private static long median(LongSupplier supplier, int samples) {
        supplier.getAsLong();
        supplier.getAsLong();
        long[] values = new long[samples];
        for (int index = 0; index < samples; index++) {
            long started = System.nanoTime();
            blackhole ^= supplier.getAsLong();
            values[index] = System.nanoTime() - started;
        }
        Arrays.sort(values);
        return values[values.length / 2];
    }

    private static int rows(String[] args) {
        int value = args.length == 0 ? 1_000_000 : Integer.parseInt(args[0]);
        if (value < 10_000 || value > 2_000_000) throw new IllegalArgumentException("rows out of profile range");
        return value;
    }

    private static int parallelism() {
        int value = Integer.getInteger("soma.profile.parallelism", 8);
        if (value < 1 || value > 16) throw new IllegalArgumentException("parallelism out of range");
        return value;
    }

    private static long ms(long nanos) { return nanos / 1_000_000L; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Baseline {
        final long fingerprint;
        Baseline(long fingerprint) { this.fingerprint = fingerprint; }
    }

    private static final class BaselineOption {
        final long id;
        final long duration;
        BaselineOption(long id, long duration) { this.id = id; this.duration = duration; }
    }
}
