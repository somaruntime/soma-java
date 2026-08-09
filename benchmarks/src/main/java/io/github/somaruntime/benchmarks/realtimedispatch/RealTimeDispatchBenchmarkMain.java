package io.github.somaruntime.benchmarks.realtimedispatch;

import io.github.somaruntime.benchmarks.BenchmarkResult;
import io.github.somaruntime.benchmarks.BenchmarkSupport;
import io.github.somaruntime.benchmarks.LongMeasurement;
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
import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.TableMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

/** Reference-mixed dispatch benchmark and scenario correctness consumer. */
public final class RealTimeDispatchBenchmarkMain {
    private RealTimeDispatchBenchmarkMain() {}

    public static void main(String[] args) {
        int rows = BenchmarkSupport.rows(args);
        String implementation = BenchmarkSupport.implementation(args);
        Expected expected = expected(rows);
        if ("manual".equals(implementation)) runManual(rows, expected);
        else runSoma(rows, implementation, expected);
    }

    private static void runSoma(int rows, String implementation, Expected expected) {
        ForkJoinPool executor = new ForkJoinPool(BenchmarkSupport.parallelism());
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
            PendingJobTable pending = group.pendingJobTable();
            EligibleMachineTable eligible = group.eligibleMachineTable();
            MachineRuntimeTable machines = group.machineRuntimeTable();
            pending.reserve(rows);
            eligible.reserve(rows);
            machines.reserve(expected.machineCount);
            String[] queues = new String[64];
            String[] zones = new String[32];
            for (int index = 0; index < queues.length; index++) queues[index] = "Q-" + index;
            for (int index = 0; index < zones.length; index++) zones[index] = "Z-" + index;

            long ingestStarted = System.nanoTime();
            for (int index = 0; index < expected.machineCount; index++) {
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
                        index % expected.machineCount + 1L, 5L + index % 120, index % 8));
            }
            long ingestNanos = System.nanoTime() - ingestStarted;
            BenchmarkSupport.require(pending.size(), rows, "dispatch pending ingest size");
            BenchmarkSupport.require(eligible.size(), rows, "dispatch eligible ingest size");

            LongMeasurement scan = BenchmarkSupport.measure(() -> pending
                    .filter(pending.deadlineMinute.ge(25_000L))
                    .mapToLong(pending.deadlineMinute)
                    .sum());
            BenchmarkSupport.require(scan.value(), expected.scan, "dispatch scan");

            LongMeasurement parallelScan = BenchmarkSupport.measure(() -> pending.parallel()
                    .filter(pending.deadlineMinute.ge(25_000L))
                    .mapToLong(pending.deadlineMinute)
                    .sum());
            BenchmarkSupport.require(parallelScan.value(), expected.scan,
                    "dispatch parallel scan");

            LongMeasurement key = BenchmarkSupport.measure(() -> {
                long value = 0L;
                for (int index = 0; index < 10_000; index++) {
                    long id = (index * 7_919L) % rows + 1L;
                    value += pending.get(id).payload().requestId().length();
                }
                return value;
            });
            BenchmarkSupport.require(key.value(), expected.key, "dispatch Key lookup");

            LongMeasurement index = BenchmarkSupport.measure(() -> pending.byQueue("Q-7").count());
            BenchmarkSupport.require(index.value(), expected.queueCount,
                    "dispatch Index selection");

            LongMeasurement join = BenchmarkSupport.measure(() -> eligible.join(machines)
                    .on(eligible.machineId, machines.machineId)
                    .inner()
                    .filter(eligible.jobId.eq(expected.selectedJob))
                    .filter(machines.enabled.eq(true))
                    .count());
            BenchmarkSupport.require(join.value(), expected.joinCount, "dispatch Join");

            long probeKey = Math.max(1L, rows / 3L);
            BenchmarkSupport.require(
                    pending.get(probeKey).payload().requestId().equals("request-" + probeKey),
                    "dispatch ordinary reference payload");
            long removeKey = Math.max(1L, rows / 2L);
            RemoveResult remove = pending.remove(removeKey);
            BenchmarkSupport.require(remove.removed(), 1L, "dispatch remove result");
            BenchmarkSupport.require(!pending.find(removeKey).isPresent(),
                    "dispatch removed Key is still present");
            BenchmarkSupport.require(pending.size(), rows - 1L, "dispatch remove size");

            TableMetadata metadata = pending._metadata();
            BenchmarkSupport.require(metadata.size(), rows - 1L, "dispatch metadata size");
            long sharedFingerprint = BenchmarkSupport.fingerprint(rows, scan.value(), key.value());
            long fingerprint = BenchmarkSupport.fingerprint(
                    sharedFingerprint,
                    index.value(), join.value(), remove.removed(), metadata.size());

            new BenchmarkResult("real-time-dispatch", "REFERENCE_MIXED", implementation, rows)
                    .put("correctness", true)
                    .put("compression", compression.name())
                    .put("ingestNanos", ingestNanos)
                    .put("scan", scan)
                    .put("parallelScan", parallelScan)
                    .put("key10k", key)
                    .put("index", index)
                    .put("join", join)
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
        List<ManualPending> list = new ArrayList<ManualPending>(rows);
        Map<Long, ManualPending> byId = new HashMap<Long, ManualPending>(rows * 4 / 3 + 1);
        long[] deadlines = new long[rows];
        for (int index = 0; index < rows; index++) {
            long id = index + 1L;
            ManualPending item = new ManualPending(id, 20_000L + index % 20_000,
                    new DispatchPayload("request-" + id));
            list.add(item);
            byId.put(id, item);
            deadlines[index] = item.deadline;
        }
        long ingestNanos = System.nanoTime() - started;

        LongMeasurement scan = BenchmarkSupport.measure(() -> {
            long value = 0L;
            for (long deadline : deadlines) {
                if (deadline >= 25_000L) value += deadline;
            }
            return value;
        });
        BenchmarkSupport.require(scan.value(), expected.scan, "manual dispatch scan");

        LongMeasurement key = BenchmarkSupport.measure(() -> {
            long value = 0L;
            for (int index = 0; index < 10_000; index++) {
                value += byId.get((index * 7_919L) % rows + 1L).payload.requestId().length();
            }
            return value;
        });
        BenchmarkSupport.require(key.value(), expected.key, "manual dispatch Key lookup");
        BenchmarkSupport.require(list.size(), rows, "manual dispatch ingest size");

        long sharedFingerprint = BenchmarkSupport.fingerprint(rows, scan.value(), key.value());
        new BenchmarkResult("real-time-dispatch", "REFERENCE_MIXED", "manual", rows)
                .put("correctness", true)
                .put("ingestNanos", ingestNanos)
                .put("scan", scan)
                .put("key10k", key)
                .put("sharedFingerprint", sharedFingerprint)
                .put("fingerprint", sharedFingerprint)
                .print();
    }

    private static Expected expected(int rows) {
        int machineCount = Math.min(16_384, Math.max(1_024, rows / 100));
        long selectedJob = Math.min(42L, rows);
        long scan = 0L;
        long queueCount = 0L;
        for (int index = 0; index < rows; index++) {
            long deadline = 20_000L + index % 20_000;
            if (deadline >= 25_000L) scan += deadline;
            if (index % 64 == 7) queueCount++;
        }
        long key = 0L;
        for (int index = 0; index < 10_000; index++) {
            long id = (index * 7_919L) % rows + 1L;
            key += 8L + Long.toString(id).length();
        }
        long selectedIndex = selectedJob - 1L;
        long machineId = selectedIndex % machineCount + 1L;
        long joinCount = (((machineId - 1L) & 15L) != 0L) ? 1L : 0L;
        return new Expected(machineCount, selectedJob, scan, key, queueCount, joinCount);
    }

    private static final class Expected {
        final int machineCount;
        final long selectedJob;
        final long scan;
        final long key;
        final long queueCount;
        final long joinCount;

        Expected(
                int machineCount,
                long selectedJob,
                long scan,
                long key,
                long queueCount,
                long joinCount) {
            this.machineCount = machineCount;
            this.selectedJob = selectedJob;
            this.scan = scan;
            this.key = key;
            this.queueCount = queueCount;
            this.joinCount = joinCount;
        }
    }

    private static final class ManualPending {
        final long id;
        final long deadline;
        final DispatchPayload payload;

        ManualPending(long id, long deadline, DispatchPayload payload) {
            this.id = id;
            this.deadline = deadline;
            this.payload = payload;
        }
    }
}
