package io.github.somaruntime.benchmarks;

/** One deterministic operation measured repeatedly inside a single benchmark JVM. */
public final class LongMeasurement {
    private final long value;
    private final long minimumNanos;
    private final long medianNanos;
    private final long maximumNanos;
    private final MemoryMeasurement memory;

    LongMeasurement(
            long value,
            long minimumNanos,
            long medianNanos,
            long maximumNanos,
            MemoryMeasurement memory) {
        this.value = value;
        this.minimumNanos = minimumNanos;
        this.medianNanos = medianNanos;
        this.maximumNanos = maximumNanos;
        this.memory = memory;
    }

    public long value() { return value; }
    public long minimumNanos() { return minimumNanos; }
    public long medianNanos() { return medianNanos; }
    public long maximumNanos() { return maximumNanos; }
    public MemoryMeasurement memory() { return memory; }
}
