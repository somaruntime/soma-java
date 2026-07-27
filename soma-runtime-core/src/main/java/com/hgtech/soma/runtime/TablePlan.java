package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.GeneratedPlanToken;
import com.hgtech.soma.runtime.metadata.SomaExactAccess;
import com.hgtech.soma.runtime.metadata.SomaPrimaryLocator;
import com.hgtech.soma.runtime.metadata.SomaStorageLayout;
import com.hgtech.soma.runtime.metadata.SomaWorkloadProfile;

import java.util.Locale;

/** Immutable effective runtime policy for one logical table。 */
public final class TablePlan {
    private static final String DENSE_ALGORITHM = "dense-soa-v1";

    private final String tableLogicalName;
    private final String algorithm;
    private final SomaStorageLayout storageLayout;
    private final SomaWorkloadProfile workloadProfile;
    private final String storageLayoutFormula;
    private final int structuralBytesPerRow;
    private final int flatHeadRows;
    private final int segmentRows;
    private final int initialCapacity;
    private final int planningRows;
    private final int maximumRows;
    private final int growthNumerator;
    private final int growthDenominator;
    private final long maximumUpdateScratchBytes;
    private final long maximumOperationScratchBytes;
    private final long maximumBulkScratchBytes;
    private final long maximumTableStorageBytes;
    private final SomaPrimaryLocator primaryLocator;
    private final SomaExactAccess exactAccess;
    private final boolean stringCapable;
    private final StringResourceProfile stringResourceProfile;

    private TablePlan(Builder builder) {
        tableLogicalName = builder.tableLogicalName;
        algorithm = builder.algorithm;
        workloadProfile = builder.workloadProfile;
        storageLayoutFormula = builder.storageLayoutFormula;
        structuralBytesPerRow = builder.structuralBytesPerRow;
        StorageLayoutFormula.Resolution layout =
                StorageLayoutFormula.resolve(
                        storageLayoutFormula,
                        workloadProfile,
                        builder.planningRows,
                        structuralBytesPerRow);
        storageLayout = layout.layout;
        flatHeadRows = layout.flatHeadRows;
        segmentRows = layout.segmentRows;
        initialCapacity = builder.initialCapacity;
        planningRows = builder.planningRows;
        maximumRows = builder.maximumRows;
        growthNumerator = builder.growthNumerator;
        growthDenominator = builder.growthDenominator;
        maximumUpdateScratchBytes = builder.maximumUpdateScratchBytes;
        maximumOperationScratchBytes = builder.maximumOperationScratchBytes;
        maximumBulkScratchBytes = builder.maximumBulkScratchBytes;
        maximumTableStorageBytes = builder.maximumTableStorageBytes;
        primaryLocator = builder.primaryLocator;
        exactAccess = builder.exactAccess;
        stringCapable = builder.stringCapable;
        stringResourceProfile = builder.stringResourceProfile;
    }

    static Builder builder(String tableLogicalName, String algorithm) {
        return new Builder(tableLogicalName, algorithm);
    }

    public static Builder generatedBuilder(
            GeneratedPlanToken token,
            String tableLogicalName,
            String algorithm) {
        GeneratedPlanToken.require(token);
        return new Builder(tableLogicalName, algorithm);
    }

    public Builder toBuilder() {
        return new Builder(tableLogicalName, algorithm)
                .initialCapacity(initialCapacity)
                .planningRows(planningRows)
                .maximumRows(maximumRows)
                .growthRatio(growthNumerator, growthDenominator)
                .maximumUpdateScratchBytes(maximumUpdateScratchBytes)
                .maximumOperationScratchBytes(maximumOperationScratchBytes)
                .maximumBulkScratchBytes(maximumBulkScratchBytes)
                .maximumTableStorageBytes(maximumTableStorageBytes)
                .primaryLocator(primaryLocator)
                .exactAccess(exactAccess)
                .workloadProfile(workloadProfile)
                .storageLayoutFormula(
                        storageLayoutFormula, structuralBytesPerRow)
                .stringCapable(stringCapable)
                .stringResourceProfile(stringResourceProfile);
    }

