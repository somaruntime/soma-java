package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.examples.fjsp.generated.CandidateMachineDefinitionBatch;
import com.hgtech.soma.examples.fjsp.generated.CandidateMachineDefinitionTable;
import com.hgtech.soma.examples.fjsp.generated.JobDefinitionBatch;
import com.hgtech.soma.examples.fjsp.generated.JobDefinitionTable;
import com.hgtech.soma.examples.fjsp.generated.JobResultBatch;
import com.hgtech.soma.examples.fjsp.generated.JobResultTable;
import com.hgtech.soma.examples.fjsp.generated.JobRuntimeStateBatch;
import com.hgtech.soma.examples.fjsp.generated.JobRuntimeStateTable;
import com.hgtech.soma.examples.fjsp.generated.MachineBatch;
import com.hgtech.soma.examples.fjsp.generated.MachineCandidateBatch;
import com.hgtech.soma.examples.fjsp.generated.MachineCandidateRows;
import com.hgtech.soma.examples.fjsp.generated.MachineCandidateTable;
import com.hgtech.soma.examples.fjsp.generated.MachineTable;
import com.hgtech.soma.examples.fjsp.generated.MaterialBatch;
import com.hgtech.soma.examples.fjsp.generated.MaterialTable;
import com.hgtech.soma.examples.fjsp.generated.OperationAssignmentBatch;
import com.hgtech.soma.examples.fjsp.generated.OperationAssignmentTable;
import com.hgtech.soma.examples.fjsp.generated.OperationDefinitionBatch;
import com.hgtech.soma.examples.fjsp.generated.OperationDefinitionTable;
import com.hgtech.soma.examples.fjsp.generated.OperationRuntimeStateBatch;
import com.hgtech.soma.examples.fjsp.generated.OperationRuntimeStateTable;
import com.hgtech.soma.examples.fjsp.generated.SetupTimeBatch;
import com.hgtech.soma.examples.fjsp.generated.SetupTimeTable;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.RemoveResult;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.TableStats;
import com.hgtech.soma.runtime.UpdateResult;

import java.util.List;

/** FJSP request -> generated tables -> keyed frontier -> detached response 的正式场景。 */
public final class FjspScenario {
    private FjspScenario() { }

