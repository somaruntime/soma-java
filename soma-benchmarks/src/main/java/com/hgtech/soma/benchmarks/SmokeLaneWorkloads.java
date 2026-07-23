package com.hgtech.soma.benchmarks;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.GeneratedColumnAccess;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.RemoveResult;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.StatsMode;
import com.hgtech.soma.runtime.TablePlan;
import com.hgtech.soma.runtime.TableStats;
import com.hgtech.soma.runtime.UpdateResult;
import com.hgtech.soma.runtime.generated.ChildOwnershipRegistry;
import com.hgtech.soma.runtime.generated.ColumnGroup;
import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.GroupedExactIndex;
import com.hgtech.soma.runtime.generated.HashCompositeKeySpace;
import com.hgtech.soma.runtime.generated.HashIntKeySpace;
import com.hgtech.soma.runtime.generated.IntColumn;
import com.hgtech.soma.runtime.generated.MaterializationAllocation;
import com.hgtech.soma.runtime.generated.MaterializationTracker;
import com.hgtech.soma.runtime.generated.OwnedChildTable;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.RuntimeCompatibility;

import com.hgtech.soma.examples.fjsp.schema.JobId;
import com.hgtech.soma.examples.fjsp.schema.MachineCandidate;
import com.hgtech.soma.examples.fjsp.schema.MachineId;
import com.hgtech.soma.examples.fjsp.schema.OperationDefinition;
import com.hgtech.soma.examples.fjsp.schema.OperationId;
import com.hgtech.soma.examples.fjsp.schema.OperationKey;
import com.hgtech.soma.examples.fjsp.schema.OperationMachineKey;
import com.hgtech.soma.examples.fjsp.schema.SetupFamilyId;
import com.hgtech.soma.examples.fjsp.schema.generated.CandidateMachineDefinitionBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateScan;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateTable;
import com.hgtech.soma.examples.fjsp.schema.generated.OperationDefinitionBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.OperationDefinitionTable;
import com.hgtech.soma.examples.simulation.SimEntityKind;
import com.hgtech.soma.examples.simulation.SimVariableKind;
import com.hgtech.soma.examples.simulation.generated.StateVectorRowBatch;
import com.hgtech.soma.examples.simulation.generated.StateVectorRowTable;
import com.hgtech.soma.examples.vrp.CustomerId;
import com.hgtech.soma.examples.vrp.InsertionCandidateRow;
import com.hgtech.soma.examples.vrp.LocationId;
import com.hgtech.soma.examples.vrp.LocationPairKey;
import com.hgtech.soma.examples.vrp.RouteId;
import com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowBatch;
import com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowScan;
import com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowTable;
import com.hgtech.soma.examples.vrp.generated.TravelCostBatch;
import com.hgtech.soma.examples.vrp.generated.TravelCostTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Concrete typed smoke workloads and benchmark-only fixtures. */
final class SmokeLaneWorkloads {
    private static volatile long blackhole;

    private SmokeLaneWorkloads() {
    }

    static LaneObservation execute(BenchmarkConfig config, String lane, int iteration) {
        if (lane.startsWith("kernel.optional_")) return optional(config, lane);
        if (lane.equals("kernel.packed_scan")) return packedScan(config, lane);
        if (lane.equals("generated.pipeline_fusion")) return generatedPipelineFusion(config, lane);
        if (lane.equals("kernel.keyspace_full_domain_load_collision_rehash")) return keySpace(config, lane);
        if (lane.equals("kernel.key_lookup_normal")
                || lane.equals("kernel.key_lookup_collision")) return generatedKeyLookup(config, lane);
        if (lane.startsWith("kernel.batch_import")) return batchImport(config, lane);
        if (lane.equals("generated.exact_index_incremental_lookup")) return generatedExactIndexLookup(config, lane);
        if (lane.equals("generated.keyed_frontier")) return generatedKeyedFrontier(config, lane);
        if (lane.equals("generated.dense_scratch_replace_sort")) return generatedDenseWorkspace(config, lane);
        if (lane.equals("kernel.column_view")) return columnView(config, lane);
        if (lane.equals("child_locality.parent_scan_vs_flat")) return childLocality(config, lane);
        if (lane.equals("generated.materialization_recursive_success")) return generatedMaterialization(config, lane);
        if (lane.equals("materialization.budget_boundary")) return generatedBudgetBoundary(config, lane);
        if (lane.equals("kernel.compaction_capacity_reuse")) return compaction(config, lane);
        if (lane.equals("kernel.exact_index_mutation_lookup_storm")) return exactIndexStorm(config, lane);
        if (lane.equals("kernel.stats_mode_overhead")) return statsOverhead(config, lane);
        throw new IllegalArgumentException("unimplemented required benchmark lane: " + lane);
    }

    private static LaneObservation optional(BenchmarkConfig config, String lane) {
        String density = lane.endsWith("all_present") ? "all-present"
                : lane.endsWith("all_absent") ? "all-absent" : "mixed-0-63-64";
        long setupStart = System.nanoTime();
        KernelTable table = new KernelTable(config.rows, density, StatsMode.SUMMARY, true);
        long setup = elapsed(setupStart);
        final long[] sum = {0L};
        long measureStart = System.nanoTime();
        GeneratedColumnAccess.intTraversal(table.state, table.values, table.presence,
                "BenchmarkRows", "value.values", "value.values.consumer")
                .forEachInt(new IntConsumer() {
            @Override public void accept(int value) { sum[0] += value; }
        });
        long measured = elapsed(measureStart);
        blackhole ^= sum[0];
        TableStats stats = table.state.statsSnapshot();
        LaneObservation result = base(lane, setup, measured, config.rows);
        result.optionalDensity = density;
        result.scanned = stats.lastScanned();
        result.matched = stats.lastMatched();
        result.selected = stats.lastMatched();
        result.touchedBytes = 4L * stats.lastMatched()
                + 8L * ((config.rows + 63L) >>> 6);
        result.workingSetBytes = 4L * table.state.capacity()
                + 8L * ((table.state.capacity() + 63L) >>> 6);
        result.selectorStats = BenchmarkModel.object("bitmapWords",
                Long.valueOf((config.rows + 63L) >>> 6), "present", Long.valueOf(stats.lastMatched()));
        result.limitations = BenchmarkModel.limitations(
                "smoke evidence only; optional density timing is not a performance claim",
                "payload and bitmap bytes are deterministic estimates; hardware counters unavailable");
        return SmokeLaneEvidence.finish(result, "optional-column-scan");
    }

    private static LaneObservation packedScan(BenchmarkConfig config, String lane) {
        long setupStart = System.nanoTime();
        KernelTable table = new KernelTable(config.rows, "required", StatsMode.SUMMARY, true);
        int[] baseline = new int[config.rows];
        for (int row = 0; row < baseline.length; row++) baseline[row] = row + 1;
        long setup = elapsed(setupStart);
        final long[] soma = {0L};
        long somaStart = System.nanoTime();
        GeneratedColumnAccess.intTraversal(table.state, table.values, null,
                "BenchmarkRows", "value.values", "value.values.consumer")
                .forEachInt(new IntConsumer() {
            @Override public void accept(int value) { soma[0] += value; }
        });
        long somaNanos = elapsed(somaStart);
        long primitive = 0L;
        long baselineStart = System.nanoTime();
        for (int value : baseline) primitive += value;
        long baselineNanos = elapsed(baselineStart);
        require(soma[0] == primitive, "packed scan baseline mismatch");
        blackhole ^= primitive;
        LaneObservation result = base(lane, setup, somaNanos + baselineNanos,
                2L * config.rows);
        result.baselineId = "handwritten-int-array-same-values-v1";
        result.scanned = config.rows * 2L;
        result.matched = config.rows * 2L;
        result.operations = 2L;
        result.touchedBytes = 8L * config.rows;
        result.workingSetBytes = 8L * config.rows;
        result.selectorStats = BenchmarkModel.object("somaNanos", Long.valueOf(somaNanos),
                "primitiveBaselineNanos", Long.valueOf(baselineNanos));
        result.limitations = BenchmarkModel.limitations(
                "smoke executes same-value SOMA and primitive-array paths but does not support a speed claim",
                "no JMH forks, GC profiler, cache or branch counters");
        return SmokeLaneEvidence.finish(result, "packed-scan-and-primitive-baseline");
    }

