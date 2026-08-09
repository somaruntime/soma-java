package io.github.somaruntime.examples.realtimedispatch.application;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.examples.realtimedispatch.EligibleMachine;
import io.github.somaruntime.examples.realtimedispatch.EligibleMachineTable;
import io.github.somaruntime.examples.realtimedispatch.MachineRuntime;
import io.github.somaruntime.examples.realtimedispatch.MachineRuntimeTable;
import io.github.somaruntime.examples.realtimedispatch.PendingJob;
import io.github.somaruntime.examples.realtimedispatch.PendingJobTable;
import io.github.somaruntime.examples.realtimedispatch.Soma;
import io.github.somaruntime.examples.realtimedispatch.SomaGroup;
import io.github.somaruntime.examples.realtimedispatch.domain.DispatchPayload;
import io.github.somaruntime.examples.realtimedispatch.schema.PendingStatus;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

public final class RealTimeDispatchMain {
    private RealTimeDispatchMain() {}

    public static void main(String[] args) {
        ForkJoinPool executor = new ForkJoinPool(4);
        try {
            Soma.configure(SomaConfiguration.builder()
                    .memoryBudgetBytes(512L << 20)
                    .parallelExecutor(executor)
                    .compression(SomaCompression.AUTO)
                    .build());
            SomaGroup group = Soma.createGroup();
            PendingJobTable pending = group.pendingJobTable();
            EligibleMachineTable eligible = group.eligibleMachineTable();
            MachineRuntimeTable machines = group.machineRuntimeTable();
            pending.add(new PendingJob(
                    41L, PendingStatus.PENDING, "Q-A", 100L, 200L,
                    new DispatchPayload("request-41")));
            machines.add(new MachineRuntime(3L, "Z-1", 110L, 5L, true));
            machines.add(new MachineRuntime(4L, "Z-1", 120L, 2L, true));
            eligible.add(new EligibleMachine(1L, 41L, 3L, 20L, 2));
            eligible.add(new EligibleMachine(2L, 41L, 4L, 5L, 1));

            AtomicReference<String> dispatched = new AtomicReference<String>();
            DispatchService service = new DispatchService(
                    pending,
                    eligible,
                    machines,
                    (decision, payload) -> dispatched.set(payload.requestId()));
            DispatchDecision decision = service.decide(41L).get();
            require(decision.machineId() == 4L && decision.completionMinute() == 125L,
                    "deterministic parallel decision");
            require(service.dispatch(41L), "dispatch succeeds");
            require("request-41".equals(dispatched.get()), "external effect boundary");
            require(!pending.find(41L).isPresent(), "pending work removed after dispatch");
            require(machines.get(4L).availableMinute() == 125L,
                    "machine runtime published");
            System.out.println("real-time-dispatch-reference: PASS");
        } finally {
            executor.shutdown();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
