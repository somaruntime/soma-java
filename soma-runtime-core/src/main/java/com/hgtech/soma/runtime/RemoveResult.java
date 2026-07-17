package com.hgtech.soma.runtime;

/** Immutable result of a successful row-pipeline structural remove terminal。 */
public final class RemoveResult {
    private final long scanned;
    private final long matched;
    private final long removed;
    private final long compacted;

    private RemoveResult(long scanned, long matched, long removed, long compacted) {
        this.scanned = scanned;
        this.matched = matched;
        this.removed = removed;
        this.compacted = compacted;
    }

    public static RemoveResult create(
            long scanned,
            long matched,
            long removed,
            long compacted) {
        if (scanned < 0L || matched < 0L || removed < 0L || compacted < 0L
                || compacted > removed || removed != matched || matched > scanned) {
            throw new IllegalArgumentException("invalid remove result counters");
        }
        return new RemoveResult(scanned, matched, removed, compacted);
    }

    public long scanned() { return scanned; }
    public long matched() { return matched; }
    public long removed() { return removed; }
    public long compacted() { return compacted; }
}