    private static LaneObservation generatedKeyLookup(BenchmarkConfig config, String lane) {
        boolean collision = lane.endsWith("collision");
        long fromA = 1L, toA = 2L, fromB = collision ? 3L : 4L;
        long hashA = (0xcbf29ce484222325L ^ fromA) * 0x100000001b3L;
        long hashB = (0xcbf29ce484222325L ^ fromB) * 0x100000001b3L;
        long toB = collision ? hashB ^ hashA ^ toA : 5L;
        LocationPairKey first = new LocationPairKey(new LocationId(fromA), new LocationId(toA));
        LocationPairKey second = new LocationPairKey(new LocationId(fromB), new LocationId(toB));
        long setupStart = System.nanoTime();
        TravelCostTable table = TravelCostTable.create();
        table.addBatch(new TravelCostBatch(2)
                .addValues(first, 101L, 11L)
                .addValues(second, 202L, 22L));
        table.resetStats();
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        long firstDistance = table.fetch(first).distanceMeters;
        TableStats firstStats = table.statsSnapshot();
        long secondDistance = table.fetch(second).distanceMeters;
        TableStats secondStats = table.statsSnapshot();
        boolean missing = !table.find(new LocationPairKey(new LocationId(9L), new LocationId(9L)))
                .isPresent();
        TableStats missingStats = table.statsSnapshot();
        long measured = elapsed(measureStart);
        TableStats stats = missingStats;
        require(firstDistance == 101L && secondDistance == 202L && missing,
                "generated key lookup must use full equality");
        if (collision) require(stats.keySpaceCollisionCount() > 0L,
                "constructed generated-key hash collision was not observed");
        table.release();
        LaneObservation result = base(lane, setup, measured, 2L);
        result.operations = 3L;
        result.lookups = 3L;
        result.missing = 1L;
        result.scanned = 2L;
        result.matched = 2L;
        result.materializationInvocations = stats.materializationInvocationCount();
        result.materialized = 2L;
        result.estimatedAllocationBytes = firstStats.lastMaterializationEstimatedAllocationBytes()
                + secondStats.lastMaterializationEstimatedAllocationBytes()
                + missingStats.lastMaterializationEstimatedAllocationBytes();
        result.materializationEstimatedAllocationBytes = result.estimatedAllocationBytes;
        result.touchedBytes = 32L * result.lookups;
        result.workingSetBytes = 32L * stats.capacity() + 13L * stats.keySpaceCapacity();
        result.keySpaceStats = BenchmarkModel.object("implementation", "generated-hash-composite-v2",
                "fullEqualityDistinguished", Boolean.TRUE,
                "collisionConstructed", Boolean.valueOf(collision),
                "collisionCount", Long.valueOf(stats.keySpaceCollisionCount()),
                "probeCount", Long.valueOf(stats.keySpaceProbeCount()),
                "rehashCount", Long.valueOf(stats.keySpaceRehashCount()));
        result.limitations = BenchmarkModel.limitations(
                "smoke constructs two distinct generated composite keys with equal FNV hash for collision coverage",
                "claimAllowed=false; timing is not a lookup performance claim");
        return SmokeLaneEvidence.finish(result, collision
                ? "generated-full-equality-collision-lookup" : "generated-normal-key-lookup");
    }

    private static LaneObservation generatedKeyedFrontier(BenchmarkConfig config, String lane) {
        int count = Math.max(8, config.rows);
        JobId job = new JobId(7L);
        MachineId machineA = new MachineId(11L);
        MachineId machineB = new MachineId(12L);
        SetupFamilyId setup = new SetupFamilyId(3L);
        OperationKey[] operations = new OperationKey[count];
        long setupStart = System.nanoTime();
        MachineCandidateTable table = MachineCandidateTable.create();
        MachineCandidateBatch batch = new MachineCandidateBatch(count);
        for (int index = 0; index < count; index++) {
            OperationKey operation = new OperationKey(job, new OperationId(index + 1L));
            operations[index] = operation;
            MachineId machine = (index & 1) == 0 ? machineA : machineB;
            batch.addValues(new OperationMachineKey(operation, machine), setup,
                    index, 0L, 0L, index, index + 1L, 0L,
                    index, index, index + 1L, false);
        }
        long setupNanos = elapsed(setupStart);
        TableStats beforeMeasurement = table.statsSnapshot();
        long measureStart = System.nanoTime();
        table.addBatch(batch);
        UpdateResult updated = table.scanByMachine(machineA).update(row -> {
            row.setIndicatorReady(true);
            row.setEffectiveReadyMinute(row.baseReadyMinute() + 1L);
        });
        MachineCandidate chosen = table.scanByMachine(machineA)
                .filter(row -> row.indicatorReady())
                .sorted(new MachineCandidateScan.Comparator() {
                    @Override public int compare(com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateCursor left,
                                                 com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateCursor right) {
                        return Long.compare(left.effectiveReadyMinute(), right.effectiveReadyMinute());
                    }
                }).firstOrThrow();
        TableStats firstStats = table.statsSnapshot();
        RemoveResult removedResult = table.scanByOperation(chosen.candidateKey.operationKey).remove();
        long removed = removedResult.removed();
        long measured = elapsed(measureStart);
        require(updated.changed() > 0L && removed == 1L && table.size() == count - 1,
                "generated keyed frontier add/update/first/remove");
        TableStats stats = table.statsSnapshot();
        table.release();
        LaneObservation result = base(lane, setupNanos, measured, count);
        result.operations = 4L;
        result.lookups = 3L;
        result.scanned = count + updated.scanned() + firstStats.lastScanned()
                + removedResult.scanned();
        result.matched = updated.matched() + firstStats.lastMatched()
                + removedResult.matched();
        result.changed = count + updated.changed();
        result.removed = removed;
        result.materializationInvocations = 1L;
        result.materialized = 1L;
        long appendValidationKeySpaceAllocationBytes =
                RuntimeCompatibility.estimatedHashKeySpaceBytes(
                        RuntimeCompatibility.HASH_COMPOSITE_KEY_SPACE, count);
        long appendValidationKeySpaceCapacity =
                appendValidationKeySpaceAllocationBytes / 13L;
        long mainKeySpaceGrowthAllocationBytes =
                stats.keySpaceCapacity() > beforeMeasurement.keySpaceCapacity()
                        ? Math.multiplyExact(13L, stats.keySpaceCapacity()) : 0L;
        long keySpaceAllocationBytes = Math.addExact(
                appendValidationKeySpaceAllocationBytes,
                mainKeySpaceGrowthAllocationBytes);
        long tableGrowthAllocationBytes =
                stats.capacity() > beforeMeasurement.capacity()
                        ? Math.multiplyExact(105L, stats.capacity()) : 0L;
        long scratchAllocationBytes = positiveDelta(
                stats.operationScratchCurrentBytes(),
                beforeMeasurement.operationScratchCurrentBytes())
                + positiveDelta(stats.updateScratchCurrentBytes(),
                beforeMeasurement.updateScratchCurrentBytes());
        long exactIndexAllocationBytes = positiveDelta(
                stats.exactIndexStorageCurrentBytes(),
                beforeMeasurement.exactIndexStorageCurrentBytes());
        result.estimatedAllocationBytes = firstStats.lastMaterializationEstimatedAllocationBytes()
                + tableGrowthAllocationBytes + keySpaceAllocationBytes
                + scratchAllocationBytes + exactIndexAllocationBytes;
        result.materializationEstimatedAllocationBytes =
                firstStats.lastMaterializationEstimatedAllocationBytes();
        result.touchedBytes = 105L * (count + updated.scanned()
                + firstStats.lastScanned() + removedResult.scanned() + 1L);
        result.workingSetBytes = 105L * stats.capacity()
                + 13L * stats.keySpaceCapacity()
                + stats.operationScratchCurrentBytes()
                + stats.updateScratchCurrentBytes()
                + stats.exactIndexStorageCurrentBytes();
        result.keySpaceStats = BenchmarkModel.object("implementation", "generated-machine-candidate-frontier",
                "added", Integer.valueOf(count), "updated", Long.valueOf(updated.changed()),
                "dynamicFirst", 1L, "removed", Long.valueOf(removed),
                "tableCapacityBeforeMeasurement", Integer.valueOf(beforeMeasurement.capacity()),
                "tableCapacity", Integer.valueOf(stats.capacity()),
                "keySpaceCapacityBeforeMeasurement",
                Integer.valueOf(beforeMeasurement.keySpaceCapacity()),
                "keySpaceCapacity", Integer.valueOf(stats.keySpaceCapacity()),
                "appendValidationKeySpaceCapacity",
                Long.valueOf(appendValidationKeySpaceCapacity),
                "appendValidationKeySpaceAllocationBytes",
                Long.valueOf(appendValidationKeySpaceAllocationBytes),
                "mainKeySpaceGrowthAllocationBytes",
                Long.valueOf(mainKeySpaceGrowthAllocationBytes),
                "keySpaceAllocationBytes", Long.valueOf(keySpaceAllocationBytes),
                "tableGrowthAllocationBytes", Long.valueOf(tableGrowthAllocationBytes),
                "scratchAllocationBytes", Long.valueOf(scratchAllocationBytes),
                "retainedScratchBytes", Long.valueOf(scratchAllocationBytes));
        result.exactIndexStats = BenchmarkModel.object(
                "implementation", "generated-grouped-exact-index",
                "indexCount", Integer.valueOf(stats.exactIndexCount()),
                "entryCount", Long.valueOf(stats.exactIndexEntryCount()),
                "groupCount", Long.valueOf(stats.exactIndexGroupCount()),
                "probeCount", Long.valueOf(stats.exactIndexProbeCount()),
                "collisionCount", Long.valueOf(stats.exactIndexCollisionCount()),
                "rehashCount", Long.valueOf(stats.exactIndexRehashCount()),
                "currentBytes", Long.valueOf(stats.exactIndexStorageCurrentBytes()),
                "highWaterBytes", Long.valueOf(stats.exactIndexStorageHighWaterBytes()),
                "allocationBytes", Long.valueOf(exactIndexAllocationBytes));
        result.limitations = BenchmarkModel.limitations(
                "integrated generated table workload covers add/update/dynamic firstOrThrow/remove in one frontier lifecycle",
                "candidate scoring is deterministic benchmark data; no solver-quality or throughput claim");
        return SmokeLaneEvidence.finish(result, "generated-keyed-frontier-lifecycle");
    }

