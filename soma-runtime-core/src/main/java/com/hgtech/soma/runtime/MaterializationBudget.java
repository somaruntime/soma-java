package com.hgtech.soma.runtime;

/** Immutable deterministic limits for one materialization invocation。 */
public final class MaterializationBudget {
    private static final String HASH_PREFIX = "soma-java:v1:materialization-budget\n";
    private static final MaterializationBudget DEFAULTS = builder().build();

    private final int maximumOwnershipDepth;
    private final long maximumTableInstances;
    private final long maximumRows;
    private final long maximumLeafValues;
    private final long maximumEstimatedAllocationBytes;
    private final String identity;

    private MaterializationBudget(Builder builder) {
        maximumOwnershipDepth = builder.maximumOwnershipDepth;
        maximumTableInstances = builder.maximumTableInstances;
        maximumRows = builder.maximumRows;
        maximumLeafValues = builder.maximumLeafValues;
        maximumEstimatedAllocationBytes = builder.maximumEstimatedAllocationBytes;
        identity = CanonicalSupport.sha256(HASH_PREFIX, toCanonicalJson());
    }

    public static MaterializationBudget defaults() { return DEFAULTS; }
    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        return new Builder()
                .maximumOwnershipDepth(maximumOwnershipDepth)
                .maximumTableInstances(maximumTableInstances)
                .maximumRows(maximumRows)
                .maximumLeafValues(maximumLeafValues)
                .maximumEstimatedAllocationBytes(maximumEstimatedAllocationBytes);
    }

    public int maximumOwnershipDepth() { return maximumOwnershipDepth; }
    public long maximumTableInstances() { return maximumTableInstances; }
    public long maximumRows() { return maximumRows; }
    public long maximumLeafValues() { return maximumLeafValues; }
    public long maximumEstimatedAllocationBytes() { return maximumEstimatedAllocationBytes; }
    public String identity() { return identity; }

    String toCanonicalJson() {
        return "{\"maximumEstimatedAllocationBytes\":" + maximumEstimatedAllocationBytes
                + ",\"maximumLeafValues\":" + maximumLeafValues
                + ",\"maximumOwnershipDepth\":" + maximumOwnershipDepth
                + ",\"maximumRows\":" + maximumRows
                + ",\"maximumTableInstances\":" + maximumTableInstances + "}";
    }

    public static final class Builder {
        private int maximumOwnershipDepth = 32;
        private long maximumTableInstances = 100000L;
        private long maximumRows = 1000000L;
        private long maximumLeafValues = 50000000L;
        private long maximumEstimatedAllocationBytes = 256L * 1024L * 1024L;

        private Builder() {
        }

        public Builder maximumOwnershipDepth(int value) {
            maximumOwnershipDepth = nonNegative(value, "maximumOwnershipDepth");
            return this;
        }

        public Builder maximumTableInstances(long value) {
            maximumTableInstances = nonNegative(value, "maximumTableInstances");
            return this;
        }

        public Builder maximumRows(long value) {
            maximumRows = nonNegative(value, "maximumRows");
            return this;
        }

        public Builder maximumLeafValues(long value) {
            maximumLeafValues = nonNegative(value, "maximumLeafValues");
            return this;
        }

        public Builder maximumEstimatedAllocationBytes(long value) {
            maximumEstimatedAllocationBytes = nonNegative(value, "maximumEstimatedAllocationBytes");
            return this;
        }

        public MaterializationBudget build() { return new MaterializationBudget(this); }

        private static int nonNegative(int value, String name) {
            if (value < 0) {
                throw new IllegalArgumentException(name + " must be non-negative");
            }
            return value;
        }

        private static long nonNegative(long value, String name) {
            if (value < 0L) {
                throw new IllegalArgumentException(name + " must be non-negative");
            }
            return value;
        }
    }
}
