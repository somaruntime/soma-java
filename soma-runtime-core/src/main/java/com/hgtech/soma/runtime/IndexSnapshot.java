package com.hgtech.soma.runtime;

import java.util.Arrays;

/** Detached current-Index sequence bound to one table identity and structural epoch。 */
public final class IndexSnapshot {
    private final Object ownerToken;
    private final long structuralEpoch;
    private final int[] indexes;

    IndexSnapshot(Object ownerToken, long structuralEpoch, int[] indexes) {
        this.ownerToken = ownerToken;
        this.structuralEpoch = structuralEpoch;
        this.indexes = indexes;
    }

    Object ownerToken() {
        return ownerToken;
    }

    public long structuralEpoch() {
        return structuralEpoch;
    }

    public int size() {
        return indexes.length;
    }

    public int indexAt(int position) {
        if (position < 0 || position >= indexes.length) {
            throw new IndexOutOfBoundsException("snapshot position out of range");
        }
        return indexes[position];
    }

    public int[] toArray() {
        return Arrays.copyOf(indexes, indexes.length);
    }
}
