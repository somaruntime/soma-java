package io.github.somaruntime.soma.internal;

final class LongStateRoot {

    final long size;
    final long capacity;
    final long stateVersion;
    final long managedBytes;
    final PagedChunkDirectory directory;
    final LongKeyIndex keyIndex;

    LongStateRoot(
            long size,
            long capacity,
            long stateVersion,
            long managedBytes,
            PagedChunkDirectory directory,
            LongKeyIndex keyIndex) {
        this.size = size;
        this.capacity = capacity;
        this.stateVersion = stateVersion;
        this.managedBytes = managedBytes;
        this.directory = directory;
        this.keyIndex = keyIndex;
    }

    static LongStateRoot empty() {
        return new LongStateRoot(
                0L,
                0L,
                0L,
                0L,
                PagedChunkDirectory.empty(),
                LongKeyIndex.empty());
    }
}
