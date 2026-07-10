package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;

/** Generated source 与 runtime-core 的 create-time compatibility authority。 */
public final class RuntimeCompatibility {
    public static final String GENERATED_TARGET = "java8-columnar";
    public static final String COMPILER_IDENTITY = "soma-value-javac8-v1";
    public static final String GENERATED_PROTOCOL = "soma-generated-runtime-v1";
    public static final String RUNTIME_COMPATIBILITY = "soma-runtime-java8-v1";
    public static final String PLAN_PROTOCOL = "soma-runtime-plan-v1";
    public static final String DENSE_ALGORITHM = "dense-soa-v1";
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

    private static void require(String code, String expected, String actual, String path) {
        if (!expected.equals(actual)) {
            throw RuntimeFailures.compatibilityMismatch(code, expected, actual, path);
        }
    }
}
