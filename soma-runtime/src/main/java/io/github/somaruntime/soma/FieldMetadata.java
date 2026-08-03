package io.github.somaruntime.soma;

/** Immutable logical Field metadata snapshot. */
public final class FieldMetadata {
    private final String logicalPath;
    private final String logicalType;
    private final boolean nullable;
    private final boolean key;
    private final boolean indexed;

    private FieldMetadata(
            String logicalPath,
            String logicalType,
            boolean nullable,
            boolean key,
            boolean indexed) {
        this.logicalPath = logicalPath;
        this.logicalType = logicalType;
        this.nullable = nullable;
        this.key = key;
        this.indexed = indexed;
    }

    static FieldMetadata trustedCreate(
            String logicalPath,
            String logicalType,
            boolean nullable,
            boolean key,
            boolean indexed) {
        return new FieldMetadata(logicalPath, logicalType, nullable, key, indexed);
    }

    public String logicalPath() {
        return logicalPath;
    }

    public String logicalType() {
        return logicalType;
    }

    public boolean nullable() {
        return nullable;
    }

    public boolean key() {
        return key;
    }

    public boolean indexed() {
        return indexed;
    }
}
