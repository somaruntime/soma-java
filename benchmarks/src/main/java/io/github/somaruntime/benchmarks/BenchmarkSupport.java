package io.github.somaruntime.benchmarks;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

/** Shared deterministic measurement and correctness utilities. */
public final class BenchmarkSupport {
    private static volatile long blackhole;
    private static volatile MemoryObserver memoryObserver;
    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();
    private static final com.sun.management.ThreadMXBean THREADS = threadBean();
    private static final List<GarbageCollectorMXBean> GARBAGE_COLLECTORS =
            ManagementFactory.getGarbageCollectorMXBeans();

    private BenchmarkSupport() {}

    public static LongMeasurement measure(LongSupplier operation) {
        int warmups = Integer.getInteger("soma.benchmark.innerWarmups", 2);
        int samples = Integer.getInteger("soma.benchmark.innerSamples", 5);
        require(warmups >= 0 && warmups <= 20, "inner warmups out of range");
        require(samples >= 1 && samples <= 21 && (samples & 1) == 1,
                "inner samples must be an odd value in [1, 21]");

        long expected = operation.getAsLong();
        blackhole ^= expected;
        for (int index = 0; index < warmups; index++) {
            long actual = operation.getAsLong();
            require(actual == expected, "operation result changed during warmup");
            blackhole ^= actual;
        }

        long[] durations = new long[samples];
        for (int index = 0; index < samples; index++) {
            long started = System.nanoTime();
            long actual = operation.getAsLong();
            durations[index] = System.nanoTime() - started;
            require(actual == expected, "operation result changed during measurement");
            blackhole ^= actual;
        }
        Arrays.sort(durations);
        MemoryMeasurement memory = null;
        if (Boolean.getBoolean("soma.benchmark.memoryAttribution")) {
            MemoryRun replay = measureMemory(operation);
            require(replay.value == expected, "operation result changed during memory replay");
            memory = replay.memory;
        }
        return new LongMeasurement(
                expected,
                durations[0],
                durations[durations.length / 2],
                durations[durations.length - 1],
                memory);
    }

    /** Measures a mutation or setup journey exactly once, without replaying it. */
    public static LongMeasurement measureOnce(LongSupplier operation) {
        if (Boolean.getBoolean("soma.benchmark.memoryAttribution")) {
            MemoryRun run = measureMemory(operation);
            blackhole ^= run.value;
            return new LongMeasurement(
                    run.value, run.durationNanos, run.durationNanos, run.durationNanos, run.memory);
        }
        long started = System.nanoTime();
        long value = operation.getAsLong();
        long duration = System.nanoTime() - started;
        blackhole ^= value;
        return new LongMeasurement(value, duration, duration, duration, null);
    }

    public static void observeMemory(MemoryObserver observer) {
        if (observer == null) throw new NullPointerException("observer");
        memoryObserver = observer;
        if (Boolean.getBoolean("soma.benchmark.memoryAttribution")) {
            measureMemory(() -> 0L);
        }
    }

    public static long fingerprint(long... values) {
        long hash = 0xcbf29ce484222325L;
        for (long value : values) {
            hash = mix(hash, value);
        }
        blackhole ^= hash;
        return hash;
    }