    public String tableLogicalName() { return tableLogicalName; }
    public String algorithm() { return algorithm; }
    public SomaStorageLayout storageLayout() { return storageLayout; }
    public SomaWorkloadProfile workloadProfile() { return workloadProfile; }
    public String storageLayoutFormula() { return storageLayoutFormula; }
    public int structuralBytesPerRow() { return structuralBytesPerRow; }
    public int flatHeadRows() { return flatHeadRows; }
    public int segmentRows() { return segmentRows; }
    public int initialCapacity() { return initialCapacity; }
    public int planningRows() { return planningRows; }
    public int maximumRows() { return maximumRows; }
    public int growthNumerator() { return growthNumerator; }
    public int growthDenominator() { return growthDenominator; }
    public long maximumUpdateScratchBytes() { return maximumUpdateScratchBytes; }
    public long maximumOperationScratchBytes() { return maximumOperationScratchBytes; }
    public long maximumBulkScratchBytes() { return maximumBulkScratchBytes; }
    public long maximumTableStorageBytes() { return maximumTableStorageBytes; }
    /** Primary-locator identity；KeySpace是兼容性字段名，不表示Sparse Set。 */
    public String keySpaceStrategy() {
        switch (primaryLocator) {
            case HASH_INT: return "hash-int-v2";
            case HASH_LONG: return "hash-long-v2";
            case HASH_COMPOSITE: return "hash-composite-v2";
            default: return "none";
        }
    }
    public String accessStrategy() {
        return exactAccess == SomaExactAccess.EXACT_HASH
                ? "primitive-exact-hash-v1" : "none";
    }
    public SomaPrimaryLocator primaryLocator() { return primaryLocator; }
    public SomaExactAccess exactAccess() { return exactAccess; }
    public boolean stringCapable() { return stringCapable; }
    public StringResourceProfile stringResourceProfile() {
        return stringResourceProfile;
    }

    String toCanonicalJson() {
        return "{\"accessStrategy\":" + CanonicalSupport.quote(accessStrategy())
                + ",\"algorithm\":" + CanonicalSupport.quote(algorithm)
                + ",\"growthDenominator\":" + growthDenominator
                + ",\"growthNumerator\":" + growthNumerator
                + ",\"initialCapacity\":" + initialCapacity
                + ",\"keySpaceStrategy\":"
                + CanonicalSupport.quote(keySpaceStrategy())
                + ",\"maximumBulkScratchBytes\":" + maximumBulkScratchBytes
                + ",\"maximumOperationScratchBytes\":"
                + maximumOperationScratchBytes
                + ",\"maximumRows\":" + maximumRows
                + ",\"maximumTableStorageBytes\":" + maximumTableStorageBytes
                + ",\"maximumUpdateScratchBytes\":" + maximumUpdateScratchBytes
                + ",\"planningRows\":" + planningRows
                + ",\"storageLayout\":"
                + CanonicalSupport.quote(
                        storageLayout.name().toLowerCase(Locale.ROOT))
                + ",\"storageLayoutFormula\":"
                + CanonicalSupport.quote(storageLayoutFormula)
                + ",\"structuralBytesPerRow\":" + structuralBytesPerRow
                + ",\"flatHeadRows\":" + flatHeadRows
                + ",\"segmentRows\":" + segmentRows
                + ",\"stringCapable\":" + stringCapable
                + ",\"stringResourceProfile\":"
                + stringResourceProfile.toCanonicalJson()
                + ",\"table\":" + CanonicalSupport.quote(tableLogicalName)
                + ",\"workloadProfile\":"
                + CanonicalSupport.quote(
                        workloadProfile.name().toLowerCase(Locale.ROOT))
                + "}";
    }

