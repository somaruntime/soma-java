package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

/** Bounded non-negative int key to packed RowSlot mapping. */
public final class SparseIntKeySpace {
    private final int maximumKey;
    private final int[] slotByKey;
    private int[] keyBySlot = new int[0];
    private int size;

    public SparseIntKeySpace(int maximumKey) {
        if (maximumKey < 0 || maximumKey == Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "maximumKey must be non-negative and array-representable");
        }
        this.maximumKey = maximumKey;
        this.slotByKey = new int[maximumKey + 1];
        Arrays.fill(slotByKey, -1);
    }

    public int size() { return size; }

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

    public void clear() {
        for (int row = 0; row < size; row++) {
            slotByKey[keyBySlot[row]] = -1;
        }
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
}
