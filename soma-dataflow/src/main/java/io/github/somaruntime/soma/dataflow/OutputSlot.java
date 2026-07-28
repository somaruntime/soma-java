package io.github.somaruntime.soma.dataflow;

/** Typed identity for one output of a finite reusable DataFlow graph. */
public final class OutputSlot<R> {
    private final Object owner;
    private final int ordinal;
    private final String name;

    OutputSlot(Object owner, int ordinal, String name) {
        this.owner = owner;
        this.ordinal = ordinal;
        this.name = name;
    }

    public int ordinal() {
        return ordinal;
    }

    public String name() {
        return name;
    }

    Object owner() {
        return owner;
    }
}
