package io.github.somaruntime.examples.realtimedispatch.application;

import io.github.somaruntime.examples.realtimedispatch.EligibleMachineTable;
import io.github.somaruntime.examples.realtimedispatch.MachineRuntimeTable;
import io.github.somaruntime.examples.realtimedispatch.PendingJob;
import io.github.somaruntime.examples.realtimedispatch.PendingJobTable;
import io.github.somaruntime.examples.realtimedispatch.schema.PendingStatus;
import java.util.Optional;

public final class DispatchService {
    private final PendingJobTable pendingJobs;
    private final EligibleMachineTable eligibleMachines;
    private final MachineRuntimeTable machines;
    private final DispatchGateway gateway;

    public DispatchService(
            PendingJobTable pendingJobs,
            EligibleMachineTable eligibleMachines,
            MachineRuntimeTable machines,
            DispatchGateway gateway) {
        this.pendingJobs = pendingJobs;
        this.eligibleMachines = eligibleMachines;
        this.machines = machines;
        this.gateway = gateway;
    }

    public Optional<DispatchDecision> decide(long jobId) {
        Optional<PendingJob> pending = pendingJobs.byStatus(PendingStatus.PENDING)
                .filter(pendingJobs.jobId.eq(jobId))
                .findFirst();
        if (!pending.isPresent()) return Optional.empty();
        final long release = pending.get().releaseMinute();
        return eligibleMachines.join(machines)
                .on(eligibleMachines.machineId, machines.machineId)
                .inner()
                .filter(eligibleMachines.jobId.eq(jobId))
                .filter(machines.enabled.eq(true))
                .parallel()
                .map(pair -> {
                    long start = Math.max(release, pair.right().availableMinute());
                    long completion = Math.addExact(start, pair.left().processingMinutes());
                    return new DispatchDecision(
                            jobId,
                            pair.right().machineId(),
                            completion,
                            pair.left().priority());
                })
                .top(1L, DispatchDecision.BEST)
                .findFirst();
    }

    public boolean dispatch(long jobId) {
        Optional<PendingJob> pending = pendingJobs.find(jobId);
        Optional<DispatchDecision> decision = decide(jobId);
        if (!pending.isPresent() || !decision.isPresent()) return false;
        gateway.dispatch(decision.get(), pending.get().payload());
        machines.update(decision.get().machineId(), editor -> {
            long previousAvailable = editor.availableMinute();
            editor.availableMinute(decision.get().completionMinute());
            editor.workloadMinutes(Math.addExact(
                    editor.workloadMinutes(),
                    decision.get().completionMinute() - previousAvailable));
        });
        pendingJobs.remove(jobId);
        return true;
    }
}
