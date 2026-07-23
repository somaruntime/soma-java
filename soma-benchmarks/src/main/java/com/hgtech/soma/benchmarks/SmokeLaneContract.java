package com.hgtech.soma.benchmarks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Canonical smoke lane manifest, identity, metadata and validation contract. */
final class SmokeLaneContract {
    static final List<String> REQUIRED_LANES = Collections.unmodifiableList(Arrays.asList(
            "kernel.optional_all_present",
            "kernel.optional_all_absent",
            "kernel.optional_mixed_chunks",
            "kernel.packed_scan",
            "generated.pipeline_fusion",
            "kernel.keyspace_full_domain_load_collision_rehash",
            "kernel.key_lookup_normal",
            "kernel.key_lookup_collision",
            "kernel.batch_import.reserve",
            "kernel.batch_import.growth",
            "generated.exact_index_incremental_lookup",
            "generated.keyed_frontier",
            "generated.dense_scratch_replace_sort",
            "kernel.column_view",
            "child_locality.parent_scan_vs_flat",
            "generated.materialization_recursive_success",
            "materialization.budget_boundary",
            "kernel.compaction_capacity_reuse",
            "kernel.exact_index_mutation_lookup_storm",
            "kernel.stats_mode_overhead"));

    private SmokeLaneContract() {
    }

    static List<String> hotColumns(String lane) {
        if (lane.startsWith("kernel.optional_")) return Arrays.asList("value", "presence");
        if (lane.equals("kernel.packed_scan")) return Arrays.asList("value");
        if (lane.equals("generated.pipeline_fusion")) {
            return Arrays.asList("vectorIndex", "entityKind", "entityId", "variableKind",
                    "value", "derivative", "scale");
        }
        if (lane.equals("kernel.keyspace_full_domain_load_collision_rehash")) {
            return Arrays.asList("key", "rowIndex", "hashSlot");
        }
        if (lane.equals("kernel.key_lookup_normal")
                || lane.equals("kernel.key_lookup_collision")) {
            return Arrays.asList("fromLocation.value", "toLocation.value",
                    "distanceMeters", "travelSeconds");
        }
        if (lane.startsWith("kernel.batch_import")) return Arrays.asList("value");
        if (lane.equals("generated.keyed_frontier")) {
            return Arrays.asList("candidateKey", "targetSetupFamily", "operationReleaseMinute",
                    "jobReadyMinute", "materialReadyMinute", "baseReadyMinute",
                    "processingMinutes", "setupMinutes", "effectiveReadyMinute",
                    "fcfsValue", "sptValue", "indicatorReady");
        }
        if (lane.equals("generated.exact_index_incremental_lookup")) {
            return Arrays.asList("candidateKey.machineId.value", "candidateKey.operationKey",
                    "baseReadyMinute", "processingMinutes", "indicatorReady");
        }
        if (lane.equals("generated.dense_scratch_replace_sort")) {
            return Arrays.asList("routeId.value", "customerId.value", "insertionOrdinal",
                    "routeVersion", "deltaDistanceMeters", "projectedArrivalSecond",
                    "projectedLoad", "projectedTotalDurationSeconds");
        }
        if (lane.equals("kernel.column_view")) return Arrays.asList("value", "presence");
        if (lane.equals("child_locality.parent_scan_vs_flat")) {
            return Arrays.asList("parentId", "value");
        }
        if (lane.equals("generated.materialization_recursive_success")
                || lane.equals("materialization.budget_boundary")) {
            return Arrays.asList("operationKey", "candidateMachines");
        }
        if (lane.equals("kernel.compaction_capacity_reuse")) return Arrays.asList("value");
        if (lane.equals("kernel.exact_index_mutation_lookup_storm")) {
            return Arrays.asList("hashGroup", "rowLink");
        }
        if (lane.equals("kernel.stats_mode_overhead")) return Arrays.asList("value");
        throw new IllegalArgumentException("missing Access Pattern Card hot columns for " + lane);
    }

    static String workloadId(String lane) {
        if (!REQUIRED_LANES.contains(lane)) {
            throw new IllegalArgumentException("unknown benchmark workload lane: " + lane);
        }
        return "soma-g5-smoke:" + lane + ":v4";
    }

    static void validateWorkloadEvidence(String lane, Map<String, Object> evidence) {
        List<String> keys = new ArrayList<String>(evidence.keySet());
        if (!keys.equals(Arrays.asList("executed", "proof", "implementation",
                "positiveCount", "phase"))) {
            throw new IllegalArgumentException("workloadEvidence exact fields mismatch for " + lane);
        }
        if (!Boolean.TRUE.equals(evidence.get("executed"))) {
            throw new IllegalArgumentException("workloadEvidence.executed must be true for " + lane);
        }
        if (!proofKey(lane).equals(evidence.get("proof"))) {
            throw new IllegalArgumentException("wrong workload proof for " + lane);
        }
        String expectedImplementation = isGeneratedLane(lane)
                ? "generated-api" : "runtime-kernel";
        if (!expectedImplementation.equals(evidence.get("implementation"))) {
            throw new IllegalArgumentException("wrong workload implementation for " + lane);
        }
        Object count = evidence.get("positiveCount");
        if (!(count instanceof Long || count instanceof Integer)
                || ((Number) count).longValue() <= 0L) {
            throw new IllegalArgumentException("workloadEvidence.positiveCount must be positive for " + lane);
        }
        if (!phaseKey(lane).equals(evidence.get("phase"))) {
            throw new IllegalArgumentException("wrong workload phase for " + lane);
        }
    }

