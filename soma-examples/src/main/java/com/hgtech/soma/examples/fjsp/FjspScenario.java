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

            Machine selectedMachine = machines.byAvailableTime().firstOrThrow();
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
            return new ScenarioResult(exported.size(), frontier.runtimePlan().schemaHash(),
                    stats.sidecarRebuildCount());
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
                facts[0] = new ReleaseFacts(row.releaseMinute(), row.setupFamily());
            }
        });
        require(facts[0] != null, "indexed operation release facts");
        OperationRuntimeState runtime = states.fetch(key);
        CandidateMachineDefinitionTable candidates = definitions.candidateMachines(key);
        MachineCandidateBatch batch = new MachineCandidateBatch(candidates.size());
        candidates.forEach(candidate -> {
            long baseReady = maximum(facts[0].releaseMinute,
                    maximum(runtime.jobReadyMinute, runtime.materialReadyMinute));
            batch.addValues(new OperationMachineKey(key, candidate.machineId()),
                    facts[0].setupFamily, facts[0].releaseMinute,
                    runtime.jobReadyMinute, runtime.materialReadyMinute, baseReady,
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
        Machine selectedMachine = machines.byAvailableTime().firstOrThrow();
        UpdateResult refreshed = frontier.findByMachine(selectedMachine.machineId)
                .update(row -> {
                    long setup = selectedMachine.lastSetupFamily == null ? 0L
                            : setupTimes.fetch(new SetupTimeKey(selectedMachine.machineId,
                                    new SetupFamilyPair(selectedMachine.lastSetupFamily,
                                            row.targetSetupFamily()))).setupMinutes;
                    long ready = maximum(selectedMachine.availableFromMinute,
                            row.baseReadyMinute());
                    row.setSetupMinutes(setup);
                    row.setEffectiveReadyMinute(ready);
                    row.setFcfsValue(row.operationReleaseMinute());
                    row.setSptValue(row.processingMinutes());
                    row.setIndicatorReady(true);
                });
        require(refreshed.changed() > 0L, "grouped indicator update");
        MachineCandidate chosen = frontier.findByMachine(selectedMachine.machineId)
                .filter(row -> row.indicatorReady())
                .sorted(dispatchComparator()).firstOrThrow();
        require(chosen.candidateKey.operationKey.equals(expectedOperation),
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
            return Long.compare(left.sptValue(), right.sptValue());
        };
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

    public static final class ScenarioResult {
        public final int assignments;
        public final String schemaHash;
        public final long sidecarRebuilds;
        ScenarioResult(int assignments, String schemaHash, long sidecarRebuilds) {
            this.assignments = assignments;
            this.schemaHash = schemaHash;
            this.sidecarRebuilds = sidecarRebuilds;
        }
    }
}
