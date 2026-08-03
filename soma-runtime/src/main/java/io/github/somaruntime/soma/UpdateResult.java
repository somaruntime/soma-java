package io.github.somaruntime.soma;

/** Stable result of a point or selection update operation. */
public final class UpdateResult {
    private final long matched;
    private final long changed;

    private UpdateResult(long matched, long changed) {
        this.matched = matched;
        this.changed = changed;
    }

    /** Number of selected rows observed by the operation. */
    public long matched() {
        return matched;
    }

    /** Number of selected rows whose stored payload changed. */
    public long changed() {
        return changed;
    }

    static UpdateResult trustedCreate(long matched, long changed) {
        return new UpdateResult(matched, changed);
    }
}