    static void validateLaneRecord(String lane, Map<String, Object> record) {
        Map<String, Object> accessPatternCard = nested(record, "accessPatternCard");
        validateAccessPatternCard(accessPatternCard);
        if (!hotColumns(lane).equals(accessPatternCard.get("hotColumns"))) {
            throw new IllegalArgumentException(
                    "Access Pattern Card hot columns contradict lane schema: " + lane);
        }
        validateDefaultMap(nested(record, "externalDtoStats"), "externalDtoStats");
        Map<String, Object> rows = nested(record, "rowCounts");
        Map<String, Object> operations = nested(record, "operationCounts");
        Map<String, Object> candidates = nested(record, "candidateCounts");
        Map<String, Object> allocation = nested(record, "allocationEstimate");
        long iterations = number(record, "measurementIterations");
        long scaleRows = number(nested(record, "scale"), "rows");
        boolean exactIndex = false, keySpace = false, selector = false;
        boolean materialization = number(operations, "materializations") > 0L;
        boolean specializedMaterialization = false, budget = false, columnView = false;

        if (lane.startsWith("kernel.optional_")) {
            selector = true;
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactIntegers(stats,
                    new String[] {"bitmapWords", "present"});
            require(number(stats, "present") == number(rows, "matched")
                            && number(stats, "present") == number(candidates, "selected"),
                    "optional present count contradicts root facts");
            require(number(stats, "bitmapWords")
                            == iterations * ((scaleRows + 63L) >>> 6),
                    "optional bitmap words contradict measurement iterations");
            require(number(record, "touchedBytesEstimate")
                            == 4L * number(stats, "present")
                            + 8L * number(stats, "bitmapWords"),
                    "optional touched bytes contradict bitmap/present facts");
        } else if (lane.equals("kernel.packed_scan")) {
            selector = true;
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactIntegers(stats,
                    new String[] {"somaNanos", "primitiveBaselineNanos"});
            require(number(stats, "somaNanos") + number(stats, "primitiveBaselineNanos")
                            == number(nested(record, "phaseTimings"), "measurementNanos"),
                    "packed nested timings contradict measurement phase");
            long processed = 2L * scaleRows * iterations;
            require(number(operations, "operations") == 2L * iterations
                            && number(rows, "source") == processed
                            && number(rows, "scanned") == processed
                            && number(rows, "matched") == processed,
                    "packed counters contradict measured same-value scans");
        } else if (lane.equals("generated.pipeline_fusion")) {
            selector = true;
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactKeys(stats, new String[] {"implementation", "filterStages", "limitStages",
                    "updateTerminal", "matched", "changed",
                    "capacity", "operationScratchHighWaterBytes",
                    "updateScratchCurrentBytes", "perRowObjects"});
            requireStringValue(stats, "implementation", "generated-candidate-scan");
            requireIntegerFields(stats, new String[] {"filterStages", "limitStages",
                    "updateTerminal", "matched", "changed",
                    "capacity", "operationScratchHighWaterBytes",
                    "updateScratchCurrentBytes", "perRowObjects"});
            requirePositive(stats, "updateTerminal", lane);
            require(number(stats, "perRowObjects") == 0L,
                    "fusion lane per-row allocation shape");
            require(number(stats, "updateTerminal") == iterations
                            && number(stats, "matched") == number(rows, "matched")
                            && number(stats, "changed") == number(rows, "changed")
                            && number(operations, "operations") == iterations
                            && number(record, "touchedBytesEstimate")
                            == 4L * number(rows, "scanned")
                            + 88L * number(rows, "matched")
                            && number(record, "workingSetEstimate")
                            == 44L * number(stats, "capacity")
                            + number(stats, "operationScratchHighWaterBytes")
                            + number(stats, "updateScratchCurrentBytes")
                            && number(allocation, "bytes") == iterations
                            * (number(stats, "operationScratchHighWaterBytes")
                            + number(stats, "updateScratchCurrentBytes")),
                    "fusion nested counters contradict root measurement facts");
        } else if (lane.equals("kernel.keyspace_full_domain_load_collision_rehash")) {
            keySpace = true;
            Map<String, Object> stats = nested(record, "keySpaceStats");
            requireExactKeys(stats, new String[] {"fullIntDomainMinimumAccepted",
                    "fullIntDomainMaximumAccepted", "hashCapacity", "hashUsed",
                    "hashLoadFactor", "hashProbeCount", "hashCollisionCount",
                    "hashRehashCount", "compositeCapacity", "compositeProbeCount",
                    "compositeCollisionCount", "compositeRehashCount",
                    "normalMissing", "duplicateAttempts"});
            requireBoolean(stats, "fullIntDomainMinimumAccepted", true);
            requireBoolean(stats, "fullIntDomainMaximumAccepted", true);
            requireIntegerFields(stats, new String[] {"hashCapacity", "hashUsed",
                    "hashProbeCount", "hashCollisionCount", "hashRehashCount",
                    "compositeCapacity", "compositeProbeCount",
                    "compositeCollisionCount", "compositeRehashCount",
                    "normalMissing", "duplicateAttempts"});
            requireRange(stats, "hashLoadFactor", 0.0d, 1.0d);
            require(number(stats, "normalMissing") == number(operations, "missing"),
                    "KeySpace missing counters contradict root facts");
            require(number(stats, "duplicateAttempts")
                            == number(operations, "duplicates"),
                    "KeySpace duplicate counters contradict root facts");
        } else if (lane.equals("kernel.key_lookup_normal")
                || lane.equals("kernel.key_lookup_collision")) {
            keySpace = true;
            Map<String, Object> stats = nested(record, "keySpaceStats");
            requireExactKeys(stats, new String[] {"implementation", "fullEqualityDistinguished",
                    "collisionConstructed", "collisionCount", "probeCount", "rehashCount"});
            requireStringValue(stats, "implementation", "generated-hash-composite-v2");
            requireBoolean(stats, "fullEqualityDistinguished", true);
            requireBoolean(stats, "collisionConstructed", lane.endsWith("collision"));
            requireIntegerFields(stats, new String[] {"collisionCount", "probeCount", "rehashCount"});
            if (lane.endsWith("collision")) requirePositive(stats, "collisionCount", lane);
            require(number(operations, "operations") == 3L * iterations
                            && number(operations, "lookups") == 3L * iterations
                            && number(operations, "materializations") == 3L * iterations
                            && number(rows, "scanned") == 2L * iterations
                            && number(rows, "matched") == 2L * iterations
                            && number(rows, "materialized") == 2L * iterations,
                    "generated key lookup counters contradict measured terminals");
        } else if (lane.startsWith("kernel.batch_import")) {
            selector = true;
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactKeys(stats, new String[] {"reserved", "capacity", "growthCount"});
            requireBoolean(stats, "reserved", lane.endsWith("reserve"));
            requireIntegerFields(stats, new String[] {"capacity", "growthCount"});
            require(number(rows, "source") == scaleRows * iterations
                            && number(rows, "scanned") == number(rows, "source")
                            && number(rows, "matched") == number(rows, "source")
                            && number(rows, "changed") == number(rows, "source")
                            && number(operations, "operations") == iterations
                            && number(stats, "growthCount")
                            == (lane.endsWith("reserve") ? 0L : iterations),
                    "batch import counters contradict measurement window");
        } else if (lane.equals("generated.exact_index_incremental_lookup")) {
            exactIndex = true;
            keySpace = true;
            Map<String, Object> stats = nested(record, "exactIndexStats");
            requireExactKeys(stats, new String[] {"implementation", "addedRows",
                    "exactLookupCount", "exactLookupRows", "lookupKeyWidthBytes",
                    "appendTouchedBytes", "lookupTouchedBytes",
                    "indexCount", "entryCount", "groupCount", "probeCount",
                    "collisionCount", "rehashCount", "currentBytes", "highWaterBytes",
                    "tableCapacity", "tableGrowthAllocationBytes", "allocationBytes",
                    "scratchAllocationBytes", "retainedScratchBytes"});
            requireStringValue(stats, "implementation", "generated-grouped-exact-index");
            requireIntegerFields(stats, new String[] {"addedRows", "exactLookupCount",
                    "exactLookupRows", "lookupKeyWidthBytes", "appendTouchedBytes",
                    "lookupTouchedBytes", "indexCount", "entryCount",
                    "groupCount", "probeCount", "collisionCount", "rehashCount",
                    "currentBytes", "highWaterBytes", "tableCapacity",
                    "tableGrowthAllocationBytes", "allocationBytes", "scratchAllocationBytes",
                    "retainedScratchBytes"});
            requirePositive(stats, "addedRows", lane);
            requirePositive(stats, "exactLookupCount", lane);
            requirePositive(stats, "indexCount", lane);
            requirePositive(stats, "entryCount", lane);
            Map<String, Object> keys = nested(record, "keySpaceStats");
            requireExactKeys(keys, new String[] {"implementation",
                    "appendValidationAllocationBytes", "mainGrowthAllocationBytes",
                    "allocationBytes", "capacityBeforeMeasurement", "capacity"});
            requireStringValue(keys, "implementation", "generated-hash-composite-v2");
            requireIntegerFields(keys, new String[] {"appendValidationAllocationBytes",
                    "mainGrowthAllocationBytes", "allocationBytes",
                    "capacityBeforeMeasurement", "capacity"});
            require(number(stats, "addedRows") == number(rows, "source")
                            && number(rows, "changed") == number(rows, "source")
                            && number(rows, "scanned") == number(stats, "addedRows")
                            + number(stats, "exactLookupRows")
                            && number(rows, "matched") == number(stats, "exactLookupCount")
                            && number(operations, "operations") == 2L * iterations
                            && number(operations, "lookups") == iterations
                            && number(operations, "materializations") == 0L
                            && number(rows, "materialized") == 0L
                            && number(stats, "exactLookupCount")
                                    == number(stats, "exactLookupRows")
                            && number(stats, "appendTouchedBytes")
                            == 105L * number(stats, "addedRows")
                            && number(stats, "lookupKeyWidthBytes") == 8L
                            && number(stats, "lookupTouchedBytes")
                            == 8L * number(stats, "exactLookupRows")
                            && number(record, "touchedBytesEstimate")
                            == number(stats, "appendTouchedBytes")
                            + number(stats, "lookupTouchedBytes")
                            && number(record, "workingSetEstimate")
                            == 105L * number(stats, "tableCapacity")
                            + 13L * number(keys, "capacity")
                            + number(stats, "currentBytes")
                            + number(stats, "retainedScratchBytes")
                            && number(allocation, "bytes")
                            == number(stats, "tableGrowthAllocationBytes")
                            + number(stats, "allocationBytes")
                            + number(keys, "allocationBytes")
                            + number(stats, "scratchAllocationBytes"),
                    "exact-index incremental lookup counters contradict the workload");
        } else if (lane.equals("generated.dense_scratch_replace_sort")) {
            selector = true;
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactKeys(stats, new String[] {"implementation", "replaceRows",
                    "dynamicFindFirst", "dynamicFirstOrThrow", "replaceTouchedBytes",
                    "dynamicComparatorRows", "dynamicComparatorWidthBytes",
                    "dynamicComparatorTouchedBytes", "materializedRowWidthBytes",
                    "materializationTouchedBytes", "tableCapacity",
                    "scratchAllocationBytes", "retainedScratchBytes"});
            requireStringValue(stats, "implementation", "generated-candidate-scan");
            requireIntegerFields(stats, new String[] {"replaceRows", "dynamicFindFirst",
                    "dynamicFirstOrThrow", "replaceTouchedBytes", "dynamicComparatorRows",
                    "dynamicComparatorWidthBytes", "dynamicComparatorTouchedBytes",
                    "materializedRowWidthBytes", "materializationTouchedBytes",
                    "tableCapacity", "scratchAllocationBytes", "retainedScratchBytes"});
            requirePositive(stats, "replaceRows", lane);
            requirePositive(stats, "dynamicFindFirst", lane);
            requirePositive(stats, "dynamicFirstOrThrow", lane);
            long terminals = number(stats, "dynamicFindFirst")
                    + number(stats, "dynamicFirstOrThrow");
            require(number(stats, "replaceRows") == number(rows, "changed")
                            && terminals == number(operations, "materializations")
                            && terminals == number(rows, "materialized")
                            && number(operations, "operations") == iterations + terminals
                            && number(stats, "replaceTouchedBytes")
                            == 56L * number(stats, "replaceRows")
                            && number(stats, "dynamicComparatorWidthBytes") == 8L
                            && number(stats, "dynamicComparatorTouchedBytes")
                            == 8L * number(stats, "dynamicComparatorRows")
                            && number(stats, "materializedRowWidthBytes") == 56L
                            && number(stats, "materializationTouchedBytes")
                            == 56L * number(rows, "materialized")
                            && number(record, "touchedBytesEstimate")
                            == number(stats, "replaceTouchedBytes")
                            + number(stats, "dynamicComparatorTouchedBytes")
                            + number(stats, "materializationTouchedBytes")
                            && number(record, "workingSetEstimate")
                            == 56L * number(stats, "tableCapacity")
                            + number(stats, "retainedScratchBytes")
                            && number(allocation, "bytes")
                            == number(nested(record, "materializationStats"), "estimatedBytes")
                            + number(stats, "scratchAllocationBytes"),
                    "dense workspace counters include setup or contradict terminals");
        } else if (lane.equals("generated.keyed_frontier")) {
            exactIndex = true;
            keySpace = true;
            Map<String, Object> keys = nested(record, "keySpaceStats");
            requireExactKeys(keys, new String[] {"implementation", "added", "updated",
                    "dynamicFirst", "removed", "tableCapacityBeforeMeasurement",
                    "tableCapacity", "keySpaceCapacityBeforeMeasurement", "keySpaceCapacity",
                    "appendValidationKeySpaceCapacity",
                    "appendValidationKeySpaceAllocationBytes",
                    "mainKeySpaceGrowthAllocationBytes", "keySpaceAllocationBytes",
                    "tableGrowthAllocationBytes", "scratchAllocationBytes",
                    "retainedScratchBytes"});
            requireStringValue(keys, "implementation", "generated-machine-candidate-frontier");
            requireIntegerFields(keys, new String[] {"added", "updated", "dynamicFirst",
                    "removed", "tableCapacityBeforeMeasurement", "tableCapacity",
                    "keySpaceCapacityBeforeMeasurement", "keySpaceCapacity",
                    "appendValidationKeySpaceCapacity",
                    "appendValidationKeySpaceAllocationBytes",
                    "mainKeySpaceGrowthAllocationBytes", "keySpaceAllocationBytes",
                    "tableGrowthAllocationBytes", "scratchAllocationBytes",
                    "retainedScratchBytes"});
            requirePositive(keys, "added", lane);
            requirePositive(keys, "updated", lane);
            requirePositive(keys, "dynamicFirst", lane);
            requirePositive(keys, "removed", lane);
            Map<String, Object> indexes = nested(record, "exactIndexStats");
            requireExactKeys(indexes, new String[] {"implementation", "indexCount",
                    "entryCount", "groupCount", "probeCount", "collisionCount",
                    "rehashCount", "currentBytes", "highWaterBytes", "allocationBytes"});
            requireStringValue(indexes, "implementation", "generated-grouped-exact-index");
            requireIntegerFields(indexes, new String[] {"indexCount", "entryCount",
                    "groupCount", "probeCount", "collisionCount", "rehashCount",
                    "currentBytes", "highWaterBytes", "allocationBytes"});
            requirePositive(indexes, "indexCount", lane);
            requirePositive(indexes, "entryCount", lane);
            require(number(operations, "operations") == 4L * iterations
                            && number(rows, "changed")
                            == number(keys, "added") + number(keys, "updated")
                            && number(rows, "removed") == number(keys, "removed")
                            && number(rows, "materialized") == number(keys, "dynamicFirst")
                            && number(operations, "materializations")
                            == number(keys, "dynamicFirst")
                            && number(record, "touchedBytesEstimate")
                            == 105L * (number(rows, "scanned") + iterations)
                            && number(record, "workingSetEstimate")
                            == 105L * number(keys, "tableCapacity")
                            + 13L * number(keys, "keySpaceCapacity")
                            + number(indexes, "currentBytes")
                            + number(keys, "retainedScratchBytes")
                            && number(keys, "appendValidationKeySpaceAllocationBytes")
                            == 13L * number(keys, "appendValidationKeySpaceCapacity")
                            * iterations
                            && number(keys, "mainKeySpaceGrowthAllocationBytes")
                            == (number(keys, "keySpaceCapacity")
                            > number(keys, "keySpaceCapacityBeforeMeasurement")
                            ? 13L * number(keys, "keySpaceCapacity") * iterations : 0L)
                            && number(keys, "keySpaceAllocationBytes")
                            == number(keys, "appendValidationKeySpaceAllocationBytes")
                            + number(keys, "mainKeySpaceGrowthAllocationBytes")
                            && number(keys, "tableGrowthAllocationBytes")
                            == (number(keys, "tableCapacity")
                            > number(keys, "tableCapacityBeforeMeasurement")
                            ? 105L * number(keys, "tableCapacity") * iterations : 0L)
                            && number(allocation, "bytes")
                            == number(nested(record, "materializationStats"), "estimatedBytes")
                            + number(keys, "tableGrowthAllocationBytes")
                            + number(keys, "keySpaceAllocationBytes")
                            + number(indexes, "allocationBytes")
                            + number(keys, "scratchAllocationBytes"),
                    "keyed frontier nested counters contradict measured lifecycle");
        } else if (lane.equals("kernel.column_view")) {
            columnView = true;
            Map<String, Object> stats = nested(record, "columnViewStats");
            requireExactIntegers(stats,
                    new String[] {"acquired", "reads", "released", "staleErrors",
                            "releasedErrors", "viewPinnedErrors", "reserveAllocationBytes"});
            require(number(stats, "acquired") == iterations
                            && number(stats, "released") == iterations
                            && number(stats, "staleErrors") == 0L
                            && number(stats, "releasedErrors") == iterations
                            && number(stats, "viewPinnedErrors") == iterations
                            && number(stats, "reads") == number(rows, "matched")
                            && number(allocation, "bytes")
                            == number(stats, "reserveAllocationBytes"),
                    "ColumnView exact lifecycle evidence");
        } else if (lane.equals("child_locality.parent_scan_vs_flat")) {
            selector = true;
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactKeys(stats, new String[] {"parentCount", "childRows", "childInstances",
                    "flatRows", "flatCandidateRows", "flatAccess",
                    "childScanNanos", "flatFilterNanos"});
            requireIntegerFields(stats, new String[] {"parentCount", "childRows", "childInstances",
                    "flatRows", "flatCandidateRows", "childScanNanos", "flatFilterNanos"});
            requireStringValue(stats, "flatAccess", "flat-scan-filter");
            require(number(stats, "childRows") == number(rows, "source")
                            && number(stats, "childRows") == number(candidates, "selected")
                            && number(stats, "flatRows") == number(candidates, "source")
                            && number(rows, "scanned") == number(stats, "childRows")
                            + number(stats, "flatCandidateRows")
                            && number(stats, "childScanNanos")
                            + number(stats, "flatFilterNanos")
                            == number(nested(record, "phaseTimings"), "measurementNanos")
                            && number(stats, "parentCount") == 8L * iterations
                            && number(stats, "childInstances") == iterations,
                    "child locality nested evidence contradicts root facts");
        } else if (lane.equals("generated.materialization_recursive_success")) {
            materialization = true;
            specializedMaterialization = true;
            budget = true;
            Map<String, Object> stats = nested(record, "materializationStats");
            requireExactKeys(stats, new String[] {"implementation", "rootMapEntries",
                    "schemaObjects", "lists", "mapEntries", "maximumDepth",
                    "tableInstances", "rows", "leafValues", "estimatedBytes",
                    "partialResults"});
            requireStringValue(stats, "implementation", "generated-recursive-materializer");
            requireIntegerFields(stats, new String[] {"rootMapEntries", "schemaObjects",
                    "lists", "mapEntries", "maximumDepth", "tableInstances", "rows",
                    "leafValues", "estimatedBytes", "partialResults"});
            requirePositive(stats, "rootMapEntries", lane);
            requirePositive(stats, "schemaObjects", lane);
            requirePositive(stats, "lists", lane);
            require(number(stats, "partialResults") == 0L, "materialization partial result");
            require(number(operations, "operations") == iterations
                            && number(operations, "materializations") == iterations
                            && number(rows, "source") == number(stats, "rows")
                            && number(rows, "scanned") == number(stats, "rows")
                            && number(rows, "matched") == number(stats, "rows")
                            && number(rows, "materialized") == number(stats, "rows")
                            && number(stats, "schemaObjects") == number(stats, "rows")
                            && number(stats, "estimatedBytes")
                            == number(nested(record, "allocationEstimate"), "bytes")
                            && number(stats, "rootMapEntries") == 2L * iterations
                            && number(stats, "lists") == 2L * iterations
                            && number(stats, "mapEntries") == 2L * iterations,
                    "recursive materialization nested/root facts contradict");
        } else if (lane.equals("materialization.budget_boundary")) {
            materialization = true;
            specializedMaterialization = true;
            budget = true;
            Map<String, Object> stats = nested(record, "materializationStats");
            requireExactKeys(stats, new String[] {"implementation", "boundarySuccesses",
                    "budgetFailures", "allocationFailures", "recoverySuccesses",
                    "partialResults", "tableRowsAfterFailures", "failureCount", "boundaries"});
            requireStringValue(stats, "implementation", "generated-recursive-materializer");
            requireIntegerFields(stats, new String[] {"boundarySuccesses", "budgetFailures",
                    "allocationFailures", "recoverySuccesses", "partialResults",
                    "tableRowsAfterFailures", "failureCount"});
            require(number(stats, "boundarySuccesses") == 5L * iterations,
                    "five boundary successes per measurement");
            require(number(stats, "budgetFailures") == 5L * iterations,
                    "five boundary failures per measurement");
            require(number(stats, "allocationFailures") == iterations,
                    "one allocation failure per measurement");
            require(number(stats, "recoverySuccesses") == iterations,
                    "one recovery success per measurement");
            require(number(stats, "partialResults") == 0L, "no partial results");
            require(number(stats, "failureCount") == 6L * iterations,
                    "materialization failure count contradicts measured failures");
            long invocations = number(stats, "boundarySuccesses")
                    + number(stats, "budgetFailures")
                    + number(stats, "allocationFailures")
                    + number(stats, "recoverySuccesses");
            long published = 4L * (number(stats, "boundarySuccesses")
                    + number(stats, "recoverySuccesses"));
            require(number(operations, "operations") == invocations
                            && number(operations, "materializations") == invocations
                            && number(rows, "materialized") == published
                            && number(rows, "scanned") == published
                            && number(rows, "matched") == published,
                    "materialization budget root counters include setup or contradict outcomes");
            validateBoundaries(stats.get("boundaries"));
        } else if (lane.equals("kernel.compaction_capacity_reuse")) {
            selector = true;
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactIntegers(stats,
                    new String[] {"compactedRows", "retainedCapacity",
                            "operationScratchCurrentBytes", "operationScratchHighWaterBytes",
                            "singleRemoved", "batchCompacted", "clearReuseCapacityBefore",
                            "clearReuseCapacityAfter", "measurementArrayAllocationBytes"});
            require(number(stats, "compactedRows") == number(rows, "removed")
                            && number(stats, "batchCompacted") == number(rows, "removed")
                            && number(stats, "singleRemoved") == iterations
                            && number(operations, "operations") == 3L * iterations
                            && number(allocation, "bytes")
                            == number(stats, "measurementArrayAllocationBytes"),
                    "compaction nested counters contradict root measurement facts");
        } else if (lane.equals("kernel.exact_index_mutation_lookup_storm")) {
            exactIndex = true;
            selector = true;
            Map<String, Object> indexStats = nested(record, "exactIndexStats");
            requireExactKeys(indexStats, new String[] {"implementation", "entryCount",
                    "groupCount", "probeCount", "collisionCount", "rehashCount",
                    "currentBytes", "highWaterBytes", "mutationCount", "lookupCount",
                    "lookupRows", "measurementAllocationBytes"});
            requireStringValue(indexStats, "implementation", "kernel-grouped-exact-index");
            requireIntegerFields(indexStats, new String[] {"entryCount", "groupCount",
                    "probeCount", "collisionCount", "rehashCount", "currentBytes",
                    "highWaterBytes", "mutationCount", "lookupCount", "lookupRows",
                    "measurementAllocationBytes"});
            require(number(indexStats, "entryCount")
                            == number(nested(record, "selectorStats"), "cardinality")
                            && number(indexStats, "lookupRows")
                                    == number(rows, "source")
                            && number(indexStats, "mutationCount") > 0L
                            && number(indexStats, "measurementAllocationBytes") == 0L
                            && number(allocation, "bytes") == 0L
                            && number(record, "workingSetEstimate")
                                    == number(indexStats, "currentBytes"),
                    "incremental exact-index storm evidence");
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactKeys(stats, new String[] {"cardinality", "selectivity",
                    "exactLookupBufferBytes"});
            requireIntegerFields(stats, new String[] {"cardinality", "exactLookupBufferBytes"});
            requireRange(stats, "selectivity", 0.0d, 1.0d);
        } else if (lane.equals("kernel.stats_mode_overhead")) {
            selector = true;
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactIntegers(stats, new String[] {"summaryNanos", "diagnosticNanos",
                    "summaryOperations", "diagnosticOperations", "rowsPerOperation"});
            requirePositive(stats, "summaryOperations", lane);
            requirePositive(stats, "diagnosticOperations", lane);
            long instrumentedOperations = number(stats, "summaryOperations")
                    + number(stats, "diagnosticOperations");
            require(number(operations, "operations") == instrumentedOperations
                            && number(rows, "scanned") == instrumentedOperations
                            * number(stats, "rowsPerOperation")
                            && number(rows, "source") == number(rows, "scanned")
                            && number(stats, "summaryNanos")
                            + number(stats, "diagnosticNanos")
                            == number(nested(record, "phaseTimings"), "measurementNanos"),
                    "stats overhead counters contradict integrated scans");
        } else {
            throw new IllegalArgumentException("missing lane evidence contract " + lane);
        }

        validateDefaultIfUnused(record, "exactIndexStats", exactIndex);
        validateDefaultIfUnused(record, "keySpaceStats", keySpace);
        validateDefaultIfUnused(record, "selectorStats", selector);
        if (!materialization) {
            validateDefaultMap(nested(record, "materializationStats"), "materializationStats");
        } else if (!specializedMaterialization) {
            validateGenericMaterialization(nested(record, "materializationStats"), record);
        }
        if (budget) validateBudget(nested(record, "effectiveMaterializationBudget"));
        else validateDefaultMap(nested(record, "effectiveMaterializationBudget"),
                "effectiveMaterializationBudget");
        validateDefaultIfUnused(record, "columnViewStats", columnView);
    }

