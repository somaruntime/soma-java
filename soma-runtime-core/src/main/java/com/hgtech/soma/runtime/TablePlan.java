package com.hgtech.soma.runtime;

/** Immutable effective runtime policy for one logical table。 */
public final class TablePlan {
    private final String tableLogicalName;
    private final String algorithm;
    private final int initialCapacity;
    private final int growthNumerator;
    private final int growthDenominator;
    private final long maximumUpdateScratchBytes;

    private TablePlan(Builder builder) {
        tableLogicalName = builder.tableLogicalName;
        algorithm = builder.algorithm;
        initialCapacity = builder.initialCapacity;
        growthNumerator = builder.growthNumerator;
        growthDenominator = builder.growthDenominator;
        maximumUpdateScratchBytes = builder.maximumUpdateScratchBytes;
    }

    public static Builder builder(String tableLogicalName, String algorithm) {
        return new Builder(tableLogicalName, algorithm);
    }

    public Builder toBuilder() {
        return new Builder(tableLogicalName, algorithm)
                .initialCapacity(initialCapacity)
                .growthRatio(growthNumerator, growthDenominator)
                .maximumUpdateScratchBytes(maximumUpdateScratchBytes);
    }

    public String tableLogicalName() { return tableLogicalName; }
    public String algorithm() { return algorithm; }
    public int initialCapacity() { return initialCapacity; }
    public int growthNumerator() { return growthNumerator; }
    public int growthDenominator() { return growthDenominator; }
    public long maximumUpdateScratchBytes() { return maximumUpdateScratchBytes; }

    String toCanonicalJson() {
        return "{\"algorithm\":" + CanonicalSupport.quote(algorithm)
                + ",\"growthDenominator\":" + growthDenominator
                + ",\"growthNumerator\":" + growthNumerator
                + ",\"initialCapacity\":" + initialCapacity
                + ",\"maximumUpdateScratchBytes\":" + maximumUpdateScratchBytes
                + ",\"table\":" + CanonicalSupport.quote(tableLogicalName) + "}";
    }

    public static final class Builder {
        private final String tableLogicalName;
        private final String algorithm;
        private int initialCapacity = 16;
        private int growthNumerator = 3;
        private int growthDenominator = 2;
        private long maximumUpdateScratchBytes = 256L * 1024L * 1024L;

        private Builder(String tableLogicalName, String algorithm) {
            this.tableLogicalName = CanonicalSupport.required(tableLogicalName, "tableLogicalName");
            this.algorithm = CanonicalSupport.required(algorithm, "algorithm");
        }

        public Builder initialCapacity(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("initialCapacity must be positive");
            }
            initialCapacity = value;
            return this;
        }

        public Builder growthRatio(int numerator, int denominator) {
            if (denominator < 1 || numerator <= denominator) {
                throw new IllegalArgumentException("growth ratio must satisfy numerator > denominator >= 1");
            }
            growthNumerator = numerator;
            growthDenominator = denominator;
            return this;
        }

        public Builder maximumUpdateScratchBytes(long value) {
            if (value <= 0L) {
                throw new IllegalArgumentException("maximumUpdateScratchBytes must be positive");
            }
            maximumUpdateScratchBytes = value;
            return this;
        }

        public TablePlan build() { return new TablePlan(this); }
    }
}
