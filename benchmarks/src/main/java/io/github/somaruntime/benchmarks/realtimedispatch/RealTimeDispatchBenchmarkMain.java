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
import io.github.somaruntime.soma.GroupedLongResult;
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
import java.util.PriorityQueue;
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
                long jobId = BenchmarkSupport.composedWorkload()
                        ? index / 2L + 1L : id;
                long machineId = BenchmarkSupport.composedWorkload() && (index & 31) == 0
                        ? expected.machineCount + 1L + index % 128L
                        : index % expected.machineCount + 1L;
                eligible.add(new EligibleMachine(
                        id, jobId, machineId, 5L + index % 120, index % 8));
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

            ComposedMetrics composed = BenchmarkSupport.composedWorkload()
                    ? runComposed(pending, eligible, machines, expected.composed)
                    : null;

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

            BenchmarkResult result = new BenchmarkResult(
                    "real-time-dispatch", "REFERENCE_MIXED", implementation, rows)
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
        long joinCount = 0L;
        boolean composed = BenchmarkSupport.composedWorkload();
        for (int index = 0; index < rows; index++) {
            long jobId = composed ? index / 2L + 1L : index + 1L;
            if (jobId != selectedJob) continue;
            long machineId = composed && (index & 31) == 0
                    ? machineCount + 1L + index % 128L
                    : index % machineCount + 1L;
            if (machineId <= machineCount && ((machineId - 1L) & 15L) != 0L) {
                joinCount++;
            }
        }
        return new Expected(
                machineCount,
                selectedJob,
                scan,
                key,
                queueCount,
                joinCount,
                composed ? composedExpected(rows, machineCount) : null);
    }

    private static ComposedExpected composedExpected(int rows, int machineCount) {
        boolean[] machineMatched = new boolean[machineCount];
        PriorityQueue<ExpectedPending> indexTop = new PriorityQueue<ExpectedPending>(
                128, Collections.reverseOrder(ExpectedPending.ORDER));
        long broadJoinSum = 0L;
        long matched = 0L;
        long missing = 0L;
        long updateCount = 0L;
        long updateAllSum = 0L;
        long[] queueCounts = new long[64];
        for (int index = 0; index < rows; index++) {
            long id = index + 1L;
            String queue = "Q-" + index % 64;
            long deadline = 20_000L + index % 20_000;
            queueCounts[index % 64]++;
            if (index % 64 == 7 && deadline >= 35_000L) {
                ExpectedPending candidate = new ExpectedPending(id, deadline);
                if (indexTop.size() < 128) indexTop.add(candidate);
                else if (ExpectedPending.ORDER.compare(candidate, indexTop.peek()) < 0) {
                    indexTop.poll();
                    indexTop.add(candidate);
                }
                updateAllSum += deadline;
                if (updateCount < 1_000L) updateCount++;
            }

            long machineId = (index & 31) == 0
                    ? machineCount + 1L + index % 128L
                    : index % machineCount + 1L;
            if (machineId > machineCount) {
                missing++;
            } else {
                matched++;
                machineMatched[(int) machineId - 1] = true;
                boolean eligiblePriority = index % 8 >= 4;
                boolean machineEnabled = ((machineId - 1L) & 15L) != 0L;
                if (eligiblePriority && machineEnabled) {
                    broadJoinSum += 5L + index % 120L
                            + (machineId - 1L) % 10_000L;
                }
            }
        }
        int unmatchedMachines = 0;
        for (boolean value : machineMatched) if (!value) unmatchedMachines++;

        List<ExpectedPending> orderedTop = new ArrayList<ExpectedPending>(indexTop);
        Collections.sort(orderedTop, ExpectedPending.ORDER);
        long indexTopFingerprint = 0xcbf29ce484222325L;
        indexTopFingerprint = BenchmarkSupport.mix(indexTopFingerprint, orderedTop.size());
        for (ExpectedPending pending : orderedTop) {
            indexTopFingerprint = BenchmarkSupport.mix(indexTopFingerprint, pending.jobId);
            indexTopFingerprint = BenchmarkSupport.mix(indexTopFingerprint, pending.deadline);
        }

        List<String> queues = new ArrayList<String>(64);
        for (int index = 0; index < 64; index++) queues.add("Q-" + index);
        Collections.sort(queues);
        long referenceFingerprint = 0xcbf29ce484222325L;
        referenceFingerprint = BenchmarkSupport.mix(referenceFingerprint, queues.size());
        for (String queue : queues) {
            referenceFingerprint = BenchmarkSupport.mix(referenceFingerprint, queue.length());
            referenceFingerprint = BenchmarkSupport.mix(referenceFingerprint, queue.hashCode());
        }

        long groupFingerprint = 0xcbf29ce484222325L;
        groupFingerprint = BenchmarkSupport.mix(groupFingerprint, 64L);
        for (int index = 0; index < queueCounts.length; index++) {
            String queue = "Q-" + index;
            groupFingerprint = BenchmarkSupport.mix(groupFingerprint, queue.hashCode());
            groupFingerprint = BenchmarkSupport.mix(groupFingerprint, queueCounts[index]);
        }

        long relationFingerprint = BenchmarkSupport.fingerprint(
                matched,
                rows,
                rows + unmatchedMachines,
                matched,
                missing,
                rows);
        long removed = Math.min(1_000L, missing);
        return new ComposedExpected(
                machineCount,
                indexTopFingerprint,
                referenceFingerprint,
                broadJoinSum,
                relationFingerprint,
                updateCount,
                updateAllSum + updateCount,
                removed,
                rows - removed,
                matched,
                missing - removed,
                groupFingerprint);
    }

    private static ComposedMetrics runComposed(
            PendingJobTable pending,
            EligibleMachineTable eligible,
            MachineRuntimeTable machines,
            ComposedExpected expected) {
        LongMeasurement indexTop = BenchmarkSupport.measure(() -> {
            PendingJob[] values = pending.byQueue("Q-7")
                    .filter(pending.deadlineMinute.ge(35_000L))
                    .top(128L, pending.deadlineMinute.asc().then(pending.jobId.asc()))
                    .toArray();
            long hash = 0xcbf29ce484222325L;
            hash = BenchmarkSupport.mix(hash, values.length);
            for (PendingJob value : values) {
                hash = BenchmarkSupport.mix(hash, value.jobId());
                hash = BenchmarkSupport.mix(hash, value.deadlineMinute());
            }
            return hash;
        });
        BenchmarkSupport.require(indexTop.value(), expected.indexTopFingerprint,
                "dispatch composed Index residual/top/materialization");

        LongMeasurement typedReference = BenchmarkSupport.measure(() -> {
            String[] values = pending.queue.distinct().sorted().limit(64L).toArray();
            return referenceFingerprint(values);
        });
        BenchmarkSupport.require(typedReference.value(), expected.referenceFingerprint,
                "dispatch composed typed reference pipeline");

        final Comparator<String> natural = new Comparator<String>() {
            @Override public int compare(String left, String right) {
                return left.compareTo(right);
            }
        };
        LongMeasurement mappedReference = BenchmarkSupport.measure(() -> {
            String[] values = pending.map(view -> view.queue())
                    .distinct()
                    .sorted(natural)
                    .limit(64L)
                    .toArray(String.class);
            return referenceFingerprint(values);
        });
        BenchmarkSupport.require(mappedReference.value(), expected.referenceFingerprint,
                "dispatch composed mapped reference pipeline");

        LongMeasurement broadJoin = BenchmarkSupport.measure(() -> eligible.join(machines)
                .on(eligible.machineId, machines.machineId)
                .inner()
                .filter(eligible.priority.ge(4))
                .filter(machines.enabled.eq(true))
                .mapToLong(pair -> pair.left().processingMinutes()
                        + pair.right().workloadMinutes())
                .sum());
        BenchmarkSupport.require(broadJoin.value(), expected.broadJoinSum,
                "dispatch composed broad Join");

        LongMeasurement broadJoinParallel = BenchmarkSupport.measure(() -> eligible.join(machines)
                .on(eligible.machineId, machines.machineId)
                .parallel()
                .inner()
                .filter(eligible.priority.ge(4))
                .filter(machines.enabled.eq(true))
                .mapToLong(pair -> pair.left().processingMinutes()
                        + pair.right().workloadMinutes())
                .sum());
        BenchmarkSupport.require(broadJoinParallel.value(), expected.broadJoinSum,
                "dispatch composed broad parallel Join");

        LongMeasurement relationKinds = BenchmarkSupport.measure(() -> {
            long inner = eligible.join(machines)
                    .on(eligible.machineId, machines.machineId).inner().count();
            long left = eligible.join(machines)
                    .on(eligible.machineId, machines.machineId).left().count();
            long full = eligible.join(machines)
                    .on(eligible.machineId, machines.machineId).full().count();
            long semi = eligible.join(machines)
                    .on(eligible.machineId, machines.machineId).semi().count();
            long anti = eligible.join(machines)
                    .on(eligible.machineId, machines.machineId).anti().count();
            long duplicate = pending.join(eligible)
                    .on(pending.jobId, eligible.jobId).inner().count();
            return BenchmarkSupport.fingerprint(inner, left, full, semi, anti, duplicate);
        });
        BenchmarkSupport.require(relationKinds.value(), expected.relationFingerprint,
                "dispatch composed relation kinds");

        long updateStarted = System.nanoTime();
        UpdateResult update = pending.byQueue("Q-7")
                .filter(pending.deadlineMinute.ge(35_000L))
                .limit(1_000L)
                .update(editor -> editor.deadlineMinute(
                        Math.addExact(editor.deadlineMinute(), 1L)));
        long updateNanos = System.nanoTime() - updateStarted;
        BenchmarkSupport.require(update.matched(), expected.updateCount,
                "dispatch composed update matched");
        BenchmarkSupport.require(update.changed(), expected.updateCount,
                "dispatch composed update changed");
        long updatedSum = pending.byQueue("Q-7")
                .filter(pending.deadlineMinute.ge(35_000L))
                .mapToLong(pending.deadlineMinute)
                .sum();
        BenchmarkSupport.require(updatedSum, expected.updatedDeadlineSum,
                "dispatch composed updated deadline sum");

        long removeStarted = System.nanoTime();
        RemoveResult remove = eligible
                .filter(eligible.machineId.ge(expected.machineCount + 1L))
                .limit(1_000L)
                .remove();
        long removeNanos = System.nanoTime() - removeStarted;
        BenchmarkSupport.require(remove.removed(), expected.removeCount,
                "dispatch composed remove count");
        BenchmarkSupport.require(eligible.size(), expected.postMutationSize,
                "dispatch composed post-mutation size");
        long postInner = eligible.join(machines)
                .on(eligible.machineId, machines.machineId).inner().count();
        long postAnti = eligible.join(machines)
                .on(eligible.machineId, machines.machineId).anti().count();
        BenchmarkSupport.require(postInner, expected.postMutationInner,
                "dispatch composed post-mutation Join");
        BenchmarkSupport.require(postAnti, expected.postMutationAnti,
                "dispatch composed post-mutation Anti Join");

        GroupedLongResult<String> grouped = pending.groupBy(pending.queue).count();
        final long[] groupHash = {0xcbf29ce484222325L};
        groupHash[0] = BenchmarkSupport.mix(groupHash[0], grouped.size());
        grouped.forEach((queue, count) -> {
            groupHash[0] = BenchmarkSupport.mix(groupHash[0], queue.hashCode());
            groupHash[0] = BenchmarkSupport.mix(groupHash[0], count);
        });
        BenchmarkSupport.require(groupHash[0], expected.groupFingerprint,
                "dispatch composed post-mutation GroupBy");
        BenchmarkSupport.require(eligible._metadata().size(), expected.postMutationSize,
                "dispatch composed post-mutation metadata");

        long mutationFingerprint = BenchmarkSupport.fingerprint(
                update.matched(), update.changed(), updatedSum,
                remove.removed(), eligible.size(), postInner, postAnti, groupHash[0]);
        return new ComposedMetrics(
                indexTop,
                typedReference,
                mappedReference,
                broadJoin,
                broadJoinParallel,
                relationKinds,
                updateNanos,
                removeNanos,
                mutationFingerprint);
    }

    private static long referenceFingerprint(String[] values) {
        long hash = 0xcbf29ce484222325L;
        hash = BenchmarkSupport.mix(hash, values.length);
        for (String value : values) {
            hash = BenchmarkSupport.mix(hash, value.length());
            hash = BenchmarkSupport.mix(hash, value.hashCode());
        }
        return hash;
    }

    private static final class Expected {
        final int machineCount;
        final long selectedJob;
        final long scan;
        final long key;
        final long queueCount;
        final long joinCount;
        final ComposedExpected composed;

        Expected(
                int machineCount,
                long selectedJob,
                long scan,
                long key,
                long queueCount,
                long joinCount,
                ComposedExpected composed) {
            this.machineCount = machineCount;
            this.selectedJob = selectedJob;
            this.scan = scan;
            this.key = key;
            this.queueCount = queueCount;
            this.joinCount = joinCount;
            this.composed = composed;
        }
    }

    private static final class ComposedExpected {
        final long machineCount;
        final long indexTopFingerprint;
        final long referenceFingerprint;
        final long broadJoinSum;
        final long relationFingerprint;
        final long updateCount;
        final long updatedDeadlineSum;
        final long removeCount;
        final long postMutationSize;
        final long postMutationInner;
        final long postMutationAnti;
        final long groupFingerprint;

        ComposedExpected(
                long machineCount,
                long indexTopFingerprint,
                long referenceFingerprint,
                long broadJoinSum,
                long relationFingerprint,
                long updateCount,
                long updatedDeadlineSum,
                long removeCount,
                long postMutationSize,
                long postMutationInner,
                long postMutationAnti,
                long groupFingerprint) {
            this.machineCount = machineCount;
            this.indexTopFingerprint = indexTopFingerprint;
            this.referenceFingerprint = referenceFingerprint;
            this.broadJoinSum = broadJoinSum;
            this.relationFingerprint = relationFingerprint;
            this.updateCount = updateCount;
            this.updatedDeadlineSum = updatedDeadlineSum;
            this.removeCount = removeCount;
            this.postMutationSize = postMutationSize;
            this.postMutationInner = postMutationInner;
            this.postMutationAnti = postMutationAnti;
            this.groupFingerprint = groupFingerprint;
        }
    }

    private static final class ComposedMetrics {
        final LongMeasurement indexTop;
        final LongMeasurement typedReference;
        final LongMeasurement mappedReference;
        final LongMeasurement broadJoin;
        final LongMeasurement broadJoinParallel;
        final LongMeasurement relationKinds;
        final long updateNanos;
        final long removeNanos;
        final long fingerprint;

        ComposedMetrics(
                LongMeasurement indexTop,
                LongMeasurement typedReference,
                LongMeasurement mappedReference,
                LongMeasurement broadJoin,
                LongMeasurement broadJoinParallel,
                LongMeasurement relationKinds,
                long updateNanos,
                long removeNanos,
                long mutationFingerprint) {
            this.indexTop = indexTop;
            this.typedReference = typedReference;
            this.mappedReference = mappedReference;
            this.broadJoin = broadJoin;
            this.broadJoinParallel = broadJoinParallel;
            this.relationKinds = relationKinds;
            this.updateNanos = updateNanos;
            this.removeNanos = removeNanos;
            this.fingerprint = BenchmarkSupport.fingerprint(
                    indexTop.value(), typedReference.value(), mappedReference.value(),
                    broadJoin.value(), broadJoinParallel.value(), relationKinds.value(),
                    mutationFingerprint);
        }

        void appendTo(BenchmarkResult result) {
            result.put("composedIndexTop", indexTop)
                    .put("composedTypedReference", typedReference)
                    .put("composedMappedReference", mappedReference)
                    .put("composedBroadJoin", broadJoin)
                    .put("composedBroadJoinParallel", broadJoinParallel)
                    .put("composedRelationKinds", relationKinds)
                    .put("composedUpdateNanos", updateNanos)
                    .put("composedRemoveNanos", removeNanos)
                    .put("composedFingerprint", fingerprint);
        }
    }

    private static final class ExpectedPending {
        static final Comparator<ExpectedPending> ORDER = new Comparator<ExpectedPending>() {
            @Override public int compare(ExpectedPending left, ExpectedPending right) {
                int deadline = Long.compare(left.deadline, right.deadline);
                return deadline != 0 ? deadline : Long.compare(left.jobId, right.jobId);
            }
        };

        final long jobId;
        final long deadline;

        ExpectedPending(long jobId, long deadline) {
            this.jobId = jobId;
            this.deadline = deadline;
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
