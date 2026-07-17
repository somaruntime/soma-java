package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.examples.fjsp.schema.JobId;
import com.hgtech.soma.examples.fjsp.schema.MachineId;
import com.hgtech.soma.examples.fjsp.schema.OperationId;
import com.hgtech.soma.examples.fjsp.schema.OperationKey;
import com.hgtech.soma.examples.fjsp.schema.OperationMachineKey;
import com.hgtech.soma.examples.fjsp.schema.SetupFamilyId;
import com.hgtech.soma.examples.fjsp.schema.generated.CandidateMachineDefinitionTable;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateBatch;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.UpdateResult;

/**
 * released operation-machine candidates 的 SOMA frontier 访问边界。
 */
final class FjspCandidateFrontier {
    private final FjspInstance instance;
    private final FcfsSptDispatchRule dispatchRule;

    FjspCandidateFrontier(
        FjspInstance instance, FcfsSptDispatchRule dispatchRule) {
        this.instance = instance;
        this.dispatchRule = dispatchRule;
    }

    void release(OperationKey operation) {
        int definitionRow = instance.definitions.rowIndexOf(operation);
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

        int stateRow = instance.operationStates.rowIndexOf(operation);
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
        MachineCandidateBatch batch = new MachineCandidateBatch(candidates.size());
        final long baseReady = maximum(
            releaseMinute, maximum(jobReady, materialReady));
        final SetupFamilyId targetFamily = new SetupFamilyId(setupFamily);
        candidates.forEach(candidate -> batch.addValues(
            new OperationMachineKey(operation,
                new MachineId(candidate.machineIdValue())),
            targetFamily, releaseMinute, jobReady, materialReady, baseReady,
            candidate.processingMinutes(), 0L, baseReady, releaseMinute,
            candidate.processingMinutes(), false));
        instance.frontier.addBatch(batch);
    }

    Candidate select(MachineId machineId, long availableFromMinute,
                     boolean lastFamilyPresent, long lastFamily) {
        refresh(machineId, availableFromMinute, lastFamilyPresent, lastFamily);
        IndexSnapshot rows = instance.frontier.findByMachine(machineId)
            .filter(row -> row.indicatorReady())
            .sorted(dispatchRule.comparator()).limit(1).rowIndexes();
        require(rows.size() == 1, "dispatch requires one candidate");
        return read(rows.indexAt(0));
    }

    private void refresh(MachineId machineId, long availableFromMinute,
                         boolean lastFamilyPresent, long lastFamily) {
        LongColumnView setupMinutes = instance.setupTimes.setupMinutesColumn();
        UpdateResult refreshed;
        try {
            refreshed = instance.frontier.findByMachine(machineId).update(row -> {
                long setup = lastFamilyPresent
                    ? setupMinutes.getLong(instance.setupTimes.rowIndexOf(
                    machineId.value, lastFamily, row.targetSetupFamilyValue()))
                    : 0L;
                row.setSetupMinutes(setup);
                row.setEffectiveReadyMinute(maximum(
                    availableFromMinute, row.baseReadyMinute()));
                row.setFcfsValue(row.operationReleaseMinute());
                row.setSptValue(row.processingMinutes());
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
            return new Candidate(jobId, operationId,
                new OperationKey(new JobId(jobId), new OperationId(operationId)),
                new SetupFamilyId(targetFamilies.getLong(row)),
                baseReady.getLong(row), processing.getLong(row), setup.getLong(row));
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
        final long jobId;
        final long operationId;
        final OperationKey operationKey;
        final SetupFamilyId targetSetupFamily;
        final long baseReadyMinute;
        final long processingMinutes;
        final long setupMinutes;

        Candidate(long jobId, long operationId, OperationKey operationKey,
                  SetupFamilyId targetSetupFamily, long baseReadyMinute,
                  long processingMinutes, long setupMinutes) {
            this.jobId = jobId;
            this.operationId = operationId;
            this.operationKey = operationKey;
            this.targetSetupFamily = targetSetupFamily;
            this.baseReadyMinute = baseReadyMinute;
            this.processingMinutes = processingMinutes;
            this.setupMinutes = setupMinutes;
        }
    }
}
