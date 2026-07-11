package com.hgtech.soma.runtime;

/** Immutable effective runtime policy for one logical table。 */
public final class TablePlan {
    private final String tableLogicalName;
    private final String algorithm;
    private final int initialCapacity;
    private final int growthNumerator;
    private final int growthDenominator;
    private final long maximumUpdateScratchBytes;
    private final long maximumOperationScratchBytes;
    private final long maximumBulkScratchBytes;
    private final long maximumTableStorageBytes;
    private final String keySpaceStrategy;
    private final long maximumSparseKey;
    private final String accessStrategy;
    private final String sidecarMaintenancePolicy;
    private final long maximumSidecarScratchBytes;

    private TablePlan(Builder builder) {
        tableLogicalName = builder.tableLogicalName;
        algorithm = builder.algorithm;
        initialCapacity = builder.initialCapacity;
        growthNumerator = builder.growthNumerator;
        growthDenominator = builder.growthDenominator;
        maximumUpdateScratchBytes = builder.maximumUpdateScratchBytes;
        maximumOperationScratchBytes = builder.maximumOperationScratchBytes;
        maximumBulkScratchBytes = builder.maximumBulkScratchBytes;
        maximumTableStorageBytes = builder.maximumTableStorageBytes;
        keySpaceStrategy = builder.keySpaceStrategy;
        maximumSparseKey = builder.maximumSparseKey;
        accessStrategy = builder.accessStrategy;
        sidecarMaintenancePolicy = builder.sidecarMaintenancePolicy;
        maximumSidecarScratchBytes = builder.maximumSidecarScratchBytes;
    }

    public static Builder builder(String tableLogicalName, String algorithm) {
        return new Builder(tableLogicalName, algorithm);
    }

    public Builder toBuilder() {
        return new Builder(tableLogicalName, algorithm)
                .initialCapacity(initialCapacity)
                .growthRatio(growthNumerator, growthDenominator)
                .maximumUpdateScratchBytes(maximumUpdateScratchBytes)
                .maximumOperationScratchBytes(maximumOperationScratchBytes)
                .maximumBulkScratchBytes(maximumBulkScratchBytes)
                .maximumTableStorageBytes(maximumTableStorageBytes)
                .keySpaceStrategy(keySpaceStrategy)
                .maximumSparseKey(maximumSparseKey)
                .accessStrategy(accessStrategy)
                .sidecarMaintenancePolicy(sidecarMaintenancePolicy)
                .maximumSidecarScratchBytes(maximumSidecarScratchBytes);
    }

    public String tableLogicalName() { return tableLogicalName; }
    public String algorithm() { return algorithm; }
    public int initialCapacity() { return initialCapacity; }
    public int growthNumerator() { return growthNumerator; }
    public int growthDenominator() { return growthDenominator; }
    public long maximumUpdateScratchBytes() { return maximumUpdateScratchBytes; }
    public long maximumOperationScratchBytes() { return maximumOperationScratchBytes; }
    public long maximumBulkScratchBytes() { return maximumBulkScratchBytes; }
    public long maximumTableStorageBytes() { return maximumTableStorageBytes; }
    public String keySpaceStrategy() { return keySpaceStrategy; }
    public long maximumSparseKey() { return maximumSparseKey; }
    public String accessStrategy() { return accessStrategy; }
    public String sidecarMaintenancePolicy() { return sidecarMaintenancePolicy; }
    public long maximumSidecarScratchBytes() { return maximumSidecarScratchBytes; }

    String toCanonicalJson() {
        return "{\"algorithm\":" + CanonicalSupport.quote(algorithm)
                + ",\"accessStrategy\":" + CanonicalSupport.quote(accessStrategy)
                + ",\"growthDenominator\":" + growthDenominator
                + ",\"growthNumerator\":" + growthNumerator
                + ",\"initialCapacity\":" + initialCapacity
                + ",\"keySpaceStrategy\":" + CanonicalSupport.quote(keySpaceStrategy)
                + ",\"maximumBulkScratchBytes\":" + maximumBulkScratchBytes
                + ",\"maximumOperationScratchBytes\":" + maximumOperationScratchBytes
                + ",\"maximumSidecarScratchBytes\":" + maximumSidecarScratchBytes
                + ",\"maximumSparseKey\":" + maximumSparseKey
                + ",\"maximumTableStorageBytes\":" + maximumTableStorageBytes
                + ",\"maximumUpdateScratchBytes\":" + maximumUpdateScratchBytes
                + ",\"sidecarMaintenancePolicy\":"
                + CanonicalSupport.quote(sidecarMaintenancePolicy)
                + ",\"table\":" + CanonicalSupport.quote(tableLogicalName) + "}";
    }

    public static final class Builder {
        private final String tableLogicalName;
        private final String algorithm;
        private int initialCapacity = 16;
        private int growthNumerator = 3;
        private int growthDenominator = 2;
        private long maximumUpdateScratchBytes = 256L * 1024L * 1024L;
        private long maximumOperationScratchBytes = 256L * 1024L * 1024L;
        private long maximumBulkScratchBytes = 256L * 1024L * 1024L;
        private long maximumTableStorageBytes = 256L * 1024L * 1024L;
        private String keySpaceStrategy = "none";
        private long maximumSparseKey = -1L;
        private String accessStrategy = "none";
        private String sidecarMaintenancePolicy = "none";
        private long maximumSidecarScratchBytes;

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

        public Builder maximumOperationScratchBytes(long value) {
            if (value <= 0L) {
                throw new IllegalArgumentException(
                        "maximumOperationScratchBytes must be positive");
            }
            maximumOperationScratchBytes = value;
            return this;
        }

        public Builder maximumBulkScratchBytes(long value) {
            if (value <= 0L) {
                throw new IllegalArgumentException(
                        "maximumBulkScratchBytes must be positive");
            }
            maximumBulkScratchBytes = value;
            return this;
        }

        public Builder maximumTableStorageBytes(long value) {
            if (value <= 0L) {
                throw new IllegalArgumentException(
                        "maximumTableStorageBytes must be positive");
            }
            maximumTableStorageBytes = value;
            return this;
        }

        public Builder keySpaceStrategy(String value) {
            keySpaceStrategy = CanonicalSupport.required(value, "keySpaceStrategy");
            return this;
        }

        public Builder maximumSparseKey(long value) {
            if (value < -1L || value >= Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "maximumSparseKey must be -1 or array-representable int");
            }
            maximumSparseKey = value;
            return this;
        }

        public Builder accessStrategy(String value) {
            accessStrategy = CanonicalSupport.required(value, "accessStrategy");
            return this;
        }

        public Builder sidecarMaintenancePolicy(String value) {
            sidecarMaintenancePolicy = CanonicalSupport.required(
                    value, "sidecarMaintenancePolicy");
            return this;
        }

        public Builder maximumSidecarScratchBytes(long value) {
            if (value < 0L) {
                throw new IllegalArgumentException(
                        "maximumSidecarScratchBytes must be non-negative");
            }
            maximumSidecarScratchBytes = value;
            return this;
        }

        public TablePlan build() { return new TablePlan(this); }
    }
}
