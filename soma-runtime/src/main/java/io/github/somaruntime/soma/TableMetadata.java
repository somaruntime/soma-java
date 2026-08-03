package io.github.somaruntime.soma;

/** Immutable Table metadata snapshot detached from the Table's published StateRoot. */
public final class TableMetadata {
    private final String logicalName;
    private final long size;
    private final long capacity;
    private final long stateVersion;
    private final long payloadBytes;
    private final long representationBytes;
    private final boolean encodedRepresentation;
    private final SomaCompression compression;

    private TableMetadata(
            String logicalName,
            long size,
            long capacity,
            long stateVersion,
            long payloadBytes,
            long representationBytes,
            boolean encodedRepresentation,
            SomaCompression compression) {
        this.logicalName = logicalName;
        this.size = size;
        this.capacity = capacity;
        this.stateVersion = stateVersion;
        this.payloadBytes = payloadBytes;
        this.representationBytes = representationBytes;
        this.encodedRepresentation = encodedRepresentation;
        this.compression = compression;
    }

    static TableMetadata trustedCreate(
            String logicalName,
            long size,
            long capacity,
            long stateVersion,
            long payloadBytes,
            long representationBytes,
            boolean encodedRepresentation,
            SomaCompression compression) {
        return new TableMetadata(
                logicalName, size, capacity, stateVersion, payloadBytes, representationBytes,
                encodedRepresentation, compression);
    }

    public String logicalName() {
        return logicalName;
    }

    public long size() {
        return size;
    }

    public long capacity() {
        return capacity;
    }

    public long stateVersion() {
        return stateVersion;
    }

    public long payloadBytes() {
        return payloadBytes;
    }

    public long representationBytes() {
        return representationBytes;
    }

    public boolean encodedRepresentation() {
        return encodedRepresentation;
    }

    public SomaCompression compression() {
        return compression;
    }
}
