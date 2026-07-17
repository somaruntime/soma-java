package com.hgtech.soma.runtime;

import java.util.Arrays;

/** Generated-runtime construction and ownership protocol for {@link IndexSnapshot}。 */
public final class IndexSnapshots {
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
        return new IndexSnapshot(
                ownerToken, structuralEpoch, Arrays.copyOf(indexes, length));
    }

    public static boolean isOwnedBy(IndexSnapshot snapshot, Object ownerToken) {
        if (snapshot == null) throw new NullPointerException("snapshot");
        if (ownerToken == null) throw new NullPointerException("ownerToken");
        return snapshot.ownerToken() == ownerToken;
    }
}
