package io.github.somaruntime.benchmarks;

import java.util.Arrays;
import java.util.function.LongSupplier;

/** Shared deterministic measurement and correctness utilities. */
public final class BenchmarkSupport {
    private static volatile long blackhole;

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
        return new LongMeasurement(
                expected,
                durations[0],
                durations[durations.length / 2],
                durations[durations.length - 1]);
    }

    public static long fingerprint(long... values) {
        long hash = 0xcbf29ce484222325L;
        for (long value : values) {
            for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
                hash ^= (value >>> shift) & 0xffL;
                hash *= 0x100000001b3L;
            }
        }
        blackhole ^= hash;
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
                        || "manual".equals(value),
                "unknown benchmark implementation");
        return value;
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
}
