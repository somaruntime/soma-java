package io.github.somaruntime.benchmarks;

/** Benchmark-only observation of SOMA managed-memory snapshots. */
public interface MemoryObserver {
    long retainedBytes();
    long temporaryBytes();
}
