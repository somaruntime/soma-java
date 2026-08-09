package io.github.somaruntime.examples.scheduling.application;

import io.github.somaruntime.examples.scheduling.Job;
import io.github.somaruntime.examples.scheduling.JobTable;
import io.github.somaruntime.examples.scheduling.MachineState;
import io.github.somaruntime.examples.scheduling.MachineStateTable;
import io.github.somaruntime.examples.scheduling.ProcessingOptionTable;
import io.github.somaruntime.examples.scheduling.schema.JobStatus;
import java.util.Optional;

public final class SchedulingService {
    private final JobTable jobs;
    private final MachineStateTable machines;
    private final ProcessingOptionTable options;

    public SchedulingService(
            JobTable jobs,
            MachineStateTable machines,
            ProcessingOptionTable options) {
        this.jobs = jobs;
        this.machines = machines;
        this.options = options;
    }

    public Optional<SchedulingDecision> decide(long jobId) {
        Optional<Job> job = jobs.find(jobId);
        if (!job.isPresent() || job.get().status() != JobStatus.PENDING) {
            return Optional.empty();
        }
        final long release = job.get().releaseMinute();
        return options.join(machines)
                .on(options.machineId, machines.machineId)
                .inner()
                .filter(options.jobId.eq(jobId))
                .filter(options.enabled.eq(true))
                .filter(machines.enabled.eq(true))
                .map(pair -> {
                    long start = Math.max(release, pair.right().availableMinute());
                    long duration = Math.addExact(
                            pair.left().processingMinutes(),
                            pair.left().setupMinutes());
                    return new SchedulingDecision(
                            jobId,
                            pair.left().optionId(),
                            pair.right().machineId(),
                            start,
                            Math.addExact(start, duration));
                })
                .top(1L, SchedulingDecision.EARLIEST_COMPLETION)
                .findFirst();
    }

    public void publish(SchedulingDecision decision) {
        MachineState previous = machines.get(decision.machineId());
        machines.update(decision.machineId(), editor ->
                editor.availableMinute(decision.completionMinute()));
        try {
            jobs.update(decision.jobId(), editor -> editor.status(JobStatus.SCHEDULED));
        } catch (RuntimeException failure) {
            machines.update(decision.machineId(), editor ->
                    editor.availableMinute(previous.availableMinute()));
            throw failure;
        }
    }
}
