package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;

/** Generated source 与 runtime-core 的 create-time compatibility authority。 */
public final class RuntimeCompatibility {
    public static final String GENERATED_TARGET = "java8-columnar";
    public static final String COMPILER_IDENTITY = "soma-value-javac8-v1";
    public static final String GENERATED_PROTOCOL = "soma-generated-runtime-v7";
    public static final String RUNTIME_COMPATIBILITY = "soma-runtime-java8-v7";
    public static final String PLAN_PROTOCOL = "soma-runtime-plan-v4";
    public static final String DENSE_ALGORITHM = "dense-soa-v1";
    public static final String NO_ACCESS_STRATEGY = "none";
    public static final String NO_KEY_SPACE = "none";
    public static final String HASH_INT_KEY_SPACE = "hash-int-v2";
    public static final String HASH_LONG_KEY_SPACE = "hash-long-v2";
    public static final String HASH_COMPOSITE_KEY_SPACE = "hash-composite-v2";
    public static final String PRIMITIVE_EXACT_HASH = "primitive-exact-hash-v1";
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
                ? PRIMITIVE_EXACT_HASH : NO_ACCESS_STRATEGY;
        require("runtime_plan_mismatch", expectedStrategy,
                plan.accessStrategy(), plan.tableLogicalName() + ".accessStrategy");
        return plan;
    }

    public static TablePlan verifyKeySpace(TablePlan plan, String expectedStrategy) {
        require("runtime_plan_mismatch", expectedStrategy, plan.keySpaceStrategy(),
                plan.tableLogicalName() + ".keySpaceStrategy");
        return plan;
    }

    public static IntKeySpace createIntKeySpace(TablePlan plan, int expectedSize) {
        verifyKeySpace(plan, HASH_INT_KEY_SPACE);
        return new HashIntKeySpace(expectedSize);
    }

    public static long estimatedKeySpaceBytes(
            TablePlan plan, String expectedStrategy, int expectedSize) {
        verifyKeySpace(plan, expectedStrategy);
        if (expectedSize < 0) {
            throw RuntimeFailures.invalidRuntimePlan(
                    plan.tableLogicalName() + ".expectedSize", "negative key-space size");
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

    private static void require(String code, String expected, String actual, String path) {
        if (!expected.equals(actual)) {
            throw RuntimeFailures.compatibilityMismatch(code, expected, actual, path);
        }
    }
}
