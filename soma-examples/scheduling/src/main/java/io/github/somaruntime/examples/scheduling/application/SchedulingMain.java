package io.github.somaruntime.examples.scheduling.application;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.examples.scheduling.Job;
import io.github.somaruntime.examples.scheduling.JobTable;
import io.github.somaruntime.examples.scheduling.MachineState;
import io.github.somaruntime.examples.scheduling.MachineStateTable;
import io.github.somaruntime.examples.scheduling.ProcessingOption;
import io.github.somaruntime.examples.scheduling.ProcessingOptionTable;
import io.github.somaruntime.examples.scheduling.Soma;
import io.github.somaruntime.examples.scheduling.SomaGroup;
import io.github.somaruntime.examples.scheduling.schema.JobStatus;
import java.util.Optional;

public final class SchedulingMain {
    private SchedulingMain() {}

    public static void main(String[] args) {
        Soma.configure(SomaConfiguration.builder()
                .memoryBudgetBytes(512L << 20)
                .compression(SomaCompression.AUTO)
                .build());
        SomaGroup group = Soma.createGroup();
        JobTable jobs = group.jobTable();
        MachineStateTable machines = group.machineStateTable();
        ProcessingOptionTable options = group.processingOptionTable();

        jobs.reserve(8);
        machines.reserve(8);
        options.reserve(16);
        jobs.add(new Job(101L, JobStatus.PENDING, 10L, 80L));
        machines.add(new MachineState(7L, 1L, 12L, true));
        machines.add(new MachineState(8L, 1L, 30L, true));
        options.add(new ProcessingOption(1L, 101L, 7L, 20L, 3L, true));
        options.add(new ProcessingOption(2L, 101L, 8L, 5L, 2L, true));

        SchedulingService service = new SchedulingService(jobs, machines, options);
        Optional<SchedulingDecision> decision = service.decide(101L);
        require(decision.isPresent(), "a feasible scheduling decision is required");
        require(decision.get().machineId() == 7L
                        && decision.get().completionMinute() == 35L,
                "earliest completion decision");
        service.publish(decision.get());
        require(jobs.get(101L).status() == JobStatus.SCHEDULED,
                "Job status publication");
        require(machines.get(7L).availableMinute() == 35L,
                "Machine state publication");
        require(options.join(machines)
                        .on(options.machineId, machines.machineId)
                        .inner()
                        .filter(options.jobId.eq(101L))
                        ._explain().contains("predicatePushdown"),
                "explain exposes optimizer decision");
        System.out.println("scheduling-reference: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
