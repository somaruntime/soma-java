package com.hgtech.soma.runtime;

import java.util.Arrays;

/**
 * Generated-runtime construction and optional ownership-check protocol for {@link IndexSnapshot}.
 * This helper does not turn a detached index sequence into a stable row snapshot.
 */
public final class IndexSnapshots {
    private static final int[] EMPTY = new int[0];

    private IndexSnapshots() {
    }

    public static IndexSnapshot copyOf(
            Object ownerToken,
            long structuralEpoch,
            int[] indexes,
            int length) {
        if (ownerToken == null) throw new NullPointerException("ownerToken");
        if (indexes == null) throw new NullPointerException("indexes");
        if (structuralEpoch < 0L || length < 0 || length > indexes.length) {
            throw new IllegalArgumentException("invalid index snapshot");
        }
        if (length == 0) {
            return new IndexSnapshot(ownerToken, structuralEpoch, EMPTY);
        }
        if (length == 1) {
            return new IndexSnapshot(ownerToken, structuralEpoch, indexes[0]);
        }
        return new IndexSnapshot(ownerToken, structuralEpoch, Arrays.copyOf(indexes, length));
    }

    public static boolean isOwnedBy(IndexSnapshot snapshot, Object ownerToken) {
        if (snapshot == null) throw new NullPointerException("snapshot");
        if (ownerToken == null) throw new NullPointerException("ownerToken");
        return snapshot.ownerToken() == ownerToken;
    }
}
