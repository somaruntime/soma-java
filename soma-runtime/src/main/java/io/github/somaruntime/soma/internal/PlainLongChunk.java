package io.github.somaruntime.soma.internal;

final class PlainLongChunk implements LongChunk {

    private final long[][] columns;

    PlainLongChunk(int fieldCount, int rowCount) {
        this.columns = new long[fieldCount][];
        for (int field = 0; field < fieldCount; field++) {
            columns[field] = new long[rowCount];
        }
    }

    private PlainLongChunk(long[][] columns) {
        this.columns = columns;
    }

    @Override
    public ChunkRepresentation representation() {
        return ChunkRepresentation.PLAIN;
    }

    long[][] columns() {
        return columns;
    }

    PlainLongChunk copy() {
        long[][] copied = new long[columns.length][];
        for (int field = 0; field < columns.length; field++) {
            copied[field] = columns[field].clone();
        }
        return new PlainLongChunk(copied);
    }
}