    private static LaneObservation generatedExactIndexLookup(
            BenchmarkConfig config, String lane) {
        int count = Math.max(8, config.rows);
        JobId job = new JobId(17L);
        MachineId machineA = new MachineId(31L);
        MachineId machineB = new MachineId(32L);
        SetupFamilyId setup = new SetupFamilyId(7L);
        long setupStart = System.nanoTime();
        MachineCandidateTable table = MachineCandidateTable.create();
        MachineCandidateBatch batch = new MachineCandidateBatch(count);
        for (int index = 0; index < count; index++) {
            OperationKey operation = new OperationKey(job, new OperationId(index + 1L));
            MachineId machine = (index & 1) == 0 ? machineA : machineB;
            batch.addValues(new OperationMachineKey(operation, machine), setup,
                    index, 0L, 0L, index, index + 1L, 0L,
                    index, index, index + 1L, false);
        }
        TableStats beforeMeasurement = table.statsSnapshot();
        long setupNanos = elapsed(setupStart);
        long measureStart = System.nanoTime();
        table.addBatch(batch);
        long exactMatches = table.scanByMachine(machineA).count();
        TableStats lookupStats = table.statsSnapshot();
        long measured = elapsed(measureStart);
        long expectedMatches = (count + 1L) / 2L;
        require(exactMatches == expectedMatches,
                "incrementally maintained exact index must return its complete group");
        TableStats stats = table.statsSnapshot();
        table.release();

        long appendValidationKeySpaceAllocationBytes =
                RuntimeCompatibility.estimatedHashKeySpaceBytes(
                        RuntimeCompatibility.HASH_COMPOSITE_KEY_SPACE, count);
        long mainKeySpaceGrowthAllocationBytes =
                stats.keySpaceCapacity() > beforeMeasurement.keySpaceCapacity()
                        ? Math.multiplyExact(13L, stats.keySpaceCapacity()) : 0L;
        long keySpaceAllocationBytes = Math.addExact(
                appendValidationKeySpaceAllocationBytes,
                mainKeySpaceGrowthAllocationBytes);
        long tableGrowthAllocationBytes =
                stats.capacity() > beforeMeasurement.capacity()
                        ? Math.multiplyExact(105L, stats.capacity()) : 0L;
        long scratchAllocationBytes = positiveDelta(
                stats.operationScratchCurrentBytes(),
                beforeMeasurement.operationScratchCurrentBytes())
                + positiveDelta(stats.updateScratchCurrentBytes(),
                beforeMeasurement.updateScratchCurrentBytes());
        long exactIndexAllocationBytes = positiveDelta(
                stats.exactIndexStorageCurrentBytes(),
                beforeMeasurement.exactIndexStorageCurrentBytes());
        long lookupRows = lookupStats.lastScanned();
        long appendTouchedBytes = Math.multiplyExact(105L, count);
        long lookupTouchedBytes = Math.multiplyExact(8L, lookupRows);

        LaneObservation result = base(lane, setupNanos, measured, count);
        result.operations = 2L;
        result.lookups = 1L;
        result.scanned = Math.addExact(count, lookupRows);
        result.matched = exactMatches;
        result.changed = count;
        result.touchedBytes = Math.addExact(appendTouchedBytes, lookupTouchedBytes);
        result.workingSetBytes = 105L * stats.capacity()
                + 13L * stats.keySpaceCapacity()
                + stats.operationScratchCurrentBytes()
                + stats.updateScratchCurrentBytes()
                + stats.exactIndexStorageCurrentBytes();
        result.estimatedAllocationBytes = tableGrowthAllocationBytes
                + keySpaceAllocationBytes + scratchAllocationBytes
                + exactIndexAllocationBytes;
        result.keySpaceStats = BenchmarkModel.object(
                "implementation", "generated-hash-composite-v2",
                "appendValidationAllocationBytes",
                Long.valueOf(appendValidationKeySpaceAllocationBytes),
                "mainGrowthAllocationBytes",
                Long.valueOf(mainKeySpaceGrowthAllocationBytes),
                "allocationBytes", Long.valueOf(keySpaceAllocationBytes),
                "capacityBeforeMeasurement",
                Integer.valueOf(beforeMeasurement.keySpaceCapacity()),
                "capacity", Integer.valueOf(stats.keySpaceCapacity()));
        result.exactIndexStats = BenchmarkModel.object(
                "implementation", "generated-grouped-exact-index",
                "addedRows", Integer.valueOf(count),
                "exactLookupCount", Long.valueOf(exactMatches),
                "exactLookupRows", Long.valueOf(lookupRows),
                "lookupKeyWidthBytes", 8L,
                "appendTouchedBytes", Long.valueOf(appendTouchedBytes),
                "lookupTouchedBytes", Long.valueOf(lookupTouchedBytes),
                "indexCount", Integer.valueOf(stats.exactIndexCount()),
                "entryCount", Long.valueOf(stats.exactIndexEntryCount()),
                "groupCount", Long.valueOf(stats.exactIndexGroupCount()),
                "probeCount", Long.valueOf(stats.exactIndexProbeCount()),
                "collisionCount", Long.valueOf(stats.exactIndexCollisionCount()),
                "rehashCount", Long.valueOf(stats.exactIndexRehashCount()),
                "currentBytes", Long.valueOf(stats.exactIndexStorageCurrentBytes()),
                "highWaterBytes", Long.valueOf(stats.exactIndexStorageHighWaterBytes()),
                "tableCapacity", Integer.valueOf(stats.capacity()),
                "tableGrowthAllocationBytes", Long.valueOf(tableGrowthAllocationBytes),
                "allocationBytes", Long.valueOf(exactIndexAllocationBytes),
                "scratchAllocationBytes", Long.valueOf(scratchAllocationBytes),
                "retainedScratchBytes", Long.valueOf(
                stats.operationScratchCurrentBytes() + stats.updateScratchCurrentBytes()));
        result.limitations = BenchmarkModel.limitations(
                "generated keyed table appends rows and immediately consumes one grouped exact index",
                "smoke validates incremental maintenance counters; claimAllowed=false");
        return SmokeLaneEvidence.finish(result, "generated-exact-index-incremental-lookup");
    }