    private static void validateAccessPatternCard(Map<String, Object> values) {
        requireExactKeys(values, new String[] {"rowsCardinality", "hotColumns", "accessSource",
                "readMutationMix", "selectivity", "optionalDensity", "childDensity",
                "workingSetBytes", "allocationExport", "phaseBoundary"});
        requireIntegerFields(values, new String[] {"rowsCardinality", "workingSetBytes"});
        requireStringFields(values, new String[] {"accessSource", "optionalDensity",
                "childDensity", "allocationExport", "phaseBoundary"});
        requireStringList(values.get("hotColumns"), "accessPatternCard.hotColumns");
        Map<String, Object> ratio = nested(values, "readMutationMix");
        requireExactIntegers(ratio, new String[] {"mutations", "reads"});
        requireRange(values, "selectivity", 0.0d, 1.0d);
    }

    private static void validateDefaultIfUnused(Map<String, Object> record, String field,
                                                boolean used) {
        if (!used) validateDefaultMap(nested(record, field), field);
    }

    private static void validateDefaultMap(Map<String, Object> values, String field) {
        requireExactKeys(values, new String[] {"applicable", "reason"});
        requireBoolean(values, "applicable", false);
        requireNonEmptyString(values, "reason", field);
    }

    private static void validateBudget(Map<String, Object> values) {
        requireExactKeys(values, new String[] {"identity", "maximumOwnershipDepth",
                "maximumTableInstances", "maximumRows", "maximumLeafValues",
                "maximumEstimatedAllocationBytes"});
        requireNonEmptyString(values, "identity", "effectiveMaterializationBudget");
        requireIntegerFields(values, new String[] {"maximumOwnershipDepth",
                "maximumTableInstances", "maximumRows", "maximumLeafValues",
                "maximumEstimatedAllocationBytes"});
    }

