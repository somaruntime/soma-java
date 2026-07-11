package com.hgtech.soma.benchmarks;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.GeneratedColumnAccess;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.StatsMode;
import com.hgtech.soma.runtime.TablePlan;
import com.hgtech.soma.runtime.TableStats;
import com.hgtech.soma.runtime.UpdateResult;
import com.hgtech.soma.runtime.generated.ChildOwnershipRegistry;
import com.hgtech.soma.runtime.generated.ColumnGroup;
import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.HashCompositeKeySpace;
import com.hgtech.soma.runtime.generated.HashIntKeySpace;
import com.hgtech.soma.runtime.generated.IntColumn;
import com.hgtech.soma.runtime.generated.MaterializationAllocation;
import com.hgtech.soma.runtime.generated.MaterializationTracker;
import com.hgtech.soma.runtime.generated.OwnedChildTable;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.RowPermutationSidecar;
import com.hgtech.soma.runtime.generated.RuntimeCompatibility;
import com.hgtech.soma.runtime.generated.SparseIntKeySpace;

import com.hgtech.soma.examples.fjsp.JobId;
import com.hgtech.soma.examples.fjsp.MachineCandidate;
import com.hgtech.soma.examples.fjsp.MachineId;
import com.hgtech.soma.examples.fjsp.OperationDefinition;
import com.hgtech.soma.examples.fjsp.OperationId;
import com.hgtech.soma.examples.fjsp.OperationKey;
import com.hgtech.soma.examples.fjsp.OperationMachineKey;
import com.hgtech.soma.examples.fjsp.SetupFamilyId;
import com.hgtech.soma.examples.fjsp.generated.CandidateMachineDefinitionBatch;
import com.hgtech.soma.examples.fjsp.generated.MachineCandidateBatch;
import com.hgtech.soma.examples.fjsp.generated.MachineCandidateRows;
import com.hgtech.soma.examples.fjsp.generated.MachineCandidateTable;
import com.hgtech.soma.examples.fjsp.generated.OperationDefinitionBatch;
import com.hgtech.soma.examples.fjsp.generated.OperationDefinitionTable;
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
import com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowRows;
import com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowTable;
import com.hgtech.soma.examples.vrp.generated.TravelCostBatch;
import com.hgtech.soma.examples.vrp.generated.TravelCostTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/** G5与runtime-state contract的dependency-free真实smoke workloads。 */
final class SmokeLaneSuite {
    static final List<String> REQUIRED_LANES = Collections.unmodifiableList(Arrays.asList(
            "kernel.optional_all_present",
            "kernel.optional_all_absent",
            "kernel.optional_mixed_chunks",
            "kernel.packed_scan",
            "generated.pipeline_fusion",
            "kernel.keyspace_domain_load_collision_rehash",
            "kernel.key_lookup_normal",
            "kernel.key_lookup_collision",
            "kernel.batch_import.reserve",
            "kernel.batch_import.growth",
            "generated.ordered_access_lazy_rebuild",
            "generated.keyed_frontier",
            "generated.dense_scratch_replace_order",
            "kernel.column_view",
            "child_locality.parent_scan_vs_flat",
            "generated.materialization_recursive_success",
            "materialization.budget_boundary",
            "kernel.compaction_capacity_reuse",
            "kernel.sidecar_clean_dirty_rebuild_storm",
            "kernel.stats_mode_overhead"));

    private static volatile long blackhole;

    private SmokeLaneSuite() {
    }

    static List<BenchmarkRecord> run(BenchmarkConfig config, BenchmarkEnvironment environment) {
        ArrayList<BenchmarkRecord> records = new ArrayList<BenchmarkRecord>();
        for (String lane : REQUIRED_LANES) {
            for (int warmup = 0; warmup < config.warmupIterations; warmup++) {
                execute(config, lane, warmup);
            }
            LaneObservation aggregate = null;
            for (int iteration = 0; iteration < config.measurementIterations; iteration++) {
                LaneObservation current = execute(config, lane, iteration + 17);
                if (aggregate == null) aggregate = current;
                else merge(aggregate, current);
            }
            if (aggregate == null || aggregate.measurementNanos <= 0L) {
                throw new IllegalStateException("lane produced no measurement: " + lane);
            }
            aggregate = finish(aggregate, phaseKey(lane));
            records.add(BenchmarkModel.record(environment, config, aggregate));
        }
        return records;
    }

