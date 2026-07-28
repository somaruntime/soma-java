package io.github.somaruntime.soma.runtime.generated;

import io.github.somaruntime.soma.runtime.ChildPlan;
import io.github.somaruntime.soma.runtime.RuntimePlan;
import io.github.somaruntime.soma.runtime.StringResourceProfile;
import io.github.somaruntime.soma.runtime.TablePlan;

/**
 * Narrow generated-code construction bridge for schema-seeded RuntimePlan。
 *
 * <p>Application configuration starts from generated
 * {@code SchemaMetadata.newPlan()} and never supplies protocol identities。</p>
 */
public final class GeneratedRuntimePlan {
    private GeneratedRuntimePlan() {
    }

    public static RuntimePlan.Builder builder(
            String schemaHash,
            String runtimeCompatibility,
            String generatedProtocol,
            String planProtocol,
            String allocationEstimator) {
        return RuntimePlan.generatedBuilder(
                GeneratedPlanToken.INSTANCE,
                schemaHash,
                runtimeCompatibility,
                generatedProtocol,
                planProtocol,
                allocationEstimator);
    }

    public static TablePlan table(
            String logicalName,
            String algorithm,
            int initialCapacity,
            int planningRows,
            int maximumRows,
            int growthNumerator,
            int growthDenominator,
            long maximumUpdateScratchBytes,
            long maximumOperationScratchBytes,
            long maximumBulkScratchBytes,
            long maximumTableStorageBytes,
            String keySpaceStrategy,
            String primaryLocatorLayoutFormula,
            String accessStrategy,
            String storageLayoutFormula,
            int structuralBytesPerRow,
            boolean stringCapable,
            StringResourceProfile stringResourceProfile) {
        return TablePlan.generatedBuilder(
                        GeneratedPlanToken.INSTANCE,
                        logicalName,
                        algorithm)
                .initialCapacity(initialCapacity)
                .planningRows(planningRows)
                .maximumRows(maximumRows)
                .growthRatio(growthNumerator, growthDenominator)
                .maximumUpdateScratchBytes(maximumUpdateScratchBytes)
                .maximumOperationScratchBytes(maximumOperationScratchBytes)
                .maximumBulkScratchBytes(maximumBulkScratchBytes)
                .maximumTableStorageBytes(maximumTableStorageBytes)
                .generatedKeySpaceStrategy(
                        GeneratedPlanToken.INSTANCE, keySpaceStrategy)
                .generatedPrimaryLocatorLayoutFormula(
                        GeneratedPlanToken.INSTANCE,
                        primaryLocatorLayoutFormula)
                .generatedAccessStrategy(
                        GeneratedPlanToken.INSTANCE, accessStrategy)
                .generatedStorageLayoutFormula(
                        GeneratedPlanToken.INSTANCE,
                        storageLayoutFormula,
                        structuralBytesPerRow)
                .generatedStringCapable(
                        GeneratedPlanToken.INSTANCE, stringCapable)
                .stringResourceProfile(stringResourceProfile)
                .build();
    }

    public static RuntimePlan.Builder addTable(
            RuntimePlan.Builder builder, TablePlan table) {
        if (builder == null) throw new NullPointerException("builder");
        return builder.generatedAddTable(
                GeneratedPlanToken.INSTANCE, table);
    }

    public static RuntimePlan.Builder addChild(
            RuntimePlan.Builder builder, ChildPlan child) {
        if (builder == null) throw new NullPointerException("builder");
        return builder.generatedAddChild(
                GeneratedPlanToken.INSTANCE, child);
    }
}
