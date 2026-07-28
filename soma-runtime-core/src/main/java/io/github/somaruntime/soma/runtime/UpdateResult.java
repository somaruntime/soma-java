package io.github.somaruntime.soma.runtime;

/** Immutable result of a successful Candidate Scan update terminal. */
public final class UpdateResult {
    private final long scanned;
    private final long matched;
    private final long changed;

    private UpdateResult(long scanned, long matched, long changed) {
        this.scanned = scanned;
        this.matched = matched;
        this.changed = changed;
    }

    public static UpdateResult create(long scanned, long matched, long changed) {
        if (scanned < 0L || matched < 0L || changed < 0L
                || changed > matched || matched > scanned) {
            throw new IllegalArgumentException("invalid update result counters");
        }
        return new UpdateResult(scanned, matched, changed);
    }

    public long scanned() { return scanned; }
    public long matched() { return matched; }
    public long changed() { return changed; }
}