    private static LaneObservation execute(BenchmarkConfig config, String lane, int iteration) {
        if (lane.startsWith("kernel.optional_")) return optional(config, lane);
        if (lane.equals("kernel.packed_scan")) return packedScan(config, lane);
        if (lane.equals("generated.pipeline_fusion")) return generatedPipelineFusion(config, lane);
        if (lane.equals("kernel.keyspace_domain_load_collision_rehash")) return keySpace(config, lane);
        if (lane.equals("kernel.key_lookup_normal")
                || lane.equals("kernel.key_lookup_collision")) return generatedKeyLookup(config, lane);
        if (lane.startsWith("kernel.batch_import")) return batchImport(config, lane);
        if (lane.equals("generated.ordered_access_lazy_rebuild")) return generatedDenseWorkspace(config, lane, false);
        if (lane.equals("generated.keyed_frontier")) return generatedKeyedFrontier(config, lane);
        if (lane.equals("generated.dense_scratch_replace_order")) return generatedDenseWorkspace(config, lane, true);
        if (lane.equals("kernel.column_view")) return columnView(config, lane);
        if (lane.equals("child_locality.parent_scan_vs_flat")) return childLocality(config, lane);
        if (lane.equals("generated.materialization_recursive_success")) return generatedMaterialization(config, lane);
        if (lane.equals("materialization.budget_boundary")) return generatedBudgetBoundary(config, lane);
        if (lane.equals("kernel.compaction_capacity_reuse")) return compaction(config, lane);
        if (lane.equals("kernel.sidecar_clean_dirty_rebuild_storm")) return sidecar(config, lane);
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
        GeneratedColumnAccess.intPipeline(table.state, table.values, table.presence,
                "BenchmarkRows", "value").forEachInt(new IntConsumer() {
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
        return finish(result, "optional-column-scan");
    }

    private static LaneObservation packedScan(BenchmarkConfig config, String lane) {
        long setupStart = System.nanoTime();
        KernelTable table = new KernelTable(config.rows, "required", StatsMode.SUMMARY, true);
        int[] baseline = new int[config.rows];
        for (int row = 0; row < baseline.length; row++) baseline[row] = row + 1;
        long setup = elapsed(setupStart);
        final long[] soma = {0L};
        long somaStart = System.nanoTime();
        GeneratedColumnAccess.intPipeline(table.state, table.values, null,
                "BenchmarkRows", "value").forEachInt(new IntConsumer() {
            @Override public void accept(int value) { soma[0] += value; }
        });
        long somaNanos = elapsed(somaStart);
        long primitive = 0L;
        long baselineStart = System.nanoTime();
        for (int value : baseline) primitive += value;
        long baselineNanos = elapsed(baselineStart);
        require(soma[0] == primitive, "packed scan baseline mismatch");
        blackhole ^= primitive;
        LaneObservation result = base(lane, setup, somaNanos + baselineNanos, config.rows);
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
        return finish(result, "packed-scan-and-primitive-baseline");
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
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        long firstDistance = table.fetch(first).distanceMeters;
        long secondDistance = table.fetch(second).distanceMeters;
        boolean missing = !table.find(new LocationPairKey(new LocationId(9L), new LocationId(9L)))
                .isPresent();
        long measured = elapsed(measureStart);
        TableStats stats = table.statsSnapshot();
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
        result.materializationInvocations = 3L;
        result.materialized = 2L;
        result.keySpaceStats = BenchmarkModel.object("implementation", "generated-hash-composite-v1",
                "fullEqualityDistinguished", Boolean.TRUE,
                "collisionConstructed", Boolean.valueOf(collision),
                "collisionCount", Long.valueOf(stats.keySpaceCollisionCount()),
                "probeCount", Long.valueOf(stats.keySpaceProbeCount()),
                "rehashCount", Long.valueOf(stats.keySpaceRehashCount()));
        result.limitations = BenchmarkModel.limitations(
                "smoke constructs two distinct generated composite keys with equal FNV hash for collision coverage",
                "claimAllowed=false; timing is not a lookup performance claim");
        return finish(result, collision
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
        table.addBatch(batch);
        long setupNanos = elapsed(setupStart);
        long measureStart = System.nanoTime();
        UpdateResult updated = table.findByMachine(machineA).update(row -> {
            row.setIndicatorReady(true);
            row.setEffectiveReadyMinute(row.baseReadyMinute() + 1L);
        });
        MachineCandidate chosen = table.findByMachine(machineA)
                .filter(row -> row.indicatorReady())
                .sorted(new MachineCandidateRows.Comparator() {
                    @Override public int compare(com.hgtech.soma.examples.fjsp.generated.MachineCandidateRow left,
                                                 com.hgtech.soma.examples.fjsp.generated.MachineCandidateRow right) {
                        return Long.compare(left.effectiveReadyMinute(), right.effectiveReadyMinute());
                    }
                }).firstOrThrow();
        long removed = table.findByOperation(chosen.candidateKey.operationKey).remove().removed();
        long measured = elapsed(measureStart);
        require(updated.changed() > 0L && removed == 1L && table.size() == count - 1,
                "generated keyed frontier add/update/first/remove");
        TableStats stats = table.statsSnapshot();
        table.release();
        LaneObservation result = base(lane, setupNanos, measured, count);
        result.operations = count + updated.matched() + 2L;
        result.lookups = 3L;
        result.scanned = count + updated.scanned();
        result.matched = updated.matched() + 1L;
        result.changed = count + updated.changed();
        result.removed = removed;
        result.materializationInvocations = 1L;
        result.materialized = 1L;
        result.keySpaceStats = BenchmarkModel.object("implementation", "generated-machine-candidate-frontier",
                "added", Integer.valueOf(count), "updated", Long.valueOf(updated.changed()),
                "dynamicFirst", 1L, "removed", Long.valueOf(removed),
                "keySpaceCapacity", Integer.valueOf(stats.keySpaceCapacity()));
        result.sidecarStats = BenchmarkModel.object("rebuildCount",
                Long.valueOf(stats.sidecarRebuildCount()), "dirtyCount",
                Long.valueOf(stats.sidecarDirtyCount()));
        result.limitations = BenchmarkModel.limitations(
                "integrated generated table workload covers add/update/dynamic firstOrThrow/remove in one frontier lifecycle",
                "candidate scoring is deterministic benchmark data; no solver-quality or throughput claim");
        return finish(result, "generated-keyed-frontier-lifecycle");
    }

    private static LaneObservation generatedDenseWorkspace(BenchmarkConfig config, String lane,
                                                            boolean replaceAndBothTerminals) {
        int count = Math.max(8, config.rows);
        long setupStart = System.nanoTime();
        InsertionCandidateRowTable table = InsertionCandidateRowTable.create();
        InsertionCandidateRowBatch first = insertionBatch(count, 1000L);
        table.replaceAll(first);
        table.byBestDelta().firstOrThrow();
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        table.replaceAll(insertionBatch(count, 2000L));
        InsertionCandidateRow maintained = table.byBestDelta().firstOrThrow();
        InsertionCandidateRow dynamic = table.rows().sorted(new InsertionCandidateRowRows.Comparator() {
            @Override public int compare(com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowRow left,
                                         com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowRow right) {
                return Long.compare(left.deltaDistanceMeters(), right.deltaDistanceMeters());
            }
        }).findFirst().get();
        if (replaceAndBothTerminals) {
            require(table.rows().sorted((left, right) -> Long.compare(
                    left.deltaDistanceMeters(), right.deltaDistanceMeters()))
                    .firstOrThrow().customerId.equals(dynamic.customerId),
                    "dynamic firstOrThrow must agree with findFirst");
        }
        long measured = elapsed(measureStart);
        require(maintained.customerId.equals(dynamic.customerId),
                "maintained and dynamic dense order terminals disagree");
        TableStats stats = table.statsSnapshot();
        table.release();
        LaneObservation result = base(lane, setup, measured, count);
        result.operations = replaceAndBothTerminals ? count + 4L : count + 3L;
        result.scanned = count * (replaceAndBothTerminals ? 3L : 2L);
        result.matched = 2L;
        result.changed = count;
        result.materializationInvocations = replaceAndBothTerminals ? 4L : 3L;
        result.materialized = result.materializationInvocations;
        result.sidecarStats = BenchmarkModel.object("implementation", "generated-insertion-workspace",
                "replaceRows", Integer.valueOf(count),
                "maintainedFirstOrThrow", 1L, "dynamicFindFirst", 1L,
                "dynamicFirstOrThrow", replaceAndBothTerminals ? 1L : 0L,
                "dirtyCount", Long.valueOf(stats.sidecarDirtyCount()),
                "rebuildCount", Long.valueOf(stats.sidecarRebuildCount()));
        result.limitations = BenchmarkModel.limitations(
                "generated dense workspace performs replaceAll and maintained/dynamic ordered terminals",
                "candidate construction is outside measured publication/terminal phase");
        return finish(result, replaceAndBothTerminals
                ? "generated-dense-replace-and-order-terminals"
                : "generated-maintained-order-dirty-lazy-rebuild");
    }

    private static InsertionCandidateRowBatch insertionBatch(int count, long base) {
        InsertionCandidateRowBatch batch = new InsertionCandidateRowBatch(count);
        for (int index = 0; index < count; index++) {
            batch.addValues(new CustomerId(index + 1L), new RouteId(1L), index,
                    base + count - index, index, 0L);
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
        UpdateResult updated = table.rows().filter(row -> (row.vectorIndex() & 1) == 0)
                .limit(limit).update(row -> row.setValue(row.value() + row.derivative()));
        long measured = elapsed(measureStart);
        require(updated.matched() == limit && updated.changed() == limit,
                "generated fused filter/limit/update terminal");
        TableStats stats = table.statsSnapshot();
        table.release();
        LaneObservation result = base(lane, setup, measured, count);
        result.operations = updated.scanned() + updated.changed();
        result.scanned = updated.scanned();
        result.matched = updated.matched();
        result.changed = updated.changed();
        result.estimatedAllocationBytes = stats.operationScratchHighWaterBytes();
        result.selectorStats = BenchmarkModel.object("implementation", "generated-row-pipeline",
                "filterStages", 1L, "limitStages", 1L, "updateTerminal", 1L,
                "matched", Long.valueOf(updated.matched()),
                "changed", Long.valueOf(updated.changed()),
                "operationScratchHighWaterBytes", Long.valueOf(stats.operationScratchHighWaterBytes()),
                "perRowObjects", 0L);
        result.limitations = BenchmarkModel.limitations(
                "generated Row Pipeline executes filter+limit+update as one terminal with reusable cursor/scratch",
                "zero per-row objects is structural generated-code evidence, not a JVM allocation-profiler result");
        return finish(result, "generated-fused-filter-limit-update-terminal");
    }

    private static LaneObservation keySpace(BenchmarkConfig config, String lane) {
        long setupStart = System.nanoTime();
        int count = Math.max(65, config.rows);
        HashIntKeySpace hash = new HashIntKeySpace(1);
        SparseIntKeySpace sparse = new SparseIntKeySpace(count * 2);
        HashCompositeKeySpace composite = new HashCompositeKeySpace(1);
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        int[] direct = new int[count * 2 + 1];
        Arrays.fill(direct, -1);
        for (int row = 0; row < count; row++) {
            hash.put(row * 17 + 3, row);
            sparse.put(row * 2, row);
            direct[row * 2] = row;
            composite.ensureInsertCapacity();
            int slot = insertionSlot(composite, lane.contains("collision") ? 7L : row * 31L + 11L);
            composite.putAt(slot, lane.contains("collision") ? 7L : row * 31L + 11L, row);
        }
        int sparseOutOfDomainMisses = 0;
        if (sparse.rowOf(-1) == -1) sparseOutOfDomainMisses++;
        if (sparse.rowOf(sparse.maximumKey() + 1) == -1) sparseOutOfDomainMisses++;
        int sparseDomainGuardRejects = 0;
        try {
            sparse.put(-1, sparse.size());
        } catch (IllegalArgumentException expected) {
            sparseDomainGuardRejects++;
        }
        long checksum = 0L;
        for (int row = 0; row < count; row++) {
            checksum += hash.rowOf(row * 17 + 3);
            checksum += sparse.rowOf(row * 2);
            checksum += direct[row * 2];
        }
        int missing = hash.rowOf(-999);
        for (int row = 0; row < count / 4; row++) hash.remove(row * 17 + 3);
        long measured = elapsed(measureStart);
        require(missing == -1 && checksum >= 0L && sparseOutOfDomainMisses == 2
                        && sparseDomainGuardRejects == 1,
                "keyspace lookup/domain-guard semantics");
        blackhole ^= checksum;
        LaneObservation result = base(lane, setup, measured, count);
        result.operations = count * 4L + count / 4L + 1L;
        result.lookups = count * 3L + 1L;
        result.missing = 1L;
        result.changed = count * 3L + count / 4L;
        result.scanned = count * 3L;
        result.matched = count * 3L;
        result.touchedBytes = 24L * count;
        result.workingSetBytes = 4L * sparse.sparseCapacity()
                + 4L * sparse.denseCapacity() + 9L * hash.capacity()
                + 17L * composite.capacity();
        result.keySpaceStats = BenchmarkModel.object(
                "sparseMaximumKey", Integer.valueOf(sparse.maximumKey()),
                "sparseCapacity", Integer.valueOf(sparse.sparseCapacity()),
                "sparseDenseCapacity", Integer.valueOf(sparse.denseCapacity()),
                "sparseOutOfDomainMisses", Integer.valueOf(sparseOutOfDomainMisses),
                "sparseDomainGuardRejects", Integer.valueOf(sparseDomainGuardRejects),
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
                "smoke validates SparseInt domain and Hash load/collision/rehash paths; no complexity or speed claim",
                "composite lane uses raw hash/row substrate; generated full-key equality is covered by G2/G4 consumers");
        result.baselineId = "primitive-direct-domain-array-v1";
        return finish(result, "key-identity-lookup-mutation");
    }

    private static LaneObservation batchImport(BenchmarkConfig config, String lane) {
        boolean reserve = lane.endsWith("reserve");
        long setupStart = System.nanoTime();
        KernelTable table = new KernelTable(0, "required", StatsMode.SUMMARY, false);
        if (reserve) table.state.reserve(config.rows);
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
        result.estimatedAllocationBytes = 4L * batch.length;
        result.touchedBytes = 8L * batch.length;
        result.workingSetBytes = 4L * table.state.capacity() + 4L * batch.length;
        result.selectorStats = BenchmarkModel.object("reserved", Boolean.valueOf(reserve),
                "capacity", Integer.valueOf(table.state.capacity()),
                "growthCount", Long.valueOf(stats.growthCount()));
        result.limitations = BenchmarkModel.limitations(
                "smoke separates batch construction and table import but does not measure JVM allocation precisely",
                "capacity is a local smoke scale, not a production sizing recommendation");
        return finish(result, reserve ? "batch-import-with-reserve" : "batch-import-growth");
    }

    private static LaneObservation sidecar(BenchmarkConfig config, String lane) {
        long setupStart = System.nanoTime();
        RowPermutationSidecar sidecar = new RowPermutationSidecar();
        KernelTable table = new KernelTable(config.rows, "required", StatsMode.DIAGNOSTIC, true);
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        long rebuildNanos = 0L;
        int rebuilds = 4;
        long checksum = 0L;
        for (int rebuild = 0; rebuild < rebuilds; rebuild++) {
            long rebuildStart = System.nanoTime();
            int[] staged = sidecar.stage(config.rows);
            for (int row = 0; row < config.rows; row++) staged[row] = config.rows - row - 1;
            Arrays.sort(staged, 0, config.rows);
            sidecar.scratch(config.rows);
            sidecar.commit(staged, config.rows);
            table.state.sidecarRebuilt(config.rows);
            table.state.sidecarScratch(sidecar.retainedBytes(), sidecar.rebuildPeakBytes(config.rows));
            rebuildNanos += elapsed(rebuildStart);
            checksum += sidecar.rowAt(config.rows / 2);
            sidecar.markDirty();
            table.state.sidecarsDirtied(1L);
        }
        long measured = elapsed(measureStart);
        blackhole ^= checksum;
        TableStats stats = table.state.statsSnapshot();
        LaneObservation result = base(lane, setup, measured, config.rows);
        result.scanned = config.rows * rebuilds;
        result.matched = result.scanned;
        result.operations = rebuilds + 1L;
        result.touchedBytes = 8L * config.rows * rebuilds;
        result.workingSetBytes = sidecar.retainedBytes();
        result.sidecarStats = BenchmarkModel.object("dirtyCount", Long.valueOf(stats.sidecarDirtyCount()),
                "rebuildCount", Long.valueOf(stats.sidecarRebuildCount()),
                "rebuildRows", Long.valueOf(stats.sidecarRebuildRows()),
                "rebuildNanos", Long.valueOf(rebuildNanos),
                "currentBytes", Long.valueOf(stats.sidecarScratchCurrentBytes()),
                "highWaterBytes", Long.valueOf(stats.sidecarScratchHighWaterBytes()),
                "cleanTraversalCount", 1L, "stormRebuilds", Integer.valueOf(rebuilds));
        result.selectorStats = BenchmarkModel.object("cardinality", Integer.valueOf(config.rows),
                "selectivity", 1.0d, "dynamicSortBufferBytes", Long.valueOf(4L * config.rows));
        result.mutationReadRatio = BenchmarkModel.object("mutations", Integer.valueOf(rebuilds),
                "reads", 1L);
        result.statsMode = "diagnostic";
        result.limitations = BenchmarkModel.limitations(
                "smoke records clean/dirty/rebuild-storm shape; timing is not a maintained-order claim",
                "Arrays.sort is the benchmark baseline; generated comparator evidence is separate");
        return finish(result, "order-sidecar-dirty-lazy-rebuild");
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
        result.estimatedAllocationBytes = 4L * scratch.length;
        result.touchedBytes = 8L * config.rows;
        result.workingSetBytes = 4L * table.state.capacity() + 4L * scratch.length;
        result.sidecarStats = BenchmarkModel.object("compactedRows", Long.valueOf(result.removed),
                "retainedCapacity", Integer.valueOf(table.state.capacity()),
                "operationScratchCurrentBytes", Long.valueOf(stats.operationScratchCurrentBytes()),
                "operationScratchHighWaterBytes", Long.valueOf(stats.operationScratchHighWaterBytes()),
                "singleRemoved", 1L, "batchCompacted", Long.valueOf(result.removed),
                "clearReuseCapacityBefore", Integer.valueOf(retained),
                "clearReuseCapacityAfter", Integer.valueOf(reuse.state.capacity()));
        result.mutationReadRatio = BenchmarkModel.object("mutations", Long.valueOf(result.removed),
                "reads", Long.valueOf(config.rows));
        result.limitations = BenchmarkModel.limitations(
                "smoke validates packed batch compaction and retained capacity/scratch; no throughput claim",
                "single-remove and clear-reuse are shape assertions in the same compact workload");
        return finish(result, "packed-remove-compaction-reuse");
    }

    private static LaneObservation columnView(BenchmarkConfig config, String lane) {
        long setupStart = System.nanoTime();
        KernelTable table = new KernelTable(config.rows, "mixed-0-63-64", StatsMode.SUMMARY, true);
        long setup = elapsed(setupStart);
        long measureStart = System.nanoTime();
        IntColumnView view = GeneratedColumnAccess.intView(
                table.state, table.values, table.presence, "BenchmarkRows", "value");
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
        result.workingSetBytes = 4L * table.state.capacity();
        result.columnViewStats = BenchmarkModel.object("acquired", 1L, "reads", Integer.valueOf(reads),
                "released", 1L, "staleErrors", 0L, "releasedErrors", 1L,
                "viewPinnedErrors", 1L);
        result.limitations = BenchmarkModel.limitations(
                "smoke covers acquire/read/release and view_pinned/released_view paths",
                "borrow scope timing is not a claim about ColumnView versus other access tiers");
        return finish(result, "column-view-lifecycle");
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
        LaneObservation result = base(lane, setup, summaryNanos + diagnosticNanos, config.rows);
        result.operations = config.rows * 2L;
        result.scanned = result.operations;
        result.matched = result.operations;
        result.statsMode = "summary-vs-diagnostic";
        result.selectorStats = BenchmarkModel.object("summaryNanos", Long.valueOf(summaryNanos),
                "diagnosticNanos", Long.valueOf(diagnosticNanos),
                "summaryOperations", Integer.valueOf(config.rows),
                "diagnosticOperations", Integer.valueOf(config.rows),
                "rowsPerOperation", Integer.valueOf(config.rows));
        result.limitations = BenchmarkModel.limitations(
                "smoke measures equal begin/scan/end operation workloads under summary and diagnostic stats modes",
                "no fork isolation or allocation profiler; claimAllowed=false");
        return finish(result, "stats-mode-integrated-operation-overhead");
    }

    private static LaneObservation childLocality(BenchmarkConfig config, String lane) {
        long setupStart = System.nanoTime();
        final KernelTable child = new KernelTable(Math.max(8, config.rows / 4),
                "required", StatsMode.SUMMARY, true);
        final ChildOwnershipRegistry registry = new ChildOwnershipRegistry();
        final long owner = registry.newOwnerToken();
        OwnedChildTable owned = new OwnedChildTable() {
            @Override public boolean hasPinnedSubtree() { return child.state.hasPinnedBorrow(); }
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
        RowPermutationSidecar grouped = new RowPermutationSidecar();
        int[] groupedRows = grouped.stage(flatParent.length);
        for (int row = 0; row < flatParent.length; row++) {
            flatParent[row] = row / child.state.size();
            flatValue[row] = row % child.state.size();
            groupedRows[row] = row;
        }
        grouped.commit(groupedRows, flatParent.length);
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
                "cache/profile counters unavailable; grouped baseline uses runtime primitive sidecar rather than generated schema API");
        return finish(result, "parent-owned-child-versus-flat");
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
        LaneObservation result = base(lane, setup, measured, 6L);
        result.operations = 1L;
        result.scanned = 4L;
        result.matched = 4L;
        result.materialized = 4L;
        result.materializationInvocations = 1L;
        result.estimatedAllocationBytes = stats.lastMaterializationEstimatedAllocationBytes();
        result.workingSetBytes = result.estimatedAllocationBytes;
        result.allocationEstimatorVersion = "soma-materialization-estimator-v1";
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
        return finish(result, "generated-recursive-map-object-list-materialization");
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
        LaneObservation result = base(lane, setup, measured, dimensions.length + 2L);
        result.operations = dimensions.length * 2L + 3L;
        result.scanned = result.operations;
        result.matched = result.operations;
        result.materializationInvocations = 13L;
        result.materialized = 28L;
        result.estimatedAllocationBytes = observed[4];
        result.allocationEstimatorVersion = "soma-materialization-estimator-v1";
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
        return finish(result, "generated-budget-allocation-no-partial-recovery");
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

    private static LaneObservation finish(LaneObservation result, String phase) {
        long reads = Math.max(result.explicitReads, result.scanned + result.lookups);
        long mutations = Math.max(result.explicitMutations, result.changed + result.removed);
        result.mutationReadRatio = BenchmarkModel.object(
                "mutations", Long.valueOf(mutations), "reads", Long.valueOf(reads));
        if (result.materializationInvocations > 0L
                && (Boolean.FALSE.equals(result.materializationStats.get("applicable"))
                || "generated-row-materializer".equals(
                        result.materializationStats.get("implementation")))) {
            result.materializationStats = BenchmarkModel.object(
                    "implementation", "generated-row-materializer",
                    "invocations", Long.valueOf(result.materializationInvocations),
                    "rows", Long.valueOf(result.materialized),
                    "estimatedBytes", null,
                    "observationKind", "measured");
        }
        result.workloadId = workloadId(result.lane);
        result.workloadEvidence = BenchmarkModel.object(
                "executed", Boolean.TRUE,
                "proof", proofKey(result.lane),
                "implementation", isGeneratedLane(result.lane)
                        ? "generated-api" : "runtime-kernel",
                "positiveCount", Long.valueOf(Math.max(1L, result.operations)),
                "phase", phase);
        result.accessPatternCard = BenchmarkModel.object(
                "rowsCardinality", Long.valueOf(result.rows),
                "hotColumns", hotColumns(result.lane),
                "accessSource", phase,
                "readMutationMix", result.mutationReadRatio,
                "selectivity", result.scanned == 0L ? 0.0d
                        : (double) result.matched / result.scanned,
                "optionalDensity", result.optionalDensity,
                "childDensity", result.lane.startsWith("child_locality") ? "one-parent-local" : "not-applicable",
                "workingSetBytes", Long.valueOf(result.workingSetBytes),
                "allocationExport", result.materializationInvocations > 0L
                        ? "explicit-materialization-boundary" : "no-materialization-in-workload",
                "phaseBoundary", phase);
        return result;
    }

    private static List<String> hotColumns(String lane) {
        if (lane.startsWith("kernel.optional_")) return Arrays.asList("value", "presence");
        if (lane.equals("kernel.packed_scan")) return Arrays.asList("value");
        if (lane.equals("generated.pipeline_fusion")) {
            return Arrays.asList("vectorIndex", "value", "derivative");
        }
        if (lane.equals("kernel.keyspace_domain_load_collision_rehash")) {
            return Arrays.asList("key", "rowIndex", "hashSlot");
        }
        if (lane.equals("kernel.key_lookup_normal")
                || lane.equals("kernel.key_lookup_collision")) {
            return Arrays.asList("locationPair.from.value", "locationPair.to.value",
                    "distanceMeters");
        }
        if (lane.startsWith("kernel.batch_import")) return Arrays.asList("value");
        if (lane.equals("generated.keyed_frontier")) {
            return Arrays.asList("candidateKey.machineId.value",
                    "candidateKey.operationKey.operationId.value", "indicatorReady",
                    "effectiveReadyMinute");
        }
        if (lane.equals("generated.ordered_access_lazy_rebuild")
                || lane.equals("generated.dense_scratch_replace_order")) {
            return Arrays.asList("deltaDistanceMeters", "customerId.value");
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
        if (lane.equals("kernel.sidecar_clean_dirty_rebuild_storm")) {
            return Arrays.asList("rowIndex", "orderKey");
        }
        if (lane.equals("kernel.stats_mode_overhead")) return Arrays.asList("value");
        throw new IllegalArgumentException("missing Access Pattern Card hot columns for " + lane);
    }

    static String workloadId(String lane) {
        if (!REQUIRED_LANES.contains(lane)) {
            throw new IllegalArgumentException("unknown benchmark workload lane: " + lane);
        }
        return "soma-g5-smoke:" + lane + ":v3";
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
        validateAccessPatternCard(nested(record, "accessPatternCard"));
        validateDefaultMap(nested(record, "externalDtoStats"), "externalDtoStats");
        boolean sidecar = false, keySpace = false, selector = false;
        boolean materialization = number(nested(record, "operationCounts"),
                "materializations") > 0L;
        boolean specializedMaterialization = false, budget = false, columnView = false;

        if (lane.startsWith("kernel.optional_")) {
            selector = true;
            requireExactIntegers(nested(record, "selectorStats"),
                    new String[] {"bitmapWords", "present"});
        } else if (lane.equals("kernel.packed_scan")) {
            selector = true;
            requireExactIntegers(nested(record, "selectorStats"),
                    new String[] {"somaNanos", "primitiveBaselineNanos"});
        } else if (lane.equals("generated.pipeline_fusion")) {
            selector = true;
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactKeys(stats, new String[] {"implementation", "filterStages", "limitStages",
                    "updateTerminal", "matched", "changed",
                    "operationScratchHighWaterBytes", "perRowObjects"});
            requireStringValue(stats, "implementation", "generated-row-pipeline");
            requireIntegerFields(stats, new String[] {"filterStages", "limitStages",
                    "updateTerminal", "matched", "changed",
                    "operationScratchHighWaterBytes", "perRowObjects"});
            requirePositive(stats, "updateTerminal", lane);
            require(number(stats, "perRowObjects") == 0L,
                    "fusion lane per-row allocation shape");
        } else if (lane.equals("kernel.keyspace_domain_load_collision_rehash")) {
            keySpace = true;
            Map<String, Object> stats = nested(record, "keySpaceStats");
            requireExactKeys(stats, new String[] {"sparseMaximumKey", "sparseCapacity",
                    "sparseDenseCapacity", "sparseOutOfDomainMisses",
                    "sparseDomainGuardRejects", "hashCapacity", "hashUsed",
                    "hashLoadFactor", "hashProbeCount", "hashCollisionCount",
                    "hashRehashCount", "compositeCapacity", "compositeProbeCount",
                    "compositeCollisionCount", "compositeRehashCount",
                    "normalMissing", "duplicateAttempts"});
            requireIntegerFields(stats, new String[] {"sparseMaximumKey", "sparseCapacity",
                    "sparseDenseCapacity", "sparseOutOfDomainMisses",
                    "sparseDomainGuardRejects", "hashCapacity", "hashUsed",
                    "hashProbeCount", "hashCollisionCount", "hashRehashCount",
                    "compositeCapacity", "compositeProbeCount",
                    "compositeCollisionCount", "compositeRehashCount",
                    "normalMissing", "duplicateAttempts"});
            requireRange(stats, "hashLoadFactor", 0.0d, 1.0d);
            require(number(stats, "sparseOutOfDomainMisses") == 2L,
                    "SparseInt out-of-domain miss evidence");
            require(number(stats, "sparseDomainGuardRejects") == 1L,
                    "SparseInt domain guard reject evidence");
        } else if (lane.equals("kernel.key_lookup_normal")
                || lane.equals("kernel.key_lookup_collision")) {
            keySpace = true;
            Map<String, Object> stats = nested(record, "keySpaceStats");
            requireExactKeys(stats, new String[] {"implementation", "fullEqualityDistinguished",
                    "collisionConstructed", "collisionCount", "probeCount", "rehashCount"});
            requireStringValue(stats, "implementation", "generated-hash-composite-v1");
            requireBoolean(stats, "fullEqualityDistinguished", true);
            requireBoolean(stats, "collisionConstructed", lane.endsWith("collision"));
            requireIntegerFields(stats, new String[] {"collisionCount", "probeCount", "rehashCount"});
            if (lane.endsWith("collision")) requirePositive(stats, "collisionCount", lane);
        } else if (lane.startsWith("kernel.batch_import")) {
            selector = true;
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactKeys(stats, new String[] {"reserved", "capacity", "growthCount"});
            requireBoolean(stats, "reserved", lane.endsWith("reserve"));
            requireIntegerFields(stats, new String[] {"capacity", "growthCount"});
        } else if (lane.equals("generated.ordered_access_lazy_rebuild")
                || lane.equals("generated.dense_scratch_replace_order")) {
            sidecar = true;
            Map<String, Object> stats = nested(record, "sidecarStats");
            requireExactKeys(stats, new String[] {"implementation", "replaceRows",
                    "maintainedFirstOrThrow", "dynamicFindFirst", "dynamicFirstOrThrow",
                    "dirtyCount", "rebuildCount"});
            requireStringValue(stats, "implementation", "generated-insertion-workspace");
            requireIntegerFields(stats, new String[] {"replaceRows", "maintainedFirstOrThrow",
                    "dynamicFindFirst", "dynamicFirstOrThrow", "dirtyCount", "rebuildCount"});
            requirePositive(stats, "replaceRows", lane);
            requirePositive(stats, "maintainedFirstOrThrow", lane);
            requirePositive(stats, "dynamicFindFirst", lane);
            if (lane.equals("generated.dense_scratch_replace_order")) {
                requirePositive(stats, "dynamicFirstOrThrow", lane);
            }
        } else if (lane.equals("generated.keyed_frontier")) {
            sidecar = true;
            keySpace = true;
            Map<String, Object> keys = nested(record, "keySpaceStats");
            requireExactKeys(keys, new String[] {"implementation", "added", "updated",
                    "dynamicFirst", "removed", "keySpaceCapacity"});
            requireStringValue(keys, "implementation", "generated-machine-candidate-frontier");
            requireIntegerFields(keys, new String[] {"added", "updated", "dynamicFirst",
                    "removed", "keySpaceCapacity"});
            requirePositive(keys, "added", lane);
            requirePositive(keys, "updated", lane);
            requirePositive(keys, "dynamicFirst", lane);
            requirePositive(keys, "removed", lane);
            requireExactIntegers(nested(record, "sidecarStats"),
                    new String[] {"rebuildCount", "dirtyCount"});
        } else if (lane.equals("kernel.column_view")) {
            columnView = true;
            Map<String, Object> stats = nested(record, "columnViewStats");
            requireExactIntegers(stats,
                    new String[] {"acquired", "reads", "released", "staleErrors",
                            "releasedErrors", "viewPinnedErrors"});
            require(number(stats, "acquired") == 1L && number(stats, "released") == 1L
                            && number(stats, "staleErrors") == 0L
                            && number(stats, "releasedErrors") == 1L
                            && number(stats, "viewPinnedErrors") == 1L,
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
            require(number(stats, "boundarySuccesses") == 5L, "five boundary successes");
            require(number(stats, "budgetFailures") == 5L, "five boundary failures");
            require(number(stats, "allocationFailures") == 1L, "allocation failure");
            require(number(stats, "partialResults") == 0L, "no partial results");
            validateBoundaries(stats.get("boundaries"));
        } else if (lane.equals("kernel.compaction_capacity_reuse")) {
            sidecar = true;
            requireExactIntegers(nested(record, "sidecarStats"),
                    new String[] {"compactedRows", "retainedCapacity",
                            "operationScratchCurrentBytes", "operationScratchHighWaterBytes",
                            "singleRemoved", "batchCompacted", "clearReuseCapacityBefore",
                            "clearReuseCapacityAfter"});
        } else if (lane.equals("kernel.sidecar_clean_dirty_rebuild_storm")) {
            sidecar = true;
            selector = true;
            requireExactIntegers(nested(record, "sidecarStats"),
                    new String[] {"dirtyCount", "rebuildCount", "rebuildRows",
                            "rebuildNanos", "currentBytes", "highWaterBytes",
                            "cleanTraversalCount", "stormRebuilds"});
            Map<String, Object> sidecarStats = nested(record, "sidecarStats");
            require(number(sidecarStats, "cleanTraversalCount") == 1L
                            && number(sidecarStats, "stormRebuilds") >= 4L,
                    "sidecar clean/rebuild-storm evidence");
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactKeys(stats, new String[] {"cardinality", "selectivity",
                    "dynamicSortBufferBytes"});
            requireIntegerFields(stats, new String[] {"cardinality", "dynamicSortBufferBytes"});
            requireRange(stats, "selectivity", 0.0d, 1.0d);
        } else if (lane.equals("kernel.stats_mode_overhead")) {
            selector = true;
            Map<String, Object> stats = nested(record, "selectorStats");
            requireExactIntegers(stats, new String[] {"summaryNanos", "diagnosticNanos",
                    "summaryOperations", "diagnosticOperations", "rowsPerOperation"});
            requirePositive(stats, "summaryOperations", lane);
            requirePositive(stats, "diagnosticOperations", lane);
        } else {
            throw new IllegalArgumentException("missing lane evidence contract " + lane);
        }

        validateDefaultIfUnused(record, "sidecarStats", sidecar);
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
    }

    @SuppressWarnings("unchecked")
    private static void validateBoundaries(Object value) {
        if (!(value instanceof List) || ((List<?>) value).size() != 5) {
            throw new IllegalArgumentException("materialization boundaries must contain five entries");
        }
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
            if (number(boundary, "successLimit")
                    != number(boundary, "failureLimit") + 1L
                    || number(boundary, "proposed")
                    != number(boundary, "successLimit")) {
                throw new IllegalArgumentException(
                        "boundary must prove exact success and limit+1 failure");
            }
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

    private static long number(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long)) {
            throw new IllegalArgumentException(field + " must be integer");
        }
        return ((Number) value).longValue();
    }

    private static String proofKey(String lane) {
        if (lane.startsWith("kernel.optional_")) return "optional-bitmap-scan";
        if (lane.equals("kernel.packed_scan")) return "packed-vs-primitive-equality";
        if (lane.equals("generated.pipeline_fusion")) return "generated-fused-terminal";
        if (lane.equals("kernel.keyspace_domain_load_collision_rehash")) return "keyspace-domain-load-rehash";
        if (lane.equals("kernel.key_lookup_normal")) return "generated-normal-full-key-lookup";
        if (lane.equals("kernel.key_lookup_collision")) return "generated-collision-full-equality";
        if (lane.startsWith("kernel.batch_import")) return "batch-capacity-path";
        if (lane.equals("generated.ordered_access_lazy_rebuild")) return "generated-lazy-order-rebuild";
        if (lane.equals("generated.keyed_frontier")) return "generated-frontier-add-update-first-remove";
        if (lane.equals("generated.dense_scratch_replace_order")) return "generated-replace-findfirst-firstorthrow";
        if (lane.equals("kernel.column_view")) return "column-view-lifecycle";
        if (lane.equals("child_locality.parent_scan_vs_flat")) return "parent-local-versus-flat";
        if (lane.equals("generated.materialization_recursive_success")) return "generated-recursive-map-object-list";
        if (lane.equals("materialization.budget_boundary")) return "generated-budget-allocation-no-partial";
        if (lane.equals("kernel.compaction_capacity_reuse")) return "compaction-capacity-scratch-reuse";
        if (lane.equals("kernel.sidecar_clean_dirty_rebuild_storm")) return "sidecar-clean-dirty-rebuild-storm";
        if (lane.equals("kernel.stats_mode_overhead")) return "summary-vs-diagnostic-operations";
        throw new IllegalArgumentException("no proof contract for " + lane);
    }

    private static String phaseKey(String lane) {
        if (lane.startsWith("kernel.optional_")) return "optional-column-scan";
        if (lane.equals("kernel.packed_scan")) return "packed-scan-and-primitive-baseline";
        if (lane.equals("generated.pipeline_fusion")) return "generated-fused-filter-limit-update-terminal";
        if (lane.equals("kernel.keyspace_domain_load_collision_rehash")) return "key-identity-lookup-mutation";
        if (lane.equals("kernel.key_lookup_normal")) return "generated-normal-key-lookup";
        if (lane.equals("kernel.key_lookup_collision")) return "generated-full-equality-collision-lookup";
        if (lane.equals("kernel.batch_import.reserve")) return "batch-import-with-reserve";
        if (lane.equals("kernel.batch_import.growth")) return "batch-import-growth";
        if (lane.equals("generated.ordered_access_lazy_rebuild")) return "generated-maintained-order-dirty-lazy-rebuild";
        if (lane.equals("generated.keyed_frontier")) return "generated-keyed-frontier-lifecycle";
        if (lane.equals("generated.dense_scratch_replace_order")) return "generated-dense-replace-and-order-terminals";
        if (lane.equals("kernel.column_view")) return "column-view-lifecycle";
        if (lane.equals("child_locality.parent_scan_vs_flat")) return "parent-owned-child-versus-flat";
        if (lane.equals("generated.materialization_recursive_success")) return "generated-recursive-map-object-list-materialization";
        if (lane.equals("materialization.budget_boundary")) return "generated-budget-allocation-no-partial-recovery";
        if (lane.equals("kernel.compaction_capacity_reuse")) return "packed-remove-compaction-reuse";
        if (lane.equals("kernel.sidecar_clean_dirty_rebuild_storm")) return "order-sidecar-dirty-lazy-rebuild";
        if (lane.equals("kernel.stats_mode_overhead")) return "stats-mode-integrated-operation-overhead";
        throw new IllegalArgumentException("no phase contract for " + lane);
    }

    private static boolean isGeneratedLane(String lane) {
        return lane.startsWith("generated.")
                || lane.equals("kernel.key_lookup_normal")
                || lane.equals("kernel.key_lookup_collision")
                || lane.equals("materialization.budget_boundary");
    }

    private static void merge(LaneObservation target, LaneObservation source) {
        target.setupNanos += source.setupNanos;
        target.measurementNanos += source.measurementNanos;
        target.exportNanos += source.exportNanos;
        target.rows += source.rows;
        target.scanned += source.scanned;
        target.matched += source.matched;
        target.changed += source.changed;
        target.removed += source.removed;
        target.materialized += source.materialized;
        target.materializationInvocations += source.materializationInvocations;
        target.candidates += source.candidates;
        target.selected += source.selected;
        target.operations += source.operations;
        target.lookups += source.lookups;
        target.missing += source.missing;
        target.duplicates += source.duplicates;
        target.touchedBytes += source.touchedBytes;
        target.estimatedAllocationBytes += source.estimatedAllocationBytes;
        target.workingSetBytes = Math.max(target.workingSetBytes, source.workingSetBytes);
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
        if (!condition) throw new AssertionError(message);
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
                    .accessStrategy("primitive-sorted-permutation-v1")
                    .sidecarMaintenancePolicy("dirty-lazy-rebuild-v1")
                    .maximumSidecarScratchBytes(16L * 1024L * 1024L)
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
