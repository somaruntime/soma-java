package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

/**
 * 复合key的primitive primary-locator hash substrate；KeySpace不表示Sparse Set。
 *
 * <p>本类只保存 hash 与 packed RowSlot，不把 schema value/object 放入 runtime storage。
 * generated table 在同 hash candidate 上静态展开完整 leaf equality，然后调用本协议完成
 * insert/remove/row-move。因此 hash collision 不能被误判为 identity equality。</p>
 */
public final class HashCompositeKeySpace {
    private static final int MAX_CAPACITY = 1 << 30;
    private static final byte EMPTY = 0;
    private static final byte LIVE = 1;
    private static final byte DELETED = 2;

    private long[] hashes;
    private int[] rows;
    private byte[] states;
    private int size;
    private int used;
    private long probeCount;
    private long collisionCount;
    private long rehashCount;

    public HashCompositeKeySpace(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be non-negative");
        }
        int capacity = capacityFor(expectedSize);
        hashes = new long[capacity];
        rows = new int[capacity];
        states = new byte[capacity];
    }

    /** Conservative exact-shape peak for constructor arrays plus a possible final insert rehash. */
    public static long estimatedPeakBytes(int expectedSize) {
        int capacity = capacityFor(expectedSize);
        return 13L * (long) capacity + 48L;
    }

    private static int capacityFor(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be non-negative");
        }
        long required = Math.max(4L, 2L * (long) expectedSize + 1L);
        int capacity = 4;
        while (capacity < required) {
            if (capacity >= MAX_CAPACITY) {
                throw new IllegalArgumentException("expectedSize is too large");
            }
            capacity <<= 1;
        }
        return capacity;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return states.length;
    }

    public long retainedBytes() { return 13L * (long) states.length; }

    /** LIVE + DELETED buckets retained by the current probe table. */
    public int used() {
        return used;
    }

    public long probeCount() {
        return probeCount;
    }

    public long collisionCount() {
        return collisionCount;
    }

    public long rehashCount() {
        return rehashCount;
    }

    public void resetMetrics() {
        probeCount = 0L;
        collisionCount = 0L;
        rehashCount = 0L;
    }

    /** Carries since-reset metrics across an atomic staged KeySpace replacement. */
    public void addMetrics(long probes, long collisions, long rehashes) {
        if (probes < 0L || collisions < 0L || collisions > probes || rehashes < 0L) {
            throw new IllegalArgumentException("invalid key space metrics");
        }
        long nextProbes = checkedMetricAdd(probeCount, probes);
        long nextCollisions = checkedMetricAdd(collisionCount, collisions);
        long nextRehashes = checkedMetricAdd(rehashCount, rehashes);
        probeCount = nextProbes;
        collisionCount = nextCollisions;
        rehashCount = nextRehashes;
    }

    /** Must run before a generated insertion probe; it may move raw hash slots by rehashing. */
    public void ensureInsertCapacity() {
        if (2L * ((long) used + 1L) >= (long) states.length) {
            if (states.length >= MAX_CAPACITY) {
                throw new IllegalStateException("key space capacity exhausted");
            }
            rehash(states.length << 1);
        }
    }

    public long retainedBytesAfterEnsureAdditional(int additional) {
        return 13L * (long) targetCapacity(additional);
    }

    public long allocationBytesDuringEnsureAdditional(int additional) {
        return requiresRehash(additional)
                ? retainedBytesAfterEnsureAdditional(additional) : 0L;
    }

    public void ensureAdditionalCapacity(int additional) {
        int target = targetCapacity(additional);
        if (requiresRehash(additional)) rehash(target);
    }

    public int firstSlot(long hash) {
        return mix(hash) & (states.length - 1);
    }

    public int nextSlot(int slot) {
        if (slot < 0 || slot >= states.length) {
            throw new IllegalArgumentException("slot out of range");
        }
        collisionCount = checkedMetricAdd(collisionCount, 1L);
        return (slot + 1) & (states.length - 1);
    }

    public boolean isEmpty(int slot) {
        byte value = state(slot);
        probeCount = checkedMetricAdd(probeCount, 1L);
        return value == EMPTY;
    }

    public boolean isLive(int slot) {
        return state(slot) == LIVE;
    }

    public long hashAt(int slot) {
        requireLive(slot);
        return hashes[slot];
    }

    public int rowAt(int slot) {
        requireLive(slot);
        return rows[slot];
    }

    /**
     * Installs a slot selected by generated full-equality probing. No object key is accepted here.
     */
    public void putAt(int slot, long hash, int rowSlot) {
        if (rowSlot < 0 || slot < 0 || slot >= states.length || states[slot] == LIVE) {
            throw new IllegalArgumentException("invalid composite key insertion");
        }
        if (states[slot] == EMPTY) {
            used++;
        }
        states[slot] = LIVE;
        hashes[slot] = hash;
        rows[slot] = rowSlot;
        size++;
    }

    public void removeAt(int slot) {
        requireLive(slot);
        states[slot] = DELETED;
        size--;
    }

    public void updateRowAt(int slot, int rowSlot) {
        requireLive(slot);
        if (rowSlot < 0) {
            throw new IllegalArgumentException("rowSlot must be non-negative");
        }
        rows[slot] = rowSlot;
    }

    public void clear() {
        Arrays.fill(states, EMPTY);
        size = 0;
        used = 0;
    }

    public void releaseStorage() {
        hashes = new long[0];
        rows = new int[0];
        states = new byte[0];
        size = 0;
        used = 0;
    }

    private byte state(int slot) {
        if (slot < 0 || slot >= states.length) {
            throw new IllegalArgumentException("slot out of range");
        }
        return states[slot];
    }

    private void requireLive(int slot) {
        if (!isLive(slot)) {
            throw new IllegalArgumentException("slot is not live");
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

    private static void requireAdditional(int additional) {
        if (additional < 0) {
            throw new IllegalArgumentException("additional must be non-negative");
        }
    }

    private void rehash(int capacity) {
        long[] oldHashes = hashes;
        int[] oldRows = rows;
        byte[] oldStates = states;

        long[] newHashes = new long[capacity];
        int[] newRows = new int[capacity];
        byte[] newStates = new byte[capacity];
        int rebuiltSize = 0;
        long rebuildProbes = 0L;
        long rebuildCollisions = 0L;
        for (int index = 0; index < oldStates.length; index++) {
            if (oldStates[index] == LIVE) {
                int mask = newStates.length - 1;
                int slot = mix(oldHashes[index]) & mask;
                rebuildProbes++;
                while (newStates[slot] != EMPTY) {
                    rebuildCollisions++;
                    slot = (slot + 1) & mask;
                    rebuildProbes++;
                }
                newStates[slot] = LIVE;
                newHashes[slot] = oldHashes[index];
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

        hashes = newHashes;
        rows = newRows;
        states = newStates;
        used = rebuiltSize;
        probeCount = committedProbeCount;
        collisionCount = committedCollisionCount;
        rehashCount = committedRehashCount;
    }

    private static long checkedMetricAdd(long current, long delta) {
        if (delta < 0L || Long.MAX_VALUE - current < delta) {
            throw new IllegalStateException("key space metric overflow");
        }
        return current + delta;
    }

    private static int mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        value ^= value >>> 33;
        return (int) (value ^ (value >>> 32));
    }
}