    public static final class Builder {
        private final String tableLogicalName;
        private final String algorithm;
        private SomaWorkloadProfile workloadProfile =
                SomaWorkloadProfile.BALANCED;
        private String storageLayoutFormula = StorageLayoutFormula.IDENTITY;
        private int structuralBytesPerRow = 1;
        private int initialCapacity = 16;
        private int planningRows = 16;
        private int maximumRows = Integer.MAX_VALUE;
        private int growthNumerator = 3;
        private int growthDenominator = 2;
        private long maximumUpdateScratchBytes = 256L * 1024L * 1024L;
        private long maximumOperationScratchBytes = 256L * 1024L * 1024L;
        private long maximumBulkScratchBytes = 256L * 1024L * 1024L;
        private long maximumTableStorageBytes = 256L * 1024L * 1024L;
        private SomaPrimaryLocator primaryLocator = SomaPrimaryLocator.NONE;
        private SomaExactAccess exactAccess = SomaExactAccess.NONE;
        private boolean stringCapable;
        private StringResourceProfile stringResourceProfile =
                StringResourceProfile.unprofiled();
        private boolean open = true;

        private Builder(String tableLogicalName, String algorithm) {
            this.tableLogicalName = CanonicalSupport.required(
                    tableLogicalName, "tableLogicalName");
            this.algorithm = CanonicalSupport.required(
                    algorithm, "algorithm");
            if (!DENSE_ALGORITHM.equals(this.algorithm)) {
                throw new IllegalArgumentException(
                        "unsupported table algorithm identity");
            }
        }

        public Builder initialCapacity(int value) {
            requireOpen();
            if (value <= 0) {
                throw new IllegalArgumentException(
                        "initialCapacity must be positive");
            }
            initialCapacity = value;
            return this;
        }

        public Builder planningRows(int value) {
            requireOpen();
            if (value <= 0) {
                throw new IllegalArgumentException(
                        "planningRows must be positive");
            }
            planningRows = value;
            return this;
        }

        public Builder maximumRows(int value) {
            requireOpen();
            if (value <= 0) {
                throw new IllegalArgumentException(
                        "maximumRows must be positive");
            }
            maximumRows = value;
            return this;
        }

        public Builder growthRatio(int numerator, int denominator) {
            requireOpen();
            if (denominator < 1 || numerator <= denominator) {
                throw new IllegalArgumentException(
                        "growth ratio must satisfy numerator > denominator >= 1");
            }
            growthNumerator = numerator;
            growthDenominator = denominator;
            return this;
        }

        public Builder maximumUpdateScratchBytes(long value) {
            requireOpen();
            maximumUpdateScratchBytes =
                    requirePositive(value, "maximumUpdateScratchBytes");
            return this;
        }

        public Builder maximumOperationScratchBytes(long value) {
            requireOpen();
            maximumOperationScratchBytes =
                    requirePositive(value, "maximumOperationScratchBytes");
            return this;
        }

        public Builder maximumBulkScratchBytes(long value) {
            requireOpen();
            maximumBulkScratchBytes =
                    requirePositive(value, "maximumBulkScratchBytes");
            return this;
        }

        public Builder maximumTableStorageBytes(long value) {
            requireOpen();
            maximumTableStorageBytes =
                    requirePositive(value, "maximumTableStorageBytes");
            return this;
        }

        public Builder workloadProfile(SomaWorkloadProfile value) {
            requireOpen();
            if (value == null) throw new NullPointerException("workloadProfile");
            workloadProfile = value;
            return this;
        }

        public Builder stringResourceProfile(StringResourceProfile value) {
            requireOpen();
            if (value == null) {
                throw new NullPointerException("stringResourceProfile");
            }
            stringResourceProfile = value;
            return this;
        }

        Builder keySpaceStrategy(String value) {
            requireOpen();
            primaryLocator = primaryLocator(value);
            return this;
        }

