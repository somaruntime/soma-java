package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

/** 基于 primitive open addressing 的 long key 到 packed RowSlot 映射。 */
public final class HashLongKeySpace {
    private static final byte EMPTY = 0;
    private static final byte LIVE = 1;
    private static final byte DELETED = 2;

    private long[] keys;
    private int[] rows;
    private byte[] states;
    private int size;
    private int used;

    public HashLongKeySpace(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be non-negative");
        }
        long required = Math.max(4L, 2L * (long) expectedSize);
        int capacity = 4;
        while (capacity < required) {
            if (capacity > (1 << 29)) {
                throw new IllegalArgumentException("expectedSize is too large");
            }
            capacity <<= 1;
        }
        keys = new long[capacity];
        rows = new int[capacity];
        states = new byte[capacity];
    }

    public int size() { return size; }

    public boolean contains(long key) { return rowOf(key) >= 0; }

    public int rowOf(long key) {
        int slot = locate(key);
        return slot < 0 ? -1 : rows[slot];
    }

    public void put(long key, int rowSlot) {
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

    public void remove(long key) {
        int slot = locate(key);
        if (slot < 0) {
            throw new IllegalArgumentException("missing key");
        }
        states[slot] = DELETED;
        size--;
    }

    public void updateRow(long key, int rowSlot) {
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

    private void ensureInsertCapacity() {
        if ((used + 1) * 2 >= states.length) {
            if (states.length > (1 << 29)) {
                throw new IllegalStateException("key space capacity exhausted");
            }
            rehash(states.length << 1);
        }
    }

    private void rehash(int capacity) {
        long[] oldKeys = keys;
        int[] oldRows = rows;
        byte[] oldStates = states;
        keys = new long[capacity];
        rows = new int[capacity];
        states = new byte[capacity];
        used = 0;
        int oldSize = size;
        size = 0;
        for (int index = 0; index < oldStates.length; index++) {
            if (oldStates[index] == LIVE) {
                put(oldKeys[index], oldRows[index]);
            }
        }
        if (size != oldSize) {
            throw new IllegalStateException("rehash identity failure");
        }
    }

    private int locate(long key) {
        int mask = states.length - 1;
        int slot = mix(key) & mask;
        while (states[slot] != EMPTY) {
            if (states[slot] == LIVE && keys[slot] == key) {
                return slot;
            }
            slot = (slot + 1) & mask;
        }
        return -1;
    }

    private int insertionSlot(long key) {
        int mask = states.length - 1;
        int slot = mix(key) & mask;
        int deleted = -1;
        while (states[slot] != EMPTY) {
            if (states[slot] == DELETED && deleted < 0) {
                deleted = slot;
            }
            slot = (slot + 1) & mask;
        }
        return deleted >= 0 ? deleted : slot;
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