    public static ScenarioResult run() {
        JobId job = new JobId(1L);
        OperationKey first = new OperationKey(job, new OperationId(10L));
        OperationKey second = new OperationKey(job, new OperationId(20L));
        MachineId machineA = new MachineId(100L);
        MachineId machineB = new MachineId(200L);
        SetupFamilyId familyA = new SetupFamilyId(7L);
        SetupFamilyId familyB = new SetupFamilyId(8L);

        JobDefinitionTable jobs = JobDefinitionTable.create();
        JobRuntimeStateTable jobStates = JobRuntimeStateTable.create();
        JobResultTable jobResults = JobResultTable.create();
        OperationDefinitionTable definitions = OperationDefinitionTable.create();
        OperationRuntimeStateTable operationStates = OperationRuntimeStateTable.create();
        OperationAssignmentTable assignments = OperationAssignmentTable.create();
        MaterialTable materials = MaterialTable.create();
        MachineTable machines = MachineTable.create();
        SetupTimeTable setupTimes = SetupTimeTable.create();
        MachineCandidateTable frontier = MachineCandidateTable.create();
        try {
            jobs.reserve(1);
            definitions.reserve(2);
            machines.reserve(2);
            frontier.reserve(4);
            assignments.reserve(2);

            jobs.addBatch(new JobDefinitionBatch(1).addValues(job, 1L, 50L, 2));
            jobStates.addBatch(new JobRuntimeStateBatch(1).addValues(job, 0));
            materials.addBatch(new MaterialBatch(1).addValues(new MaterialId(1L), 0L));
            machines.addBatch(new MachineBatch(2)
                    .addValues(machineA, MachineState.READY, 0L, false, null)
                    .addValues(machineB, MachineState.READY, 100L, true, familyB));

            CandidateMachineDefinitionBatch firstMachines =
                    new CandidateMachineDefinitionBatch(2)
                            .addValues(machineA, 6L).addValues(machineB, 4L);
            CandidateMachineDefinitionBatch secondMachines =
                    new CandidateMachineDefinitionBatch(1).addValues(machineA, 3L);
            definitions.addBatch(new OperationDefinitionBatch(2)
                    .addValues(first, 0, 0L, familyA, firstMachines)
                    .addValues(second, 1, 0L, familyB, secondMachines));
            operationStates.addBatch(new OperationRuntimeStateBatch(2)
                    .addValues(first, 0L, 0L).addValues(second, 0L, 0L));
            setupTimes.addBatch(new SetupTimeBatch(1).addValues(
                    new SetupTimeKey(machineA, new SetupFamilyPair(familyA, familyB)), 2L));

            release(first, 0, definitions, operationStates, frontier);
            require(definitions.containsKey(first), "required definition identity exists");
            require(frontier.findByOperation(first).count() == 2L,
                    "release must publish both operation-machine candidates");
            // detached parent+List只作为materialization evidence，不进入release hot path。
            require(definitions.fetch(first).candidateMachines.size() == 2,
                    "keyed fetch recursively materializes the owned candidate List");

            MachineSelection selectedMachine = selectMachine(machines);
            require(selectedMachine.machineId.equals(machineA),
                    "maintained machine order chooses the earliest machine");
            require(machines.rows().filter(row -> row.lastSetupFamilyAbsent()).count() == 1L,
                    "generated optional presence predicate distinguishes absence");

            DispatchResult firstDispatch = dispatch(first, machines, setupTimes,
                    frontier, assignments);
            require(firstDispatch.setupMinutes == 0L && frontier.size() == 0,
                    "first operation uses explicit no-prior-family setup rule");
            jobStates.mutate(job).setNextSequenceNo(1).commit();

            require(definitions.findByJobSequence(job, 1)
                            .anyMatch(row -> row.operationKey().equals(second)),
                    "next release is driven by grouped job-sequence access");
            require(!frontier.find(new OperationMachineKey(second, machineB)).isPresent(),
                    "optional lookup remains empty without becoming a runtime failure");
            release(second, 1, definitions, operationStates, frontier);
            require(frontier.findByOperation(second).count() == 1L,
                    "second operation is released into the keyed frontier");
            DispatchResult secondDispatch = dispatch(second, machines, setupTimes,
                    frontier, assignments);
            require(secondDispatch.setupMinutes == 2L && frontier.size() == 0,
                    "second operation performs positive SetupTime lookup and cleanup");
            jobStates.mutate(job).setNextSequenceNo(2).commit();
            require(assignments.containsKey(first) && assignments.containsKey(second),
                    "keyed assignment identity is queryable");
            require(assignments.fetch(second).setupMinutes == 2L,
                    "positive setup survives detached result materialization");
            jobResults.addBatch(new JobResultBatch(1)
                    .addValues(job, secondDispatch.endMinute, 0L));

            expectCode("duplicate_key", () -> assignments.addBatch(
                    new OperationAssignmentBatch(1).addValues(first, machineA,
                            0L, 0L, 0L, 1L, 1L)));
            expectCode("missing_key", () -> setupTimes.fetch(new SetupTimeKey(
                    machineA, new SetupFamilyPair(familyB, familyA))));
            expectCode("empty_result", () -> frontier.findByMachine(machineB).firstOrThrow());

            LongColumnView view = machines.availableFromMinuteColumn();
            try {
                require(view.getLong(0) == secondDispatch.endMinute,
                        "typed ColumnView reads hot machine state");
                expectCode("view_pinned", machines::clear);
            } finally {
                view.close();
            }
            expectCode("released_view", () -> view.getLong(0));
            MachineTable releasedTable = MachineTable.create();
            releasedTable.release();
            expectCode("table_released", releasedTable::size);

            List<OperationAssignment> exported = assignments.fetchAll(
                    MaterializationBudget.defaults());
            TableStats stats = frontier.statsSnapshot();
            require(exported.size() == 2, "detached result export");
            require(stats.schemaHash().equals(frontier.runtimePlan().schemaHash())
                            && stats.sidecarRebuildCount() > 0L
                            && stats.keySpaceCapacity() > 0,
                    "schema/runtime plan and runtime stats are observable");
            verifyStableTieBreakAfterCompaction(machineA, familyA);
            UpdateResult apcUpdate = assignments.update(row ->
                    row.setEndMinute(row.endMinute() + 1L));
            return new ScenarioResult(exported.size(), frontier.runtimePlan().schemaHash(),
                    stats.sidecarRebuildCount(), assignments.size(), 64,
                    (long) assignments.capacity() * 64L,
                    apcUpdate.scanned() + exported.size(), apcUpdate.changed(),
                    exported.size());
        } finally {
            frontier.release();
            setupTimes.release();
            machines.release();
            materials.release();
            assignments.release();
            operationStates.release();
            definitions.release();
            jobResults.release();
            jobStates.release();
            jobs.release();
        }
    }

