package io.github.somaruntime.soma;

/** Stable result of a point or selection remove operation. */
public final class RemoveResult {
    private final long removed;

    private RemoveResult(long removed) {
        this.removed = removed;
    }

    /** Number of records removed by the operation. */
    public long removed() {
        return removed;
    }

    static RemoveResult trustedCreate(long removed) {
        return new RemoveResult(removed);
    }
}
