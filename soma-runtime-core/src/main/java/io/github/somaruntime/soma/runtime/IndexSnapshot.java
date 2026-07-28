package io.github.somaruntime.soma.runtime;

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
    private final int singleIndex;
    private final int size;

    IndexSnapshot(Object ownerToken, long structuralEpoch, int[] indexes) {
        this.ownerToken = ownerToken;
        this.structuralEpoch = structuralEpoch;
        this.indexes = indexes;
        this.singleIndex = 0;
        this.size = indexes.length;
    }

    IndexSnapshot(Object ownerToken, long structuralEpoch, int singleIndex) {
        this.ownerToken = ownerToken;
        this.structuralEpoch = structuralEpoch;
        this.indexes = null;
        this.singleIndex = singleIndex;
        this.size = 1;
    }

    Object ownerToken() {
        return ownerToken;
    }

    public long structuralEpoch() {
        return structuralEpoch;
    }

    public int size() {
        return size;
    }

    public int indexAt(int position) {
        if (position < 0 || position >= size) {
            throw new IndexOutOfBoundsException("snapshot position out of range");
        }
        return size == 1 ? singleIndex : indexes[position];
    }

    public int[] toArray() {
        return size == 1
                ? new int[] {singleIndex}
                : Arrays.copyOf(indexes, size);
    }
}
