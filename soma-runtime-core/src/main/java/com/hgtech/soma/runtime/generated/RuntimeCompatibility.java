package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;

/** Generated source 与 runtime-core 的 create-time compatibility authority。 */
public final class RuntimeCompatibility {
    public static final String GENERATED_TARGET = "java8-columnar";
    public static final String COMPILER_IDENTITY = "soma-value-javac8-v1";
    public static final String GENERATED_PROTOCOL = "soma-generated-runtime-v2";
    public static final String RUNTIME_COMPATIBILITY = "soma-runtime-java8-v2";
    public static final String PLAN_PROTOCOL = "soma-runtime-plan-v2";
    public static final String DENSE_ALGORITHM = "dense-soa-v1";
    public static final String NO_ACCESS_STRATEGY = "none";
    public static final String NO_KEY_SPACE = "none";
    public static final String HASH_INT_KEY_SPACE = "hash-int-v1";
    public static final String HASH_LONG_KEY_SPACE = "hash-long-v1";
    public static final String HASH_COMPOSITE_KEY_SPACE = "hash-composite-v1";
    public static final String SPARSE_INT_KEY_SPACE = "sparse-int-v1";
    public static final String PRIMITIVE_SORTED_PERMUTATION =
            "primitive-sorted-permutation-v1";
    public static final String NO_SIDECAR_MAINTENANCE = "none";
    public static final String DIRTY_LAZY_REBUILD = "dirty-lazy-rebuild-v1";
    public static final String ALLOCATION_ESTIMATOR = "soma-materialization-estimator-v1";

    private RuntimeCompatibility() {
    }

    public static TablePlan verify(
            GeneratedMetadata metadata,
            RuntimePlan plan,
            String tableLogicalName) {
        require("generated_target_mismatch", GENERATED_TARGET, metadata.generatedTarget(), "generatedTarget");
        require("compiler_integration_mismatch", COMPILER_IDENTITY, metadata.compilerIdentity(),
                "compilerIdentity");
        require("runtime_compatibility_mismatch", GENERATED_PROTOCOL, metadata.generatedProtocol(),
                "generatedProtocol");
        require("runtime_compatibility_mismatch", RUNTIME_COMPATIBILITY,
                metadata.runtimeCompatibility(), "runtimeCompatibility");
        require("runtime_plan_mismatch", PLAN_PROTOCOL, metadata.planProtocol(), "planProtocol");
        require("runtime_compatibility_mismatch", DENSE_ALGORITHM, metadata.algorithm(), "algorithm");
        require("runtime_plan_mismatch", ALLOCATION_ESTIMATOR, metadata.allocationEstimator(),
                "allocationEstimator");
        require("schema_hash_mismatch", metadata.schemaHash(), plan.schemaHash(), "schemaHash");
        require("runtime_compatibility_mismatch", metadata.runtimeCompatibility(),
                plan.runtimeCompatibility(), "runtimeCompatibility");
        require("runtime_compatibility_mismatch", metadata.generatedProtocol(),
                plan.generatedProtocol(), "generatedProtocol");
        require("runtime_plan_mismatch", metadata.planProtocol(), plan.planProtocol(), "planProtocol");
        require("runtime_plan_mismatch", metadata.allocationEstimator(),
                plan.allocationEstimator(), "allocationEstimator");
        TablePlan tablePlan = plan.requireTable(tableLogicalName);
        require("runtime_plan_mismatch", metadata.algorithm(), tablePlan.algorithm(),
                tableLogicalName + ".algorithm");
        return tablePlan;
    }

    public static TablePlan verifyAccess(TablePlan plan, boolean hasSelectors) {
        String expectedStrategy = hasSelectors
                ? PRIMITIVE_SORTED_PERMUTATION : NO_ACCESS_STRATEGY;
        String expectedMaintenance = hasSelectors
                ? DIRTY_LAZY_REBUILD : NO_SIDECAR_MAINTENANCE;
        require("runtime_plan_mismatch", expectedStrategy,
                plan.accessStrategy(), plan.tableLogicalName() + ".accessStrategy");
        require("runtime_plan_mismatch", expectedMaintenance,
                plan.sidecarMaintenancePolicy(),
                plan.tableLogicalName() + ".sidecarMaintenancePolicy");
        if (hasSelectors != (plan.maximumSidecarScratchBytes() > 0L)) {
            throw RuntimeFailures.compatibilityMismatch(
                    "runtime_plan_mismatch", hasSelectors ? "positive" : "0",
                    Long.toString(plan.maximumSidecarScratchBytes()),
                    plan.tableLogicalName() + ".maximumSidecarScratchBytes");
        }
        return plan;
    }

