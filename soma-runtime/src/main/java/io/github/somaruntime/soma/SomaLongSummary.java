package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

/** Detached exact summary for integer-valued SOMA streams. */
public final class SomaLongSummary {
    static {
        SomaSharedSecrets.setLongSummaryAccess(
                new SomaSharedSecrets.LongSummaryAccess() {
                    @Override public SomaLongSummary create(
                            long count, long min, long max, long sum, double average) {
                        return new SomaLongSummary(count, min, max, sum, average);
                    }
                });
    }
    private final long count;
    private final long min;
    private final long max;
    private final long sum;
    private final double average;
    private SomaLongSummary(long count, long min, long max, long sum, double average) {
        this.count = count; this.min = min; this.max = max; this.sum = sum;
        this.average = average;
    }
    public long count() { return count; }
    public long min() { return min; }
    public long max() { return max; }
    public long sum() { return sum; }
    public double average() { return average; }
}
