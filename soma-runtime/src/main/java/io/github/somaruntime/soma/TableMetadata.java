package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

/** Detached immutable snapshot of one generated Table. */
public final class TableMetadata {

    static {
        SomaSharedSecrets.setTableMetadataAccess(
                new SomaSharedSecrets.TableMetadataAccess() {
                    @Override
                    public TableMetadata create(
                            String logicalName,
                            long size,
                            long capacity,
                            long managed,
                            long plainEquivalent,
                            long representation,
                            long encodedChunks) {
                        return new TableMetadata(
                                logicalName, size, capacity, managed,
                                plainEquivalent, representation, encodedChunks);
                    }
                });
    }

    private final String logicalName;
    private final long size;
    private final long capacity;
    private final long managedBytes;
    private final long plainEquivalentBytes;
    private final long representationBytes;
    private final long encodedChunkCount;

    private TableMetadata(
            String logicalName,
            long size,
            long capacity,
            long managedBytes,
            long plainEquivalentBytes,
            long representationBytes,
            long encodedChunkCount) {
        this.logicalName = logicalName;
        this.size = size;
        this.capacity = capacity;
        this.managedBytes = managedBytes;
        this.plainEquivalentBytes = plainEquivalentBytes;
        this.representationBytes = representationBytes;
        this.encodedChunkCount = encodedChunkCount;
    }

    public String logicalName() { return logicalName; }
    public long size() { return size; }
    public long capacity() { return capacity; }
    public long managedBytes() { return managedBytes; }
    public long plainEquivalentBytes() { return plainEquivalentBytes; }
    public long representationBytes() { return representationBytes; }
    public long savingsBytes() {
        return Math.max(0L, plainEquivalentBytes - representationBytes);
    }
    public boolean encoded() { return encodedChunkCount != 0L; }
    public long encodedChunkCount() { return encodedChunkCount; }
}
