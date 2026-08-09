package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaOperation;

/** Synchronous AUTO representation choice for a single complete Chunk. */
final class ChunkEncoder {

    private static final long CHUNK_HEADER_BYTES = 96L;
    private static final long ARRAY_HEADER_BYTES = 32L;

    private ChunkEncoder() {
    }

    static TableChunk finish(
            PlainChunk plain,
            int logicalRows,
            SomaCompression policy,
            GeneratedTableLayout layout,
            SomaOperation operation,
            Object provenance) {
        if (policy == SomaCompression.OFF
                || logicalRows != plain.rowCapacity()) {
            return plain;
        }
        EncodedChunk encoded = EncodedChunk.encode(
                plain, logicalRows, layout, operation, provenance);
        return encoded.managedBytes(layout, operation, provenance)
                < plain.managedBytes(layout, operation, provenance)
                ? encoded : plain;
    }

    static long plainManagedBytes(
            GeneratedTableLayout layout,
            int rows,
            SomaOperation operation,
            Object provenance) {
        long payload = CheckedLong.multiply(
                rows, layout.rowWidthBytes(), operation, provenance);
        long arrays = CheckedLong.multiply(
                layout.leafCount(), ARRAY_HEADER_BYTES, operation, provenance);
        return CheckedLong.add(
                CheckedLong.add(payload, arrays, operation, provenance),
                CHUNK_HEADER_BYTES,
                operation,
                provenance);
    }
}
