package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

/** Detached immutable snapshot of one logical generated Field endpoint. */
public final class FieldMetadata {

    static {
        SomaSharedSecrets.setFieldMetadataAccess(
                new SomaSharedSecrets.FieldMetadataAccess() {
                    @Override
                    public FieldMetadata create(
                            String path,
                            String type,
                            boolean nullable,
                            boolean key,
                            boolean indexed,
                            boolean equality,
                            boolean ordered,
                            long plainEquivalent,
                            long representation,
                            boolean encoded) {
                        return new FieldMetadata(
                                path, type, nullable, key, indexed,
                                equality, ordered, plainEquivalent,
                                representation, encoded);
                    }
                });
    }

    private final String logicalPath;
    private final String logicalType;
    private final boolean nullable;
    private final boolean key;
    private final boolean indexed;
    private final boolean equalityComparable;
    private final boolean ordered;
    private final long plainEquivalentBytes;
    private final long representationBytes;
    private final boolean encoded;

    private FieldMetadata(
            String logicalPath,
            String logicalType,
            boolean nullable,
            boolean key,
            boolean indexed,
            boolean equalityComparable,
            boolean ordered,
            long plainEquivalentBytes,
            long representationBytes,
            boolean encoded) {
        this.logicalPath = logicalPath;
        this.logicalType = logicalType;
        this.nullable = nullable;
        this.key = key;
        this.indexed = indexed;
        this.equalityComparable = equalityComparable;
        this.ordered = ordered;
        this.plainEquivalentBytes = plainEquivalentBytes;
        this.representationBytes = representationBytes;
        this.encoded = encoded;
    }

    public String logicalPath() { return logicalPath; }
    public String logicalType() { return logicalType; }
    public boolean nullable() { return nullable; }
    public boolean key() { return key; }
    public boolean indexed() { return indexed; }
    public boolean equalityComparable() { return equalityComparable; }
    public boolean ordered() { return ordered; }
    public long plainEquivalentBytes() { return plainEquivalentBytes; }
    public long representationBytes() { return representationBytes; }
    public long savingsBytes() {
        return Math.max(0L, plainEquivalentBytes - representationBytes);
    }
    public boolean encoded() { return encoded; }
}