    private static LaneObservation generatedDenseWorkspace(
            BenchmarkConfig config, String lane) {
        int count = Math.max(8, config.rows);
        RouteId route = new RouteId(1L);
        long setupStart = System.nanoTime();
        InsertionCandidateRowTable table = InsertionCandidateRowTable.create();
        InsertionCandidateRowBatch first = insertionBatch(count, 1000L);
        table.replaceAll(first);
        InsertionCandidateRowBatch replacement = insertionBatch(count, 2000L);
        table.resetStats();
        TableStats beforeMeasurement = table.statsSnapshot();
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        table.replaceAll(replacement);
        InsertionCandidateRow dynamic = table.sorted(new InsertionCandidateRowScan.Comparator() {
            @Override public int compare(com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowCursor left,
                                         com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowCursor right) {
                return Long.compare(left.deltaDistanceMeters(), right.deltaDistanceMeters());
            }
        }).findFirst().get();
        TableStats dynamicStats = table.statsSnapshot();
        require(table.sorted((left, right) -> Long.compare(
                left.deltaDistanceMeters(), right.deltaDistanceMeters()))
                .firstOrThrow().customerId.equals(dynamic.customerId),
                "dynamic firstOrThrow must agree with findFirst");
        TableStats secondDynamicStats = table.statsSnapshot();
        long measured = elapsed(measureStart);
        require(dynamic.routeId.equals(route),
                "dynamic sort must select from the replacement workspace");
        TableStats stats = table.statsSnapshot();
        table.release();
        LaneObservation result = base(lane, setup, measured, count);
        result.operations = 3L;
        result.scanned = count + dynamicStats.lastScanned()
                + secondDynamicStats.lastScanned();
        result.matched = dynamicStats.lastMatched()
                + secondDynamicStats.lastMatched();
        result.changed = count;
        result.materializationInvocations = 2L;
        result.materialized = result.materializationInvocations;
        long scratchAllocationBytes = positiveDelta(
                stats.operationScratchCurrentBytes(),
                beforeMeasurement.operationScratchCurrentBytes())
                + positiveDelta(stats.updateScratchCurrentBytes(),
                beforeMeasurement.updateScratchCurrentBytes());
        result.estimatedAllocationBytes = dynamicStats.lastMaterializationEstimatedAllocationBytes()
                + secondDynamicStats.lastMaterializationEstimatedAllocationBytes()
                + scratchAllocationBytes;
        result.materializationEstimatedAllocationBytes =
                dynamicStats.lastMaterializationEstimatedAllocationBytes()
                + secondDynamicStats.lastMaterializationEstimatedAllocationBytes();
        long replaceTouchedBytes = Math.multiplyExact(56L, count);
        long dynamicComparatorRows = dynamicStats.lastScanned()
                + secondDynamicStats.lastScanned();
        long dynamicComparatorTouchedBytes = Math.multiplyExact(8L, dynamicComparatorRows);
        long materializationTouchedBytes = Math.multiplyExact(56L, result.materialized);
        result.touchedBytes = replaceTouchedBytes
                + dynamicComparatorTouchedBytes + materializationTouchedBytes;
        result.workingSetBytes = 56L * stats.capacity()
                + stats.operationScratchCurrentBytes()
                + stats.updateScratchCurrentBytes();
        result.selectorStats = BenchmarkModel.object(
                "implementation", "generated-candidate-scan",
                "replaceRows", Integer.valueOf(count),
                "dynamicFindFirst", 1L,
                "dynamicFirstOrThrow", 1L,
                "replaceTouchedBytes", Long.valueOf(replaceTouchedBytes),
                "dynamicComparatorRows", Long.valueOf(dynamicComparatorRows),
                "dynamicComparatorWidthBytes", 8L,
                "dynamicComparatorTouchedBytes", Long.valueOf(dynamicComparatorTouchedBytes),
                "materializedRowWidthBytes", 56L,
                "materializationTouchedBytes", Long.valueOf(materializationTouchedBytes),
                "tableCapacity", Integer.valueOf(stats.capacity()),
                "scratchAllocationBytes", Long.valueOf(scratchAllocationBytes),
                "retainedScratchBytes", Long.valueOf(
                stats.operationScratchCurrentBytes() + stats.updateScratchCurrentBytes()));
        result.limitations = BenchmarkModel.limitations(
                "generated dense workspace performs replaceAll and two explicit sort terminals without a maintained index",
                "candidate construction is outside measured publication/terminal phase");
        return SmokeLaneEvidence.finish(result, "generated-dense-replace-and-sort-terminals");
    }

    private static InsertionCandidateRowBatch insertionBatch(int count, long base) {
        InsertionCandidateRowBatch batch = new InsertionCandidateRowBatch(count);
        for (int index = 0; index < count; index++) {
            batch.addValues(new RouteId(1L), new CustomerId(index + 1L), index,
                    1L, base + count - index, base + index,
                    index + 1, base + count + index);
        }
        return batch;
    }

    private static LaneObservation generatedPipelineFusion(BenchmarkConfig config, String lane) {
        int count = Math.max(8, config.rows);
        long setupStart = System.nanoTime();
        StateVectorRowTable table = StateVectorRowTable.create();
        StateVectorRowBatch batch = new StateVectorRowBatch(count);
        for (int index = 0; index < count; index++) {
            batch.addValues(index, SimEntityKind.TANK, index,
                    SimVariableKind.LEVEL_LITERS, index, 1.0d, 1.0d);
        }
        table.addBatch(batch);
        long setup = elapsed(setupStart);
        int limit = Math.max(1, count / 4);
        long measureStart = System.nanoTime();
        UpdateResult updated = table.filter(row -> (row.vectorIndex() & 1) == 0)
                .limit(limit).update(row -> row.setValue(row.value() + row.derivative()));
        long measured = elapsed(measureStart);
        require(updated.matched() == limit && updated.changed() == limit,
                "generated fused filter/limit/update terminal");
        TableStats stats = table.statsSnapshot();
        table.release();
        LaneObservation result = base(lane, setup, measured, count);
        result.operations = 1L;
        result.scanned = updated.scanned();
        result.matched = updated.matched();
        result.changed = updated.changed();
        result.estimatedAllocationBytes = stats.operationScratchCurrentBytes()
                + stats.updateScratchCurrentBytes();
        result.touchedBytes = 4L * updated.scanned() + 88L * updated.matched();
        result.workingSetBytes = 44L * stats.capacity()
                + stats.operationScratchCurrentBytes()
                + stats.updateScratchCurrentBytes();
        result.selectorStats = BenchmarkModel.object("implementation", "generated-candidate-scan",
                "filterStages", 1L, "limitStages", 1L, "updateTerminal", 1L,
                "matched", Long.valueOf(updated.matched()),
                "changed", Long.valueOf(updated.changed()),
                "capacity", Integer.valueOf(stats.capacity()),
                "operationScratchHighWaterBytes", Long.valueOf(stats.operationScratchHighWaterBytes()),
                "updateScratchCurrentBytes", Long.valueOf(stats.updateScratchCurrentBytes()),
                "perRowObjects", 0L);
        result.limitations = BenchmarkModel.limitations(
                "generated Candidate Scan executes filter+limit+update as one terminal with reusable cursor/scratch",
                "zero per-row objects is structural generated-code evidence, not a JVM allocation-profiler result");
        return SmokeLaneEvidence.finish(result, "generated-fused-filter-limit-update-terminal");
    }

