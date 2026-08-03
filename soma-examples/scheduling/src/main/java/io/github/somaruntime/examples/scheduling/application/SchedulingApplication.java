package io.github.somaruntime.examples.scheduling.application;

import io.github.somaruntime.examples.scheduling.domain.SchedulingDecision;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaMetadata;
import io.github.somaruntime.examples.scheduling.soma.Soma;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/** Runnable qualification entry point for the scheduling scenario. */
public final class SchedulingApplication {
    private SchedulingApplication() {
    }

    public static void main(String[] args) {
        ForkJoinPool executor = new ForkJoinPool(2);
        try {
            Soma.configure(SomaConfiguration.builder()
                    .parallelExecutor(executor)
                    .build());
            SomaMetadata metadata = Soma._metadata();
            if (metadata.effectiveMemoryBudgetBytes() <= 0L) {
                throw new AssertionError("SOMA configuration was not published");
            }

            SchedulingScenario scenario = new SchedulingScenario();
            scenario.loadInitialState();
            if (scenario.readyCount() != 3L || scenario.readyCountParallel() != 3L) {
                throw new AssertionError("ready count");
            }
            List<SchedulingDecision> candidates = scenario.candidatesForMachine(1);
            if (candidates.size() != 2 || candidates.get(0).jobId() != 102L) {
                throw new AssertionError("priority candidate ordering");
            }
            scenario.markRunning(candidates.get(0).jobId());
            scenario.complete(candidates.get(0).jobId());
            if (scenario.readyCount() != 2L || scenario.remainingJobs() != 3L) {
                throw new AssertionError("state transition");
            }
            System.out.println("scheduling qualification PASS: candidates=" + candidates.size());
        } finally {
            executor.shutdown();
        }
    }
}
