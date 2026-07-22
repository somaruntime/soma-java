package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.examples.fjsp.schema.JobId;
import com.hgtech.soma.examples.fjsp.schema.MachineId;
import com.hgtech.soma.examples.fjsp.schema.OperationId;
import com.hgtech.soma.examples.fjsp.schema.OperationKey;
import com.hgtech.soma.examples.fjsp.schema.OperationMachineKey;
import com.hgtech.soma.examples.fjsp.schema.SetupFamilyId;
import com.hgtech.soma.examples.fjsp.schema.generated.CandidateMachineDefinitionTable;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateBatch;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.UpdateResult;

/**
 * released operation-machine candidates 的 SOMA frontier 访问边界。
 */
final class FjspCandidateFrontier {
    private final FjspInstance instance;
    private final FcfsSptDispatchRule dispatchRule;
    private final MachineCandidateBatch releaseBatch;
    private final Candidate selectedCandidate = new Candidate();

    FjspCandidateFrontier(
        FjspInstance instance, FcfsSptDispatchRule dispatchRule) {
        this.instance = instance;
        this.dispatchRule = dispatchRule;
        this.releaseBatch = new MachineCandidateBatch(
            instance.maximumCandidatesPerOperation);
    }

    void release(OperationKey operation, FjspReleasedMachines releasedMachines) {
        if (releasedMachines == null) {
            throw new NullPointerException("releasedMachines");
        }
        releasedMachines.reset();
        int definitionRow = instance.definitions.requireIndex(operation);
        long releaseMinute;
        long setupFamily;
        LongColumnView releases = instance.definitions.releaseMinuteColumn();
        LongColumnView families = instance.definitions.setupFamilyValueColumn();
        try {
            releaseMinute = releases.getLong(definitionRow);
            setupFamily = families.getLong(definitionRow);
        } finally {
            families.close();
            releases.close();
        }

        int stateRow = instance.operationStates.requireIndex(operation);
        long jobReady;
        long materialReady;
        LongColumnView jobReadyMinutes =
            instance.operationStates.jobReadyMinuteColumn();
        LongColumnView materialReadyMinutes =
            instance.operationStates.materialReadyMinuteColumn();
        try {
            jobReady = jobReadyMinutes.getLong(stateRow);
            materialReady = materialReadyMinutes.getLong(stateRow);
        } finally {
            materialReadyMinutes.close();
            jobReadyMinutes.close();
        }

        CandidateMachineDefinitionTable candidates =
            instance.definitions.candidateMachines(operation);
        releaseBatch.clear();
        final long baseReady = maximum(
            releaseMinute, maximum(jobReady, materialReady));
        final SetupFamilyId targetFamily = new SetupFamilyId(setupFamily);
        candidates.forEach(candidate -> {
            MachineId machineId = new MachineId(candidate.machineIdValue());
            releaseBatch.addValues(new OperationMachineKey(operation, machineId),
                targetFamily, releaseMinute, jobReady, materialReady, baseReady,
                candidate.processingMinutes(), 0L, baseReady, 0L, 0L, false);
            releasedMachines.add(machineId.value);
        });
        // 外部heap只能在frontier已经完整发布后由solver刷新。
        instance.frontier.addBatch(releaseBatch);
    }

    Candidate select(MachineId machineId, long availableFromMinute,
                     boolean lastFamilyPresent, long lastFamily) {
        refresh(machineId, availableFromMinute, lastFamilyPresent, lastFamily);
        int selectedIndex = instance.frontier.scanByMachine(machineId)
            .filter(row -> row.indicatorReady())
            .sorted(dispatchRule.comparator()).requireIndex();
        return read(selectedIndex);
    }

    private void refresh(MachineId machineId, long availableFromMinute,
                         boolean lastFamilyPresent, long lastFamily) {
        LongColumnView setupMinutes = instance.setupTimes.setupMinutesColumn();
        UpdateResult refreshed;
        try {
            refreshed = instance.frontier.scanByMachine(machineId).update(row -> {
                long setup = lastFamilyPresent
                    ? setupMinutes.getLong(instance.setupTimes.requireIndex(
                    machineId.value, lastFamily, row.targetSetupFamilyValue()))
                    : 0L;
                row.setSetupMinutes(setup);
                row.setEffectiveReadyMinute(maximum(
                    availableFromMinute, row.baseReadyMinute()));
                row.setFcfsValue(maximum(
                    availableFromMinute, row.baseReadyMinute()));
                row.setSptValue(Math.addExact(
                    setup, row.processingMinutes()));
                row.setIndicatorReady(true);
            });
        } finally {
            setupMinutes.close();
        }
        require(refreshed.matched() > 0L,
            "selected machine must refresh at least one candidate");
    }

    private Candidate read(int row) {
        LongColumnView jobIds =
            instance.frontier.candidateKeyOperationKeyJobIdValueColumn();
        LongColumnView operationIds =
            instance.frontier.candidateKeyOperationKeyOperationIdValueColumn();
        LongColumnView targetFamilies =
            instance.frontier.targetSetupFamilyValueColumn();
        LongColumnView baseReady = instance.frontier.baseReadyMinuteColumn();
        LongColumnView processing = instance.frontier.processingMinutesColumn();
        LongColumnView setup = instance.frontier.setupMinutesColumn();
        try {
            long jobId = jobIds.getLong(row);
            long operationId = operationIds.getLong(row);
            selectedCandidate.jobId = jobId;
            selectedCandidate.operationId = operationId;
            selectedCandidate.operationKey =
                new OperationKey(new JobId(jobId), new OperationId(operationId));
            selectedCandidate.targetSetupFamily =
                new SetupFamilyId(targetFamilies.getLong(row));
            selectedCandidate.baseReadyMinute = baseReady.getLong(row);
            selectedCandidate.processingMinutes = processing.getLong(row);
            selectedCandidate.setupMinutes = setup.getLong(row);
            return selectedCandidate;
        } finally {
            setup.close();
            processing.close();
            baseReady.close();
            targetFamilies.close();
            operationIds.close();
            jobIds.close();
        }
    }

    private static long maximum(long left, long right) {
        return left >= right ? left : right;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    static final class Candidate {
        long jobId;
        long operationId;
        OperationKey operationKey;
        SetupFamilyId targetSetupFamily;
        long baseReadyMinute;
        long processingMinutes;
        long setupMinutes;

        private Candidate() {
        }
    }
}
