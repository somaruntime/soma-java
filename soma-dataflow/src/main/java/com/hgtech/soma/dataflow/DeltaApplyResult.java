package com.hgtech.soma.dataflow;

/**
 * Immutable summary of one generated keyed Delta safe-point application.
 *
 * <p>The result describes the ordered command batch that was committed, not a
 * durable transaction log.  It is detached from the source Table.</p>
 */
public final class DeltaApplyResult {
    private final long inserted;
    private final long updated;
    private final long deleted;
    private final int beforeSize;
    private final int afterSize;
    private final long beforeStructuralEpoch;
    private final long afterStructuralEpoch;

    public DeltaApplyResult(
            long inserted,
            long updated,
            long deleted,
            int beforeSize,
            int afterSize,
            long beforeStructuralEpoch,
            long afterStructuralEpoch) {
        if (inserted < 0L || updated < 0L || deleted < 0L
                || beforeSize < 0 || afterSize < 0
                || beforeStructuralEpoch < 0L || afterStructuralEpoch < 0L) {
            throw new IllegalArgumentException(
                    "Delta apply result values must be non-negative");
        }
        this.inserted = inserted;
        this.updated = updated;
        this.deleted = deleted;
        this.beforeSize = beforeSize;
        this.afterSize = afterSize;
        this.beforeStructuralEpoch = beforeStructuralEpoch;
        this.afterStructuralEpoch = afterStructuralEpoch;
    }

    public long inserted() {
        return inserted;
    }

    public long updated() {
        return updated;
    }

    public long deleted() {
        return deleted;
    }

    public long changed() {
        return inserted + updated + deleted;
    }

    public int beforeSize() {
        return beforeSize;
    }

    public int afterSize() {
        return afterSize;
    }

    public long beforeStructuralEpoch() {
        return beforeStructuralEpoch;
    }

    public long afterStructuralEpoch() {
        return afterStructuralEpoch;
    }
}
