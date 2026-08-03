package io.github.somaruntime.soma;

/** Detached immutable entry in an integer-keyed grouped result. */
public final class IntGroupedLongEntry {
    private final int key;
    private final long value;

    private IntGroupedLongEntry(int key, long value) {
        this.key = key;
        this.value = value;
    }

    public int key() {
        return key;
    }

    public long value() {
        return value;
    }

    static IntGroupedLongEntry trustedCreate(int key, long value) {
        return new IntGroupedLongEntry(key, value);
    }
}
