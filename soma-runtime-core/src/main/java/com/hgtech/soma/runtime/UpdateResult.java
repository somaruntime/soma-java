package com.hgtech.soma.runtime;

/** Immutable result of a successful row-pipeline update terminal。 */
public final class UpdateResult {
    private final long scanned;
    private final long matched;
    private final long changed;
    private final long sidecarMaintained;
    private final long sidecarRebuilt;

    private UpdateResult(
            long scanned,
            long matched,
            long changed,
            long sidecarMaintained,
            long sidecarRebuilt) {
        this.scanned = scanned;
        this.matched = matched;
        this.changed = changed;
        this.sidecarMaintained = sidecarMaintained;
        this.sidecarRebuilt = sidecarRebuilt;
    }

    public static UpdateResult create(
            long scanned,
            long matched,
            long changed,
            long sidecarMaintained,
            long sidecarRebuilt) {
        if (scanned < 0L || matched < 0L || changed < 0L
                || sidecarMaintained < 0L || sidecarRebuilt < 0L
                || changed > matched || matched > scanned) {
            throw new IllegalArgumentException("invalid update result counters");
        }
        return new UpdateResult(scanned, matched, changed,
                sidecarMaintained, sidecarRebuilt);
    }

    public long scanned() { return scanned; }
    public long matched() { return matched; }
    public long changed() { return changed; }
    public long sidecarMaintained() { return sidecarMaintained; }
    public long sidecarRebuilt() { return sidecarRebuilt; }
}