    private static void release(OperationKey key, int sequenceNo,
                                OperationDefinitionTable definitions,
                                OperationRuntimeStateTable states,
                                MachineCandidateTable frontier) {
        final ReleaseFacts[] facts = new ReleaseFacts[1];
        definitions.findByJobSequence(key.jobId, sequenceNo).forEach(row -> {
            if (row.operationKey().equals(key)) {
                facts[0] = new ReleaseFacts(row.releaseMinute(),
                        new SetupFamilyId(row.setupFamilyValue()));
            }
        });
        require(facts[0] != null, "indexed operation release facts");
        int stateRow = states.rowIndexOf(key.jobId.value, key.operationId.value);
        long jobReady;
        long materialReady;
        LongColumnView jobReadyColumn = states.jobReadyMinuteColumn();
        LongColumnView materialReadyColumn = states.materialReadyMinuteColumn();
        try {
            jobReady = jobReadyColumn.getLong(stateRow);
            materialReady = materialReadyColumn.getLong(stateRow);
        } finally {
            materialReadyColumn.close();
            jobReadyColumn.close();
        }
        CandidateMachineDefinitionTable candidates = definitions.candidateMachines(key);
        MachineCandidateBatch batch = new MachineCandidateBatch(candidates.size());
        candidates.forEach(candidate -> {
            long baseReady = maximum(facts[0].releaseMinute,
                    maximum(jobReady, materialReady));
            batch.addValues(new OperationMachineKey(key,
                            new MachineId(candidate.machineIdValue())),
                    facts[0].setupFamily, facts[0].releaseMinute,
                    jobReady, materialReady, baseReady,
                    candidate.processingMinutes(), 0L, baseReady, facts[0].releaseMinute,
                    candidate.processingMinutes(), false);
        });
        frontier.addBatch(batch);
    }

    private static DispatchResult dispatch(OperationKey expectedOperation,
                                           MachineTable machines,
                                           SetupTimeTable setupTimes,
                                           MachineCandidateTable frontier,
                                           OperationAssignmentTable assignments) {
        MachineSelection selectedMachine = selectMachine(machines);
        LongColumnView setupMinutesColumn = setupTimes.setupMinutesColumn();
        UpdateResult refreshed;
        try {
            refreshed = frontier.findByMachine(selectedMachine.machineId)
                    .update(row -> {
                    long setup = !selectedMachine.lastSetupFamilyPresent ? 0L
                            : setupMinutesColumn.getLong(setupTimes.rowIndexOf(
                                    selectedMachine.machineId.value,
                                    selectedMachine.lastSetupFamily.value,
                                    row.targetSetupFamilyValue()));
                    long ready = maximum(selectedMachine.availableFromMinute,
                            row.baseReadyMinute());
                    row.setSetupMinutes(setup);
                    row.setEffectiveReadyMinute(ready);
                    row.setFcfsValue(row.operationReleaseMinute());
                    row.setSptValue(row.processingMinutes());
                    row.setIndicatorReady(true);
                });
        } finally {
            setupMinutesColumn.close();
        }
        require(refreshed.changed() > 0L, "grouped indicator update");
        int[] chosenRows = frontier.findByMachine(selectedMachine.machineId)
                .filter(row -> row.indicatorReady())
                .sorted(dispatchComparator()).limit(1).rowIndexes();
        require(chosenRows.length == 1, "dispatch requires one selected candidate");
        CandidateSelection chosen = readCandidate(frontier, chosenRows[0]);
        require(chosen.operationKey.equals(expectedOperation),
                "dispatch selects the released operation");
        long setupStart = maximum(selectedMachine.availableFromMinute,
                chosen.baseReadyMinute);
        long start = setupStart + chosen.setupMinutes;
        long end = start + chosen.processingMinutes;

        // 跨表提交顺序属于solver；SOMA不提供跨root transaction。
        assignments.addBatch(new OperationAssignmentBatch(1).addValues(
                expectedOperation, selectedMachine.machineId, setupStart,
                chosen.setupMinutes, start, chosen.processingMinutes, end));
        machines.mutate(selectedMachine.machineId).setAvailableFromMinute(end)
                .setLastSetupFamily(chosen.targetSetupFamily).commit();
        RemoveResult removed = frontier.findByOperation(expectedOperation).remove();
        require(removed.removed() > 0L,
                "grouped removal cleans the selected operation frontier");
        return new DispatchResult(end, chosen.setupMinutes);
    }