    private static void validateGenericMaterialization(Map<String, Object> values,
                                                       Map<String, Object> record) {
        requireExactKeys(values, new String[] {"implementation", "invocations", "rows",
                "estimatedBytes", "observationKind"});
        requireStringValue(values, "implementation", "generated-row-materializer");
        requireStringValue(values, "observationKind", "measured");
        requireIntegerFields(values, new String[] {"invocations", "rows"});
        if (values.get("estimatedBytes") != null) {
            requireIntegerFields(values, new String[] {"estimatedBytes"});
        }
        long expectedInvocations = number(nested(record, "operationCounts"),
                "materializations");
        long expectedRows = number(nested(record, "rowCounts"), "materialized");
        if (number(values, "invocations") != expectedInvocations
                || number(values, "rows") != expectedRows
                || expectedInvocations <= 0L || expectedRows <= 0L) {
            throw new IllegalArgumentException("generic materialization counters contradict root facts");
        }
        Object estimated = values.get("estimatedBytes");
        if (estimated == null || ((Number) estimated).longValue() <= 0L
                || ((Number) estimated).longValue()
                > number(nested(record, "allocationEstimate"), "bytes")) {
            throw new IllegalArgumentException(
                    "generic materialization estimate exceeds measurement allocation estimate");
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateBoundaries(Object value) {
        if (!(value instanceof List) || ((List<?>) value).size() != 5) {
            throw new IllegalArgumentException("materialization boundaries must contain five entries");
        }
        java.util.HashSet<String> dimensions = new java.util.HashSet<String>();
        for (Object item : (List<Object>) value) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("materialization boundary must be object");
            }
            Map<String, Object> boundary = (Map<String, Object>) item;
            requireExactKeys(boundary, new String[] {"dimension", "successLimit",
                    "failureLimit", "current", "proposed", "path", "successAtLimit"});
            requireNonEmptyString(boundary, "dimension", "boundary");
            requireIntegerFields(boundary, new String[] {"successLimit", "failureLimit",
                    "current", "proposed"});
            requireNonEmptyString(boundary, "path", "boundary");
            requireBoolean(boundary, "successAtLimit", true);
            String dimension = (String) boundary.get("dimension");
            if (!dimensions.add(dimension)) {
                throw new IllegalArgumentException("duplicate materialization boundary " + dimension);
            }
            if (number(boundary, "successLimit")
                    != number(boundary, "failureLimit") + 1L
                    || number(boundary, "proposed")
                    != number(boundary, "successLimit")) {
                throw new IllegalArgumentException(
                        "boundary must prove exact success and limit+1 failure");
            }
        }
        if (!dimensions.equals(new java.util.HashSet<String>(Arrays.asList(
                "maximumOwnershipDepth", "maximumTableInstances", "maximumRows",
                "maximumLeafValues", "maximumEstimatedAllocationBytes")))) {
            throw new IllegalArgumentException("materialization boundary dimensions mismatch");
        }
    }

