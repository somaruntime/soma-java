package io.github.somaruntime.soma.dataflow;

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
    private final long changed;
    private final int beforeSize;
    private final int afterSize;
    private final long beforeStructuralEpoch;
    private final long afterStructuralEpoch;

    private DeltaApplyResult(
            long inserted,
            long updated,
            long deleted,
            long changed,
            int beforeSize,
            int afterSize,
            long beforeStructuralEpoch,
            long afterStructuralEpoch) {
        this.inserted = inserted;
        this.updated = updated;
        this.deleted = deleted;
        this.changed = changed;
        this.beforeSize = beforeSize;
        this.afterSize = afterSize;
        this.beforeStructuralEpoch = beforeStructuralEpoch;
        this.afterStructuralEpoch = afterStructuralEpoch;
    }

    /**
     * Creates a self-consistent summary at the generated safe-point boundary.
     */
    public static DeltaApplyResult committed(
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
        if (inserted > Integer.MAX_VALUE
                || updated > Integer.MAX_VALUE
                || deleted > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Delta apply result count exceeds Table cardinality range");
        }
        if (inserted > Long.MAX_VALUE - updated
                || inserted + updated > Long.MAX_VALUE - deleted) {
            throw new IllegalArgumentException(
                    "Delta apply result change count overflow");
        }
        long changed = inserted + updated + deleted;
        long expectedAfter = (long) beforeSize + inserted - deleted;
        if (expectedAfter != afterSize) {
            throw new IllegalArgumentException(
                    "Delta apply result size transition is inconsistent");
        }
        if ((changed == 0L
                && (beforeStructuralEpoch != afterStructuralEpoch
                        || beforeSize != afterSize))
                || (changed != 0L
                        && afterStructuralEpoch <= beforeStructuralEpoch)) {
            throw new IllegalArgumentException(
                    "Delta apply result epoch transition is inconsistent");
        }
        return new DeltaApplyResult(
                inserted,
                updated,
                deleted,
                changed,
                beforeSize,
                afterSize,
                beforeStructuralEpoch,
                afterStructuralEpoch);
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
        return changed;
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
