package com.hgtech.soma.runtime.generated;

/** Generated int-key table可选择的primitive KeySpace协议。 */
public interface IntKeySpace {
    String implementation();
    int size();
    int capacity();
    int used();
    long probeCount();
    long collisionCount();
    long rehashCount();
    long retainedBytes();
    void resetMetrics();
    void addMetrics(long probes, long collisions, long rehashes);
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