    private static LaneObservation keySpace(BenchmarkConfig config, String lane) {
        long setupStart = System.nanoTime();
        int count = Math.max(65, config.rows);
        HashIntKeySpace hash = new HashIntKeySpace(1);
        HashCompositeKeySpace composite = new HashCompositeKeySpace(1);
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        for (int row = 0; row < count; row++) {
            hash.put(row * 17 + 3, row);
            composite.ensureInsertCapacity();
            int slot = insertionSlot(composite, 7L);
            composite.putAt(slot, 7L, row);
        }
        hash.put(Integer.MIN_VALUE, count);
        hash.put(Integer.MAX_VALUE, count + 1);
        boolean minimumAccepted = hash.rowOf(Integer.MIN_VALUE) == count;
        boolean maximumAccepted = hash.rowOf(Integer.MAX_VALUE) == count + 1;
        long checksum = 0L;
        for (int row = 0; row < count; row++) {
            checksum += hash.rowOf(row * 17 + 3);
        }
        int missing = hash.rowOf(-999);
        for (int row = 0; row < count / 4; row++) hash.remove(row * 17 + 3);
        long measured = elapsed(measureStart);
        require(missing == -1 && checksum >= 0L && minimumAccepted && maximumAccepted,
                "hash keyspace accepts the complete int identity domain");
        blackhole ^= checksum;
        LaneObservation result = base(lane, setup, measured, count);
        result.operations = count * 3L + count / 4L + 5L;
        result.lookups = count + 3L;
        result.missing = 1L;
        result.changed = count * 2L + count / 4L + 2L;
        result.scanned = count;
        result.matched = count;
        result.touchedBytes = 22L * count;
        result.workingSetBytes = hash.retainedBytes() + composite.retainedBytes();
        result.estimatedAllocationBytes = hashGrowthBytes(9L, hash.capacity())
                + hashGrowthBytes(13L, composite.capacity());
        result.keySpaceStats = BenchmarkModel.object(
                "fullIntDomainMinimumAccepted", Boolean.valueOf(minimumAccepted),
                "fullIntDomainMaximumAccepted", Boolean.valueOf(maximumAccepted),
                "hashCapacity", Integer.valueOf(hash.capacity()),
                "hashUsed", Integer.valueOf(hash.used()),
                "hashLoadFactor", Double.valueOf((double) hash.size() / hash.capacity()),
                "hashProbeCount", Long.valueOf(hash.probeCount()),
                "hashCollisionCount", Long.valueOf(hash.collisionCount()),
                "hashRehashCount", Long.valueOf(hash.rehashCount()),
                "compositeCapacity", Integer.valueOf(composite.capacity()),
                "compositeProbeCount", Long.valueOf(composite.probeCount()),
                "compositeCollisionCount", Long.valueOf(composite.collisionCount()),
                "compositeRehashCount", Long.valueOf(composite.rehashCount()),
                "normalMissing", 1L, "duplicateAttempts", 0L);
        result.limitations = BenchmarkModel.limitations(
                "smoke validates full-int-domain Hash and collision/rehash paths; no complexity or speed claim",
                "composite lane uses raw hash/row substrate; generated full-key equality is covered by G2/G4 consumers");
        result.baselineId = "primitive-hash-full-int-domain-v2";
        return SmokeLaneEvidence.finish(result, "key-identity-lookup-mutation");
    }

    private static LaneObservation batchImport(BenchmarkConfig config, String lane) {
        boolean reserve = lane.endsWith("reserve");
        long setupStart = System.nanoTime();
        KernelTable table = new KernelTable(0, "required", StatsMode.SUMMARY, false);
        if (reserve) table.state.reserve(config.rows);
        long growthBefore = table.state.statsSnapshot().growthCount();
        int[] batch = new int[config.rows];
        for (int row = 0; row < batch.length; row++) batch[row] = row;
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        table.append(batch);
        long measured = elapsed(measureStart);
        require(table.state.size() == config.rows, "batch import size");
        TableStats stats = table.state.statsSnapshot();
        LaneObservation result = base(lane, setup, measured, config.rows);
        result.scanned = config.rows;
        result.matched = config.rows;
        result.changed = config.rows;
        result.estimatedAllocationBytes = reserve ? 0L
                : 4L * table.state.capacity()
                + 8L * ((table.state.capacity() + 63L) >>> 6);
        result.touchedBytes = 8L * batch.length;
        result.workingSetBytes = 4L * table.state.capacity()
                + 8L * ((table.state.capacity() + 63L) >>> 6)
                + 4L * batch.length;
        result.selectorStats = BenchmarkModel.object("reserved", Boolean.valueOf(reserve),
                "capacity", Integer.valueOf(table.state.capacity()),
                "growthCount", Long.valueOf(stats.growthCount() - growthBefore));
        result.limitations = BenchmarkModel.limitations(
                "smoke separates batch construction and table import but does not measure JVM allocation precisely",
                "capacity is a local smoke scale, not a production sizing recommendation");
        return SmokeLaneEvidence.finish(result, reserve ? "batch-import-with-reserve" : "batch-import-growth");
    }

    private static LaneObservation exactIndexStorm(BenchmarkConfig config, String lane) {
        int rows = Math.max(1, config.rows);
        int groups = rows < 2 ? 1 : Math.min(16, rows / 2);
        long setupStart = System.nanoTime();
        GroupedExactIndex index = new GroupedExactIndex(0);
        index.ensureCapacity(rows, groups);
        int[] groupIds = new int[groups];
        for (int group = 0; group < groups; group++) {
            groupIds[group] = index.createGroup(31L * group + 7L);
        }
        for (int row = 0; row < rows; row++) index.link(groupIds[row % groups], row);
        index.resetMetrics();
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        int mutationPasses = rows < 2 ? 0 : 4;
        long checksum = 0L;
        for (int pass = 0; pass < mutationPasses; pass++) {
            for (int row = 0; row < rows; row++) {
                int group = row % groups;
                index.unlink(row);
                index.link(groupIds[group], row);
            }
        }
        int lookupRows = 0;
        for (int group = 0; group < groups; group++) {
            int found = index.firstGroup(31L * group + 7L);
            require(found == groupIds[group], "exact-index group lookup identity");
            for (int row = index.firstRow(found); row >= 0; row = index.nextRow(row)) {
                checksum += row;
                lookupRows++;
            }
        }
        long measured = elapsed(measureStart);
        require(lookupRows == rows && index.entryCount() == rows
                        && index.groupCount() == groups,
                "incremental exact-index mutation/lookup cardinality");
        blackhole ^= checksum;
        LaneObservation result = base(lane, setup, measured, rows);
        result.scanned = rows * (mutationPasses + 1L);
        result.matched = result.scanned;
        result.operations = mutationPasses + groups;
        result.touchedBytes = 24L * rows * mutationPasses + 4L * rows;
        result.workingSetBytes = index.retainedBytes();
        result.estimatedAllocationBytes = 0L;
        result.explicitMutations = (long) mutationPasses * rows;
        result.explicitReads = lookupRows;
        result.exactIndexStats = BenchmarkModel.object(
                "implementation", "kernel-grouped-exact-index",
                "entryCount", Integer.valueOf(index.entryCount()),
                "groupCount", Integer.valueOf(index.groupCount()),
                "probeCount", Long.valueOf(index.probeCount()),
                "collisionCount", Long.valueOf(index.collisionCount()),
                "rehashCount", Long.valueOf(index.rehashCount()),
                "currentBytes", Long.valueOf(index.retainedBytes()),
                "highWaterBytes", Long.valueOf(index.storageHighWaterBytes()),
                "mutationCount", Long.valueOf(result.explicitMutations),
                "lookupCount", Integer.valueOf(groups),
                "lookupRows", Integer.valueOf(lookupRows),
                "measurementAllocationBytes", 0L);
        result.selectorStats = BenchmarkModel.object("cardinality", Integer.valueOf(rows),
                "selectivity", Double.valueOf(1.0d / groups),
                "exactLookupBufferBytes", 0L);
        result.statsMode = "diagnostic";
        result.limitations = BenchmarkModel.limitations(
                "smoke records pre-sized incremental unlink/link and exact traversal shape",
                "generated full-equality and collision handling are covered by external consumers");
        return SmokeLaneEvidence.finish(result, "exact-index-mutation-lookup-no-rebuild");
    }

