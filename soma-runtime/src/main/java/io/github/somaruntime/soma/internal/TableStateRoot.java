package io.github.somaruntime.soma.internal;

/** One atomically published authoritative Table generation. */
final class TableStateRoot {

    final int size;
    final int capacity;
    final long stateVersion;
    final long managedBytes;
    final TableChunkDirectory directory;
    final IdentityHashIndex key;
    final IdentityHashIndex[] indexes;

    TableStateRoot(
            int size,
            int capacity,
            long stateVersion,
            long managedBytes,
            TableChunkDirectory directory,
            IdentityHashIndex key,
            IdentityHashIndex[] indexes) {
        this.size = size;
        this.capacity = capacity;
        this.stateVersion = stateVersion;
        this.managedBytes = managedBytes;
        this.directory = directory;
        this.key = key;
        this.indexes = indexes;
    }

    static TableStateRoot empty(GeneratedTableLayout layout, int chunkRows) {
        IdentityHashIndex key = layout.keyFieldIndex() < 0
                ? null
                : IdentityHashIndex.empty(
                        layout, layout.keyFieldIndex(), true);
        IdentityHashIndex[] indexes = new IdentityHashIndex[layout.indexCount()];
        for (int ordinal = 0; ordinal < indexes.length; ordinal++) {
            indexes[ordinal] = IdentityHashIndex.empty(
                    layout, layout.indexFieldIndex(ordinal), false);
        }
        return new TableStateRoot(
                0,
                0,
                0L,
                0L,
                TableChunkDirectory.empty(chunkRows, layout),
                key,
                indexes);
    }
}
