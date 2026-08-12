package io.github.somaruntime.soma.internal;

/**
 * Nonproduction evidence seam which isolates PLAIN Chunk traversal and SOMA's
 * exact integer accumulator from public query lifecycle cost.
 */
public final class DirectChunkLongSum {

    private static final int CHUNK_ROWS = 65536;

    private final PlainChunk[] chunks;
    private final int[] logicalRows;
    private final Object provenance = new Object();

    public DirectChunkLongSum(long[] source) {
        if (source == null) throw new NullPointerException("source");
        GeneratedTableLayout layout = GeneratedTableLayout.create(
                "DirectChunkLongSum",
                0,
                new byte[] {GeneratedTableLayout.LONG},
                new byte[] {GeneratedTableLayout.EQ_LONG},
                new int[] {0},
                new int[] {1},
                new boolean[] {false},
                -1,
                new int[0]);
        int count = source.length == 0
                ? 0 : (source.length - 1) / CHUNK_ROWS + 1;
        chunks = new PlainChunk[count];
        logicalRows = new int[count];
        int offset = 0;
        for (int ordinal = 0; ordinal < count; ordinal++) {
            int rows = Math.min(CHUNK_ROWS, source.length - offset);
            PlainChunk chunk = new PlainChunk(layout, CHUNK_ROWS);
            System.arraycopy(source, offset, chunk.longs(0), 0, rows);
            chunks[ordinal] = chunk;
            logicalRows[ordinal] = rows;
            offset += rows;
        }
    }

    public long sum() {
        Signed128Accumulator accumulator = new Signed128Accumulator();
        for (int ordinal = 0; ordinal < chunks.length; ordinal++) {
            accumulator.addLongs(chunks[ordinal].longs(0), logicalRows[ordinal]);
        }
        return accumulator.longValue(provenance);
    }
}