    private static MachineCandidateRows.Comparator dispatchComparator() {
        return (left, right) -> {
            int byReady = Long.compare(left.effectiveReadyMinute(), right.effectiveReadyMinute());
            if (byReady != 0) return byReady;
            int byFcfs = Long.compare(left.fcfsValue(), right.fcfsValue());
            if (byFcfs != 0) return byFcfs;
            int bySpt = Long.compare(left.sptValue(), right.sptValue());
            if (bySpt != 0) return bySpt;
            int byJob = Long.compare(left.candidateKeyOperationKeyJobIdValue(),
                    right.candidateKeyOperationKeyJobIdValue());
            if (byJob != 0) return byJob;
            int byOperation = Long.compare(left.candidateKeyOperationKeyOperationIdValue(),
                    right.candidateKeyOperationKeyOperationIdValue());
            if (byOperation != 0) return byOperation;
            return Long.compare(left.candidateKeyMachineIdValue(),
                    right.candidateKeyMachineIdValue());
        };
    }

    private static MachineSelection selectMachine(MachineTable machines) {
        int[] rows = machines.byAvailableTime().limit(1).rowIndexes();
        require(rows.length == 1, "machine order requires one row");
        int row = rows[0];
        LongColumnView machineId = machines.machineIdValueColumn();
        LongColumnView available = machines.availableFromMinuteColumn();
        LongColumnView lastSetup = machines.lastSetupFamilyValueColumn();
        try {
            boolean present = lastSetup.isPresent(row);
            return new MachineSelection(new MachineId(machineId.getLong(row)),
                    available.getLong(row), present,
                    present ? new SetupFamilyId(lastSetup.getLong(row)) : null);
        } finally {
            lastSetup.close();
            available.close();
            machineId.close();
        }
    }

    private static CandidateSelection readCandidate(
            MachineCandidateTable frontier, int row) {
        LongColumnView jobId = frontier.candidateKeyOperationKeyJobIdValueColumn();
        LongColumnView operationId =
                frontier.candidateKeyOperationKeyOperationIdValueColumn();
        LongColumnView targetSetup = frontier.targetSetupFamilyValueColumn();
        LongColumnView baseReady = frontier.baseReadyMinuteColumn();
        LongColumnView processing = frontier.processingMinutesColumn();
        LongColumnView setup = frontier.setupMinutesColumn();
        try {
            return new CandidateSelection(
                    new OperationKey(new JobId(jobId.getLong(row)),
                            new OperationId(operationId.getLong(row))),
                    new SetupFamilyId(targetSetup.getLong(row)),
                    baseReady.getLong(row), processing.getLong(row), setup.getLong(row));
        } finally {
            setup.close();
            processing.close();
            baseReady.close();
            targetSetup.close();
            operationId.close();
            jobId.close();
        }
    }

    private static void verifyStableTieBreakAfterCompaction(
            MachineId machine, SetupFamilyId family) {
        JobId job = new JobId(77L);
        OperationKey first = new OperationKey(job, new OperationId(1L));
        OperationKey removed = new OperationKey(job, new OperationId(2L));
        OperationKey last = new OperationKey(job, new OperationId(3L));
        MachineCandidateTable table = MachineCandidateTable.create();
        try {
            MachineCandidateBatch batch = new MachineCandidateBatch(3);
            addTieCandidate(batch, last, machine, family);
            addTieCandidate(batch, removed, machine, family);
            addTieCandidate(batch, first, machine, family);
            table.addBatch(batch);
            table.delete(new OperationMachineKey(removed, machine));
            TableStats compactedStats = table.statsSnapshot();
            require(compactedStats.operationScratchCurrentBytes() == 7L
                            && compactedStats.operationScratchHighWaterBytes() == 7L,
                    "compaction fixture retains one-row selection and three remove marks");
            int[] selected = table.findByMachine(machine).filter(row -> row.indicatorReady())
                    .sorted(dispatchComparator()).limit(1).rowIndexes();
            require(selected.length == 1, "tie-break fixture selection");
            LongColumnView operationId =
                    table.candidateKeyOperationKeyOperationIdValueColumn();
            try {
                require(operationId.getLong(selected[0]) == 1L,
                        "identity tie-break must survive packed compaction");
            } finally {
                operationId.close();
            }
            TableStats topOneStats = table.statsSnapshot();
            require(topOneStats.operationScratchCurrentBytes()
                            == compactedStats.operationScratchCurrentBytes()
                            && topOneStats.operationScratchHighWaterBytes()
                            == compactedStats.operationScratchHighWaterBytes(),
                    "dynamic sorted limit(1) rowIndexes adds no full-sort scratch");
            UpdateResult updated = table.findByMachine(machine)
                    .filter(row -> row.indicatorReady())
                    .sorted(dispatchComparator()).limit(1)
                    .update(row -> row.setIndicatorReady(false));
            require(updated.matched() == 1L && updated.changed() == 1L,
                    "dynamic sorted limit(1) update affects one row");
            TableStats updateStats = table.statsSnapshot();
            require(updateStats.operationScratchCurrentBytes()
                            == topOneStats.operationScratchCurrentBytes()
                            && updateStats.operationScratchHighWaterBytes()
                            == topOneStats.operationScratchHighWaterBytes(),
                    "dynamic sorted limit(1) update adds no full-sort scratch");
            RemoveResult removedResult = table.findByMachine(machine)
                    .filter(row -> row.indicatorReady())
                    .sorted(dispatchComparator()).limit(1).remove();
            require(removedResult.matched() == 1L && removedResult.removed() == 1L,
                    "dynamic sorted limit(1) remove affects one row");
            TableStats removeStats = table.statsSnapshot();
            require(removeStats.operationScratchCurrentBytes()
                            == updateStats.operationScratchCurrentBytes()
                            && removeStats.operationScratchHighWaterBytes()
                            == updateStats.operationScratchHighWaterBytes(),
                    "dynamic sorted limit(1) remove adds no full-sort scratch");
        } finally {
            table.release();
        }
    }