    private static LaneObservation compaction(BenchmarkConfig config, String lane) {
        long setupStart = System.nanoTime();
        KernelTable table = new KernelTable(config.rows, "required", StatsMode.DIAGNOSTIC, true);
        int[] scratch = new int[config.rows];
        KernelTable single = new KernelTable(8, "required", StatsMode.SUMMARY, true);
        KernelTable reuse = new KernelTable(16, "required", StatsMode.SUMMARY, true);
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        single.state.beginOperation("benchmark.singleRemove");
        single.state.preflightStructuralOperation("benchmark.singleRemove");
        single.values.set(2, single.values.get(7));
        single.values.clearRange(7, 8);
        single.state.commitStructuralRemove(8, 7, "benchmark.singleRemove");
        single.state.endOperationSuccess("benchmark.singleRemove", 1L, 1L, 1L);
        int write = 0;
        for (int read = 0; read < config.rows; read++) {
            if ((read & 3) != 0) scratch[write++] = table.values.get(read);
        }
        table.state.beginOperation("benchmark.compact");
        table.state.preflightStructuralOperation("benchmark.compact");
        for (int row = 0; row < write; row++) table.values.set(row, scratch[row]);
        table.values.clearRange(write, config.rows);
        table.state.commitStructuralRemove(config.rows, write, "benchmark.compact");
        table.state.operationScratch(4L * scratch.length);
        table.state.endOperationSuccess("benchmark.compact", config.rows,
                config.rows - write, config.rows - write);
        int retained = reuse.state.capacity();
        int previous = reuse.state.prepareClear();
        reuse.values.clearRange(0, previous);
        reuse.presence.clearRange(0, previous);
        reuse.state.commitClear(previous);
        reuse.append(new int[] {7, 8, 9});
        long measured = elapsed(measureStart);
        require(table.state.size() == write && single.state.size() == 7
                        && reuse.state.size() == 3 && reuse.state.capacity() == retained,
                "single/batch/clear compaction semantics");
        TableStats stats = table.state.statsSnapshot();
        LaneObservation result = base(lane, setup, measured, config.rows);
        result.scanned = config.rows;
        result.matched = config.rows - write;
        result.removed = config.rows - write;
        result.changed = config.rows - write;
        result.operations = 3L;
        result.estimatedAllocationBytes = 12L;
        result.touchedBytes = 8L * config.rows;
        result.workingSetBytes = 4L * table.state.capacity() + 4L * scratch.length;
        result.selectorStats = BenchmarkModel.object("compactedRows", Long.valueOf(result.removed),
                "retainedCapacity", Integer.valueOf(table.state.capacity()),
                "operationScratchCurrentBytes", Long.valueOf(stats.operationScratchCurrentBytes()),
                "operationScratchHighWaterBytes", Long.valueOf(stats.operationScratchHighWaterBytes()),
                "singleRemoved", 1L, "batchCompacted", Long.valueOf(result.removed),
                "clearReuseCapacityBefore", Integer.valueOf(retained),
                "clearReuseCapacityAfter", Integer.valueOf(reuse.state.capacity()),
                "measurementArrayAllocationBytes", 12L);
        result.mutationReadRatio = BenchmarkModel.object("mutations", Long.valueOf(result.removed),
                "reads", Long.valueOf(config.rows));
        result.limitations = BenchmarkModel.limitations(
                "smoke validates packed batch compaction and retained capacity/scratch; no throughput claim",
                "single-remove and clear-reuse are shape assertions in the same compact workload");
        return SmokeLaneEvidence.finish(result, "packed-remove-compaction-reuse");
    }

    private static LaneObservation columnView(BenchmarkConfig config, String lane) {
        long setupStart = System.nanoTime();
        KernelTable table = new KernelTable(config.rows, "mixed-0-63-64", StatsMode.SUMMARY, true);
        int capacityBeforeMeasurement = table.state.capacity();
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        IntColumnView view = GeneratedColumnAccess.intView(
                table.state, table.values, table.presence, "BenchmarkRows", "value",
                "value.column", "value.column.isPresent", "value.column.getInt");
        long checksum = 0L;
        int reads = 0;
        for (int row = 0; row < config.rows; row++) {
            if (view.isPresent(row)) { checksum += view.getInt(row); reads++; }
        }
        boolean pinned = expectCode("view_pinned", new Action() {
            @Override public void run() { table.state.reserve(config.rows + 1); }
        });
        view.close();
        boolean released = expectCode("released_view", new Action() {
            @Override public void run() { view.getInt(0); }
        });
        table.state.reserve(config.rows + 1);
        long measured = elapsed(measureStart);
        require(pinned && released, "ColumnView lifecycle errors");
        blackhole ^= checksum;
        LaneObservation result = base(lane, setup, measured, config.rows);
        result.scanned = config.rows;
        result.matched = reads;
        result.lookups = config.rows + reads;
        result.touchedBytes = 8L * ((config.rows + 63L) >>> 6) + 4L * reads;
        result.workingSetBytes = 4L * table.state.capacity()
                + 8L * ((table.state.capacity() + 63L) >>> 6);
        long reserveAllocationBytes = table.state.capacity() == capacityBeforeMeasurement ? 0L
                : 4L * table.state.capacity()
                + 8L * ((table.state.capacity() + 63L) >>> 6);
        result.estimatedAllocationBytes = reserveAllocationBytes;
        result.columnViewStats = BenchmarkModel.object("acquired", 1L, "reads", Integer.valueOf(reads),
                "released", 1L, "staleErrors", 0L, "releasedErrors", 1L,
                "viewPinnedErrors", 1L,
                "reserveAllocationBytes", Long.valueOf(reserveAllocationBytes));
        result.limitations = BenchmarkModel.limitations(
                "smoke covers acquire/read/release and view_pinned/released_view paths",
                "borrow scope timing is not a claim about ColumnView versus other access tiers");
        return SmokeLaneEvidence.finish(result, "column-view-lifecycle");
    }

    private static LaneObservation statsOverhead(BenchmarkConfig config, String lane) {
        long setupStart = System.nanoTime();
        KernelTable summary = new KernelTable(config.rows, "required", StatsMode.SUMMARY, true);
        KernelTable diagnostic = new KernelTable(config.rows, "required", StatsMode.DIAGNOSTIC, true);
        long setup = elapsed(setupStart);
        long summaryStart = System.nanoTime();
        long first = repeatedInstrumentedScans(summary, config.rows);
        long summaryNanos = elapsed(summaryStart);
        long diagnosticStart = System.nanoTime();
        long second = repeatedInstrumentedScans(diagnostic, config.rows);
        long diagnosticNanos = elapsed(diagnosticStart);
        require(first == second, "stats mode semantic mismatch");
        blackhole ^= first;
        long processedRows = 2L * config.rows * config.rows;
        LaneObservation result = base(lane, setup, summaryNanos + diagnosticNanos,
                processedRows);
        result.operations = config.rows * 2L;
        result.scanned = processedRows;
        result.matched = processedRows;
        result.touchedBytes = 4L * processedRows;
        result.workingSetBytes = 4L * summary.state.capacity()
                + 4L * diagnostic.state.capacity();
        result.statsMode = "summary-vs-diagnostic";
        result.selectorStats = BenchmarkModel.object("summaryNanos", Long.valueOf(summaryNanos),
                "diagnosticNanos", Long.valueOf(diagnosticNanos),
                "summaryOperations", Integer.valueOf(config.rows),
                "diagnosticOperations", Integer.valueOf(config.rows),
                "rowsPerOperation", Integer.valueOf(config.rows));
        result.limitations = BenchmarkModel.limitations(
                "smoke measures equal begin/scan/end operation workloads under summary and diagnostic stats modes",
                "no fork isolation or allocation profiler; claimAllowed=false");
        return SmokeLaneEvidence.finish(result, "stats-mode-integrated-operation-overhead");
    }

