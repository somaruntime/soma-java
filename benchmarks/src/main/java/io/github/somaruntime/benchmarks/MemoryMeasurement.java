package io.github.somaruntime.benchmarks;

/** Operation-scoped allocation, managed-memory and JVM heap snapshots. */
public final class MemoryMeasurement {
    private final long allocatedBytes;
    private final long retainedBeforeBytes;
    private final long retainedAfterBytes;
    private final long peakTemporaryBytes;
    private final long peakManagedBytes;
    private final long heapUsedBeforeBytes;
    private final long heapUsedAfterBytes;
    private final long peakHeapUsedBytes;
    private final long peakHeapCommittedBytes;
    private final long garbageCollections;
    private final long garbageCollectionMillis;

    MemoryMeasurement(
            long allocatedBytes,
            long retainedBeforeBytes,
            long retainedAfterBytes,
            long peakTemporaryBytes,
            long peakManagedBytes,
            long heapUsedBeforeBytes,
            long heapUsedAfterBytes,
            long peakHeapUsedBytes,
            long peakHeapCommittedBytes,
            long garbageCollections,
            long garbageCollectionMillis) {
        this.allocatedBytes = allocatedBytes;
        this.retainedBeforeBytes = retainedBeforeBytes;
        this.retainedAfterBytes = retainedAfterBytes;
        this.peakTemporaryBytes = peakTemporaryBytes;
        this.peakManagedBytes = peakManagedBytes;
        this.heapUsedBeforeBytes = heapUsedBeforeBytes;
        this.heapUsedAfterBytes = heapUsedAfterBytes;
        this.peakHeapUsedBytes = peakHeapUsedBytes;
        this.peakHeapCommittedBytes = peakHeapCommittedBytes;
        this.garbageCollections = garbageCollections;
        this.garbageCollectionMillis = garbageCollectionMillis;
    }

    public long allocatedBytes() { return allocatedBytes; }
    public long retainedBeforeBytes() { return retainedBeforeBytes; }
    public long retainedAfterBytes() { return retainedAfterBytes; }
    public long peakTemporaryBytes() { return peakTemporaryBytes; }
    public long peakManagedBytes() { return peakManagedBytes; }
    public long heapUsedBeforeBytes() { return heapUsedBeforeBytes; }
    public long heapUsedAfterBytes() { return heapUsedAfterBytes; }
    public long peakHeapUsedBytes() { return peakHeapUsedBytes; }
    public long peakHeapCommittedBytes() { return peakHeapCommittedBytes; }
    public long garbageCollections() { return garbageCollections; }
    public long garbageCollectionMillis() { return garbageCollectionMillis; }
}