    private static void addTieCandidate(MachineCandidateBatch batch,
                                        OperationKey operation,
                                        MachineId machine,
                                        SetupFamilyId family) {
        batch.addValues(new OperationMachineKey(operation, machine), family,
                5L, 0L, 0L, 5L, 3L, 0L, 5L, 5L, 3L, true);
    }

    private static long maximum(long left, long right) {
        return left >= right ? left : right;
    }

    private static void expectCode(String code, Action action) {
        try {
            action.run();
            throw new AssertionError("expected " + code);
        } catch (SomaRuntimeException failure) {
            require(code.equals(failure.code()),
                    "expected " + code + " but got " + failure.code());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface Action { void run(); }

    private static final class ReleaseFacts {
        final long releaseMinute;
        final SetupFamilyId setupFamily;
        ReleaseFacts(long releaseMinute, SetupFamilyId setupFamily) {
            this.releaseMinute = releaseMinute;
            this.setupFamily = setupFamily;
        }
    }

    private static final class DispatchResult {
        final long endMinute;
        final long setupMinutes;
        DispatchResult(long endMinute, long setupMinutes) {
            this.endMinute = endMinute;
            this.setupMinutes = setupMinutes;
        }
    }

    private static final class MachineSelection {
        final MachineId machineId;
        final long availableFromMinute;
        final boolean lastSetupFamilyPresent;
        final SetupFamilyId lastSetupFamily;

        MachineSelection(MachineId machineId, long availableFromMinute,
                         boolean lastSetupFamilyPresent,
                         SetupFamilyId lastSetupFamily) {
            this.machineId = machineId;
            this.availableFromMinute = availableFromMinute;
            this.lastSetupFamilyPresent = lastSetupFamilyPresent;
            this.lastSetupFamily = lastSetupFamily;
        }
    }

    private static final class CandidateSelection {
        final OperationKey operationKey;
        final SetupFamilyId targetSetupFamily;
        final long baseReadyMinute;
        final long processingMinutes;
        final long setupMinutes;

        CandidateSelection(OperationKey operationKey, SetupFamilyId targetSetupFamily,
                           long baseReadyMinute, long processingMinutes,
                           long setupMinutes) {
            this.operationKey = operationKey;
            this.targetSetupFamily = targetSetupFamily;
            this.baseReadyMinute = baseReadyMinute;
            this.processingMinutes = processingMinutes;
            this.setupMinutes = setupMinutes;
        }
    }

    public static final class ScenarioResult {
        public final int assignments;
        public final String schemaHash;
        public final long sidecarRebuilds;
        public final int apcRows;
        public final int aggregateHotLeafWidths;
        public final long hotLeafWorkingSetBytes;
        public final long reads;
        public final long mutations;
        public final int exports;
        ScenarioResult(int assignments, String schemaHash, long sidecarRebuilds,
                       int apcRows, int aggregateHotLeafWidths,
                       long hotLeafWorkingSetBytes, long reads, long mutations,
                       int exports) {
            this.assignments = assignments;
            this.schemaHash = schemaHash;
            this.sidecarRebuilds = sidecarRebuilds;
            this.apcRows = apcRows;
            this.aggregateHotLeafWidths = aggregateHotLeafWidths;
            this.hotLeafWorkingSetBytes = hotLeafWorkingSetBytes;
            this.reads = reads;
            this.mutations = mutations;
            this.exports = exports;
        }
    }
}