    public static TablePlan verifyKeySpace(
            TablePlan plan, String expectedStrategy, boolean sparseEligible) {
        String actual = plan.keySpaceStrategy();
        boolean sparse = sparseEligible && SPARSE_INT_KEY_SPACE.equals(actual);
        if (!sparse) {
            require("runtime_plan_mismatch", expectedStrategy, actual,
                    plan.tableLogicalName() + ".keySpaceStrategy");
        }
        if (sparse != (plan.maximumSparseKey() >= 0L)) {
            throw RuntimeFailures.compatibilityMismatch(
                    "runtime_plan_mismatch", sparse ? "non-negative" : "-1",
                    Long.toString(plan.maximumSparseKey()),
                    plan.tableLogicalName() + ".maximumSparseKey");
        }
        return plan;
    }

    public static IntKeySpace createIntKeySpace(TablePlan plan, int expectedSize) {
        verifyKeySpace(plan, HASH_INT_KEY_SPACE, true);
        if (SPARSE_INT_KEY_SPACE.equals(plan.keySpaceStrategy())) {
            long domainEntries = plan.maximumSparseKey() + 1L;
            long retained = domainEntries > Long.MAX_VALUE / 4L
                    ? Long.MAX_VALUE : domainEntries * 4L;
            if (retained > plan.maximumTableStorageBytes()) {
                throw RuntimeFailures.memoryLimitExceeded(
                        plan.tableLogicalName(), "table.create",
                        plan.maximumTableStorageBytes(), retained);
            }
            return new SparseIntKeySpace((int) plan.maximumSparseKey(), expectedSize);
        }
        return new HashIntKeySpace(expectedSize);
    }

    public static long estimatedKeySpaceBytes(
            TablePlan plan, String expectedStrategy, boolean sparseEligible,
            int expectedSize) {
        verifyKeySpace(plan, expectedStrategy, sparseEligible);
        if (expectedSize < 0) {
            throw RuntimeFailures.invalidRuntimePlan(
                    plan.tableLogicalName() + ".expectedSize", "negative key-space size");
        }
        if (SPARSE_INT_KEY_SPACE.equals(plan.keySpaceStrategy())) {
            long denseCapacity = 0L;
            if (expectedSize > 0) {
                denseCapacity = 4L;
                while (denseCapacity < expectedSize) {
                    if (denseCapacity > (Integer.MAX_VALUE - 8L) / 2L) {
                        denseCapacity = Integer.MAX_VALUE - 8L;
                        break;
                    }
                    denseCapacity *= 2L;
                }
            }
            return checkedBytes(4L, plan.maximumSparseKey() + 1L + denseCapacity);
        }
        int capacity = hashCapacity(expectedSize);
        return (HASH_INT_KEY_SPACE.equals(expectedStrategy) ? 9L : 13L)
                * (long) capacity;
    }

    public static long estimatedHashKeySpaceBytes(String strategy, int expectedSize) {
        if (!HASH_INT_KEY_SPACE.equals(strategy)
                && !HASH_LONG_KEY_SPACE.equals(strategy)
                && !HASH_COMPOSITE_KEY_SPACE.equals(strategy)) {
            throw RuntimeFailures.invalidRuntimePlan(
                    "keySpace", "unsupported hash scratch strategy");
        }
        int capacity = hashCapacity(expectedSize);
        return (HASH_INT_KEY_SPACE.equals(strategy) ? 9L : 13L)
                * (long) capacity;
    }

    private static int hashCapacity(int expectedSize) {
        long required = Math.max(4L, 2L * (long) expectedSize + 1L);
        int capacity = 4;
        while ((long) capacity < required) {
            if (capacity >= (1 << 30)) {
                throw RuntimeFailures.memoryLimitExceeded(
                        "keySpace", "table.create", Integer.MAX_VALUE, required);
            }
            capacity <<= 1;
        }
        return capacity;
    }

    private static long checkedBytes(long width, long count) {
        return count < 0L || count > Long.MAX_VALUE / width
                ? Long.MAX_VALUE : width * count;
    }

    private static void require(String code, String expected, String actual, String path) {
        if (!expected.equals(actual)) {
            throw RuntimeFailures.compatibilityMismatch(code, expected, actual, path);
        }
    }
}