    public static long mix(long hash, long value) {
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            hash ^= (value >>> shift) & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    public static int rows(String[] args) {
        int value = args.length == 0 ? 1_000_000 : Integer.parseInt(args[0]);
        require(value >= 10_000 && value <= 20_000_000, "rows out of benchmark range");
        return value;
    }

    public static String implementation(String[] args) {
        String value = args.length < 2 ? "soma-auto" : args[1];
        require("soma-auto".equals(value) || "soma-off".equals(value)
                        || "manual".equals(value) || "java-stream".equals(value),
                "unknown benchmark implementation");
        return value;
    }

    public static String workload() {
        String value = System.getProperty("soma.benchmark.workload", "core");
        require("core".equals(value) || "composed".equals(value)
                        || "kernel".equals(value) || "frontier".equals(value),
                "unknown benchmark workload");
        return value;
    }

    public static boolean composedWorkload() {
        return "composed".equals(workload());
    }

    public static int parallelism() {
        int value = Integer.getInteger("soma.benchmark.parallelism", 8);
        require(value >= 1 && value <= 16, "parallelism out of range");
        return value;
    }

    public static long runNumber() {
        long value = Long.getLong("soma.benchmark.run", 1L);
        require(value >= 1L, "run number must be positive");
        return value;
    }

    public static void require(long actual, long expected, String subject) {
        if (actual != expected) {
            throw new AssertionError(subject + ": expected=" + expected + ", actual=" + actual);
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static MemoryRun measureMemory(LongSupplier operation) {
        MemoryObserver observer = memoryObserver;
        if (observer == null) {
            throw new IllegalStateException(
                    "memory attribution requires a managed-memory observer");
        }
        Sampler sampler = new Sampler(observer);
        Thread samplerThread = new Thread(sampler, "soma-benchmark-memory-sampler");
        samplerThread.setDaemon(true);

        long retainedBefore = observer.retainedBytes();
        long heapBefore = heap().getUsed();
        long collectionsBefore = garbageCollections();
        long collectionMillisBefore = garbageCollectionMillis();
        long allocatedBefore = participantAllocatedBytes();

        samplerThread.start();
        sampler.awaitReady();
        long actual;
        long started = System.nanoTime();
        long duration;
        try {
            actual = operation.getAsLong();
        } finally {
            duration = System.nanoTime() - started;
            sampler.stop();
            join(samplerThread);
        }

        long allocatedAfter = participantAllocatedBytes();
        long collectionMillisAfter = garbageCollectionMillis();
        long collectionsAfter = garbageCollections();
        long heapAfter = heap().getUsed();
        long retainedAfter = observer.retainedBytes();
        long allocated = allocatedBefore < 0L || allocatedAfter < allocatedBefore
                ? -1L
                : allocatedAfter - allocatedBefore;
        long peakTemporary = sampler.peakTemporaryBytes();
        long peakRetained = Math.max(
                Math.max(retainedBefore, retainedAfter),
                sampler.peakRetainedBytes());
        MemoryMeasurement memory = new MemoryMeasurement(
                allocated,
                retainedBefore,
                retainedAfter,
                peakTemporary,
                Math.max(peakRetained, sampler.peakManagedBytes()),
                heapBefore,
                heapAfter,
                Math.max(Math.max(heapBefore, heapAfter), sampler.peakHeapUsedBytes()),
                sampler.peakHeapCommittedBytes(),
                delta(collectionsBefore, collectionsAfter),
                delta(collectionMillisBefore, collectionMillisAfter));
        return new MemoryRun(actual, duration, memory);
    }

    private static com.sun.management.ThreadMXBean threadBean() {
        java.lang.management.ThreadMXBean candidate =
                ManagementFactory.getThreadMXBean();
        if (!(candidate instanceof com.sun.management.ThreadMXBean)) return null;
        com.sun.management.ThreadMXBean result =
                (com.sun.management.ThreadMXBean) candidate;
        if (!result.isThreadAllocatedMemorySupported()) return null;
        if (!result.isThreadAllocatedMemoryEnabled()) {
            result.setThreadAllocatedMemoryEnabled(true);
        }
        return result;
    }

    private static long participantAllocatedBytes() {
        if (THREADS == null) return -1L;
        long caller = Thread.currentThread().getId();
        long[] ids = THREADS.getAllThreadIds();
        ThreadInfo[] infos = THREADS.getThreadInfo(ids);
        long result = 0L;
        for (int index = 0; index < ids.length; index++) {
            ThreadInfo info = infos[index];
            if (info == null) continue;
            String name = info.getThreadName();
            if (ids[index] != caller && !name.startsWith("ForkJoinPool-")) continue;
            long allocated = THREADS.getThreadAllocatedBytes(ids[index]);
            if (allocated >= 0L) result = addSaturated(result, allocated);
        }
        return result;
    }

    private static MemoryUsage heap() {
        return MEMORY.getHeapMemoryUsage();
    }

    private static long garbageCollections() {
        long result = 0L;
        for (GarbageCollectorMXBean collector : GARBAGE_COLLECTORS) {
            long count = collector.getCollectionCount();
            if (count >= 0L) result += count;
        }
        return result;
    }

    private static long garbageCollectionMillis() {
        long result = 0L;
        for (GarbageCollectorMXBean collector : GARBAGE_COLLECTORS) {
            long time = collector.getCollectionTime();
            if (time >= 0L) result += time;
        }
        return result;
    }

    private static long delta(long before, long after) {
        return after < before ? -1L : after - before;
    }

    private static long addSaturated(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    private static void join(Thread thread) {
        boolean interrupted = false;
        for (;;) {
            try {
                thread.join();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static final class Sampler implements Runnable {
        private final MemoryObserver observer;
        private final CountDownLatch ready = new CountDownLatch(1);
        private volatile boolean running = true;
        private long peakRetainedBytes;
        private long peakTemporaryBytes;
        private long peakManagedBytes;
        private long peakHeapUsedBytes;
        private long peakHeapCommittedBytes;

        private Sampler(MemoryObserver observer) {
            this.observer = observer;
        }

        @Override
        public void run() {
            sample();
            ready.countDown();
            while (running) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
                sample();
            }
            sample();
        }

        private void sample() {
            long retained = observer.retainedBytes();
            long temporary = observer.temporaryBytes();
            peakRetainedBytes = Math.max(peakRetainedBytes, retained);
            peakTemporaryBytes = Math.max(peakTemporaryBytes, temporary);
            peakManagedBytes = Math.max(
                    peakManagedBytes, addSaturated(retained, temporary));
            MemoryUsage usage = heap();
            peakHeapUsedBytes = Math.max(peakHeapUsedBytes, usage.getUsed());
            peakHeapCommittedBytes = Math.max(
                    peakHeapCommittedBytes, usage.getCommitted());
        }

        private void awaitReady() {
            boolean interrupted = false;
            for (;;) {
                try {
                    ready.await();
                    break;
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }

        private void stop() { running = false; }
        private long peakRetainedBytes() { return peakRetainedBytes; }
        private long peakTemporaryBytes() { return peakTemporaryBytes; }
        private long peakManagedBytes() { return peakManagedBytes; }
        private long peakHeapUsedBytes() { return peakHeapUsedBytes; }
        private long peakHeapCommittedBytes() { return peakHeapCommittedBytes; }
    }

    private static final class MemoryRun {
        private final long value;
        private final long durationNanos;
        private final MemoryMeasurement memory;

        private MemoryRun(long value, long durationNanos, MemoryMeasurement memory) {
            this.value = value;
            this.durationNanos = durationNanos;
            this.memory = memory;
        }
    }
}
