package io.github.somaruntime.soma.dataflow;

/**
 * Detached finite-window membership represented by current physical indexes.
 *
 * <p>It is not a stable row snapshot and becomes stale after source mutation.</p>
 */
public final class WindowIndexResult {
    private final String source;
    private final long structuralEpoch;
    private final int[] offsets;
    private final int[] indexes;

    WindowIndexResult(
            String source,
            long structuralEpoch,
            int[] offsets,
            int[] indexes) {
        this.source = source;
        this.structuralEpoch = structuralEpoch;
        this.offsets = offsets;
        this.indexes = indexes;
    }

    public String source() {
        return source;
    }

    public long structuralEpoch() {
        return structuralEpoch;
    }

    public int windowCount() {
        return offsets.length - 1;
    }

    public int windowSize(int window) {
        checkWindow(window);
        return offsets[window + 1] - offsets[window];
    }

    public int indexAt(int window, int position) {
        checkWindow(window);
        int start = offsets[window];
        int size = offsets[window + 1] - start;
        if (position < 0 || position >= size) {
            throw new IndexOutOfBoundsException("window position out of range");
        }
        return indexes[start + position];
    }

    private void checkWindow(int window) {
        if (window < 0 || window >= offsets.length - 1) {
            throw new IndexOutOfBoundsException("window ordinal out of range");
        }
    }
}