    private static LaneObservation childLocality(BenchmarkConfig config, String lane) {
        long setupStart = System.nanoTime();
        final KernelTable child = new KernelTable(Math.max(8, config.rows / 4),
                "required", StatsMode.SUMMARY, true);
        final ChildOwnershipRegistry registry = new ChildOwnershipRegistry();
        final long owner = registry.newOwnerToken();
        OwnedChildTable owned = new OwnedChildTable() {
            @Override public boolean hasPinnedSubtree() { return child.state.hasPinnedBorrow(); }
            @Override public void preflightOwnedRelease(boolean aggregateRelease) { }
            @Override public void releaseOwnedSubtree(boolean aggregateRelease) {
                child.state.commitOwnedRelease(aggregateRelease);
            }
            @Override public long subtreeChildInstanceCount() { return 0L; }
            @Override public long subtreeDescendantRowCount() { return child.state.size(); }
        };
        long handle = registry.stage(owner, "children", "Parent.children", child, owned);
        registry.publish(handle, owner, "children");
        int parents = 8;
        int[] flatParent = new int[parents * child.state.size()];
        int[] flatValue = new int[flatParent.length];
        for (int row = 0; row < flatParent.length; row++) {
            flatParent[row] = row / child.state.size();
            flatValue[row] = row % child.state.size();
        }
        long setup = elapsed(setupStart);
        long childStart = System.nanoTime();
        KernelTable resolved = (KernelTable) registry.resolve(handle, owner, "children", "benchmark.scan");
        long childSum = resolved.sum();
        long childNanos = elapsed(childStart);
        long flatStart = System.nanoTime();
        long flatSum = 0L;
        int flatScanned = 0;
        for (int row = 0; row < flatParent.length; row++) {
            if (flatParent[row] == 0) flatSum += flatValue[row] + 1L;
            flatScanned++;
        }
        long flatNanos = elapsed(flatStart);
        require(childSum == flatSum, "child/flat semantic mismatch");
        blackhole ^= childSum;
        LaneObservation result = base(lane, setup, childNanos + flatNanos, child.state.size());
        result.baselineId = "flat-parent-id-filter-same-values-v1";
        result.scanned = child.state.size() + flatScanned;
        result.matched = child.state.size() * 2L;
        result.candidates = flatParent.length;
        result.selected = child.state.size();
        result.operations = 2L;
        result.touchedBytes = 4L * child.state.size() + 8L * flatParent.length;
        result.workingSetBytes = 4L * child.state.capacity() + 8L * flatParent.length;
        result.selectorStats = BenchmarkModel.object("parentCount", Integer.valueOf(parents),
                "childRows", Integer.valueOf(child.state.size()),
                "childInstances", 1L, "flatRows", Integer.valueOf(flatParent.length),
                "flatCandidateRows", Integer.valueOf(flatScanned),
                "flatAccess", "flat-scan-filter",
                "childScanNanos", Long.valueOf(childNanos),
                "flatFilterNanos", Long.valueOf(flatNanos));
        result.limitations = BenchmarkModel.limitations(
                "smoke compares same-value parent-local child and flat-filter paths without claiming locality advantage",
                "cache/profile counters are unavailable; flat baseline is a primitive parent-id scan");
        return SmokeLaneEvidence.finish(result, "parent-owned-child-versus-flat");
    }

    private static LaneObservation generatedMaterialization(BenchmarkConfig config, String lane) {
        long setupStart = System.nanoTime();
        OperationDefinitionTable table = materializationFixture();
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        Map<OperationKey, OperationDefinition> materialized = table.materialize();
        long measured = elapsed(measureStart);
        int childRows = 0;
        for (OperationDefinition definition : materialized.values()) {
            childRows += definition.candidateMachines.size();
        }
        require(materialized.size() == 2 && childRows == 2,
                "generated recursive Map/schema-object/List materialization");
        TableStats stats = table.statsSnapshot();
        table.release();
        LaneObservation result = base(lane, setup, measured,
                stats.lastMaterializationRows());
        result.operations = 1L;
        result.scanned = stats.lastMaterializationRows();
        result.matched = stats.lastMaterializationRows();
        result.materialized = stats.lastMaterializationRows();
        result.materializationInvocations = 1L;
        result.estimatedAllocationBytes = stats.lastMaterializationEstimatedAllocationBytes();
        result.workingSetBytes = result.estimatedAllocationBytes;
        result.touchedBytes = 8L * stats.lastMaterializationLeafValues();
        result.touchedBytesScope = "materialized leaf payload bytes traversed in measurement";
        result.workingSetScope = "maximum successfully published detached materialization payload estimate";
        result.materializationPath = "operation_definitions[].candidateMachines";
        result.materializationStats = BenchmarkModel.object("implementation", "generated-recursive-materializer",
                "rootMapEntries", Integer.valueOf(materialized.size()),
                "schemaObjects", 4L, "lists", 2L, "mapEntries", 2L,
                "maximumDepth", Integer.valueOf(stats.lastMaterializationMaximumOwnershipDepth()),
                "tableInstances", Long.valueOf(stats.lastMaterializationTableInstances()),
                "rows", Long.valueOf(stats.lastMaterializationRows()),
                "leafValues", Long.valueOf(stats.lastMaterializationLeafValues()),
                "estimatedBytes", Long.valueOf(stats.lastMaterializationEstimatedAllocationBytes()),
                "partialResults", 0L);
        result.effectiveMaterializationBudget = budgetMap(MaterializationBudget.defaults());
        result.limitations = BenchmarkModel.limitations(
                "real generated OperationDefinition materializer publishes Map of schema objects with owned List children",
                "estimated allocation follows the deterministic V1 estimator, not exact JVM heap accounting");
        return SmokeLaneEvidence.finish(result, "generated-recursive-map-object-list-materialization");
    }

    private static LaneObservation generatedBudgetBoundary(BenchmarkConfig config, String lane) {
        final String[] dimensions = {"maximumOwnershipDepth", "maximumTableInstances",
                "maximumRows", "maximumLeafValues", "maximumEstimatedAllocationBytes"};
        long setupStart = System.nanoTime();
        final OperationDefinitionTable table = materializationFixture();
        Map<OperationKey, OperationDefinition> baseline = table.materialize();
        TableStats baselineStats = table.statsSnapshot();
        final long[] observed = {
                baselineStats.lastMaterializationMaximumOwnershipDepth(),
                baselineStats.lastMaterializationTableInstances(),
                baselineStats.lastMaterializationRows(),
                baselineStats.lastMaterializationLeafValues(),
                baselineStats.lastMaterializationEstimatedAllocationBytes()
        };
        require(baseline.size() == 2, "generated materialization baseline");
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        ArrayList<Map<String, Object>> boundaries = new ArrayList<Map<String, Object>>();
        int successes = 0;
        int failures = 0;
        for (int index = 0; index < dimensions.length; index++) {
            String dimension = dimensions[index];
            long exactLimit = observed[index];
            MaterializationBudget successBudget = budgetForDimension(dimension, exactLimit);
            Map<OperationKey, OperationDefinition> atLimit = table.materialize(successBudget);
            require(atLimit.size() == 2, "generated materialization limit success: " + dimension);
            successes++;
            long failingLimit = exactLimit - 1L;
            MaterializationBudget failureBudget = budgetForDimension(dimension, failingLimit);
            Map<OperationKey, OperationDefinition> partial = null;
            try {
                partial = table.materialize(failureBudget);
                throw new AssertionError("generated materialization budget did not fail: " + dimension);
            } catch (SomaRuntimeException expected) {
                require("materialization_budget_exceeded".equals(expected.code()),
                        "generated materialization budget code for " + dimension);
                require(partial == null, "budget failure published a partial result");
                require(dimension.equals(expected.context().get("dimension")),
                        "generated materialization dimension context");
                long contextLimit = Long.parseLong(expected.context().get("limit"));
                long contextCurrent = Long.parseLong(expected.context().get("current"));
                long contextProposed = Long.parseLong(expected.context().get("proposed"));
                require(contextLimit == failingLimit && contextProposed > contextLimit,
                        "generated materialization boundary context");
                boundaries.add(BenchmarkModel.object(
                        "dimension", dimension,
                        "successLimit", Long.valueOf(exactLimit),
                        "failureLimit", Long.valueOf(contextLimit),
                        "current", Long.valueOf(contextCurrent),
                        "proposed", Long.valueOf(contextProposed),
                        "path", expected.path(),
                        "successAtLimit", Boolean.TRUE));
                failures++;
            }
        }
        Map<OperationKey, OperationDefinition> allocationPartial = null;
        MaterializationAllocation.Scope scope = MaterializationAllocation.installForCurrentThread(
                new MaterializationAllocation.Provider() {
                    @Override public boolean allow(String phase, long bytes, String path) {
                        return false;
                    }
                });
        try {
            try {
                allocationPartial = table.materialize();
                throw new AssertionError("generated allocation admission did not fail");
            } catch (SomaRuntimeException expected) {
                require("allocation_failure".equals(expected.code()), "generated allocation failure code");
                require(allocationPartial == null, "allocation failure published a partial result");
            }
        } finally {
            scope.close();
        }
        Map<OperationKey, OperationDefinition> recovery = table.materialize();
        long measured = elapsed(measureStart);
        TableStats stats = table.statsSnapshot();
        require(successes == dimensions.length && failures == dimensions.length
                        && boundaries.size() == dimensions.length
                        && recovery.size() == 2 && table.size() == 2,
                "generated materialization budget/allocation recovery");
        table.release();
        long successfulMaterializations = successes + 1L;
        long publishedRows = observed[2] * successfulMaterializations;
        LaneObservation result = base(lane, setup, measured, observed[2]);
        result.operations = dimensions.length * 2L + 2L;
        result.scanned = publishedRows;
        result.matched = publishedRows;
        result.materializationInvocations = result.operations;
        result.materialized = publishedRows;
        result.estimatedAllocationBytes = observed[4] * successfulMaterializations;
        result.workingSetBytes = observed[4];
        result.touchedBytes = 8L * observed[3] * result.operations;
        result.touchedBytesScope = "materialization leaf payload attempts in measurement";
        result.workingSetScope = "maximum successfully published detached materialization payload estimate";
        result.materializationBudgetDimension = "all-five-dimensions-plus-allocation-admission";
        result.materializationPath = "operation_definitions[].candidateMachines";
        result.materializationStats = BenchmarkModel.object("implementation", "generated-recursive-materializer",
                "boundarySuccesses", Integer.valueOf(successes),
                "budgetFailures", Integer.valueOf(failures), "allocationFailures", 1L,
                "recoverySuccesses", 1L, "partialResults", 0L,
                "tableRowsAfterFailures", Integer.valueOf(2),
                "failureCount", Long.valueOf(stats.materializationFailureCount()),
                "boundaries", boundaries);
        result.effectiveMaterializationBudget = budgetMap(MaterializationBudget.defaults());
        result.limitations = BenchmarkModel.limitations(
                "all five budget dimensions and controlled allocation admission execute through the generated recursive materializer",
                "controlled allocation admission is deterministic failure injection, not JVM OOME injection");
        return SmokeLaneEvidence.finish(result, "generated-budget-allocation-no-partial-recovery");
    }

