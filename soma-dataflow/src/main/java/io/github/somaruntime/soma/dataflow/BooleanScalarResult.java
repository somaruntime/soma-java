package io.github.somaruntime.soma.dataflow;

/** Required boolean scalar result. */
public final class BooleanScalarResult {
    private final boolean value;

    BooleanScalarResult(boolean value) {
        this.value = value;
    }

    public boolean value() {
        return value;
    }
}
