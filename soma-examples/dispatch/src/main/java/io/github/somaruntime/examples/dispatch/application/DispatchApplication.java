package io.github.somaruntime.examples.dispatch.application;

import io.github.somaruntime.examples.dispatch.domain.DispatchDecision;
import io.github.somaruntime.examples.dispatch.soma.Soma;
import io.github.somaruntime.soma.SomaConfiguration;
import java.util.concurrent.ForkJoinPool;

/** Runnable qualification entry point for the real-time dispatch scenario. */
public final class DispatchApplication {
    private DispatchApplication() {
    }

    public static void main(String[] args) {
        ForkJoinPool executor = new ForkJoinPool(2);
        try {
            Soma.configure(SomaConfiguration.builder()
                    .parallelExecutor(executor)
                    .build());
            DispatchScenario scenario = new DispatchScenario();
            scenario.loadInitialState();
            if (scenario.readyCount() != 3L || scenario.readyCountParallel() != 3L) {
                throw new AssertionError("pending ready count");
            }
            DispatchDecision decision = scenario.choose(4);
            if (decision == null || decision.requestId() != 201L) {
                throw new AssertionError("priority dispatch choice");
            }
            scenario.assign(decision);
            scenario.complete(decision);
            if (scenario.readyCount() != 2L) {
                throw new AssertionError("dispatch state transition");
            }
            System.out.println("dispatch qualification PASS: request=" + decision.requestId());
        } finally {
            executor.shutdown();
        }
    }
}