    private static void requireExactIntegers(Map<String, Object> values, String[] fields) {
        requireExactKeys(values, fields);
        requireIntegerFields(values, fields);
    }

    private static void requireExactKeys(Map<String, Object> values, String[] fields) {
        if (!new ArrayList<String>(values.keySet()).equals(Arrays.asList(fields))) {
            throw new IllegalArgumentException("exact nested fields mismatch: " + values.keySet());
        }
    }

    private static void requireIntegerFields(Map<String, Object> values, String[] fields) {
        for (String field : fields) {
            long value = number(values, field);
            if (value < 0L) throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private static void requireStringFields(Map<String, Object> values, String[] fields) {
        for (String field : fields) requireNonEmptyString(values, field, "nested");
    }

    private static void requireNonEmptyString(Map<String, Object> values, String field,
                                              String path) {
        Object value = values.get(field);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException(path + "." + field + " must be non-empty string");
        }
    }

    private static void requireStringValue(Map<String, Object> values, String field,
                                           String expected) {
        if (!expected.equals(values.get(field))) {
            throw new IllegalArgumentException(field + " const mismatch");
        }
    }

    private static void requireBoolean(Map<String, Object> values, String field,
                                       boolean expected) {
        if (!Boolean.valueOf(expected).equals(values.get(field))) {
            throw new IllegalArgumentException(field + " boolean const mismatch");
        }
    }

    private static void requireRange(Map<String, Object> values, String field,
                                     double minimum, double maximum) {
        Object value = values.get(field);
        if (!(value instanceof Number)) throw new IllegalArgumentException(field + " must be number");
        double number = ((Number) value).doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number)
                || number < minimum || number > maximum) {
            throw new IllegalArgumentException(field + " range");
        }
    }

    @SuppressWarnings("unchecked")
    private static void requireStringList(Object value, String field) {
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
            throw new IllegalArgumentException(field + " must be non-empty string array");
        }
        for (Object item : (List<Object>) value) {
            if (!(item instanceof String) || ((String) item).isEmpty()) {
                throw new IllegalArgumentException(field + " must contain non-empty strings");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> record, String field) {
        Object value = record.get(field);
        if (!(value instanceof Map) || ((Map<?, ?>) value).isEmpty()) {
            throw new IllegalArgumentException(field + " must be non-empty");
        }
        return (Map<String, Object>) value;
    }

    private static void requirePositive(Map<String, Object> values, String field, String lane) {
        if (number(values, field) <= 0L) {
            throw new IllegalArgumentException(lane + " requires positive " + field);
        }
    }

    static long number(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long)) {
            throw new IllegalArgumentException(field + " must be integer");
        }
        return ((Number) value).longValue();
    }

    static String proofKey(String lane) {
        if (lane.startsWith("kernel.optional_")) return "optional-bitmap-scan";
        if (lane.equals("kernel.packed_scan")) return "packed-vs-primitive-equality";
        if (lane.equals("generated.pipeline_fusion")) return "generated-fused-terminal";
        if (lane.equals("kernel.keyspace_full_domain_load_collision_rehash")) return "keyspace-full-domain-load-rehash";
        if (lane.equals("kernel.key_lookup_normal")) return "generated-normal-full-key-lookup";
        if (lane.equals("kernel.key_lookup_collision")) return "generated-collision-full-equality";
        if (lane.startsWith("kernel.batch_import")) return "batch-capacity-path";
        if (lane.equals("generated.exact_index_incremental_lookup")) return "generated-exact-index-incremental-lookup";
        if (lane.equals("generated.keyed_frontier")) return "generated-frontier-add-update-first-remove";
        if (lane.equals("generated.dense_scratch_replace_sort")) return "generated-replace-sort-findfirst-firstorthrow";
        if (lane.equals("kernel.column_view")) return "column-view-lifecycle";
        if (lane.equals("child_locality.parent_scan_vs_flat")) return "parent-local-versus-flat";
        if (lane.equals("generated.materialization_recursive_success")) return "generated-recursive-map-object-list";
        if (lane.equals("materialization.budget_boundary")) return "generated-budget-allocation-no-partial";
        if (lane.equals("kernel.compaction_capacity_reuse")) return "compaction-capacity-scratch-reuse";
        if (lane.equals("kernel.exact_index_mutation_lookup_storm")) return "exact-index-mutation-lookup-no-rebuild";
        if (lane.equals("kernel.stats_mode_overhead")) return "summary-vs-diagnostic-operations";
        throw new IllegalArgumentException("no proof contract for " + lane);
    }

    static String phaseKey(String lane) {
        if (lane.startsWith("kernel.optional_")) return "optional-column-scan";
        if (lane.equals("kernel.packed_scan")) return "packed-scan-and-primitive-baseline";
        if (lane.equals("generated.pipeline_fusion")) return "generated-fused-filter-limit-update-terminal";
        if (lane.equals("kernel.keyspace_full_domain_load_collision_rehash")) return "key-identity-lookup-mutation";
        if (lane.equals("kernel.key_lookup_normal")) return "generated-normal-key-lookup";
        if (lane.equals("kernel.key_lookup_collision")) return "generated-full-equality-collision-lookup";
        if (lane.equals("kernel.batch_import.reserve")) return "batch-import-with-reserve";
        if (lane.equals("kernel.batch_import.growth")) return "batch-import-growth";
        if (lane.equals("generated.exact_index_incremental_lookup")) return "generated-exact-index-incremental-lookup";
        if (lane.equals("generated.keyed_frontier")) return "generated-keyed-frontier-lifecycle";
        if (lane.equals("generated.dense_scratch_replace_sort")) return "generated-dense-replace-and-sort-terminals";
        if (lane.equals("kernel.column_view")) return "column-view-lifecycle";
        if (lane.equals("child_locality.parent_scan_vs_flat")) return "parent-owned-child-versus-flat";
        if (lane.equals("generated.materialization_recursive_success")) return "generated-recursive-map-object-list-materialization";
        if (lane.equals("materialization.budget_boundary")) return "generated-budget-allocation-no-partial-recovery";
        if (lane.equals("kernel.compaction_capacity_reuse")) return "packed-remove-compaction-reuse";
        if (lane.equals("kernel.exact_index_mutation_lookup_storm")) return "exact-index-mutation-lookup-no-rebuild";
        if (lane.equals("kernel.stats_mode_overhead")) return "stats-mode-integrated-operation-overhead";
        throw new IllegalArgumentException("no phase contract for " + lane);
    }

    static boolean isGeneratedLane(String lane) {
        return lane.startsWith("generated.")
                || lane.equals("kernel.key_lookup_normal")
                || lane.equals("kernel.key_lookup_collision")
                || lane.equals("materialization.budget_boundary");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
