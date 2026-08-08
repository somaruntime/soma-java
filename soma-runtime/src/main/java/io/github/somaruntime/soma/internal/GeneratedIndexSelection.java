package io.github.somaruntime.soma.internal;

/** Reusable exact-Index source; I2 exposes only the already-admitted count terminal. */
public final class GeneratedIndexSelection {

    private final GeneratedTable owner;
    private final int indexOrdinal;
    private final GeneratedProbe probe;

    GeneratedIndexSelection(
            GeneratedTable owner,
            int indexOrdinal,
            GeneratedProbe probe) {
        this.owner = owner;
        this.indexOrdinal = indexOrdinal;
        this.probe = probe;
    }

    public long count() {
        return owner.indexCount(indexOrdinal, probe);
    }
}
