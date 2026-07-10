package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

/**
 * 复合 key 的 primitive hash probe substrate。
 *
 * <p>本类只保存 hash 与 packed RowSlot，不把 schema value/object 放入 runtime storage。
 * generated table 在同 hash candidate 上静态展开完整 leaf equality，然后调用本协议完成
 * insert/remove/row-move。因此 hash collision 不能被误判为 identity equality。</p>
 */
public final class HashCompositeKeySpace {
    private static final byte EMPTY = 0;
    private static final byte LIVE = 1;
    private static final byte DELETED = 2;

    private long[] hashes;
    private int[] rows;
    private byte[] states;
    private int size;
    private int used;

    public HashCompositeKeySpace(int expectedSize) {
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
        hashes = new long[capacity];
        rows = new int[capacity];
        states = new byte[capacity];
    }

    public int size() {
        return size;
    }

    /** Must run before a generated insertion probe; it may move raw hash slots by rehashing. */
    public void ensureInsertCapacity() {
        if ((used + 1) * 2 >= states.length) {
            if (states.length > (1 << 29)) {
                throw new IllegalStateException("key space capacity exhausted");
            }
            rehash(states.length << 1);
        }
    }

    public int firstSlot(long hash) {
        return mix(hash) & (states.length - 1);
    }

    public int nextSlot(int slot) {
        if (slot < 0 || slot >= states.length) {
            throw new IllegalArgumentException("slot out of range");
        }
        return (slot + 1) & (states.length - 1);
    }

    public boolean isEmpty(int slot) {
        return state(slot) == EMPTY;
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

    private void rehash(int capacity) {
        long[] oldHashes = hashes;
        int[] oldRows = rows;
        byte[] oldStates = states;
        hashes = new long[capacity];
        rows = new int[capacity];
        states = new byte[capacity];
        used = 0;
        int previousSize = size;
        size = 0;
        for (int index = 0; index < oldStates.length; index++) {
            if (oldStates[index] == LIVE) {
                int slot = rawInsertionSlot(oldHashes[index]);
                states[slot] = LIVE;
                hashes[slot] = oldHashes[index];
                rows[slot] = oldRows[index];
                used++;
                size++;
            }
        }
        if (size != previousSize) {
            throw new IllegalStateException("rehash identity failure");
        }
    }

    private int rawInsertionSlot(long hash) {
        int slot = firstSlot(hash);
        while (states[slot] != EMPTY) {
            slot = nextSlot(slot);
        }
        return slot;
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
