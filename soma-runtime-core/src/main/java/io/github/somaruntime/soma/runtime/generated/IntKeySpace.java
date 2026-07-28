package io.github.somaruntime.soma.runtime.generated;

/** Generated int-key table的primitive primary-locator协议；KeySpace不表示Sparse Set。 */
public interface IntKeySpace {
    int size();
    int capacity();
    int used();
    long probeCount();
    long collisionCount();
    long rehashCount();
    long retainedBytes();
    long storageHighWaterBytes();
    void resetMetrics();
    void inheritMetrics(
            long probes,
            long collisions,
            long rehashes,
            long previousStorageHighWaterBytes);
    boolean contains(int key);
    int rowOf(int key);
    void requireInsertKey(int key, String table, String keyField, String operation);
    long retainedBytesAfterEnsureAdditional(int additional);
    long allocationBytesDuringEnsureAdditional(int additional);
    void ensureAdditionalCapacity(int additional);
    void put(int key, int rowSlot);
    void remove(int key);
    void updateRow(int key, int rowSlot);
    void clear();
    void releaseStorage();
}
