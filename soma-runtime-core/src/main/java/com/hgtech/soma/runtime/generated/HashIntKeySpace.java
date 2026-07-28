package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

/** Primitive open-addressed int-key primary locator；KeySpace不表示Sparse Set。 */
public final class HashIntKeySpace implements IntKeySpace {
    private static final int MAX_CAPACITY = 1 << 30;
    private static final byte EMPTY = 0;
    private static final byte LIVE = 1;
    private static final byte DELETED = 2;

    private int[] keys;
    private int[] rows;
    private byte[] states;
    private int size;
    private int used;
    private long probeCount;
    private long collisionCount;
    private long rehashCount;
    private long storageHighWaterBytes;

    public HashIntKeySpace(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be non-negative");
        }
        int capacity = capacityFor(expectedSize);
        keys = new int[capacity];
        rows = new int[capacity];
        states = new byte[capacity];
        storageHighWaterBytes = retainedBytes();
    }

    public int size() { return size; }

    public int capacity() { return states.length; }

    @Override
    public long retainedBytes() { return 9L * (long) states.length; }

    @Override
    public long storageHighWaterBytes() { return storageHighWaterBytes; }

    /** LIVE + DELETED buckets retained by the current probe table. */
    public int used() { return used; }

    public long probeCount() { return probeCount; }

    public long collisionCount() { return collisionCount; }

    public long rehashCount() { return rehashCount; }

    public void resetMetrics() {
        probeCount = 0L;
        collisionCount = 0L;
        rehashCount = 0L;
    }

    /** Carries since-reset metrics across an atomic staged KeySpace replacement. */
    public void inheritMetrics(
            long probes,
            long collisions,
            long rehashes,
            long previousStorageHighWaterBytes) {
        if (probes < 0L || collisions < 0L || collisions > probes
                || rehashes < 0L || previousStorageHighWaterBytes < 0L) {
            throw new IllegalArgumentException("invalid key space metrics");
        }
        long nextProbes = checkedMetricAdd(probeCount, probes);
        long nextCollisions = checkedMetricAdd(collisionCount, collisions);
        long nextRehashes = checkedMetricAdd(rehashCount, rehashes);
        probeCount = nextProbes;
        collisionCount = nextCollisions;
        rehashCount = nextRehashes;
        if (previousStorageHighWaterBytes > storageHighWaterBytes) {
            storageHighWaterBytes = previousStorageHighWaterBytes;
        }
    }

    public boolean contains(int key) { return rowOf(key) >= 0; }

    public int rowOf(int key) {
        int slot = locate(key);
        return slot < 0 ? -1 : rows[slot];
    }

    @Override
    public void requireInsertKey(int key, String table, String keyField, String operation) {
    }

    @Override
    public long retainedBytesAfterEnsureAdditional(int additional) {
        return 9L * (long) targetCapacity(additional);
    }

    @Override
    public long allocationBytesDuringEnsureAdditional(int additional) {
        return requiresRehash(additional)
                ? retainedBytesAfterEnsureAdditional(additional) : 0L;
    }

    @Override
    public void ensureAdditionalCapacity(int additional) {
        int target = targetCapacity(additional);
        if (requiresRehash(additional)) rehash(target);
    }

    public void put(int key, int rowSlot) {
        if (rowSlot < 0 || locate(key) >= 0) {
            throw new IllegalArgumentException("duplicate key or invalid rowSlot");
        }
        ensureInsertCapacity();
        int slot = insertionSlot(key);
        if (states[slot] == EMPTY) {
            used++;
        }
        states[slot] = LIVE;
        keys[slot] = key;
        rows[slot] = rowSlot;
        size++;
    }

    public void remove(int key) {
        int slot = locate(key);
        if (slot < 0) {
            throw new IllegalArgumentException("missing key");
        }
        states[slot] = DELETED;
        size--;
    }

    public void updateRow(int key, int rowSlot) {
        int slot = locate(key);
        if (slot < 0 || rowSlot < 0) {
            throw new IllegalArgumentException("missing key or invalid rowSlot");
        }
        rows[slot] = rowSlot;
    }

    public void clear() {
        Arrays.fill(states, EMPTY);
        size = 0;
        used = 0;
    }

    @Override
    public void releaseStorage() {
        keys = new int[0];
        rows = new int[0];
        states = new byte[0];
        size = 0;
        used = 0;
    }

    private void ensureInsertCapacity() {
        if (2L * ((long) used + 1L) >= (long) states.length) {
            if (states.length >= MAX_CAPACITY) {
                throw new IllegalStateException("key space capacity exhausted");
            }
            rehash(states.length << 1);
        }
    }

    private boolean requiresRehash(int additional) {
        requireAdditional(additional);
        return 2L * ((long) used + (long) additional) >= (long) states.length;
    }

    private int targetCapacity(int additional) {
        requireAdditional(additional);
        long requiredSize = (long) size + (long) additional;
        if (requiredSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("key space capacity exhausted");
        }
        int target = states.length;
        while (2L * requiredSize >= (long) target) {
            if (target >= MAX_CAPACITY) {
                throw new IllegalArgumentException("key space capacity exhausted");
            }
            target <<= 1;
        }
        return target;
    }

    private static int capacityFor(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be non-negative");
        }
        long required = Math.max(4L, 2L * (long) expectedSize + 1L);
        int capacity = 4;
        while ((long) capacity < required) {
            if (capacity >= MAX_CAPACITY) {
                throw new IllegalArgumentException("expectedSize is too large");
            }
            capacity <<= 1;
        }
        return capacity;
    }

    private static void requireAdditional(int additional) {
        if (additional < 0) {
            throw new IllegalArgumentException("additional must be non-negative");
        }
    }

    private void rehash(int capacity) {
        int[] oldKeys = keys;
        int[] oldRows = rows;
        byte[] oldStates = states;

        int[] newKeys = new int[capacity];
        int[] newRows = new int[capacity];
        byte[] newStates = new byte[capacity];
        int rebuiltSize = 0;
        long rebuildProbes = 0L;
        long rebuildCollisions = 0L;
        for (int index = 0; index < oldStates.length; index++) {
            if (oldStates[index] == LIVE) {
                int mask = newStates.length - 1;
                int slot = mix(oldKeys[index]) & mask;
                rebuildProbes++;
                while (newStates[slot] != EMPTY) {
                    rebuildCollisions++;
                    slot = (slot + 1) & mask;
                    rebuildProbes++;
                }
                newStates[slot] = LIVE;
                newKeys[slot] = oldKeys[index];
                newRows[slot] = oldRows[index];
                rebuiltSize++;
            }
        }
        if (rebuiltSize != size) {
            throw new IllegalStateException("rehash identity failure");
        }

        long committedProbeCount = checkedMetricAdd(probeCount, rebuildProbes);
        long committedCollisionCount = checkedMetricAdd(collisionCount, rebuildCollisions);
        long committedRehashCount = checkedMetricAdd(rehashCount, 1L);

        keys = newKeys;
        rows = newRows;
        states = newStates;
        used = rebuiltSize;
        probeCount = committedProbeCount;
        collisionCount = committedCollisionCount;
        rehashCount = committedRehashCount;
        updateHighWater();
    }

    private int locate(int key) {
        int mask = states.length - 1;
        int slot = mix(key) & mask;
        while (true) {
            recordProbe();
            if (states[slot] == EMPTY) {
                return -1;
            }
            if (states[slot] == LIVE && keys[slot] == key) {
                return slot;
            }
            recordCollision();
            slot = (slot + 1) & mask;
        }
    }

    private int insertionSlot(int key) {
        int mask = states.length - 1;
        int slot = mix(key) & mask;
        int deleted = -1;
        while (true) {
            recordProbe();
            if (states[slot] == EMPTY) {
                return deleted >= 0 ? deleted : slot;
            }
            recordCollision();
            if (states[slot] == DELETED && deleted < 0) {
                deleted = slot;
            }
            slot = (slot + 1) & mask;
        }
    }

    private void recordProbe() {
        probeCount = checkedMetricAdd(probeCount, 1L);
    }

    private void recordCollision() {
        collisionCount = checkedMetricAdd(collisionCount, 1L);
    }

    private static long checkedMetricAdd(long current, long delta) {
        if (delta < 0L || Long.MAX_VALUE - current < delta) {
            throw new IllegalStateException("key space metric overflow");
        }
        return current + delta;
    }

    private void updateHighWater() {
        long current = retainedBytes();
        if (current > storageHighWaterBytes) storageHighWaterBytes = current;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        return value ^ (value >>> 16);
    }
}
