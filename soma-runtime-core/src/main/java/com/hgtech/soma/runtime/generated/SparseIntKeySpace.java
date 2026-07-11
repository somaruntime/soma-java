package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

/** Bounded non-negative int key to packed RowSlot mapping. */
public final class SparseIntKeySpace implements IntKeySpace {
    private final int maximumKey;
    private int[] slotByKey;
    private int[] keyBySlot = new int[0];
    private int size;

    public SparseIntKeySpace(int maximumKey) {
        this(maximumKey, 0);
    }

    SparseIntKeySpace(int maximumKey, int expectedSize) {
        if (maximumKey < 0 || maximumKey == Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "maximumKey must be non-negative and array-representable");
        }
        if (expectedSize < 0 || expectedSize > maximumKey + 1L) {
            throw new IllegalArgumentException("expectedSize is outside sparse domain");
        }
        this.maximumKey = maximumKey;
        this.slotByKey = new int[maximumKey + 1];
        this.keyBySlot = new int[denseCapacityFor(expectedSize)];
        Arrays.fill(slotByKey, -1);
    }

    public int size() { return size; }

    @Override
    public String implementation() { return "sparse-int-v1"; }

    @Override
    public int capacity() { return slotByKey.length; }

    @Override
    public int used() { return size; }

    @Override
    public long probeCount() { return 0L; }

    @Override
    public long collisionCount() { return 0L; }

    @Override
    public long rehashCount() { return 0L; }

    @Override
    public long retainedBytes() {
        return 4L * ((long) slotByKey.length + (long) keyBySlot.length);
    }

    @Override
    public void resetMetrics() {
    }

    @Override
    public void addMetrics(long probes, long collisions, long rehashes) {
        if (probes != 0L || collisions != 0L || rehashes != 0L) {
            throw new IllegalArgumentException("sparse key space has no hash metrics");
        }
    }

    public int maximumKey() { return maximumKey; }

    public int sparseCapacity() { return slotByKey.length; }

    public int denseCapacity() { return keyBySlot.length; }

    public boolean contains(int key) { return rowOf(key) >= 0; }

    public int rowOf(int key) {
        if (key < 0 || key > maximumKey) {
            return -1;
        }
        int row = slotByKey[key];
        return row >= 0 && row < size && keyBySlot[row] == key ? row : -1;
    }

    @Override
    public void requireInsertKey(
            int key, String table, String keyField, String operation) {
        if (key < 0 || key > maximumKey) {
            throw RuntimeFailures.invalidKeyDomain(
                    table, keyField, implementation(), maximumKey, operation);
        }
    }

    @Override
    public long retainedBytesAfterEnsureAdditional(int additional) {
        int dense = targetDenseCapacity(additional);
        return 4L * ((long) slotByKey.length + (long) dense);
    }

    @Override
    public long allocationBytesDuringEnsureAdditional(int additional) {
        int dense = targetDenseCapacity(additional);
        return dense == keyBySlot.length ? 0L : 4L * (long) dense;
    }

    @Override
    public void ensureAdditionalCapacity(int additional) {
        int dense = targetDenseCapacity(additional);
        if (dense != keyBySlot.length) keyBySlot = Arrays.copyOf(keyBySlot, dense);
    }

    public void put(int key, int rowSlot) {
        requireKey(key);
        if (rowSlot != size || rowOf(key) >= 0) {
            throw new IllegalArgumentException("invalid sparse key insertion");
        }
        ensureSlotCapacity(size + 1);
        keyBySlot[size] = key;
        slotByKey[key] = size;
        size++;
    }

    public void removeAt(int rowSlot) {
        if (rowSlot < 0 || rowSlot >= size) {
            throw new IllegalArgumentException("invalid rowSlot");
        }
        int removedKey = keyBySlot[rowSlot];
        slotByKey[removedKey] = -1;
        for (int row = rowSlot + 1; row < size; row++) {
            int key = keyBySlot[row];
            keyBySlot[row - 1] = key;
            slotByKey[key] = row - 1;
        }
        size--;
    }

    @Override
    public void remove(int key) {
        requireKey(key);
        int rowSlot = rowOf(key);
        if (rowSlot < 0) {
            throw new IllegalArgumentException("missing key");
        }
        int last = size - 1;
        slotByKey[key] = -1;
        if (rowSlot != last) {
            int movedKey = keyBySlot[last];
            keyBySlot[rowSlot] = movedKey;
            slotByKey[movedKey] = rowSlot;
        }
        keyBySlot[last] = 0;
        size--;
    }

    @Override
    public void updateRow(int key, int rowSlot) {
        requireKey(key);
        if (rowSlot < 0 || rowSlot >= size || rowOf(key) < 0) {
            throw new IllegalArgumentException("missing key or invalid rowSlot");
        }
        int previous = slotByKey[key];
        if (previous != rowSlot) {
            int displaced = keyBySlot[rowSlot];
            keyBySlot[rowSlot] = key;
            slotByKey[key] = rowSlot;
            if (previous < size && displaced != key) {
                keyBySlot[previous] = displaced;
                slotByKey[displaced] = previous;
            }
        }
    }

    public void clear() {
        for (int row = 0; row < size; row++) {
            slotByKey[keyBySlot[row]] = -1;
        }
        size = 0;
    }

    @Override
    public void releaseStorage() {
        slotByKey = new int[0];
        keyBySlot = new int[0];
        size = 0;
    }

    private void requireKey(int key) {
        if (key < 0 || key > maximumKey) {
            throw new IllegalArgumentException("key outside configured sparse domain");
        }
    }

    private void ensureSlotCapacity(int required) {
        if (required > keyBySlot.length) {
            long doubled = Math.max(4L, 2L * (long) keyBySlot.length);
            long candidate = Math.max((long) required, doubled);
            if (candidate > Integer.MAX_VALUE - 8L) {
                throw new IllegalStateException("sparse key space capacity exhausted");
            }
            int next = (int) candidate;
            keyBySlot = Arrays.copyOf(keyBySlot, next);
        }
    }

    private int targetDenseCapacity(int additional) {
        if (additional < 0 || (long) size + (long) additional > slotByKey.length) {
            throw new IllegalArgumentException("additional keys exceed sparse domain");
        }
        int required = size + additional;
        return required <= keyBySlot.length
                ? keyBySlot.length : denseCapacityFor(required);
    }

    private static int denseCapacityFor(int required) {
        if (required < 0) {
            throw new IllegalArgumentException("required must be non-negative");
        }
        if (required == 0) return 0;
        long capacity = 4L;
        while (capacity < required) {
            if (capacity > (Integer.MAX_VALUE - 8L) / 2L) {
                throw new IllegalArgumentException("sparse key space capacity exhausted");
            }
            capacity *= 2L;
        }
        return (int) capacity;
    }
}
