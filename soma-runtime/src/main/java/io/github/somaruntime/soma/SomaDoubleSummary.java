package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

/** Detached deterministic summary for floating-valued SOMA streams. */
public final class SomaDoubleSummary {
    static {
        SomaSharedSecrets.setDoubleSummaryAccess(
                new SomaSharedSecrets.DoubleSummaryAccess() {
                    @Override public SomaDoubleSummary create(
                            long count, double min, double max, double sum, double average) {
                        return new SomaDoubleSummary(count, min, max, sum, average);
                    }
                });
    }
    private final long count;
    private final double min;
    private final double max;
    private final double sum;
    private final double average;
    private SomaDoubleSummary(
            long count, double min, double max, double sum, double average) {
        this.count = count; this.min = min; this.max = max; this.sum = sum;
        this.average = average;
    }
    public long count() { return count; }
    public double min() { return min; }
    public double max() { return max; }
    public double sum() { return sum; }
    public double average() { return average; }
}
