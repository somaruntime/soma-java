package io.github.somaruntime.examples.realtimedispatch.profile;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.TableMetadata;
import io.github.somaruntime.examples.realtimedispatch.EligibleMachine;
import io.github.somaruntime.examples.realtimedispatch.EligibleMachineTable;
import io.github.somaruntime.examples.realtimedispatch.MachineRuntime;
import io.github.somaruntime.examples.realtimedispatch.MachineRuntimeTable;
import io.github.somaruntime.examples.realtimedispatch.PendingJob;
import io.github.somaruntime.examples.realtimedispatch.PendingJobTable;
import io.github.somaruntime.examples.realtimedispatch.Soma;
import io.github.somaruntime.examples.realtimedispatch.SomaGroup;
import io.github.somaruntime.examples.realtimedispatch.domain.DispatchPayload;
import io.github.somaruntime.examples.realtimedispatch.schema.PendingStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.function.LongSupplier;

/** Reference-mixed million-row profile; not part of the published example artifact. */
public final class RealTimeDispatchProfileMain {
    private static volatile long blackhole;

    private RealTimeDispatchProfileMain() {}

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
            PendingJobTable pending = group.pendingJobTable();
            EligibleMachineTable eligible = group.eligibleMachineTable();
            MachineRuntimeTable machines = group.machineRuntimeTable();
            int machineCount = Math.min(16_384, Math.max(1_024, rows / 100));
            pending.reserve(rows);
            eligible.reserve(rows);
            machines.reserve(machineCount);
            String[] queues = new String[64];
            String[] zones = new String[32];
            for (int index = 0; index < queues.length; index++) queues[index] = "Q-" + index;
            for (int index = 0; index < zones.length; index++) zones[index] = "Z-" + index;

            long ingestStarted = System.nanoTime();
            for (int index = 0; index < machineCount; index++) {
                machines.add(new MachineRuntime(index + 1L, zones[index % zones.length],
                        index % 2_000, index % 10_000, (index & 15) != 0));
            }
            for (int index = 0; index < rows; index++) {
                long id = index + 1L;
                pending.add(new PendingJob(id, PendingStatus.PENDING,
                        queues[index % queues.length], index % 10_000,
                        20_000L + index % 20_000,
                        new DispatchPayload("request-" + id)));
                eligible.add(new EligibleMachine(id, id,
                        index % machineCount + 1L, 5L + index % 120, index % 8));
            }
            long ingestNanos = System.nanoTime() - ingestStarted;

            LongSupplier scan = () -> pending
                    .filter(pending.deadlineMinute.ge(25_000L))
                    .mapToLong(pending.deadlineMinute)
                    .sum();
            long somaScanNanos = median(scan, 5);
            long scanFingerprint = scan.getAsLong();
            LongSupplier parallelScan = () -> pending.parallel()
                    .filter(pending.deadlineMinute.ge(25_000L))
                    .mapToLong(pending.deadlineMinute)
                    .sum();
            long parallelNanos = median(parallelScan, 5);
            long parallelFingerprint = parallelScan.getAsLong();
            require(scanFingerprint == parallelFingerprint,
                    "sequential/parallel dispatch scan");

            long keyStarted = System.nanoTime();
            long keyFingerprint = 0L;
            for (int index = 0; index < 10_000; index++) {
                long id = (index * 7_919L) % rows + 1L;
                keyFingerprint += pending.get(id).payload().requestId().length();
            }
            long keyNanos = System.nanoTime() - keyStarted;
            long indexStarted = System.nanoTime();
            long queueCount = pending.byQueue("Q-7").count();
            long indexNanos = System.nanoTime() - indexStarted;
            long selectedJob = Math.min(42L, rows);
            long joinStarted = System.nanoTime();
            long joinCount = eligible.join(machines)
                    .on(eligible.machineId, machines.machineId)
                    .inner()
                    .filter(eligible.jobId.eq(selectedJob))
                    .filter(machines.enabled.eq(true))
                    .count();
            long joinNanos = System.nanoTime() - joinStarted;
            pending.remove(Math.max(1L, rows / 2L));

            TableMetadata metadata = pending._metadata();
            long fingerprint = scanFingerprint ^ keyFingerprint ^ queueCount
                    ^ joinCount ^ metadata.size();
            blackhole ^= fingerprint;
            System.out.println("PROFILE scenario=real-time-dispatch model=REFERENCE_MIXED rows=" + rows
                    + " baselineIngestMs=" + ms(baselineIngestNanos)
                    + " baselineScanMs=" + ms(baselineScanNanos)
                    + " baselineKeyMs=" + ms(baselineKeyNanos)
                    + " somaIngestMs=" + ms(ingestNanos)
                    + " somaScanMs=" + ms(somaScanNanos)
                    + " parallelScanMs=" + ms(parallelNanos)
                    + " somaKeyMs=" + ms(keyNanos)
                    + " indexMs=" + ms(indexNanos)
                    + " joinMs=" + ms(joinNanos)
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
        List<BaselinePending> list = new ArrayList<BaselinePending>(rows);
        Map<Long, BaselinePending> byId = new HashMap<Long, BaselinePending>(rows * 4 / 3 + 1);
        long[] deadlines = new long[rows];
        for (int index = 0; index < rows; index++) {
            long id = index + 1L;
            BaselinePending item = new BaselinePending(id, 20_000L + index % 20_000,
                    new DispatchPayload("request-" + id));
            list.add(item);
            byId.put(id, item);
            deadlines[index] = item.deadline;
        }
        baselineIngestNanos = System.nanoTime() - started;
        started = System.nanoTime();
        long scan = 0L;
        for (long deadline : deadlines) if (deadline >= 25_000L) scan += deadline;
        baselineScanNanos = System.nanoTime() - started;
        started = System.nanoTime();
        long key = 0L;
        for (int index = 0; index < 10_000; index++) {
            key += byId.get((index * 7_919L) % rows + 1L).payload.requestId().length();
        }
        baselineKeyNanos = System.nanoTime() - started;
        return new Baseline(scan ^ key ^ list.size());
    }

    private static long median(LongSupplier supplier, int samples) {
        supplier.getAsLong(); supplier.getAsLong();
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
    private static final class BaselinePending {
        final long id;
        final long deadline;
        final DispatchPayload payload;
        BaselinePending(long id, long deadline, DispatchPayload payload) {
            this.id = id; this.deadline = deadline; this.payload = payload;
        }
    }
}