    private static MaterializationBudget budgetForDimension(String dimension, long limit) {
        MaterializationBudget.Builder builder = MaterializationBudget.builder();
        if ("maximumOwnershipDepth".equals(dimension)) {
            builder.maximumOwnershipDepth((int) limit);
        } else if ("maximumTableInstances".equals(dimension)) {
            builder.maximumTableInstances(limit);
        } else if ("maximumRows".equals(dimension)) {
            builder.maximumRows(limit);
        } else if ("maximumLeafValues".equals(dimension)) {
            builder.maximumLeafValues(limit);
        } else if ("maximumEstimatedAllocationBytes".equals(dimension)) {
            builder.maximumEstimatedAllocationBytes(limit);
        } else {
            throw new IllegalArgumentException("unknown materialization dimension " + dimension);
        }
        return builder.build();
    }

    private static OperationDefinitionTable materializationFixture() {
        JobId job = new JobId(99L);
        SetupFamilyId family = new SetupFamilyId(5L);
        OperationDefinitionTable table = OperationDefinitionTable.create();
        table.addBatch(new OperationDefinitionBatch(2)
                .addValues(new OperationKey(job, new OperationId(1L)), 0, 0L, family,
                        new CandidateMachineDefinitionBatch(2)
                                .addValues(new MachineId(1L), 3L)
                                .addValues(new MachineId(2L), 4L))
                .addValues(new OperationKey(job, new OperationId(2L)), 1, 0L, family,
                        new CandidateMachineDefinitionBatch(0)));
        return table;
    }

    private static LaneObservation base(String lane, long setup, long measured, long rows) {
        LaneObservation result = new LaneObservation();
        result.scenario = lane.substring(0, lane.indexOf('.'));
        result.lane = lane;
        result.setupNanos = setup;
        result.measurementNanos = measured;
        result.rows = rows;
        result.candidates = rows;
        result.selected = rows;
        result.operations = 1L;
        return result;
    }

    private static long positiveDelta(long after, long before) {
        if (after < before) {
            throw new IllegalStateException("retained measurement storage decreased unexpectedly");
        }
        return after - before;
    }

    /** Arrays allocated by 4 -> 8 -> ... -> final open-addressing growth. */
    private static long hashGrowthBytes(long bytesPerBucket, int finalCapacity) {
        return finalCapacity <= 4 ? 0L
                : Math.multiplyExact(bytesPerBucket, 2L * finalCapacity - 8L);
    }

    private static long repeatedInstrumentedScans(KernelTable table, int repetitions) {
        long checksum = 0L;
        for (int index = 0; index < repetitions; index++) {
            table.state.beginOperation("benchmark.statsScan");
            long sum = table.sum();
            table.state.endOperationSuccess("benchmark.statsScan", table.state.size(),
                    table.state.size(), 0L);
            checksum += sum;
        }
        return checksum;
    }

    private static int insertionSlot(HashCompositeKeySpace keySpace, long hash) {
        int deleted = -1;
        for (int slot = keySpace.firstSlot(hash); !keySpace.isEmpty(slot); slot = keySpace.nextSlot(slot)) {
            if (!keySpace.isLive(slot) && deleted < 0) deleted = slot;
        }
        if (deleted >= 0) return deleted;
        for (int slot = keySpace.firstSlot(hash); ; slot = keySpace.nextSlot(slot)) {
            if (keySpace.isEmpty(slot)) return slot;
        }
    }

    private static Map<String, Object> budgetMap(MaterializationBudget budget) {
        return BenchmarkModel.object("identity", budget.identity(),
                "maximumOwnershipDepth", Integer.valueOf(budget.maximumOwnershipDepth()),
                "maximumTableInstances", Long.valueOf(budget.maximumTableInstances()),
                "maximumRows", Long.valueOf(budget.maximumRows()),
                "maximumLeafValues", Long.valueOf(budget.maximumLeafValues()),
                "maximumEstimatedAllocationBytes", Long.valueOf(budget.maximumEstimatedAllocationBytes()));
    }

    private static long elapsed(long start) {
        return Math.max(1L, System.nanoTime() - start);
    }

    private static boolean expectCode(String code, Action action) {
        try {
            action.run();
            return false;
        } catch (SomaRuntimeException expected) {
            if (!code.equals(expected.code())) {
                throw new AssertionError("expected " + code + " but got " + expected.code());
            }
            return true;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private interface Action { void run(); }

    private static final class KernelTable {
        final IntColumn values = new IntColumn();
        final PresenceBitmap presence = new PresenceBitmap();
        final DenseTableState state;

        KernelTable(int rows, String density, StatsMode statsMode, boolean reserve) {
            TablePlan tablePlan = TablePlan.builder("BenchmarkRows", RuntimeCompatibility.DENSE_ALGORITHM)
                    .initialCapacity(4)
                    .growthRatio(3, 2)
                    .maximumUpdateScratchBytes(16L * 1024L * 1024L)
                    .maximumOperationScratchBytes(16L * 1024L * 1024L)
                    .accessStrategy(RuntimeCompatibility.NO_ACCESS_STRATEGY)
                    .build();
            RuntimePlan plan = RuntimePlan.builder("benchmark-schema-v1",
                            RuntimeCompatibility.RUNTIME_COMPATIBILITY,
                            RuntimeCompatibility.GENERATED_PROTOCOL,
                            RuntimeCompatibility.PLAN_PROTOCOL,
                            RuntimeCompatibility.ALLOCATION_ESTIMATOR)
                    .statsMode(statsMode).addTable(tablePlan).build();
            ChildOwnershipRegistry ownership = new ChildOwnershipRegistry();
            state = new DenseTableState("BenchmarkRows", plan, tablePlan,
                    new ColumnGroup("BenchmarkRows", tablePlan, ownership,
                            4, values, presence));
            if (reserve && rows > 0) state.reserve(rows);
            if (rows > 0) {
                int start = state.prepareAppend(rows);
                for (int row = 0; row < rows; row++) {
                    values.set(start + row, row + 1);
                    if ("all-present".equals(density)
                            || ("mixed-0-63-64".equals(density)
                            && (row == 0 || row == 63 || row == 64))) {
                        presence.setPresent(start + row);
                    }
                }
                state.commitAppend(start, rows);
            }
        }

        void append(int[] batch) {
            int start = state.prepareAppend(batch.length);
            for (int row = 0; row < batch.length; row++) values.set(start + row, batch[row]);
            state.commitAppend(start, batch.length);
        }

        long sum() {
            long sum = 0L;
            for (int row = 0; row < state.size(); row++) sum += values.get(row);
            return sum;
        }
    }

}
