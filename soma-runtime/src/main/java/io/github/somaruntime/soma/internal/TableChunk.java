package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaOperation;

/** Representation-independent physical Chunk contract. */
interface TableChunk {

    interface PrimitiveVisitor {
        boolean visit(long raw);
    }

    ChunkRepresentation representation();

    boolean booleanValue(int slot, int offset);
    byte byteValue(int slot, int offset);
    short shortValue(int slot, int offset);
    char charValue(int slot, int offset);
    int intValue(int slot, int offset);
    long longValue(int slot, int offset);
    float floatValue(int slot, int offset);
    double doubleValue(int slot, int offset);
    Object referenceValue(int slot, int offset);

    /**
     * Representation-aware sequential primitive traversal. The physical
     * representation owns decoding; the execution engine owns the operation.
     */
    boolean visitPrimitive(
            byte kind,
            int slot,
            int logicalRows,
            PrimitiveVisitor visitor);

    void read(int offset, TypedValues destination, GeneratedTableLayout layout);

    TableChunk mutableCopy(GeneratedTableLayout layout);

    void write(int offset, TypedValues source, GeneratedTableLayout layout);

    void clear(int offset, GeneratedTableLayout layout);

    PlainChunk materialize(GeneratedTableLayout layout);

    TableChunk finish(
            int logicalRows,
            SomaCompression policy,
            GeneratedTableLayout layout,
            SomaOperation operation,
            Object provenance);

    long managedBytes(
            GeneratedTableLayout layout,
            SomaOperation operation,
            Object provenance);

    long plainEquivalentBytesForField(
            GeneratedTableLayout layout,
            int fieldIndex,
            SomaOperation operation,
            Object provenance);

    long representationBytesForField(
            GeneratedTableLayout layout,
            int fieldIndex,
            SomaOperation operation,
            Object provenance);

    boolean hasEncodedRepresentation();
}
