package io.github.somaruntime.soma.benchmarks;

import java.util.Map;
import java.util.Objects;

/** Merges repeated measurements while preserving lane invariants. */
final class SmokeLaneAggregation {
    private SmokeLaneAggregation() {
    }

    static void merge(LaneObservation target, LaneObservation source) {
        if (!target.lane.equals(source.lane)) {
            throw new IllegalArgumentException("cannot merge different benchmark lanes");
        }
        sameAggregateInvariant(target.baselineId, source.baselineId, "baselineId");
        sameAggregateInvariant(target.statsMode, source.statsMode, "statsMode");
        sameAggregateInvariant(target.optionalDensity, source.optionalDensity, "optionalDensity");
        sameAggregateInvariant(target.materializationBudgetDimension,
                source.materializationBudgetDimension, "materializationBudgetDimension");
        sameAggregateInvariant(target.materializationPath, source.materializationPath,
                "materializationPath");
        sameAggregateInvariant(target.allocationEstimatorVersion,
                source.allocationEstimatorVersion, "allocationEstimatorVersion");
        sameAggregateInvariant(target.touchedBytesScope, source.touchedBytesScope,
                "touchedBytesScope");
        sameAggregateInvariant(target.workingSetScope, source.workingSetScope,
                "workingSetScope");
        sameAggregateInvariant(target.effectiveMaterializationBudget,
                source.effectiveMaterializationBudget, "effectiveMaterializationBudget");
        sameAggregateInvariant(target.externalDtoStats, source.externalDtoStats,
                "externalDtoStats");
        sameAggregateInvariant(target.limitations, source.limitations, "knownLimitations");
        target.setupNanos = add(target.setupNanos, source.setupNanos, "setupNanos");
        target.measurementNanos = add(target.measurementNanos, source.measurementNanos,
                "measurementNanos");
        target.exportNanos = add(target.exportNanos, source.exportNanos, "exportNanos");
        target.rows = add(target.rows, source.rows, "rows");
        target.scanned = add(target.scanned, source.scanned, "scanned");
        target.matched = add(target.matched, source.matched, "matched");
        target.changed = add(target.changed, source.changed, "changed");
        target.removed = add(target.removed, source.removed, "removed");
        target.materialized = add(target.materialized, source.materialized, "materialized");
        target.materializationInvocations = add(target.materializationInvocations,
                source.materializationInvocations, "materializationInvocations");
        target.candidates = add(target.candidates, source.candidates, "candidates");
        target.selected = add(target.selected, source.selected, "selected");
        target.operations = add(target.operations, source.operations, "operations");
        target.lookups = add(target.lookups, source.lookups, "lookups");
        target.missing = add(target.missing, source.missing, "missing");
        target.duplicates = add(target.duplicates, source.duplicates, "duplicates");
        target.touchedBytes = add(target.touchedBytes, source.touchedBytes, "touchedBytes");
        target.estimatedAllocationBytes = add(target.estimatedAllocationBytes,
                source.estimatedAllocationBytes, "estimatedAllocationBytes");
        target.materializationEstimatedAllocationBytes = add(
                target.materializationEstimatedAllocationBytes,
                source.materializationEstimatedAllocationBytes,
                "materializationEstimatedAllocationBytes");
        target.explicitReads = add(target.explicitReads, source.explicitReads, "explicitReads");
        target.explicitMutations = add(target.explicitMutations, source.explicitMutations,
                "explicitMutations");
        target.workingSetBytes = Math.max(target.workingSetBytes, source.workingSetBytes);
        mergeLaneEvidence(target, source);
    }

