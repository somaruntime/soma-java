package com.hgtech.soma.runtime;

import java.util.Arrays;

/**
 * Detached copy of a current-Index sequence produced by one table operation.
 *
 * <p>An index is only a physical location in the source table's current packed state; this
 * object is not a stable row snapshot or row identity. The caller may consume the indexes only
 * immediately in one synchronous read-only batch and must treat the snapshot as invalid after
 * any source-table mutation or lifecycle change. The captured structural epoch supports optional
 * boundary diagnostics; {@code indexAt} deliberately does not perform a live table check.</p>
 */
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
