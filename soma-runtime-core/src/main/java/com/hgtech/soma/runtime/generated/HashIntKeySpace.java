package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

/** Primitive open-addressed int key to packed RowSlot mapping. */
public final class HashIntKeySpace {
    private static final byte EMPTY = 0;
    private static final byte LIVE = 1;
    private static final byte DELETED = 2;

    private int[] keys;
    private int[] rows;
    private byte[] states;
    private int size;
    private int used;

    public HashIntKeySpace(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be non-negative");
        }
        int capacity = 4;
        while (capacity < expectedSize * 2) {
            capacity <<= 1;
        }
        keys = new int[capacity];
        rows = new int[capacity];
        states = new byte[capacity];
    }

    public int size() { return size; }

    public boolean contains(int key) { return rowOf(key) >= 0; }

    public int rowOf(int key) {
        int slot = locate(key);
        return slot < 0 ? -1 : rows[slot];
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
        if (size * 4 < used && states.length > 4) {
            rehash(states.length);
        }
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

    private void ensureInsertCapacity() {
        if ((used + 1) * 2 >= states.length) {
            rehash(states.length << 1);
        }
    }

    private void rehash(int capacity) {
        int[] oldKeys = keys;
        int[] oldRows = rows;
        byte[] oldStates = states;
        keys = new int[capacity];
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

    private int locate(int key) {
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

    private int insertionSlot(int key) {
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

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        return value ^ (value >>> 16);
    }
}