        public Builder generatedKeySpaceStrategy(
                GeneratedPlanToken token, String value) {
            GeneratedPlanToken.require(token);
            return keySpaceStrategy(value);
        }

        Builder accessStrategy(String value) {
            requireOpen();
            exactAccess = exactAccess(value);
            return this;
        }

        public Builder generatedAccessStrategy(
                GeneratedPlanToken token, String value) {
            GeneratedPlanToken.require(token);
            return accessStrategy(value);
        }

        Builder storageLayoutFormula(String identity, int bytesPerRow) {
            requireOpen();
            storageLayoutFormula = CanonicalSupport.required(
                    identity, "storageLayoutFormula");
            if (bytesPerRow <= 0) {
                throw new IllegalArgumentException(
                        "structuralBytesPerRow must be positive");
            }
            structuralBytesPerRow = bytesPerRow;
            return this;
        }

        public Builder generatedStorageLayoutFormula(
                GeneratedPlanToken token,
                String identity,
                int bytesPerRow) {
            GeneratedPlanToken.require(token);
            return storageLayoutFormula(identity, bytesPerRow);
        }

        Builder primaryLocator(SomaPrimaryLocator value) {
            requireOpen();
            if (value == null) throw new NullPointerException("primaryLocator");
            primaryLocator = value;
            return this;
        }

        Builder exactAccess(SomaExactAccess value) {
            requireOpen();
            if (value == null) throw new NullPointerException("exactAccess");
            exactAccess = value;
            return this;
        }

        Builder stringCapable(boolean value) {
            requireOpen();
            stringCapable = value;
            return this;
        }

        public Builder generatedStringCapable(
                GeneratedPlanToken token, boolean value) {
            GeneratedPlanToken.require(token);
            return stringCapable(value);
        }

        public TablePlan build() {
            requireOpen();
            open = false;
            if (initialCapacity > maximumRows) {
                throw new IllegalArgumentException(
                        "initialCapacity must be <= maximumRows");
            }
            if (planningRows > maximumRows) {
                throw new IllegalArgumentException(
                        "planningRows must be <= maximumRows");
            }
            StorageLayoutFormula.resolve(
                    storageLayoutFormula,
                    workloadProfile,
                    planningRows,
                    structuralBytesPerRow);
            if (!stringCapable
                    && stringResourceProfile.status()
                    != StringResourceProfileStatus.UNPROFILED) {
                throw new IllegalArgumentException(
                        "String profile requires a String-capable table");
            }
            return new TablePlan(this);
        }

        private void requireOpen() {
            if (!open) {
                throw new IllegalStateException("TablePlan.Builder is closed");
            }
        }

        private static long requirePositive(long value, String name) {
            if (value <= 0L) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }

        private static SomaPrimaryLocator primaryLocator(String value) {
            String identity = CanonicalSupport.required(
                    value, "keySpaceStrategy");
            if ("none".equals(identity)) return SomaPrimaryLocator.NONE;
            if ("hash-int-v2".equals(identity)) {
                return SomaPrimaryLocator.HASH_INT;
            }
            if ("hash-long-v2".equals(identity)) {
                return SomaPrimaryLocator.HASH_LONG;
            }
            if ("hash-composite-v2".equals(identity)) {
                return SomaPrimaryLocator.HASH_COMPOSITE;
            }
            throw new IllegalArgumentException(
                    "unsupported primary locator identity");
        }

        private static SomaExactAccess exactAccess(String value) {
            String identity = CanonicalSupport.required(
                    value, "accessStrategy");
            if ("none".equals(identity)) return SomaExactAccess.NONE;
            if ("primitive-exact-hash-v1".equals(identity)) {
                return SomaExactAccess.EXACT_HASH;
            }
            throw new IllegalArgumentException(
                    "unsupported exact access identity");
        }
    }
}