    private static void mergeLaneEvidence(LaneObservation target, LaneObservation source) {
        String lane = target.lane;
        if (lane.startsWith("kernel.optional_")) {
            sum(target.selectorStats, source.selectorStats, "bitmapWords", "present");
        } else if (lane.equals("kernel.packed_scan")) {
            sum(target.selectorStats, source.selectorStats,
                    "somaNanos", "primitiveBaselineNanos");
        } else if (lane.equals("kernel.key_lookup_normal")
                || lane.equals("kernel.key_lookup_collision")) {
            same(target.keySpaceStats, source.keySpaceStats, "implementation",
                    "fullEqualityDistinguished", "collisionConstructed");
            sum(target.keySpaceStats, source.keySpaceStats,
                    "collisionCount", "probeCount", "rehashCount");
        } else if (lane.equals("generated.keyed_frontier")) {
            same(target.keySpaceStats, source.keySpaceStats, "implementation",
                    "tableCapacityBeforeMeasurement", "keySpaceCapacityBeforeMeasurement",
                    "appendValidationKeySpaceCapacity");
            sum(target.keySpaceStats, source.keySpaceStats,
                    "added", "updated", "dynamicFirst", "removed",
                    "appendValidationKeySpaceAllocationBytes",
                    "mainKeySpaceGrowthAllocationBytes", "keySpaceAllocationBytes",
                    "tableGrowthAllocationBytes", "scratchAllocationBytes");
            maximum(target.keySpaceStats, source.keySpaceStats, "tableCapacity",
                    "keySpaceCapacity", "retainedScratchBytes");
            same(target.exactIndexStats, source.exactIndexStats,
                    "implementation", "indexCount");
            sum(target.exactIndexStats, source.exactIndexStats,
                    "probeCount", "collisionCount", "rehashCount", "allocationBytes");
            maximum(target.exactIndexStats, source.exactIndexStats,
                    "entryCount", "groupCount", "currentBytes", "highWaterBytes");
        } else if (lane.equals("generated.exact_index_incremental_lookup")) {
            same(target.exactIndexStats, source.exactIndexStats, "implementation",
                    "lookupKeyWidthBytes", "indexCount");
            sum(target.exactIndexStats, source.exactIndexStats, "addedRows",
                    "exactLookupCount", "exactLookupRows", "appendTouchedBytes",
                    "lookupTouchedBytes", "probeCount", "collisionCount",
                    "rehashCount", "tableGrowthAllocationBytes", "allocationBytes",
                    "scratchAllocationBytes");
            maximum(target.exactIndexStats, source.exactIndexStats,
                    "entryCount", "groupCount", "currentBytes", "highWaterBytes",
                    "tableCapacity", "retainedScratchBytes");
            same(target.keySpaceStats, source.keySpaceStats, "implementation",
                    "capacityBeforeMeasurement");
            sum(target.keySpaceStats, source.keySpaceStats,
                    "appendValidationAllocationBytes", "mainGrowthAllocationBytes",
                    "allocationBytes");
            maximum(target.keySpaceStats, source.keySpaceStats, "capacity");
        } else if (lane.equals("generated.dense_scratch_replace_sort")) {
            same(target.selectorStats, source.selectorStats, "implementation",
                    "dynamicComparatorWidthBytes", "materializedRowWidthBytes");
            sum(target.selectorStats, source.selectorStats, "replaceRows",
                    "dynamicFindFirst", "dynamicFirstOrThrow", "replaceTouchedBytes",
                    "dynamicComparatorRows", "dynamicComparatorTouchedBytes",
                    "materializationTouchedBytes", "scratchAllocationBytes");
            maximum(target.selectorStats, source.selectorStats,
                    "tableCapacity", "retainedScratchBytes");
        } else if (lane.equals("generated.pipeline_fusion")) {
            same(target.selectorStats, source.selectorStats,
                    "implementation", "filterStages", "limitStages");
            sum(target.selectorStats, source.selectorStats,
                    "updateTerminal", "matched", "changed", "perRowObjects");
            maximum(target.selectorStats, source.selectorStats,
                    "capacity", "operationScratchHighWaterBytes", "updateScratchCurrentBytes");
        } else if (lane.equals("kernel.keyspace_full_domain_load_collision_rehash")) {
            same(target.keySpaceStats, source.keySpaceStats,
                    "fullIntDomainMinimumAccepted", "fullIntDomainMaximumAccepted",
                    "hashCapacity", "hashUsed", "hashLoadFactor", "compositeCapacity");
            sum(target.keySpaceStats, source.keySpaceStats,
                    "hashProbeCount", "hashCollisionCount", "hashRehashCount",
                    "compositeProbeCount", "compositeCollisionCount",
                    "compositeRehashCount", "normalMissing", "duplicateAttempts");
        } else if (lane.startsWith("kernel.batch_import")) {
            same(target.selectorStats, source.selectorStats, "reserved");
            maximum(target.selectorStats, source.selectorStats, "capacity");
            sum(target.selectorStats, source.selectorStats, "growthCount");
        } else if (lane.equals("kernel.exact_index_mutation_lookup_storm")) {
            same(target.exactIndexStats, source.exactIndexStats, "implementation");
            sum(target.exactIndexStats, source.exactIndexStats,
                    "probeCount", "collisionCount", "rehashCount", "mutationCount",
                    "lookupCount", "lookupRows", "measurementAllocationBytes");
            maximum(target.exactIndexStats, source.exactIndexStats,
                    "entryCount", "groupCount", "currentBytes", "highWaterBytes");
            same(target.selectorStats, source.selectorStats,
                    "cardinality", "selectivity", "exactLookupBufferBytes");
        } else if (lane.equals("kernel.compaction_capacity_reuse")) {
            sum(target.selectorStats, source.selectorStats,
                    "compactedRows", "singleRemoved", "batchCompacted",
                    "measurementArrayAllocationBytes");
            maximum(target.selectorStats, source.selectorStats,
                    "retainedCapacity", "operationScratchCurrentBytes",
                    "operationScratchHighWaterBytes", "clearReuseCapacityBefore",
                    "clearReuseCapacityAfter");
        } else if (lane.equals("kernel.column_view")) {
            sum(target.columnViewStats, source.columnViewStats, "acquired", "reads",
                    "released", "staleErrors", "releasedErrors", "viewPinnedErrors",
                    "reserveAllocationBytes");
        } else if (lane.equals("kernel.stats_mode_overhead")) {
            sum(target.selectorStats, source.selectorStats, "summaryNanos",
                    "diagnosticNanos", "summaryOperations", "diagnosticOperations");
            same(target.selectorStats, source.selectorStats, "rowsPerOperation");
        } else if (lane.equals("child_locality.parent_scan_vs_flat")) {
            same(target.selectorStats, source.selectorStats, "flatAccess");
            sum(target.selectorStats, source.selectorStats, "parentCount", "childRows",
                    "childInstances", "flatRows", "flatCandidateRows",
                    "childScanNanos", "flatFilterNanos");
        } else if (lane.equals("generated.materialization_recursive_success")) {
            same(target.materializationStats, source.materializationStats, "implementation");
            sum(target.materializationStats, source.materializationStats,
                    "rootMapEntries", "schemaObjects", "lists", "mapEntries",
                    "tableInstances", "rows", "leafValues", "estimatedBytes",
                    "partialResults");
            maximum(target.materializationStats, source.materializationStats, "maximumDepth");
        } else if (lane.equals("materialization.budget_boundary")) {
            same(target.materializationStats, source.materializationStats,
                    "implementation", "tableRowsAfterFailures", "boundaries");
            sum(target.materializationStats, source.materializationStats,
                    "boundarySuccesses", "budgetFailures", "allocationFailures",
                    "recoverySuccesses", "partialResults", "failureCount");
        }
    }

    private static void sum(Map<String, Object> target, Map<String, Object> source,
                            String... fields) {
        for (String field : fields) {
            target.put(field, Long.valueOf(add(SmokeLaneContract.number(target, field), SmokeLaneContract.number(source, field), field)));
        }
    }

    private static void maximum(Map<String, Object> target, Map<String, Object> source,
                                String... fields) {
        for (String field : fields) {
            target.put(field, Long.valueOf(Math.max(SmokeLaneContract.number(target, field), SmokeLaneContract.number(source, field))));
        }
    }

    private static void same(Map<String, Object> target, Map<String, Object> source,
                             String... fields) {
        for (String field : fields) {
            Object left = target.get(field);
            Object right = source.get(field);
            if (left == null ? right != null : !left.equals(right)) {
                throw new IllegalStateException("measurement evidence changed across iterations: "
                        + target + "." + field);
            }
        }
    }

    private static long add(long left, long right, String field) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            throw new IllegalStateException("benchmark counter overflow: " + field, overflow);
        }
    }

    private static void sameAggregateInvariant(Object left, Object right, String field) {
        if (!Objects.equals(left, right)) {
            throw new IllegalStateException("benchmark aggregate invariant changed: " + field);
        }
    }
}
